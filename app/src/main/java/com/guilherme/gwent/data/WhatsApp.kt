package com.guilherme.gwent.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Conversa com o WhatsApp (normal e Business). */
object WhatsApp {
    const val CONSUMER = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"

    fun installed(ctx: Context): List<String> = listOf(CONSUMER, BUSINESS).filter {
        try {
            ctx.packageManager.getPackageInfo(it, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun addIntent(ctx: Context, pack: Pack): Intent {
        val i = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
        i.putExtra("sticker_pack_id", pack.id)
        i.putExtra("sticker_pack_authority", StickerProvider.authority(ctx.packageName))
        i.putExtra("sticker_pack_name", pack.name)
        val apps = installed(ctx)
        if (apps.size == 1) i.setPackage(apps[0])
        return i
    }

    /** O pacote já está adicionado em algum WhatsApp deste celular? */
    fun isAdded(ctx: Context, packId: String): Boolean = installed(ctx).any { pkg ->
        try {
            val uri = Uri.Builder().scheme("content")
                .authority("$pkg.provider.sticker_whitelist_check")
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", StickerProvider.authority(ctx.packageName))
                .appendQueryParameter("identifier", packId)
                .build()
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow("result")) == 1
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** Avisa o WhatsApp que algo mudou nos pacotes. */
    fun notifyChanged(ctx: Context) {
        val auth = StickerProvider.authority(ctx.packageName)
        try {
            ctx.contentResolver.notifyChange(Uri.parse("content://$auth/metadata"), null)
        } catch (_: Exception) {}
    }
}
