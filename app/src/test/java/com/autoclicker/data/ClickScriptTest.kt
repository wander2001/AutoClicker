package com.autoclicker.data

import org.junit.Assert.*
import org.junit.Test

class ClickScriptTest {

    // ── ClickStep 默认值 ────────────────────────────────────────────

    @Test
    fun `ClickStep default delay is 0`() {
        val step = ClickStep(x = 100, y = 200)
        assertEquals(0L, step.delayMs)
    }

    @Test
    fun `ClickStep stores coordinates correctly`() {
        val step = ClickStep(x = 540, y = 960, delayMs = 500L)
        assertEquals(540, step.x)
        assertEquals(960, step.y)
        assertEquals(500L, step.delayMs)
    }

    // ── ClickScript 默认值 ──────────────────────────────────────────

    @Test
    fun `ClickScript default repeatCount is 10`() {
        val script = ClickScript()
        assertEquals(10, script.repeatCount)
    }

    @Test
    fun `ClickScript default intervalMs is 1000`() {
        val script = ClickScript()
        assertEquals(1000L, script.intervalMs)
    }

    @Test
    fun `ClickScript default steps list is empty`() {
        val script = ClickScript()
        assertTrue(script.steps.isEmpty())
    }

    @Test
    fun `ClickScript id is unique for each instance`() {
        val a = ClickScript()
        val b = ClickScript()
        assertNotEquals(a.id, b.id)
    }

    // ── JSON 序列化 ─────────────────────────────────────────────────

    @Test
    fun `toJson and toClickScript round-trip preserves data`() {
        val script = ClickScript(
            name = "测试脚本",
            repeatCount = 5,
            intervalMs = 200L
        ).apply {
            steps.add(ClickStep(100, 200, 50L))
            steps.add(ClickStep(300, 400))
        }

        val json = script.toJson()
        val restored = json.toClickScript()

        assertNotNull(restored)
        assertEquals(script.id, restored!!.id)
        assertEquals("测试脚本", restored.name)
        assertEquals(5, restored.repeatCount)
        assertEquals(200L, restored.intervalMs)
        assertEquals(2, restored.steps.size)
        assertEquals(100, restored.steps[0].x)
        assertEquals(50L, restored.steps[0].delayMs)
    }

    @Test
    fun `toClickScript returns null for invalid JSON`() {
        val result = "not valid json".toClickScript()
        assertNull(result)
    }

    @Test
    fun `list toJson and toScriptList round-trip`() {
        val scripts = listOf(
            ClickScript(name = "A"),
            ClickScript(name = "B")
        )
        val json = scripts.toJson()
        val restored = json.toScriptList()

        assertEquals(2, restored.size)
        assertEquals("A", restored[0].name)
        assertEquals("B", restored[1].name)
    }

    @Test
    fun `toScriptList returns empty list for invalid JSON`() {
        val result = "garbage".toScriptList()
        assertEquals(emptyList<ClickScript>(), result)
    }

    @Test
    fun `toScriptList returns empty list for empty JSON array`() {
        val result = "[]".toScriptList()
        assertTrue(result.isEmpty())
    }

    // ── RunState ────────────────────────────────────────────────────

    @Test
    fun `RunState default is not running`() {
        val state = RunState()
        assertFalse(state.isRunning)
        assertEquals(0, state.currentRound)
        assertEquals(0, state.currentStepIndex)
    }
}
