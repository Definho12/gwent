package com.guilherme.gwent

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith

/** Abre o app como se a mídia viesse do "Compartilhar", cria figurinhas e tira prints. */
@RunWith(AndroidJUnit4::class)
class ShotsTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val device = UiDevice.getInstance(inst)

    private fun publish(asset: String, mime: String, video: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "gwent_${SystemClock.uptimeMillis()}_$asset")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/GwentTest" else "Pictures/GwentTest")
        }
        val col = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = ctx.contentResolver.insert(col, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { out -> inst.context.assets.open(asset).use { it.copyTo(out) } }
        return uri
    }

    private fun share(uri: Uri, mime: String) {
        val i = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (first) {
            first = false
            inst.startActivitySync(i)
        } else {
            ctx.startActivity(i)
        }
    }
    private var first = true

    private fun need(what: String, found: Any?) {
        if (found == null) {
            device.takeScreenshot(Report.file("erro-$what.png"))
            Report.file("erro-$what.xml").outputStream().use { device.dumpWindowHierarchy(it) }
            throw AssertionError(what)
        }
    }

    private fun shot(name: String) {
        SystemClock.sleep(1200)
        device.takeScreenshot(Report.file(name))
    }

    @Test fun flow() {
        // Vídeo → figurinha animada
        val video = publish("clip.mp4", "video/mp4", true)
        share(video, "video/mp4")
        need("editor", device.wait(Until.findObject(By.text("Criar figurinha")), 20_000))
        SystemClock.sleep(2500)
        shot("1-editor-video.png")
        device.findObject(By.text("Criar figurinha")).click()
        SystemClock.sleep(1500)
        shot("2-criando.png")
        val t0 = SystemClock.elapsedRealtime()
        need("pacote", device.wait(Until.findObject(By.textContains("WhatsApp")), 120_000))
        Report.log("fluxo vídeo: ${SystemClock.elapsedRealtime() - t0} ms até abrir o pacote")
        shot("3-pacote-animadas.png")

        // Foto → figurinha com texto
        val photo = publish("photo.jpg", "image/jpeg", false)
        share(photo, "image/jpeg")
        need("editor2", device.wait(Until.findObject(By.text("Criar figurinha")), 20_000))
        SystemClock.sleep(1500)
        device.findObject(By.text("Texto")).click()
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
        field?.text = "Bom dia grupo"
        shot("4-editor-foto-texto.png")
        device.findObject(By.text("Criar figurinha")).click()
        need("pacote2", device.wait(Until.findObject(By.textContains("WhatsApp")), 60_000))
        shot("5-pacote-normais.png")

        // GIF → mais uma no pacote de animadas
        val gif = publish("anim.gif", "image/gif", false)
        share(gif, "image/gif")
        need("editor2", device.wait(Until.findObject(By.text("Criar figurinha")), 20_000))
        SystemClock.sleep(1500)
        shot("6-editor-gif.png")
        device.findObject(By.text("Criar figurinha")).click()
        need("pacote2", device.wait(Until.findObject(By.textContains("WhatsApp")), 60_000))
        SystemClock.sleep(1500)
        device.pressBack()
        SystemClock.sleep(1500)
        shot("7-inicio.png")
    }
}
