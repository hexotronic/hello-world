// JNI bridge to the LAME MP3 encoder: 16-bit PCM in, MP3 frames out.
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <lame.h>

#define EXPORT __attribute__((visibility("default")))

static jbyteArray to_array(JNIEnv *env, const unsigned char *data, int size) {
    if (size < 0) size = 0;
    jbyteArray out = (*env)->NewByteArray(env, size);
    if (out != NULL && size > 0) (*env)->SetByteArrayRegion(env, out, 0, size, (const jbyte *) data);
    return out;
}

EXPORT JNIEXPORT jlong JNICALL
Java_io_github_hexotronic_lauscher_mp3_Mp3Encoder_nativeInit(
        JNIEnv *env, jclass clazz, jint sample_rate, jint channels, jint out_rate, jint kbps, jboolean mono) {
    lame_global_flags *gf = lame_init();
    if (gf == NULL) return 0;
    lame_set_in_samplerate(gf, sample_rate);
    lame_set_num_channels(gf, channels);
    if (out_rate > 0) lame_set_out_samplerate(gf, out_rate);
    lame_set_VBR(gf, vbr_off);
    lame_set_brate(gf, kbps);
    lame_set_mode(gf, mono ? MONO : JOINT_STEREO);
    lame_set_quality(gf, 5);
    // The output is streamed, so the Xing/Info header could not be rewritten at the end;
    // constant bitrate files are seekable without it.
    lame_set_bWriteVbrTag(gf, 0);
    lame_set_write_id3tag_automatic(gf, 0);
    if (lame_init_params(gf) < 0) {
        lame_close(gf);
        return 0;
    }
    return (jlong) (intptr_t) gf;
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_io_github_hexotronic_lauscher_mp3_Mp3Encoder_nativeEncode(
        JNIEnv *env, jclass clazz, jlong handle, jshortArray pcm, jint frames) {
    lame_global_flags *gf = (lame_global_flags *) (intptr_t) handle;
    int size = (int) (1.25 * frames) + 7200;
    unsigned char *buf = malloc(size);
    if (buf == NULL) return to_array(env, NULL, 0);
    jshort *samples = (*env)->GetShortArrayElements(env, pcm, NULL);
    int written;
    if (lame_get_num_channels(gf) == 2) {
        written = lame_encode_buffer_interleaved(gf, samples, frames, buf, size);
    } else {
        written = lame_encode_buffer(gf, samples, samples, frames, buf, size);
    }
    (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
    jbyteArray out = to_array(env, buf, written);
    free(buf);
    return out;
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_io_github_hexotronic_lauscher_mp3_Mp3Encoder_nativeFlush(JNIEnv *env, jclass clazz, jlong handle) {
    lame_global_flags *gf = (lame_global_flags *) (intptr_t) handle;
    unsigned char buf[7200];
    int written = lame_encode_flush(gf, buf, sizeof(buf));
    return to_array(env, buf, written);
}

EXPORT JNIEXPORT void JNICALL
Java_io_github_hexotronic_lauscher_mp3_Mp3Encoder_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    lame_close((lame_global_flags *) (intptr_t) handle);
}
