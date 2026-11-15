package com.jackbhai.omnitools.nativ.viz;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The GLSurfaceView renderer behind VizActivity. Two engines, one surface:
 *
 *  - projectM mode (default when libomniviz.so loaded): creates the
 *    projectM instance on the GL thread, renders MilkDrop presets.
 *  - Built-in mode: FallbackVizRenderer's four OpenGL modes. Also the
 *    automatic fallback when the .so is missing or projectM creation
 *    fails — the screen degrades, it never goes black-by-crash.
 *
 * All ProjectMBridge calls happen here, on the GL thread, except PCM
 * feeding (AudioFeed, capture thread — thread-safe by design).
 */
public final class VizRenderer implements GLSurfaceView.Renderer {

    public interface Callbacks {
        /** Called on the GL thread after the projectM handle exists (or fails). */
        void onEngineReady(boolean projectmOk);
    }

    private final AudioFeed feed;
    private final FallbackVizRenderer fallback = new FallbackVizRenderer();
    private Callbacks callbacks;

    public volatile boolean useProjectM = true;
    public volatile float beatSensitivity = 1.0f;
    public volatile double presetDuration = 20.0;
    public volatile double transitionDuration = 1.5;
    public volatile boolean presetLocked = false;

    private long handle = 0;
    private long lastNs = 0;

    public VizRenderer(AudioFeed feed) { this.feed = feed; }

    public void setCallbacks(Callbacks cb) { this.callbacks = cb; }

    public long handle() { return handle; }

    public FallbackVizRenderer fallback() { return fallback; }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        lastNs = 0;
        if (useProjectM && ProjectMBridge.available()) {
            try {
                handle = ProjectMBridge.nCreate("");
                if (handle != 0) {
                    ProjectMBridge.nSetBeatSensitivity(handle, beatSensitivity);
                    ProjectMBridge.nSetPresetDuration(handle, presetDuration);
                    ProjectMBridge.nSetTransitionDuration(handle, transitionDuration);
                    ProjectMBridge.nSetPresetLocked(handle, presetLocked);
                    feed.setProjectMHandle(handle);
                }
            } catch (Throwable t) {
                handle = 0;
            }
        }
        if (callbacks != null) callbacks.onEngineReady(handle != 0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        if (handle != 0) {
            try { ProjectMBridge.nSetWindowSize(handle, width, height); }
            catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        double dt = lastNs == 0 ? 0.016 : (now - lastNs) / 1e9;
        lastNs = now;
        if (dt > 0.25) dt = 0.25; // tab-switch / GC pause: don't lurch
        if (useProjectM && handle != 0) {
            try {
                ProjectMBridge.nRenderFrame(handle, dt);
                return;
            } catch (Throwable t) {
                handle = 0; // fall through to built-in modes below
            }
        }
        fallback.draw(feed, (float) dt);
    }

    /** Call via GLSurfaceView.queueEvent. */
    public void loadPreset(String path, boolean hardCut) {
        if (handle != 0) {
            try { ProjectMBridge.nLoadPresetFile(handle, path, hardCut); }
            catch (Throwable ignored) {}
        }
    }

    /** Call via GLSurfaceView.queueEvent. */
    public void applySettings() {
        if (handle != 0) {
            try {
                ProjectMBridge.nSetBeatSensitivity(handle, beatSensitivity);
                ProjectMBridge.nSetPresetDuration(handle, presetDuration);
                ProjectMBridge.nSetTransitionDuration(handle, transitionDuration);
                ProjectMBridge.nSetPresetLocked(handle, presetLocked);
            } catch (Throwable ignored) {}
        }
        fallback.sensitivity = beatSensitivity;
    }

    /** Call via GLSurfaceView.queueEvent (e.g. from onPause teardown). */
    public void destroyEngine() {
        if (handle != 0) {
            feed.setProjectMHandle(0);
            try { ProjectMBridge.nDestroy(handle); } catch (Throwable ignored) {}
            handle = 0;
        }
    }
}
