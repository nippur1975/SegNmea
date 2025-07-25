package com.example.segnmea

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class RollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 5f
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    var roll = 0f
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
        for (i in -90..90 step 30) {
            val angleRad = Math.toRadians(i.toDouble()).toFloat()
            val startX = centerX + radius * kotlin.math.cos(angleRad)
            val startY = centerY + radius * kotlin.math.sin(angleRad)
            val endX = centerX + (radius + 20) * kotlin.math.cos(angleRad)
            val endY = centerY + (radius + 20) * kotlin.math.sin(angleRad)
            canvas.drawLine(startX, startY, endX, endY, tickPaint)
            val label = "${kotlin.math.abs(i)}°"
            val textX = centerX + (radius + 50) * kotlin.math.cos(angleRad)
            val textY = centerY + (radius + 50) * kotlin.math.sin(angleRad)
            canvas.drawText(label, textX, textY, textPaint)
        }

        // Draw the roll value
        val textY = centerY + 40
        canvas.drawText("${"%.1f".format(roll)}°", centerX, textY, textPaint)

        // Draw the arrow and letter
        val arrowPath = Path()
        if (roll > 0) {
            arrowPaint.color = Color.GREEN
            arrowPath.moveTo(centerX + 40, textY - 30)
            arrowPath.lineTo(centerX + 60, textY - 20)
            arrowPath.lineTo(centerX + 40, textY - 10)
            arrowPath.close()
            canvas.drawText("S", centerX + 80, textY, letterPaint)
        } else if (roll < 0) {
            arrowPaint.color = Color.RED
            arrowPath.moveTo(centerX - 40, textY - 10)
            arrowPath.lineTo(centerX - 60, textY - 20)
            arrowPath.lineTo(centerX - 40, textY - 30)
            arrowPath.close()
            canvas.drawText("P", centerX - 80, textY, letterPaint)
        }
        canvas.drawPath(arrowPath, arrowPaint)

        // Draw the rotating indicator
        canvas.save()
        canvas.rotate(roll, centerX, centerY)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, circlePaint)
        canvas.restore()
    }
}
