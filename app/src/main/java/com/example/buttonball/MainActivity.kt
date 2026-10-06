package com.example.buttonball

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.buttonball.control.ActionStore
import com.example.buttonball.model.Gesture
import com.example.buttonball.service.BallAccessibilityService
import com.example.buttonball.service.FloatingBallService

/**
 * 主界面：悬浮球开关、手势动作绑定（多功能）、外观定制（颜色/大小/透明度）与权限入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var ballSwitch: Switch
    private lateinit var swatchContainer: LinearLayout
    private lateinit var sizeGroup: RadioGroup
    private lateinit var alphaSeek: SeekBar
    private lateinit var alphaValue: TextView

    private val colorPresets = listOf(
        0xFF3B5BDB.toInt() to "靛蓝",
        0xFFE53935.toInt() to "红",
        0xFF2E9E5B.toInt() to "绿",
        0xFFFB8C00.toInt() to "橙",
        0xFF8E24AA.toInt() to "紫",
        0xFF00838F.toInt() to "青",
        0xFF37474F.toInt() to "深灰",
        0xFFF06292.toInt() to "粉"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        swatchContainer = findViewById(R.id.color_swatches)
        sizeGroup = findViewById(R.id.size_group)
        alphaSeek = findViewById(R.id.alpha_seek)
        alphaValue = findViewById(R.id.alpha_value)

        ballSwitch = findViewById(R.id.ball_switch)
        ballSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!hasOverlayPermission()) {
                    Toast.makeText(this, R.string.need_overlay_first, Toast.LENGTH_LONG).show()
                    ballSwitch.isChecked = false
                } else {
                    FloatingBallService.start(this)
                }
            } else {
                FloatingBallService.stop(this)
            }
        }

        findViewById<Button>(R.id.btn_reset_position).setOnClickListener {
            FloatingBallService.resetPosition(this)
        }

        // 支持作者：弹出二维码
        findViewById<Button>(R.id.btn_support).setOnClickListener { showSupportDialog() }

        // 三个手势各自的“修改”入口
        bindGestureRow(R.id.btn_gesture_single, Gesture.SINGLE)
        bindGestureRow(R.id.btn_gesture_double, Gesture.DOUBLE)
        bindGestureRow(R.id.btn_gesture_long, Gesture.LONG)

        // 权限入口
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btn_notification).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        setupSizeSelector()
        setupAlphaSeek()
    }

    private fun bindGestureRow(btnId: Int, gesture: Gesture) {
        findViewById<View>(btnId).setOnClickListener {
            startActivity(
                Intent(this, ActionPickerActivity::class.java)
                    .putExtra(ActionPickerActivity.EXTRA_GESTURE, gesture.name)
            )
        }
    }

    // ------------------------------------------------------------- 外观控件

    private fun setupColorSwatches() {
        swatchContainer.removeAllViews()
        val current = ActionStore.getColor(this)
        val swatchSize = dp(42)
        val margin = dp(6)
        colorPresets.forEach { (color, name) ->
            val swatch = View(this)
            val lp = LinearLayout.LayoutParams(swatchSize, swatchSize)
            lp.rightMargin = margin
            swatch.layoutParams = lp
            swatch.background = oval(color, selected = color == current)
            swatch.contentDescription = name
            swatch.setOnClickListener {
                ActionStore.setColor(this, color)
                FloatingBallService.applyAppearance(this)
                setupColorSwatches()
            }
            swatchContainer.addView(swatch)
        }
    }

    private fun oval(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) setStroke(dp(3), getColor(R.color.surface))
            else setStroke(dp(1), getColor(R.color.divider))
        }

    private fun setupSizeSelector() {
        sizeGroup.setOnCheckedChangeListener { _, checkedId ->
            val sizeDp = when (checkedId) {
                R.id.radio_size_s -> 44
                R.id.radio_size_m -> 56
                else -> 72
            }
            ActionStore.setSizeDp(this, sizeDp)
            FloatingBallService.applyAppearance(this)
        }
    }

    private fun setupAlphaSeek() {
        alphaSeek.max = 100
        alphaSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alphaValue.text = "$progress%"
                if (fromUser) {
                    ActionStore.setAlpha(this@MainActivity, progress / 100f)
                    FloatingBallService.applyAppearance(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ------------------------------------------------------------- 支持作者

    private fun showSupportDialog() {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_support, null)
        AlertDialog.Builder(this)
            .setTitle(R.string.support_dialog_title)
            .setView(content)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    // ------------------------------------------------------------- 状态刷新

    override fun onResume() {
        super.onResume()
        refreshUi()
    }
    private fun refreshUi() {
        ballSwitch.isChecked = FloatingBallService.running
        ballSwitch.isEnabled = hasOverlayPermission()

        // 手势绑定
        findViewById<TextView>(R.id.gesture_action_single).text =
            ActionStore.get(this, Gesture.SINGLE).label
        findViewById<TextView>(R.id.gesture_action_double).text =
            ActionStore.get(this, Gesture.DOUBLE).label
        findViewById<TextView>(R.id.gesture_action_long).text =
            ActionStore.get(this, Gesture.LONG).label

        // 外观
        setupColorSwatches()
        val size = ActionStore.getSizeDp(this)
        sizeGroup.check(
            when (size) {
                44 -> R.id.radio_size_s
                72 -> R.id.radio_size_l
                else -> R.id.radio_size_m
            }
        )
        val alpha = (ActionStore.getAlpha(this) * 100).toInt()
        alphaSeek.progress = alpha
        alphaValue.text = "$alpha%"

        // 权限
        val overlayOk = hasOverlayPermission()
        val notifOk = hasNotificationAccess()
        val accOk = hasAccessibility()
        findViewById<Button>(R.id.btn_overlay).isEnabled = !overlayOk
        findViewById<Button>(R.id.btn_notification).isEnabled = !notifOk
        findViewById<Button>(R.id.btn_accessibility).isEnabled = !accOk
        findViewById<TextView>(R.id.status_overlay).text =
            getString(if (overlayOk) R.string.granted else R.string.not_granted)
        findViewById<TextView>(R.id.status_notification).text =
            getString(if (notifOk) R.string.granted else R.string.not_granted)
        findViewById<TextView>(R.id.status_accessibility).text =
            getString(if (accOk) R.string.granted else R.string.not_granted)
    }

    // ------------------------------------------------------------- 权限检测

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun hasNotificationAccess(): Boolean =
        androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)

    private fun hasAccessibility(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val marker = "com.example.buttonball.service.BallAccessibilityService"
        return enabled.split(':').any { it.contains(marker, ignoreCase = true) }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
