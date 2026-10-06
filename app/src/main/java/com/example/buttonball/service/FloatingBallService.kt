package com.example.buttonball.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import com.example.buttonball.MainActivity
import com.example.buttonball.R
import com.example.buttonball.control.ActionExecutor
import com.example.buttonball.control.ActionStore
import com.example.buttonball.model.ActionType
import com.example.buttonball.model.Gesture
import kotlin.math.abs

/**
 * 悬浮球前台服务。
 *
 * 在系统上层绘制一个可拖动的小圆球，支持「一球多功能」：
 * - 单击 / 双击 / 长按三个手势分别执行各自绑定的动作
 * - 双击（默认）弹出快捷操作面板
 * - 拖动移动浮球，松手自动吸附到屏幕边缘，并记忆位置
 * - 颜色 / 大小 / 透明度可在主界面实时自定义
 *
 * 需要 SYSTEM_ALERT_WINDOW（悬浮窗）权限。
 */
class FloatingBallService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_ball"
        private const val NOTIF_ID = 1001
        private const val EDGE_MARGIN_DP = 8
        private const val TOUCH_SLOP = 12

        @Volatile
        var running: Boolean = false
            private set

        /** 当前运行中的服务实例（用于主界面实时修改外观） */
        @Volatile
        var instance: FloatingBallService? = null
            private set

        /** 启动悬浮球（需已授予悬浮窗权限） */
        fun start(context: Context) {
            if (running) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            running = true
            context.startForegroundService(Intent(context, FloatingBallService::class.java))
        }

        /** 停止悬浮球 */
        fun stop(context: Context) {
            running = false
            context.stopService(Intent(context, FloatingBallService::class.java))
        }

        /** 让运行中的浮球立即套用最新外观设置 */
        fun applyAppearance(context: Context) {
            instance?.applyAppearance()
        }

        /** 重置浮球位置（清空记忆位置并重新放置） */
        fun resetPosition(context: Context) {
            ActionStore.clearPosition(context)
            if (running) {
                stop(context)
                start(context)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var ballIcon: ImageView
    private var layoutParams: WindowManager.LayoutParams? = null
    private var menuPopup: PopupWindow? = null
    private lateinit var gestureDetector: GestureDetector

    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureForeground()
        createBall()
        running = true
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        if (instance === this) instance = null
        menuPopup?.dismiss()
        menuPopup = null
        layoutParams?.let { ActionStore.setPosition(this, it.x, it.y) }
        if (::ballView.isInitialized) {
            layoutParams?.let { runCatching { windowManager.removeView(ballView) } }
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 悬浮球

    private fun createBall() {
        ballView = LayoutInflater.from(this).inflate(R.layout.ball_view, null)
        ballIcon = ballView.findViewById(R.id.ball_icon)
        ballIcon.setImageResource(R.drawable.ic_ball)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isDragging) performGesture(Gesture.SINGLE)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                performGesture(Gesture.DOUBLE)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                performGesture(Gesture.LONG)
            }
        })

        ballView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams?.let { lp ->
                            lp.x = (lp.x + dx).toInt()
                            lp.y = (lp.y + dy).toInt()
                            windowManager.updateViewLayout(ballView, lp)
                        }
                        downRawX = event.rawX
                        downRawY = event.rawY
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) snapToEdge()
                    layoutParams?.let { ActionStore.setPosition(this, it.x, it.y) }
                    isDragging = false
                }
            }
            true
        }

        applyAppearance()

        val size = ActionStore.getSizeDp(this).let(::dp)
        layoutParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = ActionStore.getPosition(this@FloatingBallService)
            if (saved != null) {
                x = saved.first
                y = saved.second
            } else {
                val screenW = windowManager.defaultDisplay?.width ?: 0
                val screenH = windowManager.defaultDisplay?.height ?: 0
                x = (screenW - size - dp(EDGE_MARGIN_DP)).coerceAtLeast(0)
                y = screenH * 2 / 3
            }
        }
        windowManager.addView(ballView, layoutParams)
    }

    /** 套用最新的颜色/大小/透明度设置 */
    private fun applyAppearance() {
        if (!::ballView.isInitialized) return
        val color = ActionStore.getColor(this)
        val alpha = ActionStore.getAlpha(this)
        ballView.background = ballBackground(color)
        ballView.alpha = alpha

        val size = dp(ActionStore.getSizeDp(this))
        val lp = layoutParams
        if (lp != null) {
            lp.width = size
            lp.height = size
            if (::ballView.isInitialized) {
                runCatching { windowManager.updateViewLayout(ballView, lp) }
            }
        }
    }

    private fun ballBackground(color: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(lighten(color, 0.18f), color)
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1), 0x33FFFFFF)
        }

    private fun lighten(color: Int, factor: Float): Int {
        val a = color ushr 24 and 0xFF
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        val lr = (r + (255 - r) * factor).toInt()
        val lg = (g + (255 - g) * factor).toInt()
        val lb = (b + (255 - b) * factor).toInt()
        return (a shl 24) or (lr shl 16) or (lg shl 8) or lb
    }

    private fun snapToEdge() {
        val lp = layoutParams ?: return
        val screenW = windowManager.defaultDisplay?.width ?: return
        val half = lp.width / 2
        lp.x = if (lp.x + half < screenW / 2) {
            dp(EDGE_MARGIN_DP)
        } else {
            (screenW - lp.width - dp(EDGE_MARGIN_DP)).coerceAtLeast(0)
        }
        lp.y = lp.y.coerceIn(0, (windowManager.defaultDisplay?.height ?: 0) - lp.height)
        runCatching { windowManager.updateViewLayout(ballView, lp) }
    }

    // ---------------------------------------------------------------- 手势与动作

    private fun performGesture(gesture: Gesture) {
        val action = ActionStore.get(this, gesture)
        dispatchAction(action)
    }

    private fun dispatchAction(action: ActionType) {
        when (action) {
            ActionType.PANEL -> showPanel()
            ActionType.HIDE -> stopSelf()
            ActionType.NONE -> { /* 无操作：不做任何反馈 */ }
            else -> executeAction(action)
        }
    }

    private fun executeAction(action: ActionType) {
        val success = ActionExecutor.perform(this, action)
        vibrate(40)
        flash()
        if (!success) {
            Toast.makeText(this, getString(R.string.action_failed, action.label), Toast.LENGTH_SHORT).show()
        }
    }

    /** 单击后浮球轻微放大再还原，给出反馈 */
    private fun flash() {
        ballView.animate().scaleX(1.25f).scaleY(1.25f).setDuration(90)
            .withEndAction {
                ballView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            .start()
    }

    private fun vibrate(ms: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    // ---------------------------------------------------------------- 快捷操作面板

    private fun showPanel() {
        menuPopup?.dismiss()
        val content = LayoutInflater.from(this).inflate(R.layout.ball_panel, null)
        val popup = PopupWindow(
            content,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true

        bindPanelRow(content, R.id.panel_play, ActionType.PLAY_PAUSE, popup)
        bindPanelRow(content, R.id.panel_prev, ActionType.PREVIOUS, popup)
        bindPanelRow(content, R.id.panel_next, ActionType.NEXT, popup)
        bindPanelRow(content, R.id.panel_vol_up, ActionType.INCREASE_VOLUME, popup)
        bindPanelRow(content, R.id.panel_vol_down, ActionType.DECREASE_VOLUME, popup)
        bindPanelRow(content, R.id.panel_home, ActionType.HOME, popup)

        content.findViewById<View>(R.id.panel_settings).setOnClickListener {
            popup.dismiss()
            openSettings()
        }
        content.findViewById<View>(R.id.panel_hide).setOnClickListener {
            popup.dismiss()
            stopSelf()
        }

        val lp = layoutParams
        if (lp != null) {
            popup.showAtLocation(ballView, Gravity.TOP or Gravity.START, lp.x, lp.y + lp.height)
        } else {
            popup.showAtLocation(ballView, Gravity.CENTER, 0, 0)
        }
        menuPopup = popup
    }

    private fun bindPanelRow(content: View, id: Int, action: ActionType, popup: PopupWindow) {
        content.findViewById<View>(id).setOnClickListener {
            popup.dismiss()
            executeAction(action)
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ---------------------------------------------------------------- 前台通知

    private fun ensureForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_ball)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
