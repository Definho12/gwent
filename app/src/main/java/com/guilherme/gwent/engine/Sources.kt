package com.guilherme.gwent.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

/** Qualquer mídia de onde sai uma figurinha: foto, GIF, WebP animado ou vídeo. */
interface MediaSource : Closeable {
    val width: Int
    val height: Int
    /** 0 = imagem parada. */
    val durationMs: Long
    /** Quadros por segundo aproximados da mídia original. */
    val nativeFps: Float
    val animated: Boolean get() = durationMs > 0

    /** Um quadro para mostrar na tela ou para virar figurinha parada. */
    fun frameAt(ms: Long, maxSide: Int): Bitmap

    /**
     * Percorre o trecho [startMs, endMs) a [fps] quadros por segundo.
     * O Bitmap entregue pode ser reaproveitado: use-o só dentro do bloco.
     */
    fun forEachFrame(startMs: Long, endMs: Long, fps: Int, block: (Bitmap, Int) -> Unit)

    override fun close() {}
}

class UnsupportedMediaException(msg: String) : Exception(msg)

object Sources {
    fun open(context: Context, uri: Uri): MediaSource {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: ""
        val head = ByteArray(16)
        val n = try {
            cr.openInputStream(uri)?.use { it.read(head) } ?: -1
        } catch (e: Exception) {
            throw UnsupportedMediaException("Não consegui abrir o arquivo.")
        }
        if (n <= 0) throw UnsupportedMediaException("Arquivo vazio ou sem permissão.")
        val isGif = head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte()
        val isWebp = String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" &&
            String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP"
        val brand = String(head, 8, 4, Charsets.ISO_8859_1)
        val imageBrand = brand in setOf("heic", "heix", "heim", "heis", "mif1", "msf1", "avif", "avis")
        val isVideoHead = (String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" && !imageBrand) ||
            (head[0] == 0x1A.toByte() && head[1] == 0x45.toByte()) // mp4/mov ou webm/mkv

        if (mime.startsWith("video/") || (isVideoHead && !mime.startsWith("image/"))) {
            return VideoSource(context, uri)
        }
        if (isGif) {
            val bytes = readAll(context, uri)
            val gif = GifSource(bytes)
            if (gif.frameCount > 1) return gif
            gif.close()
        }
        if (isWebp) {
            val bytes = readAll(context, uri)
            val webp = WebpSource.tryOpen(bytes)
            if (webp != null) {
                if (webp.frameCount > 1) return webp
                webp.close()
            }
        }
        return StaticSource(context, uri)
    }

    private fun readAll(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw UnsupportedMediaException("Não consegui ler o arquivo.")

    fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val m = max(bmp.width, bmp.height)
        if (m <= maxSide) return bmp
        val f = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * f).roundToInt()), max(1, (bmp.height * f).roundToInt()), true)
    }
}

/** Foto ou imagem parada (JPG, PNG, HEIC, WebP, AVIF...). */
class StaticSource(private val context: Context, private val uri: Uri) : MediaSource {
    private var cached: Bitmap? = null
    private var cachedSide = 0
    override var width = 0; private set
    override var height = 0; private set
    override val durationMs = 0L
    override val nativeFps = 0f

    init {
        val bmp = decode(2048)
        cached = bmp
        cachedSide = 2048
    }

    private fun decode(maxSide: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return try {
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                width = info.size.width
                height = info.size.height
                val m = max(info.size.width, info.size.height)
                if (m > maxSide) {
                    val f = maxSide.toFloat() / m
                    decoder.setTargetSize(max(1, (info.size.width * f).roundToInt()), max(1, (info.size.height * f).roundToInt()))
                }
            }
        } catch (e: Exception) {
            throw UnsupportedMediaException("Esse tipo de arquivo não é suportado.")
        }.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }

    override fun frameAt(ms: Long, maxSide: Int): Bitmap {
        val c = cached!!
        return Sources.scaleDown(c, maxSide)
    }

    override fun forEachFrame(startMs: Long, endMs: Long, fps: Int, block: (Bitmap, Int) -> Unit) {
        block(cached!!, 0)
    }
}

