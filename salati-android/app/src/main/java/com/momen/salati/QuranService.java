package com.momen.salati;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Keeps the app process alive while a recitation plays in the WebView, so it continues with the screen off. */
public class QuranService extends Service {
    static volatile boolean running = false;
    private PowerManager.WakeLock lock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent != null ? intent.getStringExtra("title") : null;
        Notif.ensure(this);
        if (!running) {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notif.ID_QURAN, Notif.quran(this, title), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(Notif.ID_QURAN, Notif.quran(this, title));
            }
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null) {
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "salati:quran");
                lock.acquire(4 * 60 * 60_000L);
            }
            running = true;
        } else {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(Notif.ID_QURAN, Notif.quran(this, title));
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (lock != null && lock.isHeld()) lock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
