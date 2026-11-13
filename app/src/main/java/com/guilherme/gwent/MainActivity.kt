package com.guilherme.gwent

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.guilherme.gwent.data.Packs
import com.guilherme.gwent.data.WhatsApp
import com.guilherme.gwent.ui.EditorScreen
import com.guilherme.gwent.ui.Gw
import com.guilherme.gwent.ui.GwentTheme
import com.guilherme.gwent.ui.HomeScreen
import com.guilherme.gwent.ui.PackScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    data object Home : Screen()
    data class PackView(val id: String) : Screen()
    data class Editor(val uris: List<Uri>, val packId: String?) : Screen()
}

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Gw.Bg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Gw.Bg.toArgb()),
        )
        super.onCreate(savedInstanceState)
        Packs.load(this)
        handleShare(intent)
        setContent { GwentTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** "Compartilhar → Gwent" vindo de qualquer app. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(stream(intent))
            Intent.ACTION_SEND_MULTIPLE -> streams(intent)
            else -> emptyList()
        }
        if (uris.isNotEmpty()) screen = Screen.Editor(uris.take(30), null)
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()

    @Composable
    private fun App() {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val packs by Packs.all.collectAsState()
        var added by remember { mutableStateOf<Set<String>>(emptySet()) }
        var waError by remember { mutableStateOf<String?>(null) }
        var pickFor by remember { mutableStateOf<String?>(null) }

        fun refreshAdded() {
            scope.launch {
                added = withContext(Dispatchers.IO) {
                    Packs.load(ctx).filter { WhatsApp.isAdded(ctx, it.id) }.map { it.id }.toSet()
                }
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshAdded() }
        LaunchedEffect(packs.size) { refreshAdded() }

        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { list ->
            if (list.isNotEmpty()) screen = Screen.Editor(list, pickFor)
        }
        val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
            if (list.isNotEmpty()) screen = Screen.Editor(list.take(30), pickFor)
        }
        val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_CANCELED) {
                val err = r.data?.getStringExtra("validation_error")
                if (err != null) waError = "O WhatsApp recusou o pacote: $err"
            }
            refreshAdded()
        }

        fun openGallery(packId: String?) {
            pickFor = packId
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        fun openFiles(packId: String?) {
            pickFor = packId
            files.launch(arrayOf("image/*", "video/*"))
        }

        Box(Modifier.fillMaxSize().background(Gw.Bg)) {
            when (val s = screen) {
                is Screen.Home -> HomeScreen(
                    packs = packs,
                    addedIds = added,
                    onGallery = { openGallery(null) },
                    onFiles = { openFiles(null) },
                    onOpenPack = { screen = Screen.PackView(it) },
                )
                is Screen.PackView -> {
                    val pack = packs.firstOrNull { it.id == s.id }
                    if (pack == null) {
                        LaunchedEffect(Unit) { screen = Screen.Home }
                    } else {
                        BackHandler { screen = Screen.Home }
                        PackScreen(
                            pack = pack,
                            added = pack.id in added,
                            onBack = { screen = Screen.Home },
                            onAddToWhatsApp = {
                                if (WhatsApp.installed(ctx).isEmpty()) {
                                    waError = "Não achei o WhatsApp neste celular."
                                } else {
                                    try {
                                        addLauncher.launch(WhatsApp.addIntent(ctx, pack))
                                    } catch (e: ActivityNotFoundException) {
                                        waError = "Seu WhatsApp não aceitou o pedido. Atualize o WhatsApp e tente de novo."
                                    }
                                }
                            },
                            onNewHere = { openGallery(pack.id) },
                            onRename = { name -> scope.launch(Dispatchers.IO) { Packs.rename(ctx, pack.id, name); WhatsApp.notifyChanged(ctx) } },
                            onDeletePack = {
                                scope.launch(Dispatchers.IO) { Packs.deletePack(ctx, pack.id); WhatsApp.notifyChanged(ctx) }
                                screen = Screen.Home
                            },
                            onDeleteSticker = { f ->
                                scope.launch(Dispatchers.IO) { Packs.removeSticker(ctx, pack.id, f); WhatsApp.notifyChanged(ctx) }
                            },
                        )
                    }
                }
                is Screen.Editor -> key(s) {
                    EditorScreen(
                        uris = s.uris,
                        targetPackId = s.packId,
                        onClose = { screen = s.packId?.let { Screen.PackView(it) } ?: Screen.Home },
                        onSaved = { id ->
                            Toast.makeText(ctx, "Figurinha criada!", Toast.LENGTH_SHORT).show()
                            screen = Screen.PackView(id)
                        },
                    )
                }
            }
        }

        waError?.let { m ->
            AlertDialog(
                onDismissRequest = { waError = null },
                text = { Text(m) },
                confirmButton = { TextButton({ waError = null }) { Text("Ok") } },
                containerColor = Gw.Surface,
            )
        }
    }
}
