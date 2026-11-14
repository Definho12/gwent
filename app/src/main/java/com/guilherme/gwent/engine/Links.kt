package com.guilherme.gwent.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LinkException(msg: String) : Exception(msg)

/**
 * Baixa a mídia de um link (Pinterest, Tenor, Giphy ou link direto de GIF/vídeo/imagem)
 * para virar figurinha. Tudo acontece no próprio celular.
 */
object Links {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 80L * 1024 * 1024

    private val urlRegex = Regex("""https?://[^\s"'<>]+""")
    private val pinIdRegex = Regex("""/pin/([\w-]{6,})""")

    /** Registro do que aconteceu na última busca (para testes e diagnóstico). */
    val trace = mutableListOf<String>()

    /** Id do pin a partir do endereço (aceita "/pin/123/" e "/pin/titulo--123/"). */
    fun pinId(url: String): String? {
        val seg = pinIdRegex.find(url)?.groupValues?.get(1) ?: return null
        return if (seg.contains("--")) seg.substringAfterLast("--") else seg
    }
    private val mediaExt = Regex("""\.(gif|mp4|webm|mov|webp|jpe?g|png)(\?|$)""", RegexOption.IGNORE_CASE)

    /** Primeiro link que aparece num texto (o Pinterest compartilha "Olha isso! https://pin.it/..."). */
    fun firstUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']')

    fun isPinterest(url: String) = Regex("""(^|\.)(pinterest\.[a-z.]+|pin\.it)$""").containsMatchIn(host(url))

    private fun host(url: String) = try { URL(url).host.lowercase() } catch (e: Exception) { "" }

    /** Baixa e devolve o arquivo. [status] recebe mensagens para mostrar na tela. */
    fun fetch(text: String, dir: File, status: (String) -> Unit): File {
        val url = firstUrl(text) ?: throw LinkException("Não achei nenhum link nesse texto.")
        status(if (isPinterest(url)) "Abrindo o Pinterest…" else "Abrindo o link…")
        val media = findMedia(url)
        status("Baixando…")
        return download(media, dir)
    }

    /** Descobre o endereço do arquivo de mídia a partir do link compartilhado. */
    fun findMedia(url: String): String {
        trace.clear()
        if (mediaExt.containsMatchIn(URL(url).path)) return url
        val final = resolve(url)
        trace += "final: $final"
        if (mediaExt.containsMatchIn(URL(final).path)) return final
        val pinterest = isPinterest(final)
        if (pinterest) {
            val id = pinId(final)
            trace += "pin id: $id"
            if (id != null) {
                for (key in listOf("unauth_react_main_pin", "detailed")) {
                    try {
                        pinterestApi(id, key)?.let { trace += "api($key): $it"; return it }
                    } catch (e: Exception) { trace += "api($key) falhou: ${e.message}" }
                }
            }
        }
        val pageUrl = if (pinterest) pinId(final)?.let { "https://www.pinterest.com/pin/$it/" } ?: final else final
        val html = getText(pageUrl)
        trace += "página: ${html.length} caracteres"
        fromHtml(html, pinterest)?.let { trace += "página: $it"; return it }
        throw LinkException(
            if (isPinterest(final)) "Não encontrei um GIF ou vídeo nesse pin. Ele pode ser só uma foto, ou ser privado."
            else "Não encontrei imagem, GIF ou vídeo nesse link."
        )
    }

    /** Segue os redirecionamentos (ex.: pin.it → pinterest.com/pin/…). */
    private fun resolve(url: String): String {
        var current = url
        repeat(6) {
            val c = open(current, head = false)
            c.instanceFollowRedirects = false
            val code = c.responseCode
            val loc = c.getHeaderField("Location")
            c.disconnect()
            trace += "$code $current"
            // chegou no pin: para aqui (às vezes o Pinterest ainda redireciona para uma página de erro)
            if (isPinterest(current) && pinId(current) != null) return current
            if (code in 300..399 && loc != null) current = URL(URL(current), loc).toString() else return current
        }
        return current
    }

    /** Mesma consulta pública que a página do pin usa. */
    private fun pinterestApi(id: String, key: String): String? {
        val data = """{"options":{"id":"$id","field_set_key":"$key"}}"""
        val api = "https://www.pinterest.com/resource/PinResource/get/?data=" + URLEncoder.encode(data, "UTF-8")
        val json = JSONObject(getText(api, accept = "application/json, text/javascript, */*; q=0.01",
            extra = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "X-Pinterest-PWS-Handler" to "www/pin/[id].js",
                "X-Pinterest-AppState" to "active",
                "Referer" to "https://www.pinterest.com/pin/$id/",
            )))
        val pin = json.optJSONObject("resource_response")?.optJSONObject("data") ?: return null
        return pickFromPin(pin)
    }

    /** Escolhe a melhor mídia de um pin: vídeo MP4 (GIFs costumam vir assim), depois o GIF original. */
    fun pickFromPin(pin: JSONObject): String? {
        videoFrom(pin.optJSONObject("videos"))?.let { return it }
        // pins "story" guardam o vídeo dentro das páginas
        pin.optJSONObject("story_pin_data")?.optJSONArray("pages")?.let { pages ->
            for (i in 0 until pages.length()) {
                val blocks = pages.optJSONObject(i)?.optJSONArray("blocks") ?: continue
                for (j in 0 until blocks.length()) {
                    videoFrom(blocks.optJSONObject(j)?.optJSONObject("video"))?.let { return it }
                }
            }
        }
        val images = pin.optJSONObject("images")
        val orig = images?.optJSONObject("orig")?.optString("url").orEmpty()
        if (orig.isNotEmpty()) return orig
        return pin.optJSONObject("embed")?.optString("src")?.takeIf { it.isNotEmpty() }
    }

    private fun videoFrom(videos: JSONObject?): String? {
        val list = videos?.optJSONObject("video_list") ?: return null
        val prefs = listOf("V_720P", "V_EXP7", "V_EXP6", "V_EXP5", "V_EXP4", "V_EXP3")
        for (k in prefs) list.optJSONObject(k)?.optString("url")?.takeIf { it.contains(".mp4") }?.let { return it }
        // qualquer MP4 disponível
        val keys = list.keys()
        while (keys.hasNext()) {
            list.optJSONObject(keys.next())?.optString("url")?.takeIf { it.contains(".mp4") }?.let { return it }
        }
        return null
    }

    /** Procura a mídia nas etiquetas da página (funciona com Tenor, Giphy e muitos sites). */
    fun fromHtml(html: String, pinterest: Boolean = false): String? {
        fun meta(prop: String): String? {
            val a = Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val b = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE)
            return (a.find(html) ?: b.find(html))?.groupValues?.get(1)?.replace("&amp;", "&")
        }
        val candidates = listOfNotNull(
            meta("og:video:secure_url"), meta("og:video:url"), meta("og:video"),
            meta("twitter:player:stream"),
        ).filter { it.contains(".mp4") || it.contains(".gif") || it.contains(".webm") }
        candidates.firstOrNull()?.let { return it }
        // Pinterest: GIF original citado direto na página
        Regex("""https://i\.pinimg\.com/originals/[\w/]+\.gif""").find(html)?.let { return it.value }
        Regex("""https://v\d*\.pinimg\.com/videos/[^"\\\s]+?\.mp4""").find(html)?.let { return it.value }
        val img = meta("og:image") ?: meta("twitter:image")
        // no Pinterest, a imagem genérica do site (logo + mosaico) não serve
        if (pinterest) return img?.takeIf { it.startsWith("https://i.pinimg.com/") }
        return img?.takeIf { it.startsWith("http") }
    }

    private fun open(url: String, head: Boolean = false, accept: String? = null, extra: Map<String, String> = emptyMap()): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        if (accept != null) c.setRequestProperty("Accept", accept)
        extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (head) c.requestMethod = "HEAD"
        return c
    }

    private fun getText(url: String, accept: String? = null, extra: Map<String, String> = emptyMap()): String {
        val c = open(url, accept = accept, extra = extra)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    private fun download(url: String, dir: File): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // só guarda o último
        val c = open(url)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw LinkException("O site recusou o download (erro $code).")
            val type = c.contentType.orEmpty().lowercase()
            if (type.startsWith("text/html")) throw LinkException("Esse link abre uma página, não um arquivo de mídia.")
            val ext = when {
                "gif" in type -> "gif"
                "webp" in type -> "webp"
                "png" in type -> "png"
                "jpeg" in type || "jpg" in type -> "jpg"
                "webm" in type -> "webm"
                "video" in type -> "mp4"
                else -> mediaExt.find(URL(url).path)?.groupValues?.get(1)?.lowercase() ?: "bin"
            }
            val f = File(dir, "link_${System.currentTimeMillis()}.$ext")
            c.inputStream.use { input ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw LinkException("Arquivo grande demais (mais de 80 MB).")
                        out.write(buf, 0, n)
                    }
                }
            }
            return f
        } catch (e: IOException) {
            throw LinkException("Falhou o download. Confira a internet e tente de novo.")
        } finally {
            c.disconnect()
        }
    }

    @Suppress("unused")
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
}
