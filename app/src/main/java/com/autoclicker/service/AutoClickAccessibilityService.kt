package com.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.autoclicker.data.ClickScript
import com.autoclicker.data.RunState
import kotlinx.coroutines.*

/**
 * 无障碍服务：执行屏幕点击手势。
 * 通过单例 [instance] 供 FloatWindowService 调用。
 */
class AutoClickAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var executeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 状态回调，由 FloatWindowService 注册
    var onStateChanged: ((RunState) -> Unit)? = null
    var onStepExecuted: ((stepIndex: Int) -> Unit)? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        stopExecution()
        scope.cancel()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopExecution() }

    // ── 公开 API ─────────────────────────────────────────────────────

    fun startScript(script: ClickScript) {
        stopExecution()
        executeJob = scope.launch {
            val totalRounds = script.repeatCount   // -1 = 无限
            var round = 0

            while (isActive && (totalRounds < 0 || round < totalRounds)) {
                script.steps.forEachIndexed { idx, step ->
                    if (!isActive) return@launch

                    onStateChanged?.invoke(
                        RunState(true, round + 1, idx, if (totalRounds < 0) -1 else totalRounds)
                    )
                    onStepExecuted?.invoke(idx)

                    // 执行单次点击手势
                    performClick(step.x.toFloat(), step.y.toFloat())

                    // 等待间隔（优先使用步骤自定义延迟）
                    val delay = if (step.delayMs > 0) step.delayMs else script.intervalMs
                    delay(delay)
                }
                round++
            }

            onStateChanged?.invoke(RunState(false, round, 0, totalRounds))
        }
    }

    fun stopExecution() {
        executeJob?.cancel()
        executeJob = null
        onStateChanged?.invoke(RunState(false))
    }

    val isRunning get() = executeJob?.isActive == true

    // ── 手势执行 ─────────────────────────────────────────────────────

    private fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, CLICK_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        var instance: AutoClickAccessibilityService? = null
        private const val CLICK_DURATION_MS = 50L   // 点击持续时间

        fun isEnabled() = instance != null
    }
}
