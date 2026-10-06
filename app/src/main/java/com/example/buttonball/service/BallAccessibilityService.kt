package com.example.buttonball.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务。
 *
 * 用于执行系统级全局动作：回到桌面、返回、最近任务、打开通知栏、快捷设置。
 * 这些动作必须由无障碍服务调用 performGlobalAction 完成，
 * 需要用户到「设置 -> 无障碍」中手动开启本服务。
 */
class BallAccessibilityService : AccessibilityService() {

    companion object {
        /** 已连接的无障碍服务实例（供 ActionExecutor 调用全局动作） */
        @Volatile
        var instance: BallAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 无需处理具体事件
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
