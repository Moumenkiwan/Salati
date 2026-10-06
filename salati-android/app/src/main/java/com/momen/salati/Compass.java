package com.momen.salati;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Phone heading relative to true north, smoothed, reported a few times a second. */
final class Compass implements SensorEventListener {
    interface Out { void heading(double degrees, int accuracy); }

    private final SensorManager sm;
    private final Out out;
    private final float[] rot = new float[9];
    private final float[] orient = new float[3];
    private double declination = 0;
    private double smooth = Double.NaN;
    private long lastSent = 0;
    private int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
    boolean active = false;

    Compass(Context c, Out out) {
        this.sm = c.getSystemService(SensorManager.class);
        this.out = out;
    }

    boolean available() {
        return sm != null && (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
                || sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) != null);
    }

    void start(double lat, double lng) {
        declination = new GeomagneticField((float) lat, (float) lng, 0f, System.currentTimeMillis()).getDeclination();
        if (sm == null) return;
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        if (s == null) return;
        sm.unregisterListener(this);
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        active = true;
    }

    void stop() {
        if (sm != null) sm.unregisterListener(this);
        active = false;
    }

    void pauseSensors() { if (sm != null) sm.unregisterListener(this); }

    @Override
    public void onSensorChanged(SensorEvent e) {
        SensorManager.getRotationMatrixFromVector(rot, e.values);
        SensorManager.getOrientation(rot, orient);
        double az = Math.toDegrees(orient[0]) + declination;
        az = ((az % 360) + 360) % 360;
        if (Double.isNaN(smooth)) smooth = az;
        else {
            double d = ((az - smooth + 540) % 360) - 180;   // shortest way round
            smooth = ((smooth + d * 0.2) % 360 + 360) % 360;
        }
        long now = System.currentTimeMillis();
        if (now - lastSent >= 70) {
            lastSent = now;
            out.heading(smooth, accuracy);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int acc) { accuracy = acc; }
}
