package com.jackbhai.omnitools.nativ.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jackbhai.omnitools.nativ.Glass;

/**
 * Compass (new tool id "compass"). Rotation-vector sensor (accelerometer
 * fallback), circular low-pass smoothing from {@link ToolMath}, degree
 * readout + 16-wind cardinal. Sensor behaviour is on-device only.
 */
public final class CompassActivity extends BaseToolActivity implements SensorEventListener {

    private SensorManager sensors;
    private Sensor rotationSensor, accelSensor, magSensor;
    private CompassView dial;
    private TextView readout;
    private double heading = 0;
    private boolean haveHeading;

    private final float[] rotMatrix = new float[9];
    private final float[] orientation = new float[3];
    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private boolean haveGravity, haveMag;

    @Override protected String toolTitle() { return "Compass"; }

    @Override protected void buildUi() {
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) : null;
        if (rotationSensor == null && sensors != null) {
            accelSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magSensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        readout = Glass.text(this, "-- deg", 34, Glass.TEXT, true);
        readout.setGravity(Gravity.CENTER);
        body().addView(readout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dial = new CompassView(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(this, 300));
        dlp.topMargin = (int) Glass.dp(this, 12);
        body().addView(dial, dlp);

        if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
            body().addView(note("This device has no compass sensors, so no heading can be shown."));
        } else {
            body().addView(note("Heading is smoothed with a low-pass filter. Keep the phone "
                    + "away from magnets and metal for a truer reading, and move it in a "
                    + "figure-8 to calibrate."));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensors == null) return;
        if (rotationSensor != null) {
            sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            if (accelSensor != null)
                sensors.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI);
            if (magSensor != null)
                sensors.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override protected void onPause() {
        if (sensors != null) sensors.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        double azimuthDeg;
        if (e.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, e.values);
            SensorManager.getOrientation(rotMatrix, orientation);
            azimuthDeg = Math.toDegrees(orientation[0]);
        } else if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(e.values, 0, gravity, 0, 3);
            haveGravity = true;
            if (!haveMag) return;
            azimuthDeg = fusedAzimuth();
            if (azimuthDeg == Double.NaN) return;
        } else if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(e.values, 0, geomagnetic, 0, 3);
            haveMag = true;
            if (!haveGravity) return;
            azimuthDeg = fusedAzimuth();
            if (azimuthDeg == Double.NaN) return;
        } else {
            return;
        }
        double target = ToolMath.normaliseDeg(azimuthDeg);
        heading = haveHeading ? ToolMath.angleSmooth(heading, target, 0.15) : target;
        haveHeading = true;
        readout.setText(String.format(java.util.Locale.US, "%d deg  %s",
                Math.round(heading), ToolMath.cardinal(heading)));
        dial.setHeading((float) heading);
    }

    private double fusedAzimuth() {
        if (!SensorManager.getRotationMatrix(rotMatrix, null, gravity, geomagnetic)) return Double.NaN;
        SensorManager.getOrientation(rotMatrix, orientation);
        return Math.toDegrees(orientation[0]);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /** Rotating glass dial: the rose turns opposite the heading so the
     *  letter at the top is the direction the phone points. */
    static final class CompassView extends View {
        private float heading;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);

        CompassView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Glass.dp(c, 1.5f));
            ring.setColor(Glass.GLASS_BORDER);
            tick.setColor(Glass.TEXT_DIM);
            tick.setStrokeWidth(Glass.dp(c, 2));
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Glass.font(c, true));
            needle.setStyle(Paint.Style.FILL);
        }

        void setHeading(float h) { heading = h; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - Glass.dp(getContext(), 8);
            cv.drawCircle(cx, cy, r, ring);
            // Fixed marker at the top.
            needle.setColor(Glass.NEON_CYAN);
            android.graphics.Path top = new android.graphics.Path();
            float tw = Glass.dp(getContext(), 7);
            top.moveTo(cx, cy - r - Glass.dp(getContext(), 2));
            top.lineTo(cx - tw, cy - r - Glass.dp(getContext(), 14));
            top.lineTo(cx + tw, cy - r - Glass.dp(getContext(), 14));
            top.close();
            cv.drawPath(top, needle);

            String[] pts = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            for (int i = 0; i < 72; i++) {
                double ang = Math.toRadians(i * 5 - heading);
                boolean major = i % 18 == 0, mid = i % 6 == 0;
                float len = Glass.dp(getContext(), major ? 14 : mid ? 10 : 6);
                tick.setColor(major ? Glass.TEXT : Glass.TEXT_DIM);
                float x1 = cx + (float) Math.sin(ang) * (r - len);
                float y1 = cy - (float) Math.cos(ang) * (r - len);
                float x2 = cx + (float) Math.sin(ang) * r;
                float y2 = cy - (float) Math.cos(ang) * r;
                cv.drawLine(x1, y1, x2, y2, tick);
            }
            for (int i = 0; i < 8; i++) {
                double ang = Math.toRadians(i * 45 - heading);
                boolean north = i == 0;
                label.setColor(north ? Color.rgb(255, 92, 92) : Glass.TEXT);
                label.setTextSize(Glass.dp(getContext(), north ? 20 : 15));
                float lr = r - Glass.dp(getContext(), 30);
                float x = cx + (float) Math.sin(ang) * lr;
                float y = cy - (float) Math.cos(ang) * lr + Glass.dp(getContext(), 6);
                cv.drawText(pts[i], x, y, label);
            }
            // Needle: red half toward north, dim half toward south.
            double na = Math.toRadians(-heading);
            float nx = cx + (float) Math.sin(na) * (r * 0.55f);
            float ny = cy - (float) Math.cos(na) * (r * 0.55f);
            needle.setColor(Color.rgb(255, 92, 92));
            cv.drawLine(cx, cy, nx, ny, needle);
            needle.setColor(Glass.TEXT_DIM);
            cv.drawLine(cx, cy, 2 * cx - nx, 2 * cy - ny, needle);
            cv.drawCircle(cx, cy, Glass.dp(getContext(), 4), needle);
        }
    }
}
