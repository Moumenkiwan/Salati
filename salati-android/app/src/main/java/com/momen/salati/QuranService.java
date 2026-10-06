package com.momen.salati;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Plays Yasser Al-Dosari recitations natively, with a media notification and
 * lock-screen controls: surah name, progress bar, play/pause, previous, next, restart.
 */
public class QuranService extends Service {
    static final String ACTION_PLAY = "com.momen.salati.Q_PLAY";       // extra "i" = surah index 0..113
    static final String ACTION_TOGGLE = "com.momen.salati.Q_TOGGLE";
    static final String ACTION_PAUSE = "com.momen.salati.Q_PAUSE";
    static final String ACTION_NEXT = "com.momen.salati.Q_NEXT";
    static final String ACTION_PREV = "com.momen.salati.Q_PREV";
    static final String ACTION_RESTART = "com.momen.salati.Q_RESTART";
    static final String ACTION_SEEK = "com.momen.salati.Q_SEEK";       // extra "ms"
    static final String ACTION_CLOSE = "com.momen.salati.Q_CLOSE";

    private static final String SERVER = "https://server11.mp3quran.net/yasser/";

    /** Receives state updates (JSON) so the open app can mirror the player. */
    interface Listener { void onState(String json); }
    static volatile Listener listener;
    static volatile boolean running = false;
    private static volatile String lastState = null;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private WifiManager.WifiLock wifiLock;
    private int track = 0;
    private boolean prepared = false;
    private boolean wantPlay = false;
    private boolean inForeground = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            publish();
            if (player != null && prepared && player.isPlaying()) handler.postDelayed(this, 1000);
        }
    };

    static String[] names(Context c) {
        String[] n = new String[114];
        try {
            JSONArray a = new JSONArray(Alarms.prefs(c).getString("surahNames", "[]"));
            for (int i = 0; i < 114; i++) n[i] = i < a.length() ? a.getString(i) : String.valueOf(i + 1);
        } catch (Exception e) {
            for (int i = 0; i < 114; i++) n[i] = String.valueOf(i + 1);
        }
        return n;
    }

    static String lastState() { return lastState; }

    @Override
    public void onCreate() {
        super.onCreate();
        Notif.ensure(this);
        audio = getSystemService(AudioManager.class);
        session = new MediaSession(this, "salati-quran");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { close(); }
            @Override public void onSkipToNext() { play(track + 1); }
            @Override public void onSkipToPrevious() { play(track - 1); }
            @Override public void onSeekTo(long pos) { seek(pos); }
            @Override public void onCustomAction(String action, android.os.Bundle extras) {
                if (ACTION_RESTART.equals(action)) seek(0);
                else if (ACTION_CLOSE.equals(action)) close();
            }
        });
        session.setActive(true);
        WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
        if (wm != null) wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "salati:quran");
        track = Alarms.prefs(this).getInt("quranTrack", 0);
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_PLAY.equals(a)) play(intent.getIntExtra("i", track));
        else if (ACTION_TOGGLE.equals(a)) { if (player != null && prepared && player.isPlaying()) pause(); else resume(); }
        else if (ACTION_PAUSE.equals(a)) pause();
        else if (ACTION_NEXT.equals(a)) play(track + 1);
        else if (ACTION_PREV.equals(a)) play(track - 1);
        else if (ACTION_RESTART.equals(a)) seek(0);
        else if (ACTION_SEEK.equals(a)) seek(intent.getLongExtra("ms", 0));
        else if (ACTION_CLOSE.equals(a)) close();
        return START_NOT_STICKY;
    }

    // ---------- playback ----------

    private void play(int i) {
        track = ((i % 114) + 114) % 114;
        Alarms.prefs(this).edit().putInt("quranTrack", track).apply();
        releasePlayer();
        prepared = false;
        wantPlay = true;
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(mp -> {
            prepared = true;
            if (wantPlay) startPlayback(); else update();
        });
        player.setOnCompletionListener(mp -> {
            if (Alarms.prefs(this).getBoolean("autoNext", true) && track < 113) play(track + 1);
            else { wantPlay = false; update(); }
        });
        player.setOnErrorListener((mp, what, extra) -> {
            prepared = false;
            wantPlay = false;
            update();
            return true;
        });
        try {
            player.setDataSource(SERVER + String.format(java.util.Locale.ROOT, "%03d", track + 1) + ".mp3");
            player.prepareAsync();
        } catch (Exception e) {
            wantPlay = false;
        }
        update();
    }

    private void startPlayback() {
        if (player == null || !prepared) return;
        if (!requestFocus()) return;
        if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        player.start();
        wantPlay = true;
        update();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void resume() {
        if (player == null) { play(track); return; }
        wantPlay = true;
        if (prepared) startPlayback(); else update();
    }

    private void pause() {
        wantPlay = false;
        if (player != null && prepared && player.isPlaying()) player.pause();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        update();
    }

    private void seek(long ms) {
        if (player != null && prepared) {
            player.seekTo((int) Math.max(0, Math.min(ms, player.getDuration())));
            update();
        } else if (ms == 0) {
            play(track);
        }
    }

    private void close() {
        pause();
        stopSelf();
    }

    private boolean requestFocus() {
        if (audio == null) return true;
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause();
                    }, handler)
                    .build();
        }
        return audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void releasePlayer() {
        handler.removeCallbacks(ticker);
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        prepared = false;
    }

    // ---------- session, notification, app mirror ----------

    private boolean isPlaying() {
        return player != null && prepared && player.isPlaying();
    }

    private long position() {
        try { return player != null && prepared ? player.getCurrentPosition() : 0; } catch (Exception e) { return 0; }
    }

    private long duration() {
        try { return player != null && prepared ? player.getDuration() : 0; } catch (Exception e) { return 0; }
    }

    private String title() {
        return "سورة " + names(this)[track];
    }

    private void update() {
        boolean playing = isPlaying();
        boolean loading = wantPlay && !prepared;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "ياسر الدوسري")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "القرآن الكريم")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration())
                .build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .addCustomAction(new PlaybackState.CustomAction.Builder(ACTION_RESTART, "من البداية", android.R.drawable.ic_menu_rotate).build())
                .addCustomAction(new PlaybackState.CustomAction.Builder(ACTION_CLOSE, "إغلاق", android.R.drawable.ic_menu_close_clear_cancel).build())
                .setState(loading ? PlaybackState.STATE_BUFFERING : playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        position(), playing ? 1f : 0f)
                .build());
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && inForeground) nm.notify(Notif.ID_QURAN, buildNotification());
        publish();
    }

    private void publish() {
        try {
            JSONObject o = new JSONObject();
            o.put("i", track);
            o.put("playing", isPlaying());
            o.put("loading", wantPlay && !prepared);
            o.put("pos", position());
            o.put("dur", duration());
            String s = o.toString();
            lastState = s;
            Listener l = listener;
            if (l != null) l.onState(s);
        } catch (Exception ignored) {}
    }

    private PendingIntent action(String a, int rc) {
        Intent i = new Intent(this, QuranService.class).setAction(a);
        return PendingIntent.getService(this, rc, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        boolean playing = isPlaying() || (wantPlay && !prepared);
        Notification.Builder b = new Notification.Builder(this, Notif.CH_QURAN)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title())
                .setContentText("ياسر الدوسري")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(Notif.openApp(this))
                .setDeleteIntent(action(ACTION_CLOSE, 15))
                .setOngoing(playing)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "السابقة", action(ACTION_PREV, 11)).build())
                .addAction(new Notification.Action.Builder(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "إيقاف مؤقت" : "تشغيل", action(ACTION_TOGGLE, 12)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "التالية", action(ACTION_NEXT, 13)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_rotate, "من البداية", action(ACTION_RESTART, 14)).build())
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private void goForeground() {
        if (inForeground) return;
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notif.ID_QURAN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(Notif.ID_QURAN, n);
        }
        inForeground = true;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (audio != null && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        session.setActive(false);
        session.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        try {
            JSONObject o = new JSONObject();
            o.put("i", track); o.put("playing", false); o.put("loading", false); o.put("pos", 0); o.put("dur", 0); o.put("closed", true);
            lastState = o.toString();
            Listener l = listener;
            if (l != null) l.onState(lastState);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    static void send(Context c, String action, Intent extras) {
        Intent i = extras != null ? extras : new Intent();
        i.setClass(c, QuranService.class).setAction(action);
        c.startForegroundService(i);
    }
}
