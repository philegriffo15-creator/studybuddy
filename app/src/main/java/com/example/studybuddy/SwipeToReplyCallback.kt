package com.example.studybuddy

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeToReplyCallback(
    context: Context,
    private val onSwipe: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    private val replyIcon: Drawable? = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_revert)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Trigger the callback
        onSwipe(viewHolder.adapterPosition)
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
        // Limit the swipe distance
        val maxSwipe = 200f
        val translationX = if (dX > maxSwipe) maxSwipe else dX

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive)
            
            replyIcon?.let { icon ->
                val itemView = viewHolder.itemView
                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + icon.intrinsicHeight
                
                // Only show icon when swiping right
                if (dX > 0) {
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = iconLeft + icon.intrinsicWidth
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    icon.draw(c)
                }
            }
        }
    }
}
