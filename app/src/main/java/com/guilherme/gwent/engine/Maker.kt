package com.guilherme.gwent.engine

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class TooBigException : Exception("Ficou pesada demais para o WhatsApp. Tente um trecho mais curto.")

/** Resultado pronto para ir para um pacote. */
class MadeSticker(val webp: ByteArray, val tray: ByteArray, val animated: Boolean)

/** Transforma a mídia + enquadramento + texto em arquivos que o WhatsApp aceita. */
object Maker {

    /** Figurinha parada: WebP 512×512 com até 100 KB. */
    fun makeStatic(bmp: Bitmap, srcW: Int, srcH: Int, framing: Framing, caption: Caption?): MadeSticker {
        val frame = Renderer.render(bmp, srcW, srcH, framing, caption)
        val webp = compressStatic(frame)
        return MadeSticker(webp, tray(frame), animated = false)
    }

    fun compressStatic(frame: Bitmap): ByteArray {
        // 1) sem perdas (fica perfeito em desenhos e recortes)
        val lossless = encode(frame, lossless = true, quality = 100)
        if (lossless.size <= Limits.STATIC_TARGET_BYTES) return lossless
        // 2) com perdas, baixando a qualidade até caber
        var q = 92
        while (q >= 10) {
            val b = encode(frame, lossless = false, quality = q)
            if (b.size <= Limits.STATIC_TARGET_BYTES) return b
            q -= if (q > 60) 8 else 10
        }
        throw TooBigException()
    }

    @Suppress("DEPRECATION")
    private fun encode(frame: Bitmap, lossless: Boolean, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val fmt = if (Build.VERSION.SDK_INT >= 30) {
            if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
        } else Bitmap.CompressFormat.WEBP
        frame.compress(fmt, if (lossless) 100 else quality.coerceAtMost(99), out)
        return out.toByteArray()
    }

    /** Ícone 96×96 do pacote (PNG). */
    fun tray(frame: Bitmap): ByteArray {
        val small = Bitmap.createScaledBitmap(frame, Limits.TRAY, Limits.TRAY, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.PNG, 100, out)
        var bytes = out.toByteArray()
        if (bytes.size > Limits.TRAY_MAX_BYTES) {
            val o2 = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            small.compress(Bitmap.CompressFormat.WEBP, 80, o2)
            bytes = o2.toByteArray()
        }
        return bytes
    }

    /**
     * Figurinha animada: WebP animado 512×512, até 10 s e 500 KB.
     * [progress] vai de 0 a 1.
     */
    fun makeAnimated(
        source: MediaSource,
        startMs: Long,
        endMs: Long,
        framing: Framing,
        caption: Caption?,
        cacheDir: File,
        progress: (Float) -> Unit,
        cancelled: () -> Boolean = { false },
        speed: Float = 1f,
    ): MadeSticker {
        val (s, e) = Plan.clampRange(startMs, endMs, source.durationMs, Plan.maxSpan(speed))
        // duração da figurinha (o trecho fica mais curto ou mais longo conforme a velocidade)
        val duration = minOf(Limits.MAX_DURATION_MS, ((e - s) / speed).toLong().coerceAtLeast(100))
        val storedFps = (source.nativeFps * speed).roundToInt().coerceIn(4, Limits.MAX_FPS)
        val total = Plan.frameCount(duration, storedFps)
        val store = FrameStore(File(cacheDir, "frames.raw"))
        var trayBytes: ByteArray? = null
        try {
            // 1) desenha todos os quadros uma vez e guarda num arquivo temporário
            val canvasBmp = Bitmap.createBitmap(Limits.SIZE, Limits.SIZE, Bitmap.Config.ARGB_8888)
            val px = IntArray(Limits.SIZE * Limits.SIZE)
            source.forEachFrame(s, e, storedFps / speed.toDouble()) { bmp, k ->
                if (k >= total) return@forEachFrame
                if (cancelled()) throw InterruptedException()
                Renderer.render(bmp, source.width, source.height, framing, caption, canvasBmp)
                if (k == 0) trayBytes = tray(canvasBmp)
                canvasBmp.getPixels(px, 0, Limits.SIZE, 0, 0, Limits.SIZE, Limits.SIZE)
                store.write(px)
                progress(0.45f * (k + 1) / total)
            }
            if (store.count == 0) throw UnsupportedMediaException("Não consegui ler os quadros.")

            // 2) tenta comprimir, baixando qualidade/fps até caber
            val attempts = Plan.attempts(storedFps)
            var i = 0
            var tried = 0
            while (i < attempts.size) {
                val a = attempts[i]
                val bytes = encode(store, storedFps, duration, a, cancelled) { f ->
                    val base = 0.45f + 0.55f * (tried / 3f).coerceAtMost(0.85f)
                    progress((base + f * 0.15f).coerceAtMost(0.98f))
                }
                tried++
                if (bytes.size <= Limits.ANIMATED_TARGET_BYTES) {
                    progress(1f)
                    return MadeSticker(bytes, trayBytes!!, animated = true)
                }
                i += Plan.skip(bytes.size.toLong(), Limits.ANIMATED_TARGET_BYTES.toLong())
                if (i >= attempts.size && attempts.last() != a) i = attempts.size - 1
            }
            throw TooBigException()
        } finally {
            store.close()
        }
    }

    private fun encode(
        store: FrameStore, storedFps: Int, duration: Long, a: Attempt,
        cancelled: () -> Boolean, progress: (Float) -> Unit,
    ): ByteArray {
        val picks = Plan.pick(store.count, storedFps, duration, a.fps)
        val enc = NativeWebp.encoderNew(Limits.SIZE, Limits.SIZE)
        if (enc == 0L) throw IllegalStateException("Falha ao iniciar o codificador")
        try {
            val px = IntArray(Limits.SIZE * Limits.SIZE)
            picks.forEachIndexed { k, idx ->
                if (cancelled()) throw InterruptedException()
                store.read(idx, px)
                val method = if (picks.size > 90) 1 else 2
                if (!NativeWebp.encoderAdd(enc, px, Plan.frameTime(k, a.fps), a.quality, method)) {
                    throw IllegalStateException("Falha ao montar a animação")
                }
                progress((k + 1f) / picks.size)
            }
            val end = maxOf(duration.toInt(), Plan.frameTime(picks.size - 1, a.fps) + 10)
            return NativeWebp.encoderFinish(enc, end) ?: throw IllegalStateException("Falha ao finalizar a animação")
        } finally {
            NativeWebp.encoderFree(enc)
        }
    }
}

/** Guarda quadros 512×512 num arquivo temporário (para não estourar a memória). */
class FrameStore(private val file: File) : AutoCloseable {
    private val frameBytes = Limits.SIZE * Limits.SIZE * 4
    private val raf = RandomAccessFile(file, "rw").apply { setLength(0) }
    private val buf: ByteBuffer = ByteBuffer.allocateDirect(frameBytes).order(ByteOrder.nativeOrder())
    var count = 0; private set

    fun write(px: IntArray) {
        buf.clear()
        buf.asIntBuffer().put(px)
        buf.position(0).limit(frameBytes)
        raf.channel.write(buf, count.toLong() * frameBytes)
        count++
    }

    fun read(i: Int, into: IntArray) {
        buf.clear()
        var pos = i.toLong() * frameBytes
        while (buf.hasRemaining()) {
            val n = raf.channel.read(buf, pos)
            if (n <= 0) break
            pos += n
        }
        buf.flip()
        buf.asIntBuffer().get(into)
    }

    override fun close() {
        try { raf.close() } catch (_: Exception) {}
        file.delete()
    }
}
