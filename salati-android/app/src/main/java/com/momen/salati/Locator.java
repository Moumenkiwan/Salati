package com.momen.salati;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** One-shot location fix: a recent cached fix if there is one, otherwise a fresh one. */
final class Locator {
    interface Out { void done(Location loc, String error); }

    private Locator() {}

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    static void locate(Context c, Out out) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null) { out.done(null, "nolm"); return; }
        boolean net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!net && !gps) { out.done(null, "off"); return; }

        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {}
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 30 * 60_000L) { out.done(best, null); return; }

        final Location fallback = best;
        final boolean[] finished = {false};
        Handler h = new Handler(Looper.getMainLooper());
        String provider = net ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
        Runnable timeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            out.done(fallback, fallback == null ? "timeout" : null);
        };
        h.postDelayed(timeout, 20_000);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, c.getMainExecutor(), loc -> {
                    if (finished[0]) return;
                    finished[0] = true;
                    h.removeCallbacks(timeout);
                    out.done(loc != null ? loc : fallback, loc == null && fallback == null ? "timeout" : null);
                });
            } else {
                lm.requestSingleUpdate(provider, new LocationListener() {
                    @Override public void onLocationChanged(Location loc) {
                        if (finished[0]) return;
                        finished[0] = true;
                        h.removeCallbacks(timeout);
                        out.done(loc, null);
                    }
                    // Older Android versions call these and have no default implementation.
                    @Override public void onStatusChanged(String p, int status, android.os.Bundle extras) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                }, Looper.getMainLooper());
            }
        } catch (Exception e) {
            if (!finished[0]) { finished[0] = true; h.removeCallbacks(timeout); out.done(fallback, fallback == null ? "error" : null); }
        }
    }
}
