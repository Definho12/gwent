package com.guilherme.gwent.engine

/** Ponte para a libwebp (código em app/src/main/cpp). */
object NativeWebp {
    init {
        System.loadLibrary("gwentwebp")
    }

    @JvmStatic external fun encoderNew(width: Int, height: Int): Long
    @JvmStatic external fun encoderAdd(handle: Long, argb: IntArray, timestampMs: Int, quality: Float, method: Int): Boolean
    @JvmStatic external fun encoderFinish(handle: Long, endTimestampMs: Int): ByteArray?
    @JvmStatic external fun encoderFree(handle: Long)

    @JvmStatic external fun decoderNew(data: ByteArray): Long
    /** {largura, altura, quadros} */
    @JvmStatic external fun decoderInfo(handle: Long): IntArray?
    /** Preenche [out] e devolve o fim do quadro em ms, ou -1 quando acabar. */
    @JvmStatic external fun decoderNext(handle: Long, out: IntArray): Int
    @JvmStatic external fun decoderReset(handle: Long)
    @JvmStatic external fun decoderFree(handle: Long)
}
