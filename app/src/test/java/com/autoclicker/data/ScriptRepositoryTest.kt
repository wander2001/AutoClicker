package com.autoclicker.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScriptRepositoryTest {

    private lateinit var repo: ScriptRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        repo = ScriptRepository(context)
    }

    // ── getAll ──────────────────────────────────────────────────────

    @Test
    fun `getAll returns empty list when no scripts saved`() {
        assertTrue(repo.getAll().isEmpty())
    }

    // ── save & get ──────────────────────────────────────────────────

    @Test
    fun `save then get returns same script`() {
        val script = ClickScript(name = "脚本1")
        repo.save(script)
        val found = repo.get(script.id)
        assertNotNull(found)
        assertEquals("脚本1", found!!.name)
    }

    @Test
    fun `save new script adds it to list`() {
        repo.save(ClickScript(name = "A"))
        repo.save(ClickScript(name = "B"))
        assertEquals(2, repo.getAll().size)
    }

    @Test
    fun `save existing script updates it`() {
        val script = ClickScript(name = "原始名")
        repo.save(script)

        val updated = script.copy(name = "新名字")
        repo.save(updated)

        assertEquals(1, repo.getAll().size)
        assertEquals("新名字", repo.get(script.id)!!.name)
    }

    @Test
    fun `new script is inserted at top of list`() {
        val first = ClickScript(name = "第一")
        val second = ClickScript(name = "第二")
        repo.save(first)
        repo.save(second)

        val list = repo.getAll()
        assertEquals("第二", list[0].name)
        assertEquals("第一", list[1].name)
    }

    // ── delete ──────────────────────────────────────────────────────

    @Test
    fun `delete removes script by id`() {
        val script = ClickScript(name = "删除我")
        repo.save(script)
        repo.delete(script.id)
        assertNull(repo.get(script.id))
    }

    @Test
    fun `delete non-existent id does not throw`() {
        repo.save(ClickScript(name = "保留"))
        repo.delete("不存在的id")
        assertEquals(1, repo.getAll().size)
    }

    // ── get ─────────────────────────────────────────────────────────

    @Test
    fun `get returns null for unknown id`() {
        assertNull(repo.get("unknown"))
    }

    // ── steps 持久化 ────────────────────────────────────────────────

    @Test
    fun `script with steps is saved and restored correctly`() {
        val script = ClickScript(name = "带步骤").apply {
            steps.add(ClickStep(10, 20, 100L))
            steps.add(ClickStep(30, 40))
        }
        repo.save(script)

        val restored = repo.get(script.id)!!
        assertEquals(2, restored.steps.size)
        assertEquals(10, restored.steps[0].x)
        assertEquals(100L, restored.steps[0].delayMs)
        assertEquals(30, restored.steps[1].x)
    }
}
