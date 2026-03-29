package com.autoclicker.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class ScriptRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("autoclicker_prefs", Context.MODE_PRIVATE)

    // ── CRUD ────────────────────────────────────────────────────────

    fun getAll(): MutableList<ClickScript> {
        val json = prefs.getString(KEY_SCRIPTS, null) ?: return mutableListOf()
        return json.toScriptList().toMutableList()
    }

    fun save(script: ClickScript) {
        val list = getAll()
        val idx = list.indexOfFirst { it.id == script.id }
        if (idx >= 0) list[idx] = script else list.add(0, script)
        prefs.edit { putString(KEY_SCRIPTS, list.toJson()) }
    }

    fun delete(id: String) {
        val list = getAll().filter { it.id != id }
        prefs.edit { putString(KEY_SCRIPTS, list.toJson()) }
    }

    fun get(id: String): ClickScript? = getAll().firstOrNull { it.id == id }

    // ── 导出到 Uri（SAF） ────────────────────────────────────────────

    fun exportToUri(context: Context, uri: Uri, scriptId: String? = null): Boolean = try {
        val data = if (scriptId != null) {
            val s = get(scriptId) ?: return false
            listOf(s).toJson()
        } else {
            getAll().toJson()
        }
        context.contentResolver.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os).use { it.write(data) }
        }
        true
    } catch (e: Exception) { false }

    // ── 从 Uri 导入 ──────────────────────────────────────────────────

    fun importFromUri(context: Context, uri: Uri): Int {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins)).readText()
            } ?: return 0
            val imported = json.toScriptList()
            val existing = getAll()
            var count = 0
            imported.forEach { script ->
                if (existing.none { it.id == script.id }) {
                    existing.add(0, script)
                    count++
                }
            }
            prefs.edit { putString(KEY_SCRIPTS, existing.toJson()) }
            count
        } catch (e: Exception) { 0 }
    }

    companion object {
        private const val KEY_SCRIPTS = "scripts_json"
    }
}
