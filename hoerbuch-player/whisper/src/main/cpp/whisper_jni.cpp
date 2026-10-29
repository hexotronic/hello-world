// JNI bridge between Kotlin (WhisperNative) and whisper.cpp speech recognition.

#include <jni.h>

#include <algorithm>
#include <string>

#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "LauscherWhisper"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGW(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#endif

namespace {

void log_callback(ggml_log_level level, const char * text, void *) {
    // Only real problems; loading progress (dots) and info lines would flood logcat.
    if (level == GGML_LOG_LEVEL_WARN || level == GGML_LOG_LEVEL_ERROR) {
        LOGW("%s", text);
    }
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_hexotronic_lauscher_whisper_WhisperNative_nativeInit(JNIEnv * env, jclass, jstring path) {
    whisper_log_set(log_callback, nullptr);
    const char * p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = true;
    whisper_context * ctx = whisper_init_from_file_with_params(p, cparams);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

/**
 * Transcribes 16 kHz mono float samples. Returns UTF-8 lines "t0_ms<TAB>t1_ms<TAB>text";
 * an empty result means nothing was recognised (or an error occurred).
 */
JNIEXPORT jbyteArray JNICALL
Java_io_github_hexotronic_lauscher_whisper_WhisperNative_nativeTranscribe(
        JNIEnv * env, jclass, jlong handle, jfloatArray samples, jstring language, jint n_threads) {
    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    const char * lang = env->GetStringUTFChars(language, nullptr);
    const std::string lang_code(lang);
    env->ReleaseStringUTFChars(language, lang);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = n_threads;
    params.language = lang_code.c_str();
    params.detect_language = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    // Speed: we only need the words, one segment, no timestamps and no slow re-decoding
    // with higher temperatures when the first pass looks uncertain.
    params.no_timestamps = true;
    params.single_segment = true;
    params.temperature_inc = 0.0f;

    const jsize n = env->GetArrayLength(samples);
    // Shorter clips need less encoder context (1500 frames = 30 s); this saves encoder time.
    const int seconds = static_cast<int>(n / 16000) + 1;
    if (seconds < 30) params.audio_ctx = std::min(1500, seconds * 50 + 64);
    jfloat * data = env->GetFloatArrayElements(samples, nullptr);
    const int rc = whisper_full(ctx, params, data, n);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (rc != 0) {
        LOGW("whisper_full failed: %d", rc);
        return to_bytes(env, "");
    }

    std::string out;
    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; ++i) {
        // Timestamps are in units of 10 ms.
        const long long t0 = whisper_full_get_segment_t0(ctx, i) * 10;
        const long long t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        out += std::to_string(t0) + "\t" + std::to_string(t1) + "\t" + whisper_full_get_segment_text(ctx, i) + "\n";
    }
    return to_bytes(env, out);
}

JNIEXPORT void JNICALL
Java_io_github_hexotronic_lauscher_whisper_WhisperNative_nativeFree(JNIEnv *, jclass, jlong handle) {
    if (handle != 0) whisper_free(reinterpret_cast<whisper_context *>(handle));
}

} // extern "C"
