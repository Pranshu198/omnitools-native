package com.jackbhai.omnitools.nativ.viz;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Built-in OpenGL ES 2.0 visual modes, driven by the same Visualizer
 * capture (AudioFeed bands/waveform) as projectM. This is BOTH the
 * no-.so fallback (x86 emulator, native build missing) and a user-
 * selectable lightweight mode set inside VizActivity.
 *
 * Modes: 0 Spectrum bars, 1 Wave ribbon, 2 Particles, 3 Kaleidoscope.
 * Plain shader quads/lines/points — no external library.
 */
public final class FallbackVizRenderer {

    public static final int MODE_SPECTRUM = 0;
    public static final int MODE_WAVE = 1;
    public static final int MODE_PARTICLES = 2;
    public static final int MODE_KALEIDOSCOPE = 3;
    public static final int MODE_COUNT = 4;
    public static final String[] MODE_NAMES = {"Spectrum", "Wave", "Particles", "Kaleidoscope"};

    private int program;
    private int pointProgram;
    private int posLoc, colorLoc, pointPosLoc, pointColorLoc, pointSizeLoc;
    private FloatBuffer verts;
    private float hue = 0.55f;
    public volatile int mode = MODE_SPECTRUM;
    public volatile float sensitivity = 1.0f;

    // Particle state
    private final int particleCount = 240;
    private final float[] px = new float[particleCount];
    private final float[] py = new float[particleCount];
    private final float[] pvx = new float[particleCount];
    private final float[] pvy = new float[particleCount];
    private boolean particlesInit = false;

    private static final String VERT =
            "attribute vec2 aPos;\nvoid main(){gl_Position=vec4(aPos,0.0,1.0);}";
    private static final String FRAG =
            "precision mediump float;\nuniform vec4 uColor;\nvoid main(){gl_FragColor=uColor;}";
    private static final String POINT_VERT =
            "attribute vec2 aPos;\nuniform float uSize;\n"
                    + "void main(){gl_Position=vec4(aPos,0.0,1.0);gl_PointSize=uSize;}";
    private static final String POINT_FRAG =
            "precision mediump float;\nuniform vec4 uColor;\n"
                    + "void main(){vec2 d=gl_PointCoord-vec2(0.5);"
                    + "if(dot(d,d)>0.25) discard; gl_FragColor=uColor;}";

