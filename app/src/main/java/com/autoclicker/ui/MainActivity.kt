package com.autoclicker.ui

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.*
import com.autoclicker.data.*
import com.autoclicker.service.*
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * 主界面职责：
 *  1. 权限引导（悬浮窗 + 无障碍）
 *  2. 脚本列表（增删改查）
 *  3. 导入 / 导出
 *  4. 启动 / 停止悬浮服务
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repo: ScriptRepository
    private lateinit var scriptAdapter: ScriptListAdapter
    private val scripts get() = scriptAdapter.scripts

    // SAF 文件选择器
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        val ok = repo.exportToUri(this, uri)
        toast(if (ok) "导出成功" else "导出失败")
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val count = repo.importFromUri(this, uri)
        toast(if (count > 0) "导入 $count 条脚本" else "无新脚本")
        refreshList()
    }

    // ── 生命周期 ─────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ScriptRepository(this)
        buildUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        updatePermStatus()
    }

    // ── UI（程序化，无 XML）────────────────────────────────────────────

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111827.toInt())
        }

        // 顶部标题栏
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        val titleTv = TextView(this).apply {
            text = "AutoClicker"
            textSize = 20f; setTextColor(0xE6FFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val menuBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_more)
            setBackgroundColor(0); setColorFilter(0xB3FFFFFF.toInt())
            setOnClickListener { showMenu(it) }
        }
        titleBar.addView(titleTv); titleBar.addView(menuBtn)

        // 权限状态卡片
        val permCard = buildPermCard()

        // 脚本列表
        scriptAdapter = ScriptListAdapter(
            onRun   = { startScript(it) },
            onEdit  = { editScript(it) },
            onDelete = { confirmDelete(it) }
        )
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = scriptAdapter
            setPadding(dp(8), 0, dp(8), dp(80))
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // FAB
        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setOnClickListener { newScript() }
        }
        val fabWrap = FrameLayout(this).apply {
            setPadding(0, 0, dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            addView(fab, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ))
        }
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(rv)
            addView(fabWrap, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        root.addView(titleBar)
        root.addView(permCard)
        root.addView(frame)
        setContentView(root)
    }

    private fun buildPermCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xFF1A2236.toInt())

            val overlayRow = makePermRow("悬浮窗权限", "overlayPerm") { requestOverlay() }
            val accessRow  = makePermRow("无障碍服务", "accessPerm") { openAccessibility() }
            addView(overlayRow); addView(accessRow)

            val startBtn = Button(this@MainActivity).apply {
                text = "显示悬浮工具条"
                setOnClickListener { startFloatService() }
            }
            addView(startBtn)
            tag = "permCard"
        }
    }

    private fun makePermRow(label: String, tag: String, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))

            val lbl = TextView(this@MainActivity).apply {
                text = label; setTextColor(0xB3FFFFFF.toInt()); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val status = TextView(this@MainActivity).apply {
                textSize = 11f; this.tag = tag; setPadding(dp(8), 0, dp(8), 0)
            }
            val btn = Button(this@MainActivity).apply {
                text = "去开启"; textSize = 11f; setOnClickListener { action() }
            }
            addView(lbl); addView(status); addView(btn)
        }
    }

    private fun updatePermStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessOk  = AutoClickAccessibilityService.isEnabled()
        updateStatus("overlayPerm", overlayOk)
        updateStatus("accessPerm", accessOk)
    }

    private fun updateStatus(tag: String, ok: Boolean) {
        val tv = window.decorView.findViewWithTag<TextView>(tag) ?: return
        tv.text = if (ok) "✓ 已开启" else "✗ 未开启"
        tv.setTextColor(if (ok) 0xFF639922.toInt() else 0xFFA32D2D.toInt())
    }

    // ── 权限 ─────────────────────────────────────────────────────────

    private fun checkPermissions() {
        if (!Settings.canDrawOverlays(this)) requestOverlay()
    }

    private fun requestOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        toast("请找到 AutoClicker 并开启无障碍服务")
    }

    // ── 服务控制 ─────────────────────────────────────────────────────

    private fun startFloatService() {
        if (!Settings.canDrawOverlays(this)) { toast("请先开启悬浮窗权限"); return }
        FloatWindowService.start(this)
        toast("悬浮工具条已显示")
    }

    private fun startScript(script: ClickScript) {
        if (!Settings.canDrawOverlays(this)) { toast("请先开启悬浮窗权限"); return }
        if (!AutoClickAccessibilityService.isEnabled()) { openAccessibility(); return }
        FloatWindowService.start(this)
        FloatWindowService.loadScript(this, script.id)
        toast("脚本「${script.name}」已加载到悬浮窗")
        finish()
    }

    // ── 脚本 CRUD ────────────────────────────────────────────────────

    private fun newScript() {
        val script = ClickScript(name = "新脚本 ${System.currentTimeMillis() % 1000}")
        repo.save(script)
        refreshList()
        editScript(script)
    }

    private fun editScript(script: ClickScript) {
        val et = EditText(this).apply { setText(script.name); textSize = 14f; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        AlertDialog.Builder(this)
            .setTitle("脚本名称")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                script.name = et.text.toString().ifBlank { script.name }
                repo.save(script)
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(script: ClickScript) {
        AlertDialog.Builder(this)
            .setTitle("删除脚本")
            .setMessage("确定删除「${script.name}」？")
            .setPositiveButton("删除") { _, _ -> repo.delete(script.id); refreshList() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshList() {
        scriptAdapter.scripts = repo.getAll().toMutableList()
        scriptAdapter.notifyDataSetChanged()
    }

    // ── 菜单 ─────────────────────────────────────────────────────────

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("导入脚本")
            menu.add("导出全部脚本")
            menu.add("停止悬浮窗")
            setOnMenuItemClickListener { item ->
                when (item.title) {
                    "导入脚本" -> importLauncher.launch(arrayOf("application/json"))
                    "导出全部脚本" -> exportLauncher.launch("autoclicker_scripts.json")
                    "停止悬浮窗" -> FloatWindowService.stop(this@MainActivity)
                }
                true
            }
            show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ── ScriptListAdapter ────────────────────────────────────────────────────────

class ScriptListAdapter(
    val onRun: (ClickScript) -> Unit,
    val onEdit: (ClickScript) -> Unit,
    val onDelete: (ClickScript) -> Unit
) : RecyclerView.Adapter<ScriptListAdapter.VH>() {

    var scripts: MutableList<ClickScript> = mutableListOf()

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val nameTv: TextView = root.getChildAt(0) as TextView
        val infoTv: TextView = root.getChildAt(1) as TextView
        val runBtn: Button   = root.getChildAt(2) as Button
        val editBtn: Button  = root.getChildAt(3) as Button
        val delBtn: Button   = root.getChildAt(4) as Button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp = { v: Int -> (v * ctx.resources.displayMetrics.density).toInt() }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            setBackgroundColor(0xFF141E2E.toInt())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(1)) }
        }
        val name = TextView(ctx).apply { textSize = 14f; setTextColor(0xE6FFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val info = TextView(ctx).apply { textSize = 10f; setTextColor(0x60FFFFFF.toInt()); setPadding(dp(8), 0, 0, 0) }
        val run  = Button(ctx).apply { text = "▶"; textSize = 12f }
        val edit = Button(ctx).apply { text = "✎"; textSize = 12f }
        val del  = Button(ctx).apply { text = "✕"; textSize = 12f }
        row.addView(name); row.addView(info); row.addView(run); row.addView(edit); row.addView(del)
        return VH(row)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = scripts[pos]
        h.nameTv.text = s.name
        h.infoTv.text = "${s.steps.size}步 · ${if (s.repeatCount < 0) "∞" else s.repeatCount}次 · ${s.intervalMs}ms"
        h.runBtn.setOnClickListener  { onRun(s) }
        h.editBtn.setOnClickListener { onEdit(s) }
        h.delBtn.setOnClickListener  { onDelete(s) }
    }

    override fun getItemCount() = scripts.size
}
