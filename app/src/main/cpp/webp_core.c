// Núcleo em C (sem JNI) para gerar e ler WebP animado com a libwebp.
// Fica separado do JNI para poder ser testado fora do Android.
#include "webp_core.h"

#include <stdlib.h>
#include <string.h>

#include "webp/demux.h"
#include "webp/encode.h"
#include "webp/mux.h"

struct GwEncoder {
  WebPAnimEncoder* enc;
  int width, height;
};

GwEncoder* gw_encoder_new(int width, int height) {
  WebPAnimEncoderOptions opts;
  if (!WebPAnimEncoderOptionsInit(&opts)) return NULL;
  opts.anim_params.loop_count = 0;          // repete para sempre
  opts.anim_params.bgcolor = 0x00000000;    // fundo transparente
  opts.minimize_size = 0;
  opts.allow_mixed = 0;
  opts.kmin = 2;
  opts.kmax = 8;
  WebPAnimEncoder* enc = WebPAnimEncoderNew(width, height, &opts);
  if (enc == NULL) return NULL;
  GwEncoder* g = (GwEncoder*)calloc(1, sizeof(GwEncoder));
  g->enc = enc;
  g->width = width;
  g->height = height;
  return g;
}

int gw_encoder_add(GwEncoder* g, const uint32_t* argb, int timestamp_ms,
                   float quality, int method) {
  WebPConfig config;
  WebPPicture pic;
  if (!WebPConfigInit(&config)) return 0;
  config.lossless = 0;
  config.quality = quality;
  config.method = method;
  config.alpha_quality = 80;
  config.alpha_filtering = 1;
  config.use_sharp_yuv = 0;
  config.thread_level = 1;
  if (!WebPValidateConfig(&config)) return 0;
  if (!WebPPictureInit(&pic)) return 0;
  pic.use_argb = 1;
  pic.width = g->width;
  pic.height = g->height;
  if (!WebPPictureAlloc(&pic)) return 0;
  for (int y = 0; y < g->height; ++y) {
    memcpy(pic.argb + (size_t)y * pic.argb_stride, argb + (size_t)y * g->width,
           (size_t)g->width * 4);
  }
  int ok = WebPAnimEncoderAdd(g->enc, &pic, timestamp_ms, &config);
  WebPPictureFree(&pic);
  return ok;
}

int gw_encoder_finish(GwEncoder* g, int end_timestamp_ms, uint8_t** out,
                      size_t* out_size) {
  *out = NULL;
  *out_size = 0;
  if (!WebPAnimEncoderAdd(g->enc, NULL, end_timestamp_ms, NULL)) return 0;
  WebPData data;
  WebPDataInit(&data);
  if (!WebPAnimEncoderAssemble(g->enc, &data)) return 0;
  *out = (uint8_t*)malloc(data.size);
  if (*out == NULL) {
    WebPDataClear(&data);
    return 0;
  }
  memcpy(*out, data.bytes, data.size);
  *out_size = data.size;
  WebPDataClear(&data);
  return 1;
}

void gw_encoder_free(GwEncoder* g) {
  if (g == NULL) return;
  if (g->enc != NULL) WebPAnimEncoderDelete(g->enc);
  free(g);
}

struct GwDecoder {
  uint8_t* bytes;
  WebPData data;
  WebPAnimDecoder* dec;
  WebPAnimInfo info;
};

GwDecoder* gw_decoder_new(const uint8_t* bytes, size_t size) {
  GwDecoder* d = (GwDecoder*)calloc(1, sizeof(GwDecoder));
  if (d == NULL) return NULL;
  d->bytes = (uint8_t*)malloc(size);
  if (d->bytes == NULL) {
    free(d);
    return NULL;
  }
  memcpy(d->bytes, bytes, size);
  d->data.bytes = d->bytes;
  d->data.size = size;
  WebPAnimDecoderOptions opts;
  if (!WebPAnimDecoderOptionsInit(&opts)) {
    gw_decoder_free(d);
    return NULL;
  }
  opts.color_mode = MODE_RGBA;  // sem pré-multiplicar
  opts.use_threads = 0;
  d->dec = WebPAnimDecoderNew(&d->data, &opts);
  if (d->dec == NULL || !WebPAnimDecoderGetInfo(d->dec, &d->info)) {
    gw_decoder_free(d);
    return NULL;
  }
  return d;
}

void gw_decoder_info(GwDecoder* d, int* width, int* height, int* frames) {
  *width = (int)d->info.canvas_width;
  *height = (int)d->info.canvas_height;
  *frames = (int)d->info.frame_count;
}

int gw_decoder_next(GwDecoder* d, uint32_t* out_argb, int* timestamp_ms) {
  if (!WebPAnimDecoderHasMoreFrames(d->dec)) return 0;
  uint8_t* rgba;
  int ts;
  if (!WebPAnimDecoderGetNext(d->dec, &rgba, &ts)) return 0;
  const size_t n = (size_t)d->info.canvas_width * d->info.canvas_height;
  for (size_t i = 0; i < n; ++i) {
    const uint8_t* p = rgba + i * 4;
    out_argb[i] = ((uint32_t)p[3] << 24) | ((uint32_t)p[0] << 16) |
                  ((uint32_t)p[1] << 8) | (uint32_t)p[2];
  }
  *timestamp_ms = ts;
  return 1;
}

void gw_decoder_reset(GwDecoder* d) { WebPAnimDecoderReset(d->dec); }

void gw_decoder_free(GwDecoder* d) {
  if (d == NULL) return;
  if (d->dec != NULL) WebPAnimDecoderDelete(d->dec);
  free(d->bytes);
  free(d);
}
