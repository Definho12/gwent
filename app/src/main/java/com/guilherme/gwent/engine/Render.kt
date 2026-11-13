package com.guilherme.gwent.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min

/**
 * Como a mídia fica dentro do quadrado de 512×512.
 * [scale] = pixels da figurinha por pixel da mídia original;
 * [dx], [dy] = deslocamento do centro da mídia em relação ao centro do quadrado.
 */
data class Framing(val scale: Float, val dx: Float = 0f, val dy: Float = 0f) {
    companion object {
        const val S = Limits.SIZE.toFloat()
        fun fill(w: Int, h: Int) = Framing(max(S / w, S / h))
        fun fit(w: Int, h: Int) = Framing(min(S / w, S / h))
    }
}

enum class CaptionPos { TOP, BOTTOM }

data class Caption(val text: String, val pos: CaptionPos = CaptionPos.BOTTOM, val color: Int = Color.WHITE) {
    val visible get() = text.isNotBlank()
}

object Renderer {
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Desenha a mídia num canvas de 512×512 (o canvas pode estar escalado para a tela).
     * [srcW]/[srcH] = tamanho real da mídia; [bmp] pode ser uma versão reduzida dela.
     */
    fun draw(canvas: Canvas, bmp: Bitmap, srcW: Int, srcH: Int, framing: Framing, caption: Caption?) {
        val kx = srcW.toFloat() / bmp.width
        val ky = srcH.toFloat() / bmp.height
        val m = Matrix()
        m.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        m.postScale(framing.scale * kx, framing.scale * ky)
        m.postTranslate(Framing.S / 2 + framing.dx, Framing.S / 2 + framing.dy)
        canvas.drawBitmap(bmp, m, bmpPaint)
        if (caption != null && caption.visible) drawCaption(canvas, caption)
    }

    fun render(bmp: Bitmap, srcW: Int, srcH: Int, framing: Framing, caption: Caption?, into: Bitmap? = null): Bitmap {
        val out = into ?: Bitmap.createBitmap(Limits.SIZE, Limits.SIZE, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.TRANSPARENT)
        draw(Canvas(out), bmp, srcW, srcH, framing, caption)
        return out
    }

    private fun drawCaption(canvas: Canvas, c: Caption) {
        val maxW = 476
        val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = c.color
        }
        var size = 78f
        var layout: StaticLayout
        while (true) {
            fill.textSize = size
            layout = StaticLayout.Builder.obtain(c.text.trim(), 0, c.text.trim().length, fill, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(3)
                .build()
            val longest = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
            val fits = layout.lineCount <= 2 && longest <= maxW && layout.height <= 200
            if (fits || size <= 30f) break
            size -= 4f
        }
        val stroke = TextPaint(fill).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.16f
            strokeJoin = Paint.Join.ROUND
            color = if (isLight(c.color)) Color.BLACK else Color.WHITE
        }
        val strokeLayout = StaticLayout.Builder.obtain(c.text.trim(), 0, c.text.trim().length, stroke, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(3)
            .build()
        val top = if (c.pos == CaptionPos.TOP) 18f else Framing.S - 18f - layout.height
        canvas.save()
        canvas.translate((Framing.S - maxW) / 2f, top)
        strokeLayout.draw(canvas)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun isLight(c: Int): Boolean =
        (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 128
}
