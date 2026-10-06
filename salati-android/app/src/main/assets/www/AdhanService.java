package com.momen.salati;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class AdhanService extends Service {
    static final String ACTION_STOP = "com.momen.salati.STOP_ADHAN";
    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String name = intent != null ? intent.getStringExtra("name") : null;
        if (name == null) name = "";
        Notif.ensure(this);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notif.ID_ADHAN, Notif.adhan(this, name), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(Notif.ID_ADHAN, Notif.adhan(this, name));
        }
        play();
        return START_NOT_STICKY;
    }

    private void play() {
        release();
        String saved = Alarms.prefs(this).getString("adhanUri", null);
        boolean ok = false;
        if (saved != null) ok = start(Uri.parse(saved), null);           // user's own file
        if (!ok) ok = start(null, "www/adhan.mp3");                       // adhan bundled with the app
        boolean longSound = ok;
        if (!ok) {                                                        // last resort: phone alarm tone
            Uri tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (tone == null) tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            start(tone, null);
        }
        // An alarm tone may loop forever; cap it. A real adhan stops on its own.
        handler.postDelayed(this::stopSelf, longSound ? 6 * 60_000L : 45_000L);
    }

    private boolean start(Uri uri, String asset) {
        if (uri == null && asset == null) return false;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (asset != null) {
                try (AssetFileDescriptor fd = getAssets().openFd(asset)) {
                    player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                }
            } else {
                player.setDataSource(this, uri);
            }
            player.setOnCompletionListener(mp -> stopSelf());
            player.prepare();
            player.start();
            return true;
        } catch (Exception e) {
            release();
            return false;
        }
    }

    private void release() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
