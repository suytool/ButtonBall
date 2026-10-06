package com.example.buttonball.service

import android.app.Notification
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 媒体会话监听器。
 *
 * 现代 Android（8.0+）不允许第三方应用直接获取任意应用的媒体会话，
 * 但应用可以通过 NotificationListenerService 读取媒体通知中携带的
 * MediaSession.Token，从而获得对该媒体会话的控制权（播放/暂停、上下曲等）。
 *
 * 本服务持续缓存最近一条仍在播放的媒体会话 Token，
 * 供 [ActionExecutor] 通过 MediaController 下发控制指令。
 */
class MediaNotificationListener : NotificationListenerService() {

    companion object {
        /** 当前可控制的媒体会话 Token（最新一条媒体通知） */
        @Volatile
        var currentToken: MediaSession.Token? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications.forEach { collect(it) }
        } catch (_: SecurityException) {
            // 权限被撤销等异常情况，忽略即可
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        collect(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 保留最近一条，避免单条被移除后立即失去控制能力
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        currentToken = null
    }

    private fun collect(sbn: StatusBarNotification) {
        val token: MediaSession.Token? = readMediaSessionToken(sbn) ?: return
        currentToken = token
    }

    @Suppress("DEPRECATION")
    private fun readMediaSessionToken(sbn: StatusBarNotification): MediaSession.Token? {
        val extras = sbn.notification.extras
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
    }
}
