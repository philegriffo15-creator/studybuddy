package com.example.studybuddy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class AudioWaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFF9C27B0.toInt() // Planner Purple Outline Color
        strokeWidth = 10f // Slightly thicker
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val amplitudes = mutableListOf<Float>()
    private val maxAmplitudes = 30 // Reduced count to increase spacing

    fun addAmplitude(amp: Float) {
        amplitudes.add(amp)
        if (amplitudes.size > maxAmplitudes) {
            amplitudes.removeAt(0)
        }
        invalidate()
    }

    fun clear() {
        amplitudes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val spacing = width / maxAmplitudes.toFloat()
        val startPadding = spacing / 2f

        amplitudes.forEachIndexed { index, amp ->
            val x = startPadding + (index * spacing)
            val heightPercent = amp / 32767f // Max amplitude for MediaRecorder
            val barHeight = (height * 0.8f) * heightPercent + 10f
            
            canvas.drawLine(x, centerY - barHeight / 2, x, centerY + barHeight / 2, paint)
        }
    }
}
