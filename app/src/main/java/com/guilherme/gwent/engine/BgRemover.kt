package com.guilherme.gwent.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class BgRemovalException(msg: String) : Exception(msg)

/** Tira o fundo da foto, deixando só a pessoa/objeto principal (no próprio celular). */
object BgRemover {

    private val client by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
    }

    suspend fun remove(context: Context, bmp: Bitmap, onStatus: (String) -> Unit): Bitmap {
        ensureModule(context, onStatus)
        onStatus("Recortando…")
        val input = Sources.scaleDown(bmp, 1600)
        val result = try {
            client.process(InputImage.fromBitmap(input, 0)).await()
        } catch (e: Exception) {
            throw BgRemovalException("Não deu para recortar agora. Confira a internet e tente de novo.")
        }
        return result.foregroundBitmap
            ?: throw BgRemovalException("Não encontrei ninguém nem nada em destaque na foto.")
    }

    private suspend fun ensureModule(context: Context, onStatus: (String) -> Unit) {
        val mi = ModuleInstall.getClient(context)
        val available = try {
            mi.areModulesAvailable(client).await().areModulesAvailable()
        } catch (e: Exception) {
            throw BgRemovalException("Este celular não tem o Google Play Services necessário para recortar o fundo.")
        }
        if (available) return
        onStatus("Baixando o recurso de recorte (só na 1ª vez)…")
        try {
            withTimeout(120_000) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : InstallStatusListener {
                        override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                            when (update.installState) {
                                ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED ->
                                    if (cont.isActive) cont.resume(true)
                                ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                                ModuleInstallStatusUpdate.InstallState.STATE_CANCELED ->
                                    if (cont.isActive) cont.resume(false)
                                else -> {}
                            }
                        }
                    }
                    val req = ModuleInstallRequest.newBuilder().addApi(client).setListener(listener).build()
                    mi.installModules(req)
                        .addOnSuccessListener { if (it.areModulesAlreadyInstalled() && cont.isActive) cont.resume(true) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(false) }
                    cont.invokeOnCancellation { mi.unregisterListener(listener) }
                }
            }.let { ok -> if (!ok) throw BgRemovalException("Não consegui baixar o recurso de recorte. Tente com Wi-Fi.") }
        } catch (e: BgRemovalException) {
            throw e
        } catch (e: Exception) {
            throw BgRemovalException("O download do recurso de recorte demorou demais. Tente de novo daqui a pouco.")
        }
    }

    /** Retângulo onde há algo visível (para enquadrar o recorte). */
    fun visibleBounds(bmp: Bitmap): Rect? {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 24) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    b = y
                }
            }
        }
        return if (r < 0) null else Rect(l, t, r + 1, b + 1)
    }

    /** Enquadramento que centraliza a área visível com uma pequena margem. */
    fun framingFor(bmp: Bitmap, srcW: Int, srcH: Int): Framing {
        val rect = visibleBounds(bmp) ?: return Framing.fit(srcW, srcH)
        val kx = srcW.toFloat() / bmp.width
        val ky = srcH.toFloat() / bmp.height
        val bw = rect.width() * kx
        val bh = rect.height() * ky
        val scale = minOf(472f / bw, 472f / bh)
        val cx = rect.exactCenterX() * kx - srcW / 2f
        val cy = rect.exactCenterY() * ky - srcH / 2f
        return Framing(scale, -cx * scale, -cy * scale)
    }
}
