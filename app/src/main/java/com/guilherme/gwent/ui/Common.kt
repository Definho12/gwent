package com.guilherme.gwent.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder

/** Carregador de imagens que anima WebP e GIF. */
object Images {
    @Volatile private var loader: ImageLoader? = null
    fun get(ctx: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(ctx.applicationContext)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .crossfade(false)
            .build().also { loader = it }
    }
}

/** Fundo xadrez discreto que mostra onde a figurinha é transparente. */
@Composable
fun Checker(modifier: Modifier = Modifier, cell: Float = 14f) {
    Canvas(modifier) {
        drawRect(Color(0xFF1C1D21))
        val c = Color(0xFF232429)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(c, Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gw.Accent, contentColor = Gw.OnAccent,
            disabledContainerColor = Gw.Surface2, disabledContentColor = Gw.Muted),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gw.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gw.Text),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text)
    }
}

/** Botão-pílula de opção (selecionado fica dourado). */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (selected) Gw.Accent else Gw.Surface2
    val fg = when {
        !enabled -> Gw.Muted.copy(alpha = 0.5f)
        selected -> Gw.OnAccent
        else -> Gw.Text
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SegmentRow(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Gw.Surface2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, s ->
            val sel = i == selected
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Gw.Bg else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s, color = if (sel) Gw.Accent else Gw.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun secs(ms: Long): String {
    val s = ms / 1000.0
    return String.format(java.util.Locale("pt", "BR"), "%.1f s", s)
}

fun Modifier.fullWidth() = this.fillMaxWidth()
