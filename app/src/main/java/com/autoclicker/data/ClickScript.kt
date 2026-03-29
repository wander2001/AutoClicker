package com.autoclicker.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 单个点击点位 */
data class ClickStep(
    val x: Int,          // 屏幕绝对坐标 X（像素）
    val y: Int,          // 屏幕绝对坐标 Y（像素）
    val delayMs: Long = 0L  // 执行前额外延迟（ms），0=使用全局间隔
)

/** 一条完整点击脚本 */
data class ClickScript(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "脚本 ${System.currentTimeMillis() % 10000}",
    val steps: MutableList<ClickStep> = mutableListOf(),
    var repeatCount: Int = 10,       // 重复次数，-1=无限
    var intervalMs: Long = 1000L,    // 全局点击间隔（毫秒）
    val createdAt: Long = System.currentTimeMillis()
)

/** 执行状态 */
data class RunState(
    val isRunning: Boolean = false,
    val currentRound: Int = 0,
    val currentStepIndex: Int = 0,
    val totalRounds: Int = 0
)

// ── JSON 序列化工具 ──────────────────────────────────────────────
private val gson = Gson()

fun ClickScript.toJson(): String = gson.toJson(this)

fun String.toClickScript(): ClickScript? = try {
    gson.fromJson(this, ClickScript::class.java)
} catch (e: Exception) { null }

fun List<ClickScript>.toJson(): String = gson.toJson(this)

fun String.toScriptList(): List<ClickScript> = try {
    val type = object : TypeToken<List<ClickScript>>() {}.type
    gson.fromJson(this, type) ?: emptyList()
} catch (e: Exception) { emptyList() }
