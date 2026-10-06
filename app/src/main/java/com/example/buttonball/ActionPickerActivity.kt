package com.example.buttonball

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buttonball.control.ActionStore
import com.example.buttonball.model.ActionType
import com.example.buttonball.model.Category
import com.example.buttonball.model.Gesture
import com.example.buttonball.ui.ActionAdapter

/**
 * 动作选择界面：为某个手势绑定动作。
 * 顶部「媒体 / 系统 / 特殊功能」分类标签页，下方为对应动作列表。
 * 点击某个动作即保存为该手势的绑定动作，并返回主界面。
 */
class ActionPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GESTURE = "extra_gesture"
    }

    private lateinit var adapter: ActionAdapter
    private lateinit var gesture: Gesture

    private val tabs = listOf(
        Category.MEDIA to TabSpec(R.id.tab_media, R.id.tab_underline_media),
        Category.SYSTEM to TabSpec(R.id.tab_system, R.id.tab_underline_system),
        Category.SPECIAL to TabSpec(R.id.tab_special, R.id.tab_underline_special)
    )

    private data class TabSpec(val tabId: Int, val underlineId: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_picker)

        gesture = try {
            Gesture.valueOf(intent?.getStringExtra(EXTRA_GESTURE) ?: "")
        } catch (_: IllegalArgumentException) {
            Gesture.SINGLE
        }

        findViewById<TextView>(R.id.picker_title).text =
            getString(R.string.picker_title_for, gesture.label)

        tabs.forEach { (category, spec) ->
            findViewById<TextView>(spec.tabId).setOnClickListener {
                selectCategory(category)
            }
        }

        adapter = ActionAdapter { action ->
            ActionStore.set(this, gesture, action)
            Toast.makeText(
                this, getString(R.string.action_set_fmt, gesture.label, action.label),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }

        findViewById<RecyclerView>(R.id.action_list).apply {
            layoutManager = LinearLayoutManager(this@ActionPickerActivity)
            adapter = this@ActionPickerActivity.adapter
        }

        selectCategory(Category.MEDIA)
    }

    private fun selectCategory(category: Category) {
        tabs.forEach { (cat, spec) ->
            findViewById<TextView>(spec.tabId).isSelected = cat == category
            findViewById<View>(spec.underlineId).visibility =
                if (cat == category) View.VISIBLE else View.INVISIBLE
        }
        adapter.submit(ActionType.ofCategory(category), ActionStore.get(this, gesture))
    }
}
