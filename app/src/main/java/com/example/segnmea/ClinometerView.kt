package com.example.segnmea

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ClinometerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 5f
    }

    var angle = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) * 0.8f

        // Draw the circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Draw the tick marks and labels
        for (i in 0 until 360 step 30) {
            val angleRad = Math.toRadians(i.toDouble()).toFloat()
            val startX = centerX + radius * kotlin.math.cos(angleRad)
            val startY = centerY + radius * kotlin.math.sin(angleRad)
            val endX = centerX + (radius + 20) * kotlin.math.cos(angleRad)
            val endY = centerY + (radius + 20) * kotlin.math.sin(angleRad)
            canvas.drawLine(startX, startY, endX, endY, tickPaint)

            val label = when (i) {
                0 -> "0°"
                30 -> "30°"
                60 -> "60°"
                90 -> "90°"
                120 -> "60°"
                150 -> "30°"
                180 -> "0°"
                210 -> "-30°"
                240 -> "-60°"
                270 -> "-90°"
                300 -> "-60°"
                330 -> "-30°"
                else -> ""
            }

            if (label.isNotEmpty()) {
                val textX = centerX + (radius + 50) * kotlin.math.cos(angleRad)
                val textY = centerY + (radius + 50) * kotlin.math.sin(angleRad)
                canvas.drawText(label, textX, textY, textPaint)
            }
        }

        // Draw the rotating indicator
        canvas.save()
        canvas.rotate(angle, centerX, centerY)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, circlePaint)
        canvas.restore()
    }
}
