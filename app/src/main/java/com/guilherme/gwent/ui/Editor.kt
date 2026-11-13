package com.guilherme.gwent.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guilherme.gwent.data.Packs
import com.guilherme.gwent.data.WhatsApp
import com.guilherme.gwent.engine.BgRemover
import com.guilherme.gwent.engine.Caption
import com.guilherme.gwent.engine.CaptionPos
import com.guilherme.gwent.engine.Framing
import com.guilherme.gwent.engine.Limits
import com.guilherme.gwent.engine.Maker
import com.guilherme.gwent.engine.MediaSource
import com.guilherme.gwent.engine.Plan
import com.guilherme.gwent.engine.Renderer
import com.guilherme.gwent.engine.Sources
import com.guilherme.gwent.engine.TooBigException
import com.guilherme.gwent.engine.UnsupportedMediaException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs

private const val PREVIEW_FPS = 10

private val CaptionColors = listOf(Color.White, Color(0xFFFFE45C), Color(0xFF111111))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uris: List<Uri>,
    targetPackId: String?,
    onClose: () -> Unit,
    onSaved: (packId: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Toda leitura da mídia passa por uma única linha de trabalho (os leitores não aceitam acesso simultâneo).
    val worker = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    DisposableEffect(Unit) { onDispose { worker.close() } }

    var index by remember { mutableIntStateOf(0) }
    val uri = uris.getOrNull(index)
    var lastPackId by remember { mutableStateOf<String?>(null) }

    var source by remember { mutableStateOf<MediaSource?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var asAnimation by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    var end by remember { mutableLongStateOf(0L) }
    var stillAt by remember { mutableLongStateOf(0L) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var cutout by remember { mutableStateOf<Bitmap?>(null) }
    var framing by remember { mutableStateOf(Framing(1f)) }
    var captionText by remember { mutableStateOf("") }
    var captionPos by remember { mutableStateOf(CaptionPos.BOTTOM) }
    var captionColor by remember { mutableIntStateOf(0) }
    var showText by remember { mutableStateOf(false) }
    var chosenPack by remember { mutableStateOf<String?>(targetPackId) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var cancelled by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var boxPx by remember { mutableIntStateOf(1) }

    fun next(savedPack: String?) {
        if (savedPack != null) lastPackId = savedPack
        if (index < uris.size - 1) index++
        else {
            val p = lastPackId
            if (p != null) onSaved(p) else onClose()
        }
    }

    // Abre a mídia atual
    LaunchedEffect(uri) {
        val old = source
        source = null; preview = null; cutout = null; openError = null
        captionText = ""; showText = false
        if (old != null) withContext(worker) { old.close() }
        if (uri == null) return@LaunchedEffect
        try {
            val s = withContext(worker) { Sources.open(ctx, uri) }
            asAnimation = s.animated
            start = 0L
            end = if (s.animated) Plan.defaultEnd(s.durationMs) else 0L
            stillAt = 0L
            framing = Framing.fill(s.width, s.height)
            source = s
        } catch (e: UnsupportedMediaException) {
            openError = e.message
        } catch (e: Throwable) {
            openError = "Não consegui abrir esse arquivo."
        }
    }
    DisposableEffect(Unit) { onDispose { source?.let { s -> scope.launch(Dispatchers.IO) { s.close() } } } }

    // Atualiza o quadro mostrado
    val previewAt = if (asAnimation) start else stillAt
    LaunchedEffect(source, previewAt) {
        val s = source ?: return@LaunchedEffect
        if (preview != null) delay(120)
        try {
            preview = withContext(worker) { s.frameAt(previewAt, 1200) }
        } catch (e: Exception) {
            if (preview == null) openError = "Não consegui ler esse arquivo."
        }
    }

    // Prévia animada: lê o trecho em tamanho pequeno e fica repetindo
    var playFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var playIdx by remember { mutableIntStateOf(0) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(source, asAnimation, start, end, reloadKey) {
        playFrames = emptyList()
        val s = source ?: return@LaunchedEffect
        if (!asAnimation || !s.animated) return@LaunchedEffect
        delay(350) // espera o usuário soltar a alça do trecho
        loadJob = coroutineContext[Job]
        try {
            withContext(worker) {
                val job = coroutineContext[Job]
                val acc = ArrayList<Bitmap>()
                s.forEachFrame(start, end, PREVIEW_FPS) { bmp, k ->
                    if (job?.isActive == false) throw CancellationException()
                    val small = Sources.scaleDown(bmp, 420)
                    acc += if (small === bmp) bmp.copy(Bitmap.Config.ARGB_8888, false) else small
                    if (k < 4 || k % 5 == 0) playFrames = acc.toList()
                }
                playFrames = acc.toList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // sem prévia animada; fica o quadro parado
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L / PREVIEW_FPS)
            val n = playFrames.size
            if (n > 0) playIdx = (playIdx + 1) % n
        }
    }

    val packs by Packs.all.collectAsState()
    val caption = Caption(captionText, captionPos, CaptionColors[captionColor].toArgb())

    fun create() {
        val s = source ?: return
        loadJob?.cancel() // libera a leitura da mídia para a criação
        cancelled = false
        progress = 0f
        busy = "Criando figurinha…"
        scope.launch {
            try {
                val made = withContext(worker) {
                    if (asAnimation) {
                        Maker.makeAnimated(s, start, end, framing, caption, ctx.cacheDir,
                            progress = { progress = it }, cancelled = { cancelled })
                    } else {
                        val bmp = cutout ?: s.frameAt(stillAt, 2048)
                        Maker.makeStatic(bmp, s.width, s.height, framing, caption)
                    }
                }
                val pack = withContext(Dispatchers.IO) { Packs.add(ctx, made, chosenPack) }
                WhatsApp.notifyChanged(ctx)
                busy = null
                if (index < uris.size - 1) Toast.makeText(ctx, "Salva em ${pack.name}", Toast.LENGTH_SHORT).show()
                chosenPack = pack.id
                next(pack.id)
            } catch (e: InterruptedException) {
                busy = null; reloadKey++
            } catch (e: TooBigException) {
                busy = null; errorMsg = e.message; reloadKey++
            } catch (e: OutOfMemoryError) {
                busy = null; errorMsg = "Faltou memória no celular. Tente um trecho mais curto."
            } catch (e: Exception) {
                busy = null; errorMsg = "Algo deu errado: ${e.message ?: e.javaClass.simpleName}"; reloadKey++
            }
        }
    }

    fun removeBackground() {
        val s = source ?: return
        if (cutout != null) {
            cutout = null
            framing = Framing.fill(s.width, s.height)
            return
        }
        progress = -1f
        busy = "Preparando…"
        scope.launch {
            try {
                val frame = withContext(worker) { s.frameAt(stillAt, 1600) }
                val fg = BgRemover.remove(ctx, frame) { msg -> busy = msg }
                val f = withContext(Dispatchers.Default) { BgRemover.framingFor(fg, s.width, s.height) }
                cutout = fg
                framing = f
            } catch (e: Exception) {
                errorMsg = e.message ?: "Não deu para tirar o fundo."
            } finally {
                busy = null
            }
        }
    }

    BackHandler(enabled = busy == null) { onClose() }

    Column(Modifier.fillMaxSize().background(Gw.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Topo
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fechar", tint = Gw.Text) }
            Text("Nova figurinha", style = MaterialTheme.typography.titleMedium, color = Gw.Text, modifier = Modifier.weight(1f))
            if (uris.size > 1) {
                Text("${index + 1} de ${uris.size}", style = MaterialTheme.typography.bodySmall, color = Gw.Muted)
                TextButton(onClick = { next(null) }) { Text("Pular") }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            // Prévia (arraste para mover, pinça para zoom, dois toques alternam preencher/inteira)
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Gw.Line, RoundedCornerShape(20.dp))
                    .onSizeChanged { boxPx = maxOf(1, it.width) }
                    .pointerInput(source) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val s = source ?: return@detectTransformGestures
                            val k = Framing.S / boxPx
                            val minS = Framing.fit(s.width, s.height).scale * 0.3f
                            val maxS = Framing.fill(s.width, s.height).scale * 6f
                            val ns = (framing.scale * zoom).coerceIn(minS, maxS)
                            val z = ns / framing.scale
                            framing = Framing(ns, framing.dx * z + pan.x * k, framing.dy * z + pan.y * k)
                        }
                    }
                    .pointerInput(source) {
                        detectTapGestures(onDoubleTap = {
                            val s = source ?: return@detectTapGestures
                            val fill = Framing.fill(s.width, s.height)
                            framing = if (abs(framing.scale - fill.scale) < 0.001f && framing.dx == 0f && framing.dy == 0f)
                                Framing.fit(s.width, s.height) else fill
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Checker(Modifier.fillMaxSize())
                val s = source
                val frames = playFrames
                val shown = if (asAnimation && frames.isNotEmpty()) frames[playIdx % frames.size]
                    else cutout?.takeIf { !asAnimation } ?: preview
                if (s != null && shown != null) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas
                            nc.save()
                            val f = size.width / Framing.S
                            nc.scale(f, f)
                            nc.clipRect(0f, 0f, Framing.S, Framing.S)
                            Renderer.draw(nc, shown, s.width, s.height, framing, caption)
                            nc.restore()
                        }
                    }
                } else if (openError == null) {
                    CircularProgressIndicator(color = Gw.Accent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                }
                if (openError != null) {
                    Text(openError!!, color = Gw.Muted, modifier = Modifier.padding(24.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Arraste para mover · pinça para zoom · dois toques: preencher/inteira",
                style = MaterialTheme.typography.bodySmall, color = Gw.Muted.copy(alpha = 0.8f),
            )

            val s = source
            if (s != null) {
                Spacer(Modifier.height(14.dp))
                if (s.animated) {
                    SegmentRow(listOf("Animada", "Parada"), if (asAnimation) 0 else 1, Modifier.fillMaxWidth()) {
                        val anim = it == 0
                        if (anim != asAnimation) {
                            asAnimation = anim
                            if (!anim) stillAt = start else { cutout = null }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (asAnimation) {
                        val dur = s.durationMs.toFloat()
                        Text(
                            "Trecho: ${secs(start)} – ${secs(end)}  (${secs(end - start)})",
                            style = MaterialTheme.typography.bodySmall, color = Gw.Text,
                        )
                        RangeSlider(
                            value = start.toFloat()..end.toFloat(),
                            onValueChange = { r ->
                                var ns = r.start.toLong()
                                var ne = r.endInclusive.toLong()
                                if (ne - ns > Limits.MAX_DURATION_MS) {
                                    if (ns != start) ne = ns + Limits.MAX_DURATION_MS else ns = ne - Limits.MAX_DURATION_MS
                                }
                                if (ne - ns < 200) { if (ns != start) ns = ne - 200 else ne = ns + 200 }
                                val (cs, ce) = Plan.clampRange(ns, ne, s.durationMs)
                                start = cs; end = ce
                            },
                            valueRange = 0f..dur,
                            colors = SliderDefaults.colors(thumbColor = Gw.Accent, activeTrackColor = Gw.Accent, inactiveTrackColor = Gw.Surface2),
                        )
                        Text(
                            "Máximo de 10 s. Trechos curtos ficam mais nítidos.",
                            style = MaterialTheme.typography.bodySmall, color = Gw.Muted,
                        )
                    } else {
                        Text("Quadro: ${secs(stillAt)}", style = MaterialTheme.typography.bodySmall, color = Gw.Text)
                        Slider(
                            value = stillAt.toFloat(),
                            onValueChange = { stillAt = it.toLong(); cutout = null },
                            valueRange = 0f..s.durationMs.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Gw.Accent, activeTrackColor = Gw.Accent, inactiveTrackColor = Gw.Surface2),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Ferramentas
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fill = Framing.fill(s.width, s.height)
                    val fit = Framing.fit(s.width, s.height)
                    Chip("Preencher", cutout == null && framing == fill) { framing = fill }
                    Chip("Inteira", cutout == null && framing == fit) { framing = fit }
                    if (!asAnimation) Chip(if (cutout != null) "Com fundo" else "Tirar fundo", cutout != null) { removeBackground() }
                    Chip("Texto", showText || captionText.isNotBlank()) { showText = !showText }
                }

                if (showText) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it.take(80) },
                        placeholder = { Text("Escreva o texto") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Em cima", captionPos == CaptionPos.TOP) { captionPos = CaptionPos.TOP }
                        Chip("Embaixo", captionPos == CaptionPos.BOTTOM) { captionPos = CaptionPos.BOTTOM }
                        Spacer(Modifier.width(6.dp))
                        CaptionColors.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(c)
                                    .border(2.dp, if (i == captionColor) Gw.Accent else Gw.Line, CircleShape)
                                    .clickable { captionColor = i },
                            )
                        }
                    }
                }

                // Pacote de destino
                Spacer(Modifier.height(16.dp))
                val options = packs.filter { it.animated == asAnimation && !it.full }
                val current = options.firstOrNull { it.id == chosenPack } ?: options.lastOrNull()
                var open by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Gw.Surface)
                            .clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Salvar em", style = MaterialTheme.typography.bodySmall, color = Gw.Muted)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            current?.let { "${it.name} (${it.stickers.size}/${Limits.MAX_STICKERS})" }
                                ?: if (asAnimation) "Pacote novo de animadas" else "Pacote novo",
                            color = Gw.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        Text("Trocar", color = Gw.Accent, style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        options.reversed().forEach { p ->
                            DropdownMenuItem(text = { Text("${p.name} (${p.stickers.size}/${Limits.MAX_STICKERS})") },
                                onClick = { chosenPack = p.id; open = false })
                        }
                        DropdownMenuItem(text = { Text("+ Pacote novo") }, onClick = {
                            open = false
                            scope.launch {
                                val p = withContext(Dispatchers.IO) { Packs.create(ctx, asAnimation) }
                                chosenPack = p.id
                            }
                        })
                    }
                }
                if (asAnimation) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O WhatsApp não mistura animadas e normais no mesmo pacote; por isso elas ficam separadas.",
                        style = MaterialTheme.typography.bodySmall, color = Gw.Muted,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            if (openError != null) {
                SecondaryButton(if (index < uris.size - 1) "Pular este" else "Voltar", Modifier.fillMaxWidth()) {
                    if (index < uris.size - 1) next(null) else onClose()
                }
            } else {
                val ready = source != null && preview != null && busy == null
                PrimaryButton(
                    if (index < uris.size - 1) "Criar e ir para a próxima" else "Criar figurinha",
                    Modifier.fillMaxWidth(), enabled = ready,
                ) {
                    val opts = packs.filter { it.animated == asAnimation && !it.full }
                    if (chosenPack == null || opts.none { it.id == chosenPack }) chosenPack = opts.lastOrNull()?.id
                    create()
                }
            }
        }
    }

    // Trabalhando…
    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(msg) },
            text = {
                Column {
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) }, color = Gw.Accent, trackColor = Gw.Surface2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${(progress * 100).toInt()}%", color = Gw.Muted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(color = Gw.Accent, trackColor = Gw.Surface2, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (progress >= 0f && asAnimation) TextButton({ cancelled = true }) { Text("Cancelar") }
            },
            containerColor = Gw.Surface,
        )
    }
    errorMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            text = { Text(m) },
            confirmButton = { TextButton({ errorMsg = null }) { Text("Ok") } },
            containerColor = Gw.Surface,
        )
    }
}
