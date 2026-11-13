package com.guilherme.gwent.engine

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Regras do WhatsApp para figurinhas. */
object Limits {
    const val SIZE = 512
    const val TRAY = 96
    const val STATIC_MAX_BYTES = 100 * 1024
    const val ANIMATED_MAX_BYTES = 500 * 1024
    const val TRAY_MAX_BYTES = 50 * 1024
    const val MAX_DURATION_MS = 10_000L
    const val MAX_STICKERS = 30
    const val MIN_STICKERS = 3
    const val MAX_FPS = 15
    /** Margem de segurança abaixo do limite de 500 KB. */
    const val ANIMATED_TARGET_BYTES = ANIMATED_MAX_BYTES - 12 * 1024
    const val STATIC_TARGET_BYTES = STATIC_MAX_BYTES - 2 * 1024
}

/** Uma tentativa de compressão: quadros por segundo e qualidade (0–100). */
data class Attempt(val fps: Int, val quality: Float)

/** Lógica pura (testável) de como espremer uma animação no limite de tamanho. */
object Plan {

    /** Tentativas em ordem, da mais bonita (e pesada) para a mais leve. */
    fun attempts(maxFps: Int): List<Attempt> {
        val top = maxFps.coerceIn(1, Limits.MAX_FPS)
        val fpsList = listOf(top, 12, 10, 8, 6).filter { it <= top }.distinct()
        val list = mutableListOf<Attempt>()
        fpsList.forEachIndexed { i, f ->
            val qs = if (i == 0) listOf(82f, 65f, 50f, 38f) else listOf(36f, 26f)
            qs.forEach { list += Attempt(f, it) }
        }
        val last = fpsList.last()
        list += Attempt(last, 15f)
        list += Attempt(last, 6f)
        return list
    }

    /** Quantas tentativas pular, conforme o quanto o arquivo passou do limite. */
    fun skip(size: Long, limit: Long): Int = when {
        size > limit * 3 -> 4
        size > limit * 2 -> 3
        size > limit * 1.4 -> 2
        else -> 1
    }

    /** Número de quadros de uma animação com essa duração e fps. */
    fun frameCount(durationMs: Long, fps: Int): Int =
        maxOf(1, ceil(durationMs * fps / 1000.0).toInt())

    /** Momento (ms, a partir do início do trecho) do quadro k. */
    fun frameTime(k: Int, fps: Int): Int = (k * 1000.0 / fps).roundToInt()

    /**
     * Os quadros foram guardados a [storedFps]. Para montar a animação a [fps],
     * devolve qual quadro guardado usar em cada posição.
     */
    fun pick(storedCount: Int, storedFps: Int, durationMs: Long, fps: Int): List<Int> {
        val n = frameCount(durationMs, fps)
        return List(n) { k ->
            val t = k * 1000.0 / fps
            (t * storedFps / 1000.0).roundToInt().coerceIn(0, storedCount - 1)
        }
    }

    /** Duração inicial sugerida para um trecho (curto fica mais nítido). */
    fun defaultEnd(durationMs: Long): Long = minOf(durationMs, 5_000L)

    /** Corrige um trecho escolhido pelo usuário para caber nas regras. */
    fun clampRange(start: Long, end: Long, total: Long): Pair<Long, Long> {
        val s = start.coerceIn(0, maxOf(0, total - 100))
        var e = end.coerceIn(s + 100, maxOf(s + 100, total))
        if (e - s > Limits.MAX_DURATION_MS) e = s + Limits.MAX_DURATION_MS
        return s to e
    }

    /** Quantas figurinhas repetir para o pacote chegar ao mínimo do WhatsApp. */
    fun padded(count: Int): List<Int> =
        if (count == 0) emptyList()
        else List(maxOf(count, Limits.MIN_STICKERS)) { it % count }
}
