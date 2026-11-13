package com.guilherme.gwent.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guilherme.gwent.engine.LinkException
import com.guilherme.gwent.engine.Links
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Baixa a mídia de um link e depois abre o editor. */
@Composable
fun LinkScreen(text: String, onReady: (Uri) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Abrindo o link…") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    BackHandler { onClose() }
    LaunchedEffect(attempt) {
        error = null
        try {
            val f = withContext(Dispatchers.IO) {
                Links.fetch(text, File(ctx.cacheDir, "links")) { status = it }
            }
            onReady(Uri.fromFile(f))
        } catch (e: LinkException) {
            error = e.message
        } catch (e: Exception) {
            error = "Não consegui abrir esse link. Confira a internet e tente de novo."
        }
    }

    Column(
        Modifier.fillMaxSize().background(Gw.Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (error == null) {
            CircularProgressIndicator(color = Gw.Accent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(18.dp))
            Text(status, color = Gw.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(Links.firstUrl(text) ?: "", color = Gw.Muted, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, maxLines = 2)
        } else {
            Text(error!!, color = Gw.Text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Tentar de novo", Modifier.fillMaxWidth()) { attempt++ }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Voltar", Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

/** Caixa para colar um link (já vem preenchida se houver um link copiado). */
@Composable
fun PasteLinkDialog(onDismiss: () -> Unit, onGo: (String) -> Unit) {
    val ctx = LocalContext.current
    var text by remember {
        val clip = try {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        } catch (e: Exception) { null }
        mutableStateOf(clip?.let { Links.firstUrl(it) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colar link") },
        text = {
            Column {
                Text(
                    "Link de um pin do Pinterest, GIF do Tenor/Giphy ou de qualquer imagem/vídeo.",
                    style = MaterialTheme.typography.bodySmall, color = Gw.Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { text = it }, placeholder = { Text("https://pin.it/…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onGo(text) }, enabled = Links.firstUrl(text) != null) { Text("Baixar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Gw.Surface,
    )
}
