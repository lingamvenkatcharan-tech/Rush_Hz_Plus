// ui/contact/adapter/GuardianAdapter.kt
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
import com.example.rush_hz_plus.ui.contact.GuardianDisplayInfo


/* 스와이프 지원 */

class GuardianAdapter : ListAdapter<GuardianDisplayInfo, GuardianAdapter.ViewHolder>(GuardianDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val nameText: TextView = itemView.findViewById(R.id.tv_contact_name)
        private val phoneText: TextView = itemView.findViewById(R.id.tv_phone_number)

        fun bind(item: GuardianDisplayInfo) {
            nameText.text = item.nickname
            phoneText.text = item.phoneNumber

            // 비앱 사용자 시각적 표시 (옵션)
            if (!item.isAppUser) {
                nameText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_cellphone, // 또는 원하는 아이콘
                    0, 0, 0
                )
            } else {
                nameText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }

    class GuardianDiffCallback : DiffUtil.ItemCallback<GuardianDisplayInfo>() {
        override fun areItemsTheSame(oldItem: GuardianDisplayInfo, newItem: GuardianDisplayInfo): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: GuardianDisplayInfo, newItem: GuardianDisplayInfo): Boolean {
            return oldItem == newItem
        }
    }
}