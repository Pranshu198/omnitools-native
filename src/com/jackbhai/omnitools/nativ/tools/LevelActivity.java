package com.jackbhai.omnitools.nativ.tools;

import android.content.Context;
import android.graphics.Canvas;
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
 * Bubble Level (new tool id "level"). Accelerometer-driven: the bubble
 * drifts toward the high side; centre it to level the surface. Works
 * flat (both axes) and shows each axis angle — hold the phone upright
 * against a wall and the horizontal bubble still reads the vertical
 * surface's lean via tiltX. Angle maths lives in {@link ToolMath}.
 * Sensor behaviour is on-device only.
 */
public final class LevelActivity extends BaseToolActivity implements SensorEventListener {

    private SensorManager sensors;
    private Sensor accel;
    private BubbleView bubble;
    private TextView readout;
    private double smoothX, smoothY;
    private boolean haveSample;

    @Override protected String toolTitle() { return "Level"; }

    @Override protected void buildUi() {
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        readout = Glass.text(this, "X 0.0 deg   Y 0.0 deg", 18, Glass.TEXT, true);
        readout.setGravity(Gravity.CENTER);
        body().addView(readout);

        bubble = new BubbleView(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(this, 320));
        blp.topMargin = (int) Glass.dp(this, 12);
        body().addView(bubble, blp);

        body().addView(note(accel == null
                ? "This device has no accelerometer, so no level reading is possible."
                : "Lay the phone flat on a surface, or hold its edge against one. "
                + "The bubble turns cyan and the readout shows LEVEL when both axes "
                + "are within 0.5 degrees."));
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensors != null && accel != null)
            sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
    }

    @Override protected void onPause() {
        if (sensors != null) sensors.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        double[] t = ToolMath.levelAngles(e.values[0], e.values[1], e.values[2]);
        if (!haveSample) { smoothX = t[0]; smoothY = t[1]; haveSample = true; }
        else {
            smoothX += 0.2 * (t[0] - smoothX);
            smoothY += 0.2 * (t[1] - smoothY);
        }
        boolean level = ToolMath.isLevel(smoothX, smoothY, 0.5);
        readout.setText(String.format(java.util.Locale.US, "X %.1f deg   Y %.1f deg%s",
                smoothX, smoothY, level ? "   LEVEL" : ""));
        bubble.setTilt((float) smoothX, (float) smoothY, level);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /** Circular vial: bubble offset maps tilt (30 deg = full travel). */
    static final class BubbleView extends View {
        private float tiltX, tiltY;
        private boolean level;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bub = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);

        BubbleView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Glass.dp(c, 1.5f));
            ring.setColor(Glass.GLASS_BORDER);
            cross.setColor(Glass.GLASS_BORDER);
            cross.setStrokeWidth(Glass.dp(c, 1));
            bub.setStyle(Paint.Style.FILL);
            target.setStyle(Paint.Style.STROKE);
            target.setStrokeWidth(Glass.dp(c, 1.5f));
            target.setColor(Glass.TEXT_DIM);
        }

        void setTilt(float x, float y, boolean lvl) {
            tiltX = x; tiltY = y; level = lvl; invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - Glass.dp(getContext(), 10);
            cv.drawCircle(cx, cy, r, ring);
            cv.drawLine(cx - r, cy, cx + r, cy, cross);
            cv.drawLine(cx, cy - r, cx, cy + r, cross);
            float br = Glass.dp(getContext(), 26);
            cv.drawCircle(cx, cy, br, target);
            // Bubble moves opposite gravity's lean: toward the high side.
            float maxTravel = r - br - Glass.dp(getContext(), 4);
            float ox = clamp(tiltX / 30f) * maxTravel;
            float oy = clamp(-tiltY / 30f) * maxTravel;
            float len = (float) Math.sqrt(ox * ox + oy * oy);
            if (len > maxTravel) { ox *= maxTravel / len; oy *= maxTravel / len; }
            bub.setColor(level ? Glass.NEON_CYAN : Glass.NEON_VIOLET);
            bub.setAlpha(190);
            cv.drawCircle(cx + ox, cy + oy, br, bub);
        }

        private float clamp(float v) { return v < -1 ? -1 : (v > 1 ? 1 : v); }
    }
}