/** Base para mídias que entregam quadros em sequência (GIF e WebP animado). */
abstract class SequentialSource : MediaSource {
    /** Volta ao primeiro quadro. */
    protected abstract fun rewind()
    /** Próximo quadro e o momento em que ele termina (ms), ou null no fim. */
    protected abstract fun next(): Pair<Bitmap, Long>?

    override fun frameAt(ms: Long, maxSide: Int): Bitmap {
        rewind()
        var last: Bitmap? = null
        while (true) {
            val (bmp, end) = next() ?: break
            last = bmp
            if (end > ms) break
        }
        val b = last ?: throw UnsupportedMediaException("Animação sem quadros.")
        return Sources.scaleDown(b, maxSide).let { if (it === b) b.copy(Bitmap.Config.ARGB_8888, false) else it }
    }

    override fun forEachFrame(startMs: Long, endMs: Long, fps: Int, block: (Bitmap, Int) -> Unit) {
        rewind()
        val count = Plan.frameCount(endMs - startMs, fps)
        var current = next() ?: return
        var k = 0
        while (k < count) {
            val t = startMs + Plan.frameTime(k, fps)
            // avança até o quadro que está na tela no momento t
            while (current.second <= t) {
                current = next() ?: break
            }
            block(current.first, k)
            k++
        }
    }
}

class GifSource(bytes: ByteArray) : SequentialSource() {
    private val decoder: StandardGifDecoder
    val frameCount: Int
    override val width: Int
    override val height: Int
    override val durationMs: Long
    override val nativeFps: Float
    private var elapsed = 0L

    init {
        decoder = StandardGifDecoder(SimpleProvider)
        decoder.setDefaultBitmapConfig(Bitmap.Config.ARGB_8888)
        val status = decoder.read(bytes)
        if (status != GifDecoder.STATUS_OK && decoder.frameCount == 0) {
            throw UnsupportedMediaException("Não consegui ler esse GIF.")
        }
        frameCount = decoder.frameCount
        width = decoder.width
        height = decoder.height
        var total = 0L
        for (i in 0 until frameCount) total += delay(i)
        durationMs = if (frameCount > 1) total else 0L
        nativeFps = if (total > 0) frameCount * 1000f / total else 10f
    }

    private fun delay(i: Int): Long {
        val d = decoder.getDelay(i)
        return if (d < 20) 100L else d.toLong()
    }

    override fun rewind() {
        decoder.resetFrameIndex()
        elapsed = 0L
    }

    override fun next(): Pair<Bitmap, Long>? {
        if (decoder.currentFrameIndex >= frameCount - 1) return null
        decoder.advance()
        val bmp = decoder.nextFrame ?: return null
        elapsed += delay(decoder.currentFrameIndex)
        return bmp to elapsed
    }

    override fun close() {
        decoder.clear()
    }

    private object SimpleProvider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) {}
        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
        override fun release(bytes: ByteArray) {}
        override fun obtainIntArray(size: Int): IntArray = IntArray(size)
        override fun release(array: IntArray) {}
    }
}

/** WebP (inclusive figurinhas animadas de outros apps), lido pela libwebp. */
class WebpSource private constructor(private val handle: Long) : SequentialSource() {
    override val width: Int
    override val height: Int
    val frameCount: Int
    override val durationMs: Long
    override val nativeFps: Float
    private val pixels: IntArray
    private val bitmap: Bitmap

    init {
        val info = NativeWebp.decoderInfo(handle)!!
        width = info[0]; height = info[1]; frameCount = info[2]
        pixels = IntArray(width * height)
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var last = 0L
        if (frameCount > 1) {
            NativeWebp.decoderReset(handle)
            while (true) {
                val ts = NativeWebp.decoderNext(handle, pixels)
                if (ts < 0) break
                last = ts.toLong()
            }
        }
        durationMs = if (frameCount > 1) max(last, 100L) else 0L
        nativeFps = if (durationMs > 0) frameCount * 1000f / durationMs else 0f
        NativeWebp.decoderReset(handle)
    }

    override fun rewind() = NativeWebp.decoderReset(handle)

    override fun next(): Pair<Bitmap, Long>? {
        val ts = NativeWebp.decoderNext(handle, pixels)
        if (ts < 0) return null
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap to ts.toLong()
    }

    override fun close() = NativeWebp.decoderFree(handle)

