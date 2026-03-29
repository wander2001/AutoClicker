package com.autoclicker.ui

import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.*
import com.autoclicker.data.ClickStep
import kotlin.math.roundToInt

/**
 * 点位列表 Adapter，支持：
 *  - 显示序号、坐标、自定义延迟
 *  - 滑动删除
 *  - 长按拖拽排序
 *  - 执行时高亮当前步骤
 */
class StepAdapter(
    private val steps: MutableList<ClickStep>,
    private val onDelete: (Int) -> Unit,
    private val onMove: (Int, Int) -> Unit
) : RecyclerView.Adapter<StepAdapter.VH>() {

    private var activeStep = -1

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val numTv: TextView   = root.getChildAt(0) as TextView
        val xyTv: TextView    = root.getChildAt(1) as TextView
        val delTv: TextView   = root.getChildAt(2) as TextView
        val delBtn: ImageButton = root.getChildAt(3) as ImageButton
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp = { v: Int -> (v * ctx.resources.displayMetrics.density).roundToInt() }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(8), dp(7))
        }

        val num = TextView(ctx).apply {
            textSize = 11f; setTextColor(0xFF85B7EB.toInt())
            minWidth = dp(24); gravity = Gravity.CENTER
            setBackgroundColor(0x261D6B8E)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val xy = TextView(ctx).apply {
            textSize = 11f; setTextColor(0xB3FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(8), 0, 0, 0)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val del = TextView(ctx).apply {
            textSize = 9f; setTextColor(0x40FFFFFF.toInt())
            setPadding(dp(4), 0, dp(8), 0)
        }
        val delBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }

        row.addView(num); row.addView(xy); row.addView(del); row.addView(delBtn)
        return VH(row)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val step = steps[pos]
        h.numTv.text = (pos + 1).toString()
        h.xyTv.text  = "X: ${step.x}   Y: ${step.y}"
        h.delTv.text = if (step.delayMs > 0) "+${step.delayMs}ms" else ""
        h.delBtn.setOnClickListener { onDelete(h.bindingAdapterPosition) }

        // 当前执行步骤高亮
        h.root.setBackgroundColor(
            if (pos == activeStep) 0x263B6D11 else Color.TRANSPARENT
        )
        h.numTv.setBackgroundColor(
            if (pos == activeStep) 0x60639922 else 0x261D6B8E
        )
    }

    override fun getItemCount() = steps.size

    fun setActiveStep(idx: Int) {
        val old = activeStep
        activeStep = idx
        if (old >= 0 && old < steps.size) notifyItemChanged(old)
        if (idx >= 0 && idx < steps.size) notifyItemChanged(idx)
    }

    /** 附加滑动删除 + 拖拽排序 */
    fun attachToRecycler(rv: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                onMove(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                onDelete(vh.bindingAdapterPosition)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(rv)
    }
}
