package com.example.buttonball.model

/**
 * 浮球手势。每个手势都可以独立绑定一个动作，实现「一球多功能」。
 */
enum class Gesture(
    val key: String,
    val label: String,
    val hint: String,
    val default: ActionType
) {
    SINGLE("action_single", "单击", "轻点一下浮球", ActionType.NEXT),
    DOUBLE("action_double", "双击", "快速连点两下", ActionType.PANEL),
    LONG("action_long", "长按", "长按约 0.5 秒", ActionType.PLAY_PAUSE);
}
