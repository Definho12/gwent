package com.guilherme.gwent.data

import android.content.Context
import com.guilherme.gwent.engine.Limits
import com.guilherme.gwent.engine.MadeSticker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Sticker(val file: String, val emoji: String = DEFAULT_EMOJI) {
    /** Miniatura 96×96 guardada junto (vira o ícone do pacote quando é a primeira). */
    val trayFile get() = "t_" + file.substringBeforeLast('.') + ".png"

    companion object {
        const val DEFAULT_EMOJI = "😀"
    }
}

data class Pack(
    val id: String,
    val name: String,
    val animated: Boolean,
    val version: Int,
    val stickers: List<Sticker>,
    val created: Long,
) {
    val full get() = stickers.size >= Limits.MAX_STICKERS
    val trayFile get() = stickers.firstOrNull()?.trayFile
}

/** Pacotes de figurinhas guardados dentro do app (files/packs/<id>/). */
object Packs {
    private val _all = MutableStateFlow<List<Pack>>(emptyList())
    val all: StateFlow<List<Pack>> = _all
    private var loaded = false

    private fun root(ctx: Context) = File(ctx.filesDir, "packs").apply { mkdirs() }
    fun dir(ctx: Context, id: String) = File(root(ctx), id)
    fun file(ctx: Context, id: String, name: String) = File(dir(ctx, id), name)

    @Synchronized
    fun load(ctx: Context): List<Pack> {
        if (loaded) return _all.value
        val list = root(ctx).listFiles()?.mapNotNull { d ->
            try { parse(File(d, "pack.json").readText()) } catch (e: Exception) { null }
        }.orEmpty().sortedBy { it.created }
        _all.value = list
        loaded = true
        return list
    }

    fun get(ctx: Context, id: String): Pack? = load(ctx).firstOrNull { it.id == id }

    @Synchronized
    private fun save(ctx: Context, p: Pack) {
        val d = dir(ctx, p.id).apply { mkdirs() }
        val tmp = File(d, "pack.json.tmp")
        tmp.writeText(toJson(p).toString())
        tmp.renameTo(File(d, "pack.json"))
        val list = load(ctx).filter { it.id != p.id } + p
        _all.value = list.sortedBy { it.created }
    }

    @Synchronized
    fun create(ctx: Context, animated: Boolean, name: String? = null): Pack {
        val existing = load(ctx).count { it.animated == animated }
        val base = if (animated) "Animadas" else "Figurinhas"
        val id = "p" + System.currentTimeMillis().toString(36) + (if (animated) "a" else "s")
        val p = Pack(id, name?.takeIf { it.isNotBlank() } ?: "$base ${existing + 1}", animated, 1, emptyList(), System.currentTimeMillis())
        save(ctx, p)
        return p
    }

    /** Pacote onde a próxima figurinha entra: o último do mesmo tipo que ainda tem espaço. */
    fun suggested(ctx: Context, animated: Boolean): Pack? =
        load(ctx).lastOrNull { it.animated == animated && !it.full }

    @Synchronized
    fun add(ctx: Context, made: MadeSticker, packId: String?): Pack {
        var p = packId?.let { get(ctx, it) }?.takeIf { it.animated == made.animated && !it.full }
            ?: suggested(ctx, made.animated)
            ?: create(ctx, made.animated)
        val name = "s" + System.currentTimeMillis().toString(36) + ".webp"
        val st = Sticker(name)
        file(ctx, p.id, name).writeBytes(made.webp)
        file(ctx, p.id, st.trayFile).writeBytes(made.tray)
        p = p.copy(stickers = p.stickers + st, version = p.version + 1)
        save(ctx, p)
        return p
    }

    @Synchronized
    fun removeSticker(ctx: Context, packId: String, fileName: String) {
        val p = get(ctx, packId) ?: return
        val st = p.stickers.firstOrNull { it.file == fileName } ?: return
        file(ctx, p.id, st.file).delete()
        file(ctx, p.id, st.trayFile).delete()
        save(ctx, p.copy(stickers = p.stickers - st, version = p.version + 1))
    }

    @Synchronized
    fun rename(ctx: Context, packId: String, name: String) {
        val p = get(ctx, packId) ?: return
        val clean = name.trim().take(60)
        if (clean.isEmpty()) return
        save(ctx, p.copy(name = clean, version = p.version + 1))
    }

    @Synchronized
    fun deletePack(ctx: Context, packId: String) {
        dir(ctx, packId).deleteRecursively()
        _all.value = load(ctx).filter { it.id != packId }
    }

    private fun toJson(p: Pack) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("animated", p.animated)
        put("version", p.version); put("created", p.created)
        put("stickers", JSONArray().apply {
            p.stickers.forEach { put(JSONObject().put("file", it.file).put("emoji", it.emoji)) }
        })
    }

    private fun parse(s: String): Pack {
        val o = JSONObject(s)
        val arr = o.getJSONArray("stickers")
        return Pack(
            id = o.getString("id"),
            name = o.getString("name"),
            animated = o.getBoolean("animated"),
            version = o.optInt("version", 1),
            stickers = List(arr.length()) {
                val j = arr.getJSONObject(it)
                Sticker(j.getString("file"), j.optString("emoji", Sticker.DEFAULT_EMOJI))
            },
            created = o.optLong("created", 0L),
        )
    }
}
