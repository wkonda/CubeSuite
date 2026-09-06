#include <jni.h>
#include <algorithm>
#include "tuner_engine.h"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_wkonda_cubesuite_tuner_audio_AudioEngine_nativeInit(
        JNIEnv *,
        jobject,
        jint sampleRate,
        jint bufferSize) {
    return tuner_init(sampleRate, bufferSize) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_wkonda_cubesuite_tuner_audio_AudioEngine_nativeProcess(
        JNIEnv *env,
        jobject,
        jshortArray jbuffer,
        jint count) {
    const jsize length = env->GetArrayLength(jbuffer);
    const int usable = std::max(0, std::min<int>(count, length));

    float out[12] = {};
    if (usable > 0) {
        jshort *buffer = env->GetShortArrayElements(jbuffer, nullptr);
        tuner_process(buffer, usable, out);
        env->ReleaseShortArrayElements(jbuffer, buffer, JNI_ABORT);
    }

    jfloatArray result = env->NewFloatArray(12);
    env->SetFloatArrayRegion(result, 0, 12, out);
    return result;
}

}
