package com.example.studybuddy

import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils

object AnimationHelper {
    fun applyScaleAnimation(view: View) {
        val pressAnim = AnimationUtils.loadAnimation(view.context, R.anim.button_press)
        val releaseAnim = AnimationUtils.loadAnimation(view.context, R.anim.button_release)

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.startAnimation(pressAnim)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.startAnimation(releaseAnim)
                }
            }
            false // return false so onClick still triggers
        }
    }

    fun staggeredFadeIn(vararg views: View, startDelay: Long = 100, interval: Long = 150) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(startDelay + (index * interval))
                .start()
        }
    }
}