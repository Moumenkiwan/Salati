package com.momen.salati;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int REQ_PICK_ADHAN = 7;
    static final int REQ_LOCATION = 8;
    WebView web;
    Compass compass;
    private boolean compassWanted = false;
    private double compassLat, compassLng;

    void js(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    // ----- compass -----
    void compassStart(double lat, double lng) {
        compassLat = lat; compassLng = lng; compassWanted = true;
        runOnUiThread(() -> {
            if (compass == null) compass = new Compass(this, (deg, acc) -> js("window.onHeading&&window.onHeading(" + deg + "," + acc + ")"));
            if (!compass.available()) { js("window.onHeading&&window.onHeading(-1,0)"); return; }
            compass.start(lat, lng);
        });
    }

    void compassStop() {
        compassWanted = false;
        runOnUiThread(() -> { if (compass != null) compass.stop(); });
    }

    // ----- location -----
    void locate() {
        runOnUiThread(() -> {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            } else {
                doLocate();
            }
        });
    }

    private void doLocate() {
        Locator.locate(this, (loc, err) -> {
            if (loc != null) js("window.onLocation&&window.onLocation({lat:" + loc.getLatitude() + ",lng:" + loc.getLongitude() + "})");
            else js("window.onLocation&&window.onLocation({error:" + JSONObject.quote(err == null ? "error" : err) + "})");
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_LOCATION) return;
        boolean ok = false;
        for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
        if (ok) doLocate();
        else js("window.onLocation&&window.onLocation({error:\"denied\"})");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (compass != null) compass.pauseSensors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (compassWanted && compass != null) compass.start(compassLat, compassLng);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notif.ensure(this);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#155E59"));

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.addJavascriptInterface(new Bridge(this), "Android");
        web.setWebChromeClient(new WebChromeClient());
        QuranService.listener = json -> runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window.onNativeAudio&&window.onNativeAudio(" + json + ")", null);
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String s = QuranService.lastState();
                if (s != null) view.evaluateJavascript("window.onNativeAudio&&window.onNativeAudio(" + s + ")", null);
            }
        });
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/www/index.html");

        Alarms.scheduleNextAdhan(this);
        Alarms.scheduleZikr(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true); // keep running so recitation continues
    }

    void pickAdhan() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_PICK_ADHAN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_ADHAN || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        String name = "ملف أذان";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {}
        Alarms.prefs(this).edit().putString("adhanUri", uri.toString()).putString("adhanName", name).apply();
        web.evaluateJavascript("window.onAdhanPicked&&window.onAdhanPicked(" + JSONObject.quote(name) + ")", null);
    }

    @Override
    protected void onDestroy() {
        QuranService.listener = null;
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
