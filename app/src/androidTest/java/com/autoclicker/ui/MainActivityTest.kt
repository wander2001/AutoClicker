package com.autoclicker.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // 清除测试数据，避免测试间相互影响
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("autoclicker_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()

        // 先初始化 Intents 再启动，确保 onCreate 中的权限 Intent 被拦截
        Intents.init()
        Intents.intending(hasAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        Intents.intending(hasAction(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        Intents.release()
        scenario.close()
    }

    // ── 启动 & 基础 UI ────────────────────────────────────────────────

    @Test
    fun app_launches_and_shows_float_button() {
        onView(withText("显示悬浮工具条")).check(matches(isDisplayed()))
    }

    @Test
    fun permission_card_shows_overlay_label() {
        onView(withText("悬浮窗权限")).check(matches(isDisplayed()))
    }

    @Test
    fun permission_card_shows_accessibility_label() {
        onView(withText("无障碍服务")).check(matches(isDisplayed()))
    }

    @Test
    fun two_enable_buttons_exist() {
        // 验证悬浮窗权限和无障碍各有一个"去开启"按钮，共 2 个
        onView(allOf(withText("去开启"), hasSibling(withText("悬浮窗权限"))))
            .check(matches(isDisplayed()))
        onView(allOf(withText("去开启"), hasSibling(withText("无障碍服务"))))
            .check(matches(isDisplayed()))
    }

    // ── FAB / 新建脚本 ────────────────────────────────────────────────

    @Test
    fun fab_click_shows_edit_dialog() {
        onView(withClassName(containsString("FloatingActionButton"))).perform(click())
        onView(withText("脚本名称")).check(matches(isDisplayed()))
    }

    @Test
    fun new_script_dialog_can_be_cancelled() {
        onView(withClassName(containsString("FloatingActionButton"))).perform(click())
        onView(withText("取消")).perform(click())
        onView(withText("脚本名称")).check(doesNotExist())
        onView(withText("显示悬浮工具条")).check(matches(isDisplayed()))
    }

    @Test
    fun new_script_save_closes_dialog() {
        onView(withClassName(containsString("FloatingActionButton"))).perform(click())
        onView(withClassName(containsString("EditText"))).perform(replaceText("TestScript"))
        onView(withText("保存")).perform(click())
        onView(withText("脚本名称")).check(doesNotExist())
    }

    // ── 权限 Intent 验证（只验证 onCreate 已触发，不额外点击）────────

    @Test
    fun overlay_permission_intent_fired_on_launch() {
        // 模拟器无悬浮窗权限，onCreate 应触发 Intent
        Intents.intended(hasAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }
}
