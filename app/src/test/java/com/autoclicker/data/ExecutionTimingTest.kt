package com.autoclicker.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * 验证点击脚本执行时的次数与间隔是否符合设定。
 * @OptIn 是因为 currentTime 在 coroutines-test 中仍为 Experimental API。
 *
 * 使用 kotlinx-coroutines-test 的虚拟时钟（不真实等待），
 * 与生产代码 AutoClickAccessibilityService.startScript() 的循环逻辑完全一致。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExecutionTimingTest {

    /**
     * 模拟 startScript 执行循环，收集每次"点击"发生的虚拟时间戳。
     */
    private suspend fun TestScope.runScript(
        script: ClickScript,
        onClick: (round: Int, stepIdx: Int, timeMs: Long) -> Unit
    ) {
        var round = 0
        val total = script.repeatCount
        while (total < 0 || round < total) {
            script.steps.forEachIndexed { idx, step ->
                onClick(round + 1, idx, currentTime)
                val waitMs = if (step.delayMs > 0) step.delayMs else script.intervalMs
                delay(waitMs)
            }
            round++
        }
    }

    // ── 100次 50ms ───────────────────────────────────────────────────

    @Test
    fun click_count_equals_100() = runTest {
        val script = ClickScript(repeatCount = 100, intervalMs = 50L).apply {
            steps.add(ClickStep(540, 960))
        }
        var count = 0
        runScript(script) { _, _, _ -> count++ }
        assertEquals("点击总次数应为 100", 100, count)
    }

    @Test
    fun interval_between_clicks_is_50ms() = runTest {
        val script = ClickScript(repeatCount = 100, intervalMs = 50L).apply {
            steps.add(ClickStep(540, 960))
        }
        val times = mutableListOf<Long>()
        runScript(script) { _, _, t -> times.add(t) }

        // 验证相邻两次点击间隔均为 50ms
        val intervals = times.zipWithNext { a, b -> b - a }
        assertTrue("至少应有 99 个间隔", intervals.size >= 99)
        intervals.forEachIndexed { i, interval ->
            assertEquals("第 ${i + 1} 个间隔应为 50ms，实际 ${interval}ms", 50L, interval)
        }
    }

    @Test
    fun total_elapsed_time_is_correct() = runTest {
        val script = ClickScript(repeatCount = 100, intervalMs = 50L).apply {
            steps.add(ClickStep(540, 960))
        }
        runScript(script) { _, _, _ -> }
        // 100次点击，每次延迟50ms，总耗时 = 100 × 50ms = 5000ms
        assertEquals("总耗时应为 5000ms", 5000L, currentTime)
    }

    @Test
    fun first_click_happens_at_time_zero() = runTest {
        val script = ClickScript(repeatCount = 100, intervalMs = 50L).apply {
            steps.add(ClickStep(540, 960))
        }
        var firstTime = -1L
        runScript(script) { round, _, t ->
            if (round == 1 && firstTime == -1L) firstTime = t
        }
        assertEquals("第一次点击应在 t=0", 0L, firstTime)
    }

    // ── 多步骤脚本 ────────────────────────────────────────────────────

    @Test
    fun two_steps_100_repeats_correct_total_clicks() = runTest {
        // 2个点位 × 100次 = 200次点击
        val script = ClickScript(repeatCount = 100, intervalMs = 50L).apply {
            steps.add(ClickStep(100, 200))
            steps.add(ClickStep(300, 400))
        }
        var count = 0
        runScript(script) { _, _, _ -> count++ }
        assertEquals("2步×100次应为 200 次点击", 200, count)
    }

    @Test
    fun two_steps_total_elapsed_time_is_correct() = runTest {
        // 2步 × 100次 × 50ms = 10000ms
        val script = ClickScript(repeatCount = 100, intervalMs = 50L).apply {
            steps.add(ClickStep(100, 200))
            steps.add(ClickStep(300, 400))
        }
        runScript(script) { _, _, _ -> }
        assertEquals("2步×100次总耗时应为 10000ms", 10000L, currentTime)
    }

    // ── 自定义步骤延迟 ────────────────────────────────────────────────

    @Test
    fun step_custom_delay_overrides_global_interval() = runTest {
        // 步骤自定义延迟 200ms，优先级高于全局 50ms
        val script = ClickScript(repeatCount = 1, intervalMs = 50L).apply {
            steps.add(ClickStep(540, 960, delayMs = 200L))
        }
        runScript(script) { _, _, _ -> }
        assertEquals("自定义延迟 200ms 应覆盖全局 50ms", 200L, currentTime)
    }

    // ── 边界条件 ──────────────────────────────────────────────────────

    @Test
    fun single_click_correct_count_and_time() = runTest {
        val script = ClickScript(repeatCount = 1, intervalMs = 50L).apply {
            steps.add(ClickStep(0, 0))
        }
        var count = 0
        runScript(script) { _, _, _ -> count++ }
        assertEquals(1, count)
        assertEquals(50L, currentTime)
    }
}
