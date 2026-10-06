package com.momen.salati;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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
        boolean custom = saved != null;
        Uri uri = custom ? Uri.parse(saved) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (!start(uri) && custom) {
            custom = false;
            start(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        }
        // A system alarm tone may loop forever; cap it. A real adhan file stops on its own.
        handler.postDelayed(this::stopSelf, custom ? 6 * 60_000L : 45_000L);
    }

    private boolean start(Uri uri) {
        if (uri == null) return false;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
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