    public void init() {
        program = buildProgram(VERT, FRAG);
        pointProgram = buildProgram(POINT_VERT, POINT_FRAG);
        posLoc = GLES20.glGetAttribLocation(program, "aPos");
        colorLoc = GLES20.glGetUniformLocation(program, "uColor");
        pointPosLoc = GLES20.glGetAttribLocation(pointProgram, "aPos");
        pointColorLoc = GLES20.glGetUniformLocation(pointProgram, "uColor");
        pointSizeLoc = GLES20.glGetUniformLocation(pointProgram, "uSize");
        verts = ByteBuffer.allocateDirect(16384 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static int buildProgram(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        return p;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    private void setColor(float h, float alpha) {
        // HSV->RGB, s=0.85, v=1
        float r, g, b;
        float x = h * 6f;
        int i = (int) x;
        float fr = x - i;
        float q = 1f - 0.85f * fr, t = 0.15f + 0.85f * fr;
        switch (i % 6) {
            case 0: r = 1; g = t; b = 0.15f; break;
            case 1: r = q; g = 1; b = 0.15f; break;
            case 2: r = 0.15f; g = 1; b = t; break;
            case 3: r = 0.15f; g = q; b = 1; break;
            case 4: r = t; g = 0.15f; b = 1; break;
            default: r = 1; g = 0.15f; b = q; break;
        }
        GLES20.glUniform4f(colorLoc, r, g, b, alpha);
    }

    private void drawTriangles(float[] data, int floats) {
        verts.clear();
        verts.put(data, 0, floats);
        verts.position(0);
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, verts);
        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, floats / 2);
        GLES20.glDisableVertexAttribArray(posLoc);
    }

    public void draw(AudioFeed feed, float dt) {
        GLES20.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        hue += dt * 0.02f;
        if (hue > 1f) hue -= 1f;
        if (program == 0) init();
        switch (mode) {
            case MODE_WAVE: drawWave(feed); break;
            case MODE_PARTICLES: drawParticles(feed, dt); break;
            case MODE_KALEIDOSCOPE: drawKaleidoscope(feed); break;
            default: drawSpectrum(feed); break;
        }
    }

    private void drawSpectrum(AudioFeed feed) {
        GLES20.glUseProgram(program);
        int n = feed.bands.length;
        float[] data = new float[n * 12];
        float bw = 2f / n;
        for (int i = 0; i < n; i++) {
            float h = Math.min(1f, feed.bands[i] * sensitivity) * 1.7f;
            float x0 = -1f + i * bw + bw * 0.15f, x1 = -1f + (i + 1) * bw - bw * 0.15f;
            float y0 = -1f, y1 = -1f + h;
            int o = i * 12;
            data[o] = x0; data[o + 1] = y0; data[o + 2] = x1; data[o + 3] = y0;
            data[o + 4] = x0; data[o + 5] = y1; data[o + 6] = x1; data[o + 7] = y0;
            data[o + 8] = x1; data[o + 9] = y1; data[o + 10] = x0; data[o + 11] = y1;
        }
        setColor(hue, 0.9f);
        drawTriangles(data, data.length);
    }

    private void drawWave(AudioFeed feed) {
        GLES20.glUseProgram(program);
        int n = feed.waveform.length;
        float[] data = new float[(n - 1) * 12];
        float thick = 0.03f;
        for (int i = 0; i < n - 1; i++) {
            float x0 = -1f + 2f * i / (n - 1), x1 = -1f + 2f * (i + 1) / (n - 1);
            float y0 = feed.waveform[i] * sensitivity * 0.8f;
            float y1 = feed.waveform[i + 1] * sensitivity * 0.8f;
            int o = i * 12;
            data[o] = x0; data[o + 1] = y0 - thick; data[o + 2] = x1; data[o + 3] = y1 - thick;
            data[o + 4] = x0; data[o + 5] = y0 + thick; data[o + 6] = x1; data[o + 7] = y1 - thick;
            data[o + 8] = x1; data[o + 9] = y1 + thick; data[o + 10] = x0; data[o + 11] = y0 + thick;
        }
        setColor(hue + 0.1f, 0.95f);
        drawTriangles(data, data.length);
    }

    private void drawParticles(AudioFeed feed, float dt) {
        if (!particlesInit) {
            for (int i = 0; i < particleCount; i++) {
                px[i] = (float) (Math.random() * 2 - 1);
                py[i] = (float) (Math.random() * 2 - 1);
                pvx[i] = (float) (Math.random() - 0.5) * 0.2f;
                pvy[i] = (float) (Math.random() - 0.5) * 0.2f;
            }
            particlesInit = true;
        }
        float energy = 0.2f + feed.bassEnergy * sensitivity;
        for (int i = 0; i < particleCount; i++) {
            // Gentle outward push from centre scaled by bass.
            pvx[i] += px[i] * energy * dt * 0.35f;
            pvy[i] += py[i] * energy * dt * 0.35f;
            pvx[i] *= 0.985f; pvy[i] *= 0.985f;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
            if (px[i] < -1f || px[i] > 1f || py[i] < -1f || py[i] > 1f) {
                px[i] = (float) (Math.random() * 0.4 - 0.2);
                py[i] = (float) (Math.random() * 0.4 - 0.2);
                pvx[i] = (float) (Math.random() - 0.5) * 0.3f;
                pvy[i] = (float) (Math.random() - 0.5) * 0.3f;
            }
        }
        GLES20.glUseProgram(pointProgram);
        float[] data = new float[particleCount * 2];
        for (int i = 0; i < particleCount; i++) {
            data[i * 2] = px[i]; data[i * 2 + 1] = py[i];
        }
        verts.clear();
        verts.put(data);
        verts.position(0);
        GLES20.glVertexAttribPointer(pointPosLoc, 2, GLES20.GL_FLOAT, false, 0, verts);
        GLES20.glEnableVertexAttribArray(pointPosLoc);
        float r = (float) Math.abs(Math.sin(hue * 6.28f)), g = (float) Math.abs(Math.sin(hue * 6.28f + 2.1f)),
                b = (float) Math.abs(Math.sin(hue * 6.28f + 4.2f));
        GLES20.glUniform4f(pointColorLoc, r, g, b, 0.9f);
        GLES20.glUniform1f(pointSizeLoc, 10f + feed.bassEnergy * 26f * sensitivity);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, particleCount);
        GLES20.glDisableVertexAttribArray(pointPosLoc);
    }

    private void drawKaleidoscope(AudioFeed feed) {
        GLES20.glUseProgram(program);
        int segments = 6;
        int n = 64;
        // Build one waveform spoke, then emit rotated/mirrored triangle ribbons.
        float[] spoke = new float[n];
        for (int i = 0; i < n; i++) {
            int idx = (int) ((i / (float) (n - 1)) * (feed.waveform.length - 1));
            spoke[i] = feed.waveform[idx] * sensitivity;
        }
        float thick = 0.012f;
        float[] data = new float[segments * 2 * (n - 1) * 12];
        int o = 0;
        for (int s = 0; s < segments * 2; s++) {
            double ang = (Math.PI * 2 / (segments * 2)) * s + hue * 0.6f;
            double cos = Math.cos(ang), sin = Math.sin(ang);
            float flip = (s % 2 == 0) ? 1f : -1f;
            for (int i = 0; i < n - 1; i++) {
                float r0 = i / (float) (n - 1), r1 = (i + 1) / (float) (n - 1);
                float a0 = spoke[i] * 0.25f * flip, a1 = spoke[i + 1] * 0.25f * flip;
                // Point = rotate( (r, a) ) in spoke space.
                float[] p = new float[8];
                p[0] = (float) (r0 * cos - (a0 - thick) * sin);
                p[1] = (float) (r0 * sin + (a0 - thick) * cos);
                p[2] = (float) (r1 * cos - (a1 - thick) * sin);
                p[3] = (float) (r1 * sin + (a1 - thick) * cos);
                p[4] = (float) (r0 * cos - (a0 + thick) * sin);
                p[5] = (float) (r0 * sin + (a0 + thick) * cos);
                p[6] = (float) (r1 * cos - (a1 + thick) * sin);
                p[7] = (float) (r1 * sin + (a1 + thick) * cos);
                data[o++] = p[0]; data[o++] = p[1]; data[o++] = p[2]; data[o++] = p[3];
                data[o++] = p[4]; data[o++] = p[5]; data[o++] = p[2]; data[o++] = p[3];
                data[o++] = p[6]; data[o++] = p[7]; data[o++] = p[4]; data[o++] = p[5];
            }
        }
        setColor(hue + 0.3f, 0.85f);
        drawTriangles(data, o);
    }
}
