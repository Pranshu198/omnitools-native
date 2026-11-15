/*
 * libomniviz — JNI shim between com.jackbhai.omnitools.nativ.viz.ProjectMBridge
 * and libprojectM (projectM v4.1.8, LGPL-2.1, static-linked into this .so).
 *
 * Threading: all functions except nPcmAddFloat must run on the GL thread
 * (projectM renders into the current GL context). nPcmAddFloat only
 * appends to projectM's internal PCM ring buffer and is thread-safe.
 *
 * projectM 4.1 keeps its own internal clock (TimeKeeper), so the dt
 * argument of nRenderFrame is accepted for interface stability and
 * intentionally unused natively.
 */
#include <jni.h>
#include <cstdint>
#include <vector>

#include <projectM-4/projectM.h>

static projectm_handle as_handle(jlong h) {
    return reinterpret_cast<projectm_handle>(static_cast<uintptr_t>(h));
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nCreate(JNIEnv* env, jclass clazz,
                                                             jstring texture_path) {
    (void) clazz;
    (void) texture_path; /* projectM 4.1 create() takes no paths; kept for API stability */
    (void) env;
    projectm_handle h = projectm_create();
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(h));
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nDestroy(JNIEnv* env, jclass clazz,
                                                              jlong handle) {
    (void) env; (void) clazz;
    if (handle) projectm_destroy(as_handle(handle));
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nSetWindowSize(JNIEnv* env, jclass clazz,
                                                                    jlong handle,
                                                                    jint width, jint height) {
    (void) env; (void) clazz;
    if (handle && width > 0 && height > 0) {
        projectm_set_window_size(as_handle(handle),
                                 static_cast<size_t>(width), static_cast<size_t>(height));
    }
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nRenderFrame(JNIEnv* env, jclass clazz,
                                                                  jlong handle, jdouble dt) {
    (void) env; (void) clazz; (void) dt; /* projectM 4.1 times frames internally */
    if (handle) projectm_opengl_render_frame(as_handle(handle));
}

JNIEXPORT jboolean JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nLoadPresetFile(JNIEnv* env, jclass clazz,
                                                                     jlong handle, jstring path,
                                                                     jboolean hard_cut) {
    (void) clazz;
    if (!handle || !path) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (!p) return JNI_FALSE;
    projectm_load_preset_file(as_handle(handle), p, hard_cut == JNI_FALSE);
    env->ReleaseStringUTFChars(path, p);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nPcmAddFloat(JNIEnv* env, jclass clazz,
                                                                  jlong handle, jfloatArray samples,
                                                                  jint channels) {
    (void) clazz;
    if (!handle || !samples) return;
    jsize len = env->GetArrayLength(samples);
    if (len <= 0) return;
    projectm_channels ch = (channels == 2) ? PROJECTM_STEREO : PROJECTM_MONO;
    unsigned int per_channel = static_cast<unsigned int>(len) /
                               static_cast<unsigned int>(ch);
    if (per_channel == 0) return;
    /* Copy out of the JVM array: GetFloatArrayElements may pin/copy;
     * projectM copies into its own buffer synchronously, so a scoped
     * copy is safe and keeps the capture thread stall-free. */
    std::vector<float> buf(static_cast<size_t>(len));
    env->GetFloatArrayRegion(samples, 0, len, buf.data());
    projectm_pcm_add_float(as_handle(handle), buf.data(), per_channel, ch);
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nSetBeatSensitivity(JNIEnv* env, jclass clazz,
                                                                         jlong handle, jfloat value) {
    (void) env; (void) clazz;
    if (handle) projectm_set_beat_sensitivity(as_handle(handle), value);
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nSetPresetDuration(JNIEnv* env, jclass clazz,
                                                                        jlong handle, jdouble seconds) {
    (void) env; (void) clazz;
    if (handle) projectm_set_preset_duration(as_handle(handle), seconds);
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nSetTransitionDuration(JNIEnv* env, jclass clazz,
                                                                            jlong handle, jdouble seconds) {
    (void) env; (void) clazz;
    if (handle) projectm_set_soft_cut_duration(as_handle(handle), seconds);
}

JNIEXPORT void JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nSetPresetLocked(JNIEnv* env, jclass clazz,
                                                                      jlong handle, jboolean locked) {
    (void) env; (void) clazz;
    if (handle) projectm_set_preset_locked(as_handle(handle), locked == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_jackbhai_omnitools_nativ_viz_ProjectMBridge_nPresetLocked(JNIEnv* env, jclass clazz,
                                                                   jlong handle) {
    (void) env; (void) clazz;
    if (!handle) return JNI_FALSE;
    return projectm_get_preset_locked(as_handle(handle)) ? JNI_TRUE : JNI_FALSE;
}

} /* extern "C" */
