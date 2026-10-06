package com.momen.salati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

final class Alarms {
    static final String PREFS = "salati";
    private static final int RC_ADHAN = 100;
    private static final int RC_ZIKR = 200;

    private Alarms() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when the user picked English inside the app. */
    static boolean en(Context c) {
        return "en".equals(prefs(c).getString("lang", "ar"));
    }

    /** Picks the Arabic or English text for the user's language. */
    static String L(Context c, String ar, String en) {
        return en(c) ? en : ar;
    }

    /** Schedules the next enabled adhan from the stored list [{n:name, t:epochMillis}, ...]. */
    static void scheduleNextAdhan(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent base = new Intent(c, AdhanReceiver.class);
        PendingIntent old = PendingIntent.getBroadcast(c, RC_ADHAN, base,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(old);

        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        String name = null;
        try {
            JSONArray a = new JSONArray(prefs(c).getString("adhanList", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long t = o.getLong("t");
                if (t > now + 1000 && t < best) {
                    best = t;
                    name = o.getString("n");
                }
            }
        } catch (Exception ignored) {
            return;
        }
        if (name == null) return;

        Intent i = new Intent(c, AdhanReceiver.class).putExtra("name", name);
        PendingIntent pi = PendingIntent.getBroadcast(c, RC_ADHAN, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
        }
    }

    static void scheduleZikr(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, RC_ZIKR, new Intent(c, ZikrReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("zikrOn", false)) return;
        long every = Math.max(15, p.getInt("zikrEvery", 30)) * 60_000L;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + every, every, pi);
    }
}
