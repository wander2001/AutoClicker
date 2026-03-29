package com.autoclicker.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.WindowManager.LayoutParams as WMLayoutParams
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autoclicker.data.*
import com.autoclicker.service.AutoClickAccessibilityService
import kotlin.math.roundToInt

/**
 * 悬浮工具条视图，直接 inflate 并附加到 WindowManager。
 * 三态：collapsed（只有两个按钮）→ normal（加计数+间隔）→ expanded（全功能）
 */
@SuppressLint("ClickableViewAccessibility")
class FloatBarView(
    private val ctx: Context,
    private val wm: WindowManager,
    private val params: WMLayoutParams,
    private val repo: ScriptRepository
) : FrameLayout(ctx) {

    var onScriptChanged: ((ClickScript) -> Unit)? = null

    // 当前脚本
    private var script: ClickScript = ClickScript()
    private var isRunning = false
    private var isRecording = false
    private var expandLevel = 0   // 0=最小  1=普通  2=完整

    // 间隔分段
    private var segMin = 0; private var segSec = 1; private var segMs = 0

    private val handler = Handler(Looper.getMainLooper())
    private var runCount = 0
    private var runRound = 0

    // 步骤 adapter
    private lateinit var stepAdapter: StepAdapter
    private lateinit var stepRecycler: RecyclerView

    // ── Views ────────────────────────────────────────────────────────
    private lateinit var playBtn: ImageButton
    private lateinit var expandBtn: ImageButton
    private lateinit var pillNow: TextView
    private lateinit var pillTotal: TextView
    private lateinit var pillItv: TextView
    private lateinit var extPanel: View
    private lateinit var segMinTv: TextView
    private lateinit var segSecTv: TextView
    private lateinit var segMsTv: TextView
    private lateinit var totalDisplay: TextView
    private lateinit var progBar: ProgressBar
    private lateinit var progPct: TextView
    private lateinit var addStepBtn: Button
    private lateinit var recBtn: Button
    private lateinit var infBtn: Button
    private lateinit var stepBadge: TextView

    init {
        buildUI()
        wireListeners()
        syncInterval()
    }

    // ── UI 构建 ──────────────────────────────────────────────────────

    private fun buildUI() {
        setBackgroundColor(Color.TRANSPARENT)

        // 根容器（竖向弹性布局）
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
        }

        // ── 顶部行（始终可见）──
        val topRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        // 拖把手
        val pip = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(3)).also { it.marginEnd = dp(6) }
            setBackgroundColor(0x30FFFFFF)
        }

        playBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(0xFF185FA5.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val sep1 = makeSep()

        pillNow = makeCountTv("0")
        val slash = makeLabel("/")
        pillTotal = makeCountTv("10")
        val pillBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x0DFFFFFF)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(pillNow); addView(slash); addView(pillTotal)
        }

        val sep2 = makeSep()

        pillItv = makeCountTv("1000ms").apply { textSize = 11f }
        val itvBox = LinearLayout(ctx).apply {
            setBackgroundColor(0x0DFFFFFF)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(pillItv)
        }

        stepBadge = TextView(ctx).apply {
            textSize = 9f; setTextColor(Color.WHITE)
            setBackgroundColor(0xFFA32D2D.toInt())
            setPadding(dp(3), dp(1), dp(3), dp(1))
            visibility = View.GONE
        }

        val expandWrap = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            expandBtn = ImageButton(ctx).apply {
                setImageResource(android.R.drawable.arrow_down_float)
                setBackgroundColor(0x12FFFFFF)
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28))
            }
            addView(expandBtn)
            addView(stepBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }

        topRow.addView(pip)
        topRow.addView(playBtn)
        topRow.addView(sep1)
        topRow.addView(pillBox)
        topRow.addView(sep2)
        topRow.addView(itvBox)
        topRow.addView(expandWrap)

        // ── 展开面板 ──
        extPanel = buildExtPanel()
        extPanel.visibility = View.GONE

        root.addView(topRow)
        root.addView(extPanel)
        addView(root)
        makeDraggable(pip)
        makeDraggable(topRow)
    }

    private fun buildExtPanel(): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }

        // 间隔编辑器
        panel.addView(makeSectionLabel("点击间隔（拖动数字调节）"))
        val itvRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        segMinTv = makeSegTv("00", "min")
        segSecTv = makeSegTv("01", "sec")
        segMsTv  = makeSegTv("000", "ms")
        itvRow.addView(wrapSeg(segMinTv, "分"))
        itvRow.addView(makeLabel(":").apply { textSize = 20f; setPadding(dp(2),0,dp(2),0) })
        itvRow.addView(wrapSeg(segSecTv, "秒"))
        itvRow.addView(makeLabel(".").apply { textSize = 20f; setPadding(dp(1),0,dp(1),0) })
        itvRow.addView(wrapSeg(segMsTv, "ms"))
        panel.addView(itvRow)

        // 快捷预设
        val presetRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(100L to "100ms", 200L to "200ms", 500L to "500ms", 1000L to "1s", 2000L to "2s", 5000L to "5s").forEach { (ms, label) ->
            presetRow.addView(Button(ctx).apply {
                text = label; textSize = 9f; setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnClickListener { setIntervalMs(ms) }
            })
        }
        panel.addView(presetRow)

        // 次数
        panel.addView(makeSectionLabel("执行次数"))
        val countRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val btnMM = makeSmBtn("−−") { changeTotal(-10) }
        val btnM  = makeSmBtn("−") { changeTotal(-1) }
        totalDisplay = makeCountTv(script.repeatCount.toString()).apply { textSize = 18f; minWidth = dp(48) }
        val btnP  = makeSmBtn("+") { changeTotal(1) }
        val btnPP = makeSmBtn("++") { changeTotal(10) }
        infBtn = Button(ctx).apply { text = "∞"; textSize = 10f; setOnClickListener { toggleInfinite() } }
        countRow.addView(btnMM); countRow.addView(btnM); countRow.addView(totalDisplay)
        countRow.addView(btnP); countRow.addView(btnPP); countRow.addView(infBtn)
        panel.addView(countRow)

        // 进度
        panel.addView(makeSectionLabel("进度"))
        val progRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        progBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; layoutParams = LinearLayout.LayoutParams(0, dp(6), 1f)
        }
        progPct = makeLabel("0%").apply { minWidth = dp(32) }
        progRow.addView(progBar); progRow.addView(progPct)
        panel.addView(progRow)

        // 脚本步骤列表
        panel.addView(makeSectionLabel("点击步骤"))
        stepAdapter = StepAdapter(script.steps,
            onDelete = { idx -> script.steps.removeAt(idx); stepAdapter.notifyItemRemoved(idx); syncBadge() },
            onMove = { from, to ->
                val item = script.steps.removeAt(from)
                script.steps.add(to, item)
                stepAdapter.notifyItemMoved(from, to)
            }
        )
        stepRecycler = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = stepAdapter
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160))
        }
        panel.addView(stepRecycler)

        // 添加点位 / 录制 / 保存
        val fnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        addStepBtn = Button(ctx).apply {
            text = "+ 添加点位"; textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showAddStepDialog() }
        }
        recBtn = Button(ctx).apply {
            text = "● 录制"; textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { toggleRecording() }
        }
        val saveBtn = Button(ctx).apply {
            text = "保存"; textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { saveScript() }
        }
        fnRow.addView(addStepBtn); fnRow.addView(recBtn); fnRow.addView(saveBtn)
        panel.addView(fnRow)

        return panel
    }

    // ── 事件绑定 ─────────────────────────────────────────────────────

    private fun wireListeners() {
        playBtn.setOnClickListener { if (isRunning) stopExecution() else startExecution() }
        expandBtn.setOnClickListener { cycleExpand() }
        wireSegDrag(segMinTv, "min")
        wireSegDrag(segSecTv, "sec")
        wireSegDrag(segMsTv, "ms")

        // 无障碍服务回调
        AutoClickAccessibilityService.instance?.apply {
            onStateChanged = { state ->
                handler.post {
                    this@FloatBarView.isRunning = state.isRunning
                    this@FloatBarView.runRound = state.currentRound
                    updateRunUI()
                }
            }
            onStepExecuted = { idx ->
                handler.post { stepAdapter.setActiveStep(idx) }
            }
        }
    }

    // ── 拖拽工具条 ───────────────────────────────────────────────────

    private var dX = 0f; private var dY = 0f
    private fun makeDraggable(v: View) {
        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = event.rawX - params.x; dY = event.rawY - params.y; false }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - dX).roundToInt()
                    params.y = (event.rawY - dY).roundToInt()
                    wm.updateViewLayout(this@FloatBarView, params); true
                }
                else -> false
            }
        }
    }

    // ── 展开级别 ─────────────────────────────────────────────────────

    private fun cycleExpand() {
        expandLevel = (expandLevel + 1) % 3
        extPanel.visibility = if (expandLevel == 2) View.VISIBLE else View.GONE
        wm.updateViewLayout(this, params)
    }

    // ── 间隔调节 ─────────────────────────────────────────────────────

    private fun wireSegDrag(tv: TextView, seg: String) {
        var startY = 0f; var startVal = 0
        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startVal = getSegVal(seg)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = startY - event.rawY
                    val step = if (seg == "ms") 10 else 1
                    val max  = if (seg == "ms") 999 else 59
                    val nv = (startVal + (dy / 6).roundToInt() * step).coerceIn(0, max)
                    setSegVal(seg, nv)
                    syncInterval()
                    true
                }
                else -> false
            }
        }
    }

    private fun getSegVal(seg: String) = when(seg) { "min" -> segMin; "sec" -> segSec; else -> segMs }
    private fun setSegVal(seg: String, v: Int) { when(seg) { "min" -> segMin=v; "sec" -> segSec=v; else -> segMs=v } }

    private fun syncInterval() {
        script.intervalMs = (segMin * 60_000L + segSec * 1_000L + segMs).coerceAtLeast(1L)
        segMinTv.text = pad(segMin, 2)
        segSecTv.text = pad(segSec, 2)
        segMsTv.text  = pad(segMs, 3)
        pillItv.text  = fmtMs(script.intervalMs)
    }

    private fun setIntervalMs(ms: Long) {
        segMin = (ms / 60_000).toInt()
        segSec = ((ms % 60_000) / 1_000).toInt()
        segMs  = (ms % 1_000).toInt()
        syncInterval()
    }

    // ── 次数 ─────────────────────────────────────────────────────────

    private fun changeTotal(d: Int) {
        if (script.repeatCount < 0) return
        script.repeatCount = (script.repeatCount + d).coerceIn(1, 9999)
        totalDisplay.text = script.repeatCount.toString()
        pillTotal.text = script.repeatCount.toString()
    }

    private fun toggleInfinite() {
        script.repeatCount = if (script.repeatCount >= 0) -1 else 10
        totalDisplay.text = if (script.repeatCount < 0) "∞" else script.repeatCount.toString()
        pillTotal.text = totalDisplay.text
    }

    // ── 脚本执行 ─────────────────────────────────────────────────────

    private fun startExecution() {
        val svc = AutoClickAccessibilityService.instance
        if (svc == null) { toast("请先开启无障碍服务"); return }
        if (script.steps.isEmpty()) { toast("请先添加点位"); return }
        isRunning = true
        runCount = 0; runRound = 0
        playBtn.setImageResource(android.R.drawable.ic_media_pause)
        svc.onStateChanged = { state ->
            handler.post { isRunning = state.isRunning; runRound = state.currentRound; updateRunUI() }
        }
        svc.onStepExecuted = { idx -> handler.post { stepAdapter.setActiveStep(idx) } }
        svc.startScript(script)
    }

    private fun stopExecution() {
        AutoClickAccessibilityService.instance?.stopExecution()
        isRunning = false
        playBtn.setImageResource(android.R.drawable.ic_media_play)
        stepAdapter.setActiveStep(-1)
        updateRunUI()
    }

    private fun updateRunUI() {
        pillNow.text = runRound.toString()
        val total = script.repeatCount
        val pct = if (total <= 0) 100 else ((runRound.toFloat() / total) * 100).roundToInt().coerceIn(0, 100)
        progBar.progress = pct
        progPct.text = if (total <= 0) "∞" else "$pct%"
        if (!isRunning) { playBtn.setImageResource(android.R.drawable.ic_media_play); stepAdapter.setActiveStep(-1) }
    }

    // ── 添加点位弹窗 ─────────────────────────────────────────────────

    private fun showAddStepDialog() {
        val dialog = android.app.AlertDialog.Builder(ctx)
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val xEdit = EditText(ctx).apply { hint = "X 坐标（像素）"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val yEdit = EditText(ctx).apply { hint = "Y 坐标（像素）"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        layout.addView(xEdit); layout.addView(yEdit)
        dialog.setTitle("添加点位 #${script.steps.size + 1}")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val x = xEdit.text.toString().toIntOrNull() ?: return@setPositiveButton
                val y = yEdit.text.toString().toIntOrNull() ?: return@setPositiveButton
                val idx = script.steps.size
                script.steps.add(ClickStep(x, y))
                stepAdapter.notifyItemInserted(idx)
                stepRecycler.scrollToPosition(idx)
                syncBadge()
            }
            .setNegativeButton("取消", null)
            .create().also {
                it.window?.setType(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else android.view.WindowManager.LayoutParams.TYPE_PHONE)
            }.show()
    }

    // ── 录制模式 ─────────────────────────────────────────────────────

    private fun toggleRecording() {
        isRecording = !isRecording
        recBtn.text = if (isRecording) "■ 停止录制" else "● 录制"
        toast(if (isRecording) "录制中：点击屏幕记录位置" else "录制结束")
        // 实际录制通过 TouchEvent 覆盖实现，参见 onRecordTouch
    }

    /** 外部（录制模式下）调用此方法添加触摸点 */
    fun onRecordTouch(x: Int, y: Int) {
        if (!isRecording) return
        val idx = script.steps.size
        script.steps.add(ClickStep(x, y))
        stepAdapter.notifyItemInserted(idx)
        syncBadge()
        toast("已记录 #${idx + 1}  ($x, $y)")
    }

    // ── 保存脚本 ─────────────────────────────────────────────────────

    private fun saveScript() {
        repo.save(script)
        onScriptChanged?.invoke(script)
        toast("脚本已保存：${script.name}")
    }

    // ── 公开 API ─────────────────────────────────────────────────────

    fun loadScript(s: ClickScript?) {
        script = s ?: ClickScript()
        stepAdapter.notifyDataSetChanged()
        setIntervalMs(script.intervalMs)
        totalDisplay.text = if (script.repeatCount < 0) "∞" else script.repeatCount.toString()
        pillTotal.text = totalDisplay.text
        syncBadge()
    }

    // ── 辅助 ─────────────────────────────────────────────────────────

    private fun syncBadge() {
        val n = script.steps.size
        stepBadge.visibility = if (n > 0) View.VISIBLE else View.GONE
        stepBadge.text = n.toString()
    }

    private fun toast(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).roundToInt()
    private fun pad(n: Int, l: Int) = n.toString().padStart(l, '0')
    private fun fmtMs(ms: Long) = when {
        ms < 1000 -> "${ms}ms"
        ms < 60000 -> "${ms / 1000.0}s".replace(Regex("\\.0$"), "s").replace("s","s")
        else -> "${ms / 60000}m${(ms % 60000) / 1000}s"
    }

    private fun makeSep() = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(20)).also { it.setMargins(dp(4),0,dp(4),0) }
        setBackgroundColor(0x1AFFFFFF)
    }
    private fun makeLabel(t: String) = TextView(ctx).apply { text = t; setTextColor(0x80FFFFFF.toInt()); textSize = 13f }
    private fun makeCountTv(t: String) = TextView(ctx).apply { text = t; setTextColor(0xE6FFFFFF.toInt()); textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private fun makeSectionLabel(t: String) = TextView(ctx).apply { text = t; setTextColor(0x4DFFFFFF.toInt()); textSize = 10f; setPadding(0, dp(8), 0, dp(4)) }
    private fun makeSmBtn(label: String, action: () -> Unit) = Button(ctx).apply {
        text = label; textSize = 12f
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
    }
    private fun makeSegTv(t: String, seg: String) = TextView(ctx).apply {
        text = t; setTextColor(0xE0FFFFFF.toInt())
        textSize = if (seg == "ms") 18f else 22f
        typeface = android.graphics.Typeface.MONOSPACE
        gravity = Gravity.CENTER; minWidth = dp(if (seg == "ms") 46 else 38)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setBackgroundColor(0x0DFFFFFF)
    }
    private fun wrapSeg(tv: TextView, label: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(tv)
        addView(makeLabel(label).apply { textSize = 9f; gravity = Gravity.CENTER })
    }
}
