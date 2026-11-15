package com.jackbhai.omnitools.nativ.viz;

/**
 * JNI bridge to libprojectm (projectM v4.1.8, LGPL-2.1) via the small
 * libomniviz.so shim in projectm/jni/omniviz.cpp.
 *
 * Threading contract: every method except nPcmAddFloat must be called on
 * the GL thread (the GLSurfaceView renderer thread), because projectM
 * renders into the *current* GL context. nPcmAddFloat is thread-safe
 * (projectM copies the samples into its own ring buffer) and is called
 * from the Visualizer capture thread.
 *
 * If libomniviz.so is absent (x86 emulator, failed native build), {@link
 * #available()} is false and VizActivity falls back to the built-in
 * OpenGL modes in FallbackVizRenderer — the screen never hard-crashes.
 */
public final class ProjectMBridge {

    private static final boolean LOADED;

    static {
        boolean ok = false;
        try {
            System.loadLibrary("omniviz");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        LOADED = ok;
    }

    public static boolean available() { return LOADED; }

    private ProjectMBridge() {}

    /** Create the projectM instance. texturePath may be "" (no sprite dir). */
    public static native long nCreate(String texturePath);
    public static native void nDestroy(long handle);

    /** Must be called whenever the GL surface size changes. */
    public static native void nSetWindowSize(long handle, int width, int height);

    /** Render one frame into the current GL context. dtSeconds = frame delta. */
    public static native void nRenderFrame(long handle, double dtSeconds);

    /** Load a .milk preset file. hardCut=true swaps instantly, false crossfades. */
    public static native boolean nLoadPresetFile(long handle, String path, boolean hardCut);

    /** Feed mono/stereo PCM. samples are interleaved when channels==2. Thread-safe. */
    public static native void nPcmAddFloat(long handle, float[] samples, int channels);

    /** 0..~4 typical. Maps to projectM mesh + beat sensitivity. */
    public static native void nSetBeatSensitivity(long handle, float value);

    /** Seconds a preset stays before auto-advance (projectM preset duration). */
    public static native void nSetPresetDuration(long handle, double seconds);

    /** Seconds of crossfade between presets (projectM transition duration). */
    public static native void nSetTransitionDuration(long handle, double seconds);

    /** Lock the current preset (stops auto-advance / shuffle picks). */
    public static native void nSetPresetLocked(long handle, boolean locked);

    public static native boolean nPresetLocked(long handle);
}
