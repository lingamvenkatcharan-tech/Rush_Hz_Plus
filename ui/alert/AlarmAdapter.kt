package com.example.rush_hz_plus.ui.alert

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.domain.entity.DetectionResult
import com.example.rush_hz_plus.domain.score.HazardScore
import java.text.SimpleDateFormat
import java.util.*

class AlarmAdapter(
    private val onItemClick: (DetectionResult) -> Unit
) : ListAdapter<DetectionResult, AlarmAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inference_result, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View, private val onItemClick: (DetectionResult) -> Unit) :
        RecyclerView.ViewHolder(itemView) {

        private val tvDate: TextView = itemView.findViewById(R.id.text_alarm_date)
        private val tvNumber: TextView = itemView.findViewById(R.id.text_alarm_circle_number) // ← 이 TextView
        private val tvMessage: TextView = itemView.findViewById(R.id.text_alarm_message)

        fun bind(item: DetectionResult) {
            // currentItem = item
            val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
            tvDate.text = sdf.format(Date(item.timestamp))
            tvMessage.text = item.alertMessage.ifEmpty { "⚠️ ${item.soundLabel} 감지됨" }

            // 위험 레벨에 따라 숫자 표시
            tvNumber.text = when (item.alertType) {
                HazardScore.LEVEL_L3 -> "3"
                HazardScore.LEVEL_L2 -> "2"
                HazardScore.LEVEL_L1 -> "1"
                else -> "0"
            }

            // 선택적: 색상 변경
            val colorRes = when (item.alertType) {
                HazardScore.LEVEL_L3 -> R.color.level3_red
                HazardScore.LEVEL_L2 -> R.color.level2_orange
                HazardScore.LEVEL_L1 -> R.color.level1_yellow
                else -> R.color.safe_green
            }
            tvNumber.setTextColor(itemView.context.getColor(colorRes))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DetectionResult>() {
        override fun areItemsTheSame(oldItem: DetectionResult, newItem: DetectionResult): Boolean {
            return oldItem.id == newItem.id
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: DetectionResult, newItem: DetectionResult): Boolean {
            return oldItem == newItem
        }
    }
}