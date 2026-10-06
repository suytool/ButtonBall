package com.example.buttonball.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.example.buttonball.model.ActionType
import com.example.buttonball.service.BallAccessibilityService
import com.example.buttonball.service.MediaNotificationListener
import kotlin.math.max

/**
 * 动作执行器：根据动作类型分发到不同的实现通道。
 *
 * - 音量类动作：AudioManager（无需额外权限）
 * - 媒体播放类动作：优先使用已获取的媒体会话 Token（需通知使用权），
 *   否则回退到 AudioManager.dispatchMediaKeyEvent —— 直接把媒体键事件
 *   发给当前活动媒体会话，无需任何权限即可控制播放/上下曲/快进快退。
 * - 系统类动作：无障碍服务的 performGlobalAction（需开启无障碍服务）
 */
object ActionExecutor {

    /** 执行动作，返回是否成功下发（用于界面提示） */
    fun perform(context: Context, action: ActionType): Boolean = when (action) {
        ActionType.INCREASE_VOLUME -> adjustVolume(context, AudioManager.ADJUST_RAISE)
        ActionType.DECREASE_VOLUME -> adjustVolume(context, AudioManager.ADJUST_LOWER)
        ActionType.MUTE_VOLUME -> toggleMute(context)

        ActionType.PLAY_PAUSE,
        ActionType.STOP,
        ActionType.NEXT,
        ActionType.PREVIOUS,
        ActionType.FORWARD,
        ActionType.REWIND -> performTransport(context, action)

        ActionType.HOME,
        ActionType.BACK,
        ActionType.RECENTS,
        ActionType.NOTIFICATIONS,
        ActionType.QUICK_SETTINGS -> performSystem(action)

        ActionType.PANEL,
        ActionType.HIDE -> false // 由悬浮球服务内部处理
        ActionType.NONE -> true  // 无操作：什么都不做，视为成功
    }

    // ---------------------------------------------------------------- 音量

    private fun adjustVolume(context: Context, direction: Int): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val flags = AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
        return true
    }

    private fun toggleMute(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        return true
    }

    // ---------------------------------------------------------------- 媒体

    private fun performTransport(context: Context, action: ActionType): Boolean {
        // 通道一：若能拿到媒体会话 Token（已授予通知使用权且媒体在播），用 MediaController 精确控制
        val token = MediaNotificationListener.currentToken
        if (token != null && transportWithController(context, token, action)) return true

        // 通道二：无权限兜底 —— 向当前活动媒体会话发送媒体键
        return dispatchMediaKey(context, action)
    }

    private fun transportWithController(
        context: Context,
        token: android.media.session.MediaSession.Token,
        action: ActionType
    ): Boolean {
        return try {
            val mc = MediaController(context, token)
            val tc = mc.transportControls
            val state = mc.playbackState?.state ?: PlaybackState.STATE_NONE
            when (action) {
                ActionType.NEXT -> tc.skipToNext()
                ActionType.PREVIOUS -> tc.skipToPrevious()
                ActionType.STOP -> tc.stop()
                ActionType.PLAY_PAUSE ->
                    if (state == PlaybackState.STATE_PLAYING) tc.pause() else tc.play()
                ActionType.FORWARD -> tc.seekTo((mc.playbackState?.position ?: 0L) + FORWARD_SEEK_MS)
                ActionType.REWIND -> tc.seekTo(max(0L, (mc.playbackState?.position ?: 0L) - FORWARD_SEEK_MS))
                else -> return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 通过媒体键事件控制当前活动媒体会话，无需任何权限 */
    private fun dispatchMediaKey(context: Context, action: ActionType): Boolean {
        val keyCode = when (action) {
            ActionType.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            ActionType.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            ActionType.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            ActionType.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ActionType.FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            ActionType.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
            else -> -1
        }
        if (keyCode < 0) return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return true
    }

    // ---------------------------------------------------------------- 系统

    private fun performSystem(action: ActionType): Boolean {
        val svc = BallAccessibilityService.instance ?: return false
        val g = when (action) {
            ActionType.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            ActionType.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            ActionType.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            ActionType.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            ActionType.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            else -> -1
        }
        return g >= 0 && svc.performGlobalAction(g)
    }

    private const val FORWARD_SEEK_MS = 15_000L
}
