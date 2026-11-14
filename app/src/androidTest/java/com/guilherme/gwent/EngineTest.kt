package com.guilherme.gwent

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guilherme.gwent.data.Packs
import com.guilherme.gwent.data.StickerProvider
import com.guilherme.gwent.engine.Caption
import com.guilherme.gwent.engine.Framing
import com.guilherme.gwent.engine.GifSource
import com.guilherme.gwent.engine.Limits
import com.guilherme.gwent.engine.Links
import com.guilherme.gwent.engine.MadeSticker
import com.guilherme.gwent.engine.Maker
import com.guilherme.gwent.engine.NativeWebp
import com.guilherme.gwent.engine.Sources
import com.guilherme.gwent.engine.StaticSource
import com.guilherme.gwent.engine.VideoSource
import com.guilherme.gwent.engine.WebpSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Teste de ponta a ponta do motor, rodando num emulador no GitHub. */
@RunWith(AndroidJUnit4::class)
class EngineTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val testCtx = InstrumentationRegistry.getInstrumentation().context

    private fun asset(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        testCtx.assets.open(name).use { input -> f.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(f)
    }

    private fun checkAnimated(name: String, made: MadeSticker, ms: Long): Int {
        val h = NativeWebp.decoderNew(made.webp)
        assertTrue("$name: decodificar", h != 0L)
        val info = NativeWebp.decoderInfo(h)!!
        val px = IntArray(info[0] * info[1])
        var last = 0
        while (true) { val t = NativeWebp.decoderNext(h, px); if (t < 0) break; last = t }
        NativeWebp.decoderFree(h)
        Report.log("$name: ${made.webp.size / 1024} KB, ${info[0]}x${info[1]}, ${info[2]} quadros, ${last} ms, feito em ${ms} ms")
        assertEquals(512, info[0]); assertEquals(512, info[1])
        assertTrue("$name: precisa ser animada", info[2] > 1)
        assertTrue("$name: tamanho", made.webp.size <= Limits.ANIMATED_MAX_BYTES)
        assertTrue("$name: duração", last <= Limits.MAX_DURATION_MS)
        checkTray(name, made)
        return last
    }

    private fun checkTray(name: String, made: MadeSticker) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(made.tray, 0, made.tray.size, o)
        assertEquals("$name: ícone", 96, o.outWidth)
        assertTrue("$name: ícone pesado", made.tray.size <= Limits.TRAY_MAX_BYTES)
    }

    private fun animate(name: String, startMs: Long, endMs: Long, caption: Caption? = null): MadeSticker {
        val src = Sources.open(ctx, asset(name))
        Report.log("$name: ${src.javaClass.simpleName} ${src.width}x${src.height}, ${src.durationMs} ms, ${src.nativeFps} fps")
        val t0 = SystemClock.elapsedRealtime()
        val made = Maker.makeAnimated(src, startMs, endMs, Framing.fill(src.width, src.height), caption, ctx.cacheDir, {})
        val ms = SystemClock.elapsedRealtime() - t0
        src.close()
        checkAnimated(name, made, ms)
        return made
    }

    @Test fun photoBecomesStaticSticker() {
        val src = Sources.open(ctx, asset("photo.jpg"))
        assertTrue(src is StaticSource)
        assertEquals(1600, src.width)
        val t0 = SystemClock.elapsedRealtime()
        val made = Maker.makeStatic(src.frameAt(0, 2048), src.width, src.height, Framing.fill(src.width, src.height), Caption("Bom dia grupo"))
        Report.log("photo.jpg: ${made.webp.size / 1024} KB em ${SystemClock.elapsedRealtime() - t0} ms")
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(made.webp, 0, made.webp.size, o)
        assertEquals(512, o.outWidth); assertEquals(512, o.outHeight)
        assertTrue(made.webp.size <= Limits.STATIC_MAX_BYTES)
        checkTray("photo", made)
        Report.saveSticker("static.webp", made.webp)
    }

    @Test fun gif() {
        val src = Sources.open(ctx, asset("anim.gif"))
        assertTrue(src is GifSource); src.close()
        animate("anim.gif", 0, 10_000)
    }

    @Test fun animatedWebp() {
        val src = Sources.open(ctx, asset("anim.webp"))
        assertTrue(src is WebpSource); src.close()
        animate("anim.webp", 0, 10_000)
    }

    @Test fun video() {
        val src = Sources.open(ctx, asset("clip.mp4"))
        assertTrue(src is VideoSource); src.close()
        val made = animate("clip.mp4", 0, 4_000, Caption("Kkkkk"))
        Report.saveSticker("video.webp", made.webp)
    }

    @Test fun rotatedVideo() {
        val src = Sources.open(ctx, asset("rotated.mp4"))
        Report.log("rotated.mp4: ${src.width}x${src.height}")
        assertEquals(720, src.width); assertEquals(1280, src.height)
        val f = src.frameAt(500, 800)
        Report.log("rotated.mp4 quadro: ${f.width}x${f.height}")
        assertTrue(f.height > f.width)
        src.close()
        animate("rotated.mp4", 0, 3_000)
    }

    /** Link real que o Guilherme mandou (pin com GIF). */
    @Test fun pinterestLink() {
        val link = "https://pin.it/3A3P3jqK9"
        val media = try { Links.findMedia(link) } catch (e: Exception) { Report.log("pinterest: ERRO ${e.javaClass.simpleName}: ${e.message}"); throw e }
        Report.log("pinterest: mídia = $media")
        val f = Links.fetch(link, File(ctx.cacheDir, "links")) {}
        Report.log("pinterest: baixou ${f.name} (${f.length() / 1024} KB)")
        val src = Sources.open(ctx, Uri.fromFile(f))
        Report.log("pinterest: ${src.javaClass.simpleName} ${src.width}x${src.height}, ${src.durationMs} ms, ${src.nativeFps} fps")
        assertTrue("pinterest: precisa ser animado", src.animated)
        val t0 = SystemClock.elapsedRealtime()
        val made = Maker.makeAnimated(src, 0, minOf(src.durationMs, 5_000), Framing.fit(src.width, src.height), null, ctx.cacheDir, {})
        src.close()
        checkAnimated("pinterest", made, SystemClock.elapsedRealtime() - t0)
        Report.saveSticker("pinterest.webp", made.webp)
    }

    /** Segundo link real que ele mandou (antes vinha a imagem genérica do Pinterest). */
    @Test fun pinterestLink2() {
        val link = "https://pin.it/7vJj846RC"
        try {
            val media = Links.findMedia(link)
            Report.log("pinterest2: mídia = $media")
        } catch (e: Exception) {
            Report.log("pinterest2: ERRO ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            Links.trace.forEach { Report.log("pinterest2 · $it") }
        }
        // diagnóstico extra: o que a página traz
        try {
            val c = java.net.URL(Links.trace.firstOrNull { it.startsWith("final: ") }?.removePrefix("final: ") ?: link)
                .openConnection() as java.net.HttpURLConnection
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
            val html = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            Regex("""https?://[a-z0-9.]*pinimg\.com/[^"'\\\s]+""").findAll(html).map { it.value }.distinct().take(25)
                .forEach { Report.log("pinterest2 página · $it") }
            Regex("""<meta[^>]+og:[^>]+>""").findAll(html).take(10).forEach { Report.log("pinterest2 meta · ${it.value.take(200)}") }
        } catch (e: Exception) { Report.log("pinterest2 página ERRO ${e.message}") }
        val media = Links.findMedia(link)
        assertTrue("pinterest2: não pode ser a imagem genérica", media.contains("pinimg.com"))
    }

    @Test fun speed() {
        val v = Sources.open(ctx, asset("clip.mp4"))
        val fast = Maker.makeAnimated(v, 0, 4_000, Framing.fill(v.width, v.height), null, ctx.cacheDir, {}, speed = 2f)
        v.close()
        val fastMs = checkAnimated("clip.mp4 2x", fast, 0)
        assertTrue("2x: ~2 s, deu $fastMs", fastMs in 1800..2200)
        val g = Sources.open(ctx, asset("anim.gif"))
        val slow = Maker.makeAnimated(g, 0, g.durationMs, Framing.fill(g.width, g.height), null, ctx.cacheDir, {}, speed = 0.5f)
        val expected = g.durationMs * 2
        g.close()
        val slowMs = checkAnimated("anim.gif 0.5x", slow, 0)
        assertTrue("0.5x: ~$expected ms, deu $slowMs", slowMs in (expected - 250)..(expected + 250))
        // 3x num trecho de 30 s ficaria com 10 s; aqui o vídeo tem 4 s
        val v3 = Sources.open(ctx, asset("clip.mp4"))
        val f3 = Maker.makeAnimated(v3, 0, 4_000, Framing.fill(v3.width, v3.height), null, ctx.cacheDir, {}, speed = 3f)
        v3.close()
        checkAnimated("clip.mp4 3x", f3, 0)
    }

    @Test fun providerServesPackToWhatsApp() {
        val src = Sources.open(ctx, asset("photo.jpg"))
        val made = Maker.makeStatic(src.frameAt(0, 2048), src.width, src.height, Framing.fit(src.width, src.height), null)
        val pack = Packs.add(ctx, made, Packs.create(ctx, false, "Teste").id)
        val auth = StickerProvider.authority(ctx.packageName)
        val cr = ctx.contentResolver
        cr.query(Uri.parse("content://$auth/metadata/${pack.id}"), null, null, null, null)!!.use { c ->
            assertEquals(1, c.count); c.moveToFirst()
            assertEquals("Teste", c.getString(c.getColumnIndexOrThrow("sticker_pack_name")))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("animated_sticker_pack")))
            val tray = c.getString(c.getColumnIndexOrThrow("sticker_pack_icon"))
            cr.openAssetFileDescriptor(Uri.parse("content://$auth/stickers_asset/${pack.id}/$tray"), "r")!!.use {
                assertTrue(it.length > 0)
            }
        }
        val names = mutableListOf<String>()
        cr.query(Uri.parse("content://$auth/stickers/${pack.id}"), null, null, null, null)!!.use { c ->
            while (c.moveToNext()) names += c.getString(0)
        }
        Report.log("provider: ${names.joinToString()}")
        assertEquals(3, names.size)
        assertEquals(3, names.toSet().size)
        names.forEach { n ->
            cr.openAssetFileDescriptor(Uri.parse("content://$auth/stickers_asset/${pack.id}/$n"), "r")!!.use {
                assertEquals(made.webp.size.toLong(), it.length)
            }
        }
        Packs.deletePack(ctx, pack.id)
    }
}

object Report {
    private val dir get() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
    @Synchronized fun log(s: String) {
        android.util.Log.i("GwentTest", s)
        File(dir, "log.txt").appendText(s + "\n")
    }
    fun saveSticker(name: String, bytes: ByteArray) = File(dir, name).writeBytes(bytes)
    fun file(name: String) = File(dir, name)
}