    companion object {
        fun tryOpen(bytes: ByteArray): WebpSource? {
            val h = NativeWebp.decoderNew(bytes)
            if (h == 0L) return null
            return WebpSource(h)
        }
    }
}

/** Vídeo (MP4, WebM, MOV, 3GP...). O som é ignorado. */
class VideoSource(context: Context, uri: Uri) : MediaSource {
    private val mmr = MediaMetadataRetriever()
    override val width: Int
    override val height: Int
    override val durationMs: Long
    override val nativeFps: Float
    private val frameCount: Int

    init {
        try {
            mmr.setDataSource(context, uri)
        } catch (e: Exception) {
            mmr.release()
            throw UnsupportedMediaException("Não consegui abrir esse vídeo.")
        }
        fun meta(key: Int) = mmr.extractMetadata(key)
        val hasVideo = meta(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        if (!hasVideo) {
            mmr.release()
            throw UnsupportedMediaException("Esse arquivo não tem imagem (só áudio?).")
        }
        val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { width = h; height = w } else { width = w; height = h }
        durationMs = max(100L, meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
        frameCount = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
        val capFps = meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
        nativeFps = when {
            frameCount > 0 -> frameCount * 1000f / durationMs
            capFps != null && capFps > 0 -> capFps
            else -> 30f
        }
        if (width <= 0 || height <= 0) {
            val probe = mmr.getFrameAtTime(0)
            if (probe == null) {
                mmr.release()
                throw UnsupportedMediaException("Não consegui ler os quadros desse vídeo.")
            }
        }
    }

    /** Corrige a orientação caso o aparelho entregue o quadro deitado. */
    private fun upright(b: Bitmap): Bitmap {
        val landscape = width > height
        val bLandscape = b.width > b.height
        if (width == height || b.width == b.height || landscape == bLandscape) return b
        val m = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private fun argb(b: Bitmap): Bitmap =
        if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)

    override fun frameAt(ms: Long, maxSide: Int): Bitmap {
        val us = ms.coerceIn(0, durationMs - 1) * 1000
        val b = try {
            mmr.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, maxSide, maxSide)
        } catch (e: Exception) { null }
            ?: mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: throw UnsupportedMediaException("Não consegui ler esse trecho do vídeo.")
        return argb(upright(Sources.scaleDown(b, maxSide)))
    }

    override fun forEachFrame(startMs: Long, endMs: Long, fps: Int, block: (Bitmap, Int) -> Unit) {
        val count = Plan.frameCount(endMs - startMs, fps)
        val bigFrame = width.toLong() * height > 2_400_000L
        if (frameCount > 0 && !bigFrame) {
            val ok = try {
                byIndex(startMs, count, fps, block)
                true
            } catch (e: Exception) { false }
            if (ok) return
        }
        for (k in 0 until count) {
            val t = startMs + Plan.frameTime(k, fps)
            block(frameAt(t, 1024), k)
        }
    }

    /** Lê quadros seguidos em lotes (bem mais rápido que procurar um por um). */
    private fun byIndex(startMs: Long, count: Int, fps: Int, block: (Bitmap, Int) -> Unit) {
        val srcFps = frameCount * 1000.0 / durationMs
        val wanted = IntArray(count) { k ->
            val t = startMs + Plan.frameTime(k, fps)
            (t * srcFps / 1000.0).toInt().coerceIn(0, frameCount - 1)
        }
        val batch = (64_000_000L / (width.toLong() * height * 4)).toInt().coerceIn(2, 24)
        var k = 0
        var idx = wanted[0]
        val last = wanted[count - 1]
        while (idx <= last && k < count) {
            val n = minOf(batch, last - idx + 1)
            val frames = mmr.getFramesAtIndex(idx, n)
            if (frames.isEmpty()) throw IllegalStateException("sem quadros")
            for ((j, f) in frames.withIndex()) {
                val fi = idx + j
                var prepared: Bitmap? = null
                while (k < count && wanted[k] == fi) {
                    if (prepared == null) prepared = argb(upright(f))
                    block(prepared, k)
                    k++
                }
            }
            idx += frames.size
        }
        while (k < count) { // fim do vídeo antes do previsto: repete o último
            block(frameAt(startMs + Plan.frameTime(k, fps), 1024), k)
            k++
        }
    }

    override fun close() {
        try { mmr.release() } catch (_: Exception) {}
    }
}
