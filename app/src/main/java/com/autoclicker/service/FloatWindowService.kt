package com.autoclicker.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.autoclicker.R
import com.autoclicker.data.*
import com.autoclicker.ui.FloatBarView

/**
 * 前台服务：管理悬浮窗生命周期。
 * 悬浮窗 UI 委托给 [FloatBarView]。
 */
class FloatWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatBarView: FloatBarView? = null
    private lateinit var repo: ScriptRepository

    // 当前激活脚本
    var activeScript: ClickScript? = null
        private set

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repo = ScriptRepository(this)
        startForegroundNotification()
        showFloatBar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOAD_SCRIPT -> {
                val id = intent.getStringExtra(EXTRA_SCRIPT_ID) ?: return START_STICKY
                activeScript = repo.get(id)
                floatBarView?.loadScript(activeScript)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AutoClickAccessibilityService.instance?.stopExecution()
        floatBarView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@FloatWindowService
    }

    // ── 悬浮窗 ──────────────────────────────────────────────────────

    private fun showFloatBar() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            TYPE_APPLICATION_OVERLAY else TYPE_PHONE

        val params = LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            layoutFlag,
            FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40; y = 120
        }

        floatBarView = FloatBarView(this, windowManager, params, repo).apply {
            onScriptChanged = { script -> activeScript = script }
        }
        windowManager.addView(floatBarView, params)
    }

    // ── 前台通知 ────────────────────────────────────────────────────

    private fun startForegroundNotification() {
        val channelId = "autoclicker_float"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    companion object {
        const val ACTION_LOAD_SCRIPT = "com.autoclicker.LOAD_SCRIPT"
        const val ACTION_STOP = "com.autoclicker.STOP"
        const val EXTRA_SCRIPT_ID = "script_id"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, FloatWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatWindowService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun loadScript(context: Context, scriptId: String) {
            context.startService(
                Intent(context, FloatWindowService::class.java).apply {
                    action = ACTION_LOAD_SCRIPT
                    putExtra(EXTRA_SCRIPT_ID, scriptId)
                }
            )
        }
    }
}
