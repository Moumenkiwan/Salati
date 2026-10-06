package com.momen.salati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Home-screen widget: next prayer with a live countdown, plus today's five times. */
public class PrayerWidget extends AppWidgetProvider {
    private static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    private static final String[] NAMES_AR = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final String[] NAMES_EN = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final int[] NAME_IDS = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    private static final int[] TIME_IDS = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4};
    private static final int RC_REFRESH = 300;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        String a = intent.getAction();
        if (Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a) || Intent.ACTION_DATE_CHANGED.equals(a)) refresh(c);
    }

    @Override
    public void onDisabled(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(refreshIntent(c));
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, PrayerWidget.class));
        if (ids.length > 0) render(c, m, ids);
    }

    private static PendingIntent refreshIntent(Context c) {
        Intent i = new Intent(c, PrayerWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, m.getAppWidgetIds(new ComponentName(c, PrayerWidget.class)));
        return PendingIntent.getBroadcast(c, RC_REFRESH, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void render(Context c, AppWidgetManager m, int[] ids) {
        boolean en = Alarms.en(c);
        String[] NAMES = en ? NAMES_EN : NAMES_AR;
        RemoteViews v = new RemoteViews(c.getPackageName(), en ? R.layout.widget_prayer_en : R.layout.widget_prayer);
        v.setOnClickPendingIntent(R.id.root, Notif.openApp(c));
        long now = System.currentTimeMillis();
        long nextAt = 0;
        try {
            JSONObject data = new JSONObject(Alarms.prefs(c).getString("prayerData", "{}"));
            String city = data.optString("city", "");
            SimpleDateFormat f = new SimpleDateFormat("h:mm", Locale.ENGLISH);
            f.setTimeZone(TimeZone.getTimeZone(data.optString("tz", TimeZone.getDefault().getID())));
            JSONArray days = data.optJSONArray("days");
            JSONObject today = null, tomorrow = null;
            if (days != null) {
                for (int i = 0; i < days.length(); i++) {
                    JSONObject d = days.getJSONObject(i);
                    if (d.getLong("isha") >= now - 6 * 3600_000L && today == null) {
                        // the first day whose isha is not long past is "today"
                        today = d;
                        if (i + 1 < days.length()) tomorrow = days.getJSONObject(i + 1);
                    }
                }
            }
            if (today == null) {
                v.setTextViewText(R.id.city, en ? "Salati" : "صلاتي");
                v.setTextViewText(R.id.nextName, en ? "Open the app" : "افتح التطبيق");
                v.setTextViewText(R.id.nextAt, en ? "to load prayer times" : "لتحديث المواقيت");
            } else {
                int nextIdx = -1;
                for (int k = 0; k < 5; k++) {
                    long t = today.getLong(KEYS[k]);
                    v.setTextViewText(NAME_IDS[k], NAMES[k]);
                    v.setTextViewText(TIME_IDS[k], f.format(new Date(t)));
                    if (nextIdx < 0 && t > now) nextIdx = k;
                }
                String name;
                if (nextIdx >= 0) {
                    nextAt = today.getLong(KEYS[nextIdx]);
                    name = NAMES[nextIdx];
                } else if (tomorrow != null) {
                    nextAt = tomorrow.getLong("fajr");
                    name = NAMES[0];
                } else {
                    name = NAMES[0];
                }
                for (int k = 0; k < 5; k++) {
                    int color = k == nextIdx ? 0xFFE3A848 : 0xFFE4EEEA;
                    v.setTextColor(NAME_IDS[k], color);
                    v.setTextColor(TIME_IDS[k], color);
                }
                v.setTextViewText(R.id.city, city);
                v.setTextViewText(R.id.nextName, name);
                v.setTextViewText(R.id.nextAt, nextAt > 0 ? (en ? "at " : "الساعة ") + f.format(new Date(nextAt)) : "");
                if (nextAt > now) {
                    long base = SystemClock.elapsedRealtime() + (nextAt - now);
                    v.setChronometer(R.id.countdown, base, null, true);
                    if (Build.VERSION.SDK_INT >= 24) v.setChronometerCountDown(R.id.countdown, true);
                }
            }
        } catch (Exception e) {
            v.setTextViewText(R.id.nextName, en ? "Open the app" : "افتح التطبيق");
        }
        m.updateAppWidget(ids, v);

        // redraw right after the next prayer time so "next" moves on
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) {
            long at = nextAt > now ? nextAt + 2000 : now + 30 * 60_000L;
            am.set(AlarmManager.RTC, at, refreshIntent(c));
        }
    }
}
