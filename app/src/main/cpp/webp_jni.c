#include <jni.h>
#include <stdlib.h>

#include "webp_core.h"

#define FN(name) Java_com_guilherme_gwent_engine_NativeWebp_##name

JNIEXPORT jlong JNICALL FN(encoderNew)(JNIEnv* env, jclass cls, jint w, jint h) {
  return (jlong)(intptr_t)gw_encoder_new(w, h);
}

JNIEXPORT jboolean JNICALL FN(encoderAdd)(JNIEnv* env, jclass cls, jlong handle,
                                          jintArray argb, jint ts, jfloat quality,
                                          jint method) {
  GwEncoder* g = (GwEncoder*)(intptr_t)handle;
  if (g == NULL) return JNI_FALSE;
  jint* px = (*env)->GetIntArrayElements(env, argb, NULL);
  if (px == NULL) return JNI_FALSE;
  int ok = gw_encoder_add(g, (const uint32_t*)px, ts, quality, method);
  (*env)->ReleaseIntArrayElements(env, argb, px, JNI_ABORT);
  return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL FN(encoderFinish)(JNIEnv* env, jclass cls,
                                               jlong handle, jint end_ts) {
  GwEncoder* g = (GwEncoder*)(intptr_t)handle;
  if (g == NULL) return NULL;
  uint8_t* out = NULL;
  size_t size = 0;
  if (!gw_encoder_finish(g, end_ts, &out, &size)) return NULL;
  jbyteArray arr = (*env)->NewByteArray(env, (jsize)size);
  if (arr != NULL) {
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)size, (const jbyte*)out);
  }
  free(out);
  return arr;
}

JNIEXPORT void JNICALL FN(encoderFree)(JNIEnv* env, jclass cls, jlong handle) {
  gw_encoder_free((GwEncoder*)(intptr_t)handle);
}

JNIEXPORT jlong JNICALL FN(decoderNew)(JNIEnv* env, jclass cls, jbyteArray data) {
  jsize n = (*env)->GetArrayLength(env, data);
  jbyte* bytes = (*env)->GetByteArrayElements(env, data, NULL);
  if (bytes == NULL) return 0;
  GwDecoder* d = gw_decoder_new((const uint8_t*)bytes, (size_t)n);
  (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
  return (jlong)(intptr_t)d;
}

// Devolve {largura, altura, quadros}.
JNIEXPORT jintArray JNICALL FN(decoderInfo)(JNIEnv* env, jclass cls, jlong handle) {
  GwDecoder* d = (GwDecoder*)(intptr_t)handle;
  if (d == NULL) return NULL;
  int v[3];
  gw_decoder_info(d, &v[0], &v[1], &v[2]);
  jintArray arr = (*env)->NewIntArray(env, 3);
  if (arr != NULL) (*env)->SetIntArrayRegion(env, arr, 0, 3, (const jint*)v);
  return arr;
}

// Preenche "out" com o próximo quadro e devolve o tempo dele (ms), ou -1 no fim.
JNIEXPORT jint JNICALL FN(decoderNext)(JNIEnv* env, jclass cls, jlong handle,
                                       jintArray out) {
  GwDecoder* d = (GwDecoder*)(intptr_t)handle;
  if (d == NULL) return -1;
  jint* px = (*env)->GetIntArrayElements(env, out, NULL);
  if (px == NULL) return -1;
  int ts = 0;
  int ok = gw_decoder_next(d, (uint32_t*)px, &ts);
  (*env)->ReleaseIntArrayElements(env, out, px, 0);
  return ok ? ts : -1;
}

JNIEXPORT void JNICALL FN(decoderReset)(JNIEnv* env, jclass cls, jlong handle) {
  GwDecoder* d = (GwDecoder*)(intptr_t)handle;
  if (d != NULL) gw_decoder_reset(d);
}

JNIEXPORT void JNICALL FN(decoderFree)(JNIEnv* env, jclass cls, jlong handle) {
  gw_decoder_free((GwDecoder*)(intptr_t)handle);
}
