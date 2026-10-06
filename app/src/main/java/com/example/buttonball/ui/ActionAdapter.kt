package com.example.buttonball.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.buttonball.R
import com.example.buttonball.model.ActionType

/** 动作列表适配器：展示图标、名称，选中项右侧显示对勾 */
class ActionAdapter(
    private val onClick: (ActionType) -> Unit
) : RecyclerView.Adapter<ActionAdapter.VH>() {

    private var items: List<ActionType> = emptyList()
    private var selected: ActionType? = null

    fun submit(list: List<ActionType>, sel: ActionType) {
        items = list
        selected = sel
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.action_icon)
        val label: TextView = view.findViewById(R.id.action_label)
        val check: View = view.findViewById(R.id.action_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val action = items[position]
        holder.icon.setImageResource(action.iconRes)
        holder.label.text = action.label
        holder.check.visibility = if (action == selected) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(action) }
    }

    override fun getItemCount(): Int = items.size
}
