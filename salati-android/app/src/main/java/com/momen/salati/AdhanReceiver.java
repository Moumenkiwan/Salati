package com.momen.salati;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String name = intent.getStringExtra("name");
        if (name == null) name = "";
        Notif.ensure(c);
        try {
            Intent s = new Intent(c, AdhanService.class).putExtra("name", name);
            c.startForegroundService(s);
        } catch (Exception e) {
            // Could not start the player (background limits): at least show the notification.
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(Notif.ID_ADHAN, Notif.adhan(c, name));
        }
        Alarms.scheduleNextAdhan(c);
    }
}
