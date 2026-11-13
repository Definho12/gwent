package com.guilherme.gwent.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.guilherme.gwent.engine.Plan
import java.io.FileNotFoundException

/**
 * Por aqui o WhatsApp lê os pacotes (formato oficial do WhatsApp para apps de figurinhas).
 * Pacotes com menos de 3 figurinhas são completados repetindo as existentes.
 */
class StickerProvider : ContentProvider() {

    companion object {
        const val PUBLISHER = "Guilherme"
        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val ASSET = "stickers_asset"
        private const val DUP = "dup"

        fun authority(pkg: String) = "$pkg.stickercontentprovider"

        private const val M_ALL = 1
        private const val M_ONE = 2
        private const val M_STICKERS = 3
        private const val M_ASSET = 4
    }

    private lateinit var auth: String
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        auth = authority(context!!.packageName)
        matcher.addURI(auth, METADATA, M_ALL)
        matcher.addURI(auth, "$METADATA/*", M_ONE)
        matcher.addURI(auth, "$STICKERS/*", M_STICKERS)
        matcher.addURI(auth, "$ASSET/*/*", M_ASSET)
        return true
    }

    private val packCols = arrayOf(
        "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
        "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
        "sticker_pack_publisher_email", "sticker_pack_publisher_website",
        "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
        "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
    )

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = context!!
        val packs = Packs.load(ctx).filter { it.stickers.isNotEmpty() }
        return when (matcher.match(uri)) {
            M_ALL -> packCursor(packs)
            M_ONE -> packCursor(packs.filter { it.id == uri.lastPathSegment })
            M_STICKERS -> {
                val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
                packs.firstOrNull { it.id == uri.lastPathSegment }?.let { p ->
                    Plan.padded(p.stickers.size).forEachIndexed { i, idx ->
                        val st = p.stickers[idx]
                        val name = if (i < p.stickers.size) st.file else "$DUP${i}_${st.file}"
                        c.addRow(arrayOf<Any>(name, st.emoji, "Figurinha"))
                    }
                }
                c.setNotificationUri(ctx.contentResolver, uri)
                c
            }
            else -> throw IllegalArgumentException("URI desconhecida: $uri")
        }
    }

    private fun packCursor(list: List<Pack>): Cursor {
        val c = MatrixCursor(packCols)
        list.forEach { p ->
            c.addRow(arrayOf<Any?>(
                p.id, p.name, PUBLISHER, p.trayFile, "", "", "", "", "", "",
                p.version.toString(), 0, if (p.animated) 1 else 0,
            ))
        }
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (matcher.match(uri) != M_ASSET) throw FileNotFoundException(uri.toString())
        val seg = uri.pathSegments
        if (seg.size != 3) throw FileNotFoundException(uri.toString())
        val packId = seg[1]
        var name = seg[2]
        if (name.startsWith(DUP)) name = name.substringAfter('_')
        if (name.contains('/') || name.contains("..")) throw FileNotFoundException(uri.toString())
        val f = Packs.file(context!!, packId, name)
        if (!f.exists()) throw FileNotFoundException(uri.toString())
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, f.length())
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        M_ALL -> "vnd.android.cursor.dir/vnd.$auth.$METADATA"
        M_ONE -> "vnd.android.cursor.item/vnd.$auth.$METADATA"
        M_STICKERS -> "vnd.android.cursor.dir/vnd.$auth.$STICKERS"
        M_ASSET -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> throw IllegalArgumentException("URI desconhecida: $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
