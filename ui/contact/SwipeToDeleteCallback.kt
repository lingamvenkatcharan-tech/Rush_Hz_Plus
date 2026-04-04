// ui/contact/helper/SwipeToDeleteCallback.kt
package com.example.rush_hz_plus.ui.contact

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.rush_hz_plus.R

class SwipeToDeleteCallback(
    private val context: Context,
    private val onDelete: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val background = ColorDrawable(Color.RED)
    private val iconMargin = context.resources.getDimensionPixelSize(R.dimen.swipe_icon_margin)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onDelete(viewHolder.adapterPosition)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView

        if (dX < 0) {
            // 왼쪽 스와이프 - 삭제 모드
            background.setBounds(
                itemView.right + dX.toInt(),
                itemView.top,
                itemView.right,
                itemView.bottom
            )
            background.draw(c)

            val text = "삭제"
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = context.resources.getDimension(R.dimen.swipe_text_size)
                isFakeBoldText = true
            }

            val textWidth = paint.measureText(text)
            val textHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
            val textX = itemView.right - textWidth - iconMargin
            val textY = itemView.top + (itemView.height + textHeight) / 2 - paint.fontMetrics.descent

            c.drawText(text, textX, textY, paint)

            itemView.translationX = dX
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
}