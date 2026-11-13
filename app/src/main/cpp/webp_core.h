#ifndef GWENT_WEBP_CORE_H_
#define GWENT_WEBP_CORE_H_

#include <stddef.h>
#include <stdint.h>

typedef struct GwEncoder GwEncoder;
typedef struct GwDecoder GwDecoder;

// Codificador de WebP animado. Os quadros chegam como ARGB (0xAARRGGBB,
// sem pré-multiplicar), do mesmo jeito que o Bitmap.getPixels() do Android.
GwEncoder* gw_encoder_new(int width, int height);
int gw_encoder_add(GwEncoder* g, const uint32_t* argb, int timestamp_ms,
                   float quality, int method);
// Fecha a animação (a duração do último quadro termina em end_timestamp_ms).
// *out deve ser liberado com free().
int gw_encoder_finish(GwEncoder* g, int end_timestamp_ms, uint8_t** out,
                      size_t* out_size);
void gw_encoder_free(GwEncoder* g);

// Leitor de WebP (animado ou não).
GwDecoder* gw_decoder_new(const uint8_t* bytes, size_t size);
void gw_decoder_info(GwDecoder* d, int* width, int* height, int* frames);
// Escreve o próximo quadro inteiro (largura*altura ARGB). Retorna 0 no fim.
int gw_decoder_next(GwDecoder* d, uint32_t* out_argb, int* timestamp_ms);
void gw_decoder_reset(GwDecoder* d);
void gw_decoder_free(GwDecoder* d);

#endif
