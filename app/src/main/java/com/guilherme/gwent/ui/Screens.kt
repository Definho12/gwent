package com.guilherme.gwent.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.guilherme.gwent.R
import com.guilherme.gwent.data.Pack
import com.guilherme.gwent.data.Packs
import com.guilherme.gwent.engine.Limits

@Composable
fun HomeScreen(
    packs: List<Pack>,
    addedIds: Set<String>,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
    onLink: () -> Unit,
    onOpenPack: (String) -> Unit,
) {
    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().background(Gw.Bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.mark), null, Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Gwent", style = MaterialTheme.typography.headlineSmall, color = Gw.Text)
                    Text("Figurinhas para o WhatsApp", style = MaterialTheme.typography.bodySmall, color = Gw.Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Gw.Surface).padding(18.dp)
            ) {
                Text("Nova figurinha", style = MaterialTheme.typography.titleMedium, color = Gw.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Foto, vídeo, GIF, figurinha de outro app ou link do Pinterest. Vídeos e GIFs viram figurinhas animadas.",
                    style = MaterialTheme.typography.bodySmall, color = Gw.Muted,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Galeria", Modifier.weight(1f), icon = Icons.Filled.Add, onClick = onGallery)
                    SecondaryButton("Arquivos", Modifier.weight(1f), onClick = onFiles)
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Colar link (Pinterest, Tenor…)", Modifier.fillMaxWidth(), onClick = onLink)
            }
        }
        if (packs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text("Seus pacotes", style = MaterialTheme.typography.labelLarge, color = Gw.Muted)
            }
        } else {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Dica: no app de galeria, WhatsApp ou qualquer outro, toque em Compartilhar → Gwent para virar figurinha na hora.",
                    style = MaterialTheme.typography.bodySmall, color = Gw.Muted,
                )
            }
        }
        items(packs.reversed(), key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Gw.Surface)
                    .clickable { onOpenPack(p.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Gw.Surface2), contentAlignment = Alignment.Center) {
                    val first = p.stickers.firstOrNull()
                    if (first != null) {
                        AsyncImage(
                            model = Packs.file(ctx, p.id, first.file), contentDescription = null,
                            imageLoader = Images.get(ctx), modifier = Modifier.size(46.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, color = Gw.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val kind = if (p.animated) "animadas" else "normais"
                    Text("${p.stickers.size}/${Limits.MAX_STICKERS} · $kind", style = MaterialTheme.typography.bodySmall, color = Gw.Muted)
                }
                if (p.id in addedIds) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = Gw.Ok, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("No WhatsApp", style = MaterialTheme.typography.bodySmall, color = Gw.Ok)
                    }
                }
            }
        }
    }
}

@Composable
fun PackScreen(
    pack: Pack,
    added: Boolean,
    onBack: () -> Unit,
    onAddToWhatsApp: () -> Unit,
    onNewHere: () -> Unit,
    onRename: (String) -> Unit,
    onDeletePack: () -> Unit,
    onDeleteSticker: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Gw.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Gw.Text) }
            Column(Modifier.weight(1f).clickable { renaming = true }.padding(vertical = 4.dp)) {
                Text(pack.name, style = MaterialTheme.typography.titleMedium, color = Gw.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val kind = if (pack.animated) "Animadas" else "Normais"
                Text("$kind · ${pack.stickers.size} de ${Limits.MAX_STICKERS}", style = MaterialTheme.typography.bodySmall, color = Gw.Muted)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais", tint = Gw.Text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renomear") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Apagar pacote") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; confirmDelete = true })
                }
            }
        }

        if (pack.stickers.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Pacote vazio", color = Gw.Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pack.stickers, key = { it.file }) { st ->
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Gw.Surface)
                            .clickable { selected = st.file }.padding(8.dp),
                    ) {
                        AsyncImage(
                            model = Packs.file(ctx, pack.id, st.file), contentDescription = null,
                            imageLoader = Images.get(ctx), modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (!pack.full) {
                    item {
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Gw.Surface)
                                .clickable(onClick = onNewHere),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Add, "Nova figurinha neste pacote", tint = Gw.Accent, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val missing = Limits.MIN_STICKERS - pack.stickers.size
            val note = when {
                pack.stickers.isEmpty() -> "Crie uma figurinha para poder enviar este pacote."
                missing > 0 -> "O WhatsApp pede pelo menos 3 por pacote. Até você criar mais, as que faltam aparecem repetidas."
                added -> "Já está no WhatsApp. Figurinhas novas aparecem lá sozinhas (às vezes depois de reabrir o WhatsApp)."
                else -> null
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = Gw.Muted)
                Spacer(Modifier.height(10.dp))
            }
            PrimaryButton(
                if (added) "Atualizar no WhatsApp" else "Adicionar ao WhatsApp",
                Modifier.fillMaxWidth(),
                enabled = pack.stickers.isNotEmpty(),
                onClick = onAddToWhatsApp,
            )
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(pack.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nome do pacote") },
            text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton({ onRename(text); renaming = false }) { Text("Salvar") } },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancelar") } },
            containerColor = Gw.Surface,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar o pacote?") },
            text = { Text("As ${pack.stickers.size} figurinhas dele somem daqui e do WhatsApp. Não dá para desfazer.") },
            confirmButton = { TextButton({ confirmDelete = false; onDeletePack() }) { Text("Apagar", color = Gw.Danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar") } },
            containerColor = Gw.Surface,
        )
    }
    selected?.let { file ->
        AlertDialog(
            onDismissRequest = { selected = null },
            text = {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                    Checker(Modifier.fillMaxSize())
                    AsyncImage(
                        model = Packs.file(ctx, pack.id, file), contentDescription = null,
                        imageLoader = Images.get(ctx), modifier = Modifier.fillMaxSize().padding(10.dp),
                    )
                }
            },
            confirmButton = { TextButton({ selected = null; onDeleteSticker(file) }) { Text("Apagar figurinha", color = Gw.Danger) } },
            dismissButton = { TextButton({ selected = null }) { Text("Fechar") } },
            containerColor = Gw.Surface,
        )
    }
}
