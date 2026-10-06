package com.momen.salati;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.OutputStream;

/** Methods the web page calls through window.Android. */
public class Bridge {
    private final MainActivity act;

    Bridge(MainActivity act) { this.act = act; }

    @JavascriptInterface
    public boolean isNative() { return true; }

    @JavascriptInterface
    public void scheduleAdhan(String json) {
        Alarms.prefs(act).edit().putString("adhanList", json).apply();
        Alarms.scheduleNextAdhan(act);
    }

    @JavascriptInterface
    public void pickAdhan() { act.runOnUiThread(act::pickAdhan); }

    @JavascriptInterface
    public String adhanName() { return Alarms.prefs(act).getString("adhanName", ""); }

    @JavascriptInterface
    public void testAdhan() {
        Intent s = new Intent(act, AdhanService.class).putExtra("name", Alarms.L(act, "(تجربة)", "(test)"));
        act.startForegroundService(s);
    }

    @JavascriptInterface
    public void stopAdhan() {
        act.startService(new Intent(act, AdhanService.class).setAction(AdhanService.ACTION_STOP));
    }

    @JavascriptInterface
    public void setZikr(boolean on, int minutes) {
        Alarms.prefs(act).edit().putBoolean("zikrOn", on).putInt("zikrEvery", minutes).apply();
        Alarms.scheduleZikr(act);
    }

    @JavascriptInterface
    public void mediaState(boolean playing, String title) { /* playback is native now */ }

    // ----- Quran player (native, with lock-screen controls) -----
    @JavascriptInterface
    public void quranInit(String namesJson) { Alarms.prefs(act).edit().putString("surahNames", namesJson).apply(); }

    @JavascriptInterface
    public void quranPlay(int i) { QuranService.send(act, QuranService.ACTION_PLAY, new Intent().putExtra("i", i)); }

    @JavascriptInterface
    public void quranToggle(int i) {
        if (QuranService.running) QuranService.send(act, QuranService.ACTION_TOGGLE, null);
        else quranPlay(i);
    }

    @JavascriptInterface
    public void quranRestart() { if (QuranService.running) QuranService.send(act, QuranService.ACTION_RESTART, null); }

    @JavascriptInterface
    public void quranSeek(double ms) {
        if (QuranService.running) QuranService.send(act, QuranService.ACTION_SEEK, new Intent().putExtra("ms", (long) ms));
    }

    @JavascriptInterface
    public void quranAutoNext(boolean on) { Alarms.prefs(act).edit().putBoolean("autoNext", on).apply(); }

    @JavascriptInterface
    public String quranState() { String s = QuranService.lastState(); return s == null ? "" : s; }

    // ----- qibla, location, widget, updates -----
    @JavascriptInterface
    public void compassStart(double lat, double lng) { act.compassStart(lat, lng); }

    @JavascriptInterface
    public void compassStop() { act.compassStop(); }

    @JavascriptInterface
    public void locate() { act.locate(); }

    @JavascriptInterface
    public void setPrayerData(String json) {
        Alarms.prefs(act).edit().putString("prayerData", json).apply();
        PrayerWidget.refresh(act);
    }

    @JavascriptInterface
    public void checkUpdate() {
        Updates.check(act, json -> act.js("window.onUpdate&&window.onUpdate(" + json + ")"));
    }

    @JavascriptInterface
    public void openUrl(String url) {
        act.runOnUiThread(() -> {
            try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
        });
    }

    @JavascriptInterface
    public void setLang(String lang) {
        Alarms.prefs(act).edit().putString("lang", "en".equals(lang) ? "en" : "ar").apply();
        PrayerWidget.refresh(act);
    }

    @JavascriptInterface
    public boolean isNight() {
        int m = act.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @JavascriptInterface
    public String version() {
        try { return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    @JavascriptInterface
    public boolean saveImage(String dataUrl, String name) {
        if (android.os.Build.VERSION.SDK_INT < 29) return false;
        try {
            int comma = dataUrl.indexOf(',');
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Salati");
            Uri uri = act.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            try (OutputStream out = act.getContentResolver().openOutputStream(uri)) {
                if (out == null) return false;
                out.write(bytes);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void toast(String msg) {
        act.runOnUiThread(() -> Toast.makeText(act, msg, Toast.LENGTH_SHORT).show());
    }
}
