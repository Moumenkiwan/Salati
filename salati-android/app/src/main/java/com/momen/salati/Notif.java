package com.momen.salati;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class Notif {
    static final String CH_ADHAN = "adhan";
    static final String CH_ZIKR = "zikr";
    static final String CH_QURAN = "quran";
    static final int ID_ADHAN = 1;
    static final int ID_QURAN = 2;
    static final int ID_ZIKR = 3;

    private Notif() {}

    static void ensure(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel a = new NotificationChannel(CH_ADHAN, "الأذان", NotificationManager.IMPORTANCE_HIGH);
        a.setSound(null, null);
        a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel z = new NotificationChannel(CH_ZIKR, "الأذكار", NotificationManager.IMPORTANCE_DEFAULT);
        z.setSound(null, null);
        z.enableVibration(false);
        z.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel q = new NotificationChannel(CH_QURAN, "تلاوة القرآن", NotificationManager.IMPORTANCE_LOW);
        q.setSound(null, null);
        q.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(z);
        nm.createNotificationChannel(q);
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static Notification adhan(Context c, String name) {
        Intent stop = new Intent(c, AdhanService.class).setAction(AdhanService.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(c, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, CH_ADHAN)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الآن موعد أذان " + name)
                .setContentText("اضغط إيقاف لإيقاف الأذان")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(c))
                .setOngoing(true)
                .addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "إيقاف", stopPi).build())
                .build();
    }

    static Notification quran(Context c, String title) {
        return new Notification.Builder(c, CH_QURAN)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title == null || title.isEmpty() ? "تلاوة القرآن" : title)
                .setContentText("ياسر الدوسري")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(c))
                .setOngoing(true)
                .build();
    }

    static Notification zikr(Context c, String text) {
        return new Notification.Builder(c, CH_ZIKR)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("ذكر")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(c))
                .setAutoCancel(true)
                .build();
    }
}
