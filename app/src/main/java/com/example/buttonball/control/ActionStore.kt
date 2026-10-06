package com.example.buttonball.control

import android.content.Context
import com.example.buttonball.model.ActionType
import com.example.buttonball.model.Gesture

/**
 * 悬浮球配置的持久化（SharedPreferences）。
 * 包括：各手势绑定的动作，以及浮球外观（颜色/大小/透明度）与位置。
 */
object ActionStore {

    private const val PREF = "buttonball_pref"

    private const val KEY_COLOR = "ball_color"
    private const val KEY_SIZE = "ball_size_dp"
    private const val KEY_ALPHA = "ball_alpha"
    private const val KEY_POS_X = "ball_pos_x"
    private const val KEY_POS_Y = "ball_pos_y"

    /** 默认颜色：靛蓝 */
    const val DEFAULT_COLOR = 0xFF3B5BDB.toInt()
    const val DEFAULT_SIZE_DP = 56
    const val DEFAULT_ALPHA = 1f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ------------------------------------------------------------- 手势绑定

    fun get(context: Context, gesture: Gesture): ActionType {
        val id = prefs(context).getString(gesture.key, null)
        return ActionType.fromId(id) ?: gesture.default
    }

    fun set(context: Context, gesture: Gesture, action: ActionType) {
        prefs(context).edit().putString(gesture.key, action.id).apply()
    }

    // ------------------------------------------------------------- 外观

    fun getColor(context: Context): Int =
        prefs(context).getInt(KEY_COLOR, DEFAULT_COLOR)

    fun setColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_COLOR, color).apply()
    }

    fun getSizeDp(context: Context): Int =
        prefs(context).getInt(KEY_SIZE, DEFAULT_SIZE_DP)

    fun setSizeDp(context: Context, sizeDp: Int) {
        prefs(context).edit().putInt(KEY_SIZE, sizeDp).apply()
    }

    fun getAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_ALPHA, DEFAULT_ALPHA)

    fun setAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_ALPHA, alpha).apply()
    }

    // ------------------------------------------------------------- 位置记忆

    /** 返回保存的浮球位置 (x, y)；未保存过返回 null（使用默认位置） */
    fun getPosition(context: Context): Pair<Int, Int>? {
        val sp = prefs(context)
        if (!sp.contains(KEY_POS_X) || !sp.contains(KEY_POS_Y)) return null
        return sp.getInt(KEY_POS_X, 0) to sp.getInt(KEY_POS_Y, 0)
    }

    fun setPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt(KEY_POS_X, x).putInt(KEY_POS_Y, y).apply()
    }

    fun clearPosition(context: Context) {
        prefs(context).edit().remove(KEY_POS_X).remove(KEY_POS_Y).apply()
    }
}
