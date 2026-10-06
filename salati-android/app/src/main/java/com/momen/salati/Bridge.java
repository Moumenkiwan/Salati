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
        Intent s = new Intent(act, AdhanService.class).putExtra("name", "(تجربة)");
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
    public void mediaState(boolean playing, String title) {
        try {
            if (playing) {
                act.startForegroundService(new Intent(act, QuranService.class).putExtra("title", title));
            } else {
                act.stopService(new Intent(act, QuranService.class));
            }
        } catch (Exception ignored) {}
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
