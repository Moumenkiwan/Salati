package com.momen.salati;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Asks GitHub for the latest release; the workflow publishes one per build, tagged v<run number>. */
final class Updates {
    static final String REPO = "Moumenkiwan/Salati";

    interface Out { void result(String json); }

    private Updates() {}

    @SuppressWarnings("deprecation")
    static long versionCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static void check(Context c, Out out) {
        long mine = versionCode(c);
        new Thread(() -> {
            String result = "null";
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                con.setConnectTimeout(10_000);
                con.setReadTimeout(10_000);
                con.setRequestProperty("Accept", "application/vnd.github+json");
                con.setRequestProperty("User-Agent", "salati-app");
                if (con.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                    JSONObject rel = new JSONObject(sb.toString());
                    String digits = rel.optString("tag_name", "").replaceAll("[^0-9]", "");
                    long latest = digits.isEmpty() ? 0 : Long.parseLong(digits);
                    String url = null;
                    JSONArray assets = rel.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject a = assets.getJSONObject(i);
                            if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break; }
                        }
                    }
                    if (url == null) url = rel.optString("html_url");
                    JSONObject o = new JSONObject();
                    o.put("latest", latest);
                    o.put("mine", mine);
                    o.put("newer", latest > mine);
                    o.put("url", url);
                    o.put("page", rel.optString("html_url"));
                    result = o.toString();
                }
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
            out.result(result);
        }).start();
    }
}
