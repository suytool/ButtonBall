package com.example.buttonball.model

import com.example.buttonball.R

/** 动作所属分类，对应选择界面顶部的标签页 */
enum class Category(val title: String) {
    MEDIA("媒体"),
    SYSTEM("系统"),
    SPECIAL("特殊功能")
}

/**
 * 浮球手势可绑定的动作集合。
 * 枚举各动作的唯一 id、显示名称、图标与所属分类。
 */
enum class ActionType(
    val id: String,
    val label: String,
    val iconRes: Int,
    val category: Category
) {
    // ---- 媒体 ----
    INCREASE_VOLUME("increase_volume", "增加音量", R.drawable.ic_volume_up, Category.MEDIA),
    DECREASE_VOLUME("decrease_volume", "减少音量", R.drawable.ic_volume_down, Category.MEDIA),
    MUTE_VOLUME("mute_volume", "静音音量", R.drawable.ic_volume_off, Category.MEDIA),
    PLAY_PAUSE("play_pause", "播放/暂停", R.drawable.ic_play_pause, Category.MEDIA),
    STOP("stop", "停止", R.drawable.ic_stop, Category.MEDIA),
    NEXT("next", "跳至下一个", R.drawable.ic_next, Category.MEDIA),
    PREVIOUS("previous", "跳至上一个", R.drawable.ic_previous, Category.MEDIA),
    FORWARD("forward", "快进", R.drawable.ic_forward, Category.MEDIA),
    REWIND("rewind", "快退", R.drawable.ic_rewind, Category.MEDIA),

    // ---- 系统（需无障碍服务） ----
    HOME("home", "回到桌面", R.drawable.ic_home, Category.SYSTEM),
    BACK("back", "返回", R.drawable.ic_back, Category.SYSTEM),
    RECENTS("recents", "最近任务", R.drawable.ic_recents, Category.SYSTEM),
    NOTIFICATIONS("notifications", "打开通知栏", R.drawable.ic_notifications, Category.SYSTEM),
    QUICK_SETTINGS("quick_settings", "快捷设置", R.drawable.ic_quick_settings, Category.SYSTEM),

    // ---- 特殊功能（由悬浮球服务内部处理） ----
    PANEL("panel", "打开操作面板", R.drawable.ic_panel, Category.SPECIAL),
    HIDE("hide", "隐藏浮球", R.drawable.ic_hide, Category.SPECIAL),
    NONE("none", "无操作", R.drawable.ic_none, Category.SPECIAL);

    companion object {
        /** 根据存储的 id 还原动作，未找到时返回 null（由调用方决定回退） */
        fun fromId(id: String?): ActionType? = entries.firstOrNull { it.id == id }

        /** 返回指定分类下的所有动作 */
        fun ofCategory(category: Category): List<ActionType> =
            entries.filter { it.category == category }
    }
}
