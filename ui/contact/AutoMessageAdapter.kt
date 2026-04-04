package com.example.rush_hz_plus.ui.contact

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.rush_hz_plus.R

class AutoMessageAdapter : ListAdapter<AutoMessage, AutoMessageAdapter.ViewHolder>(AutoMessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_auto_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val contentText: TextView = itemView.findViewById(R.id.tv_message_text)

        // 버튼은 참조만 하고 리스너는 등록하지 않음
        private val btnEdit: Button = itemView.findViewById(R.id.btn_edit)
        private val btnDelete: Button = itemView.findViewById(R.id.btn_delete)

        fun bind(item: AutoMessage) {
            contentText.text = item.content
            contentText.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_sms,0, 0, 0)

            // 수정/삭제 버튼 비활성화
            btnEdit.isEnabled = false
            btnEdit.visibility = View.GONE // 또는 INVISIBLE
            btnDelete.isEnabled = false
            btnDelete.visibility = View.GONE
        }

    }

    class AutoMessageDiffCallback : DiffUtil.ItemCallback<AutoMessage>() {
        override fun areItemsTheSame(oldItem: AutoMessage, newItem: AutoMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AutoMessage, newItem: AutoMessage): Boolean {
            return oldItem == newItem
        }
    }
}