package com.guilherme.gwent

import com.guilherme.gwent.engine.Limits
import com.guilherme.gwent.engine.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {
    @Test fun attemptsGetLighter() {
        val a = Plan.attempts(15)
        assertEquals(15, a.first().fps)
        for (i in 1 until a.size) {
            assertTrue(a[i].fps <= a[i - 1].fps)
            assertTrue(a[i].fps < a[i - 1].fps || a[i].quality < a[i - 1].quality)
        }
        assertTrue(Plan.attempts(5).all { it.fps == 5 })
    }

    @Test fun frameMath() {
        assertEquals(75, Plan.frameCount(5000, 15))
        assertEquals(1, Plan.frameCount(10, 15))
        assertEquals(67, Plan.frameTime(1, 15))
        val picks = Plan.pick(75, 15, 5000, 10)
        assertEquals(50, picks.size)
        assertEquals(0, picks.first())
        assertTrue(picks.last() <= 74)
        assertTrue(picks.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun rangeRespectsTenSeconds() {
        val (s, e) = Plan.clampRange(1000, 30000, 60000)
        assertEquals(1000, s)
        assertEquals(1000 + Limits.MAX_DURATION_MS, e)
        val (s2, e2) = Plan.clampRange(0, 3000, 2000)
        assertEquals(0, s2); assertEquals(2000, e2)
    }

    @Test fun padding() {
        assertEquals(listOf(0, 0, 0), Plan.padded(1))
        assertEquals(listOf(0, 1, 0), Plan.padded(2))
        assertEquals(listOf(0, 1, 2, 3), Plan.padded(4))
        assertEquals(emptyList<Int>(), Plan.padded(0))
    }
}
