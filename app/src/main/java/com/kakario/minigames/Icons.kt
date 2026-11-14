package com.kakario.minigames

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** 첫 화면 게임 아이콘 (코드로 그림, 흑백). */
object Icons {
    private const val S = 360
    private fun make(draw: (Canvas, Float) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(Color.WHITE); draw(c, S.toFloat()); return b
    }
    private fun ink(w: Float = 6f, fill: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; strokeWidth = w; style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
    }
    private fun text(size: Float, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    fun janggi(a: Activity) = make { c, s ->
        val r = s * 0.36f; val cx = s / 2; val cy = s / 2; val k = r * 0.414f
        val p = Path().apply {
            moveTo(cx - k, cy - r); lineTo(cx + k, cy - r); lineTo(cx + r, cy - k); lineTo(cx + r, cy + k)
            lineTo(cx + k, cy + r); lineTo(cx - k, cy + r); lineTo(cx - r, cy + k); lineTo(cx - r, cy - k); close()
        }
        c.drawPath(p, ink(10f))
        c.drawText("車", cx, cy + s * 0.12f, text(s * 0.36f))
    }

    fun omok(a: Activity) = make { c, s ->
        val m = s * 0.12f; val g = (s - 2 * m) / 6
        for (k in 0..6) { c.drawLine(m, m + k * g, s - m, m + k * g, ink(3f)); c.drawLine(m + k * g, m, m + k * g, s - m, ink(3f)) }
        val stones = listOf(2 to 2, 3 to 3, 4 to 4, 3 to 2, 2 to 4, 4 to 2)
        for ((idx, p) in stones.withIndex()) {
            val x = m + p.first * g; val y = m + p.second * g
            if (idx < 3) c.drawCircle(x, y, g * 0.42f, ink(fill = true))
            else { c.drawCircle(x, y, g * 0.42f, Paint().apply { color = Color.WHITE }); c.drawCircle(x, y, g * 0.42f, ink(4f)) }
        }
    }

    fun mines(a: Activity) = make { c, s ->
        val cx = s / 2; val cy = s / 2; val r = s * 0.2f
        for (k in 0 until 4) {
            val ang = Math.PI * k / 4
            val dx = (Math.cos(ang) * r * 1.6).toFloat(); val dy = (Math.sin(ang) * r * 1.6).toFloat()
            c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, ink(12f))
        }
        c.drawCircle(cx, cy, r, ink(fill = true))
        c.drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    fun fifteen(a: Activity) = make { c, s ->
        val m = s * 0.1f; val g = (s - 2 * m) / 4; val t = text(g * 0.45f)
        val nums = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0)
        for (i in 0 until 16) {
            if (nums[i] == 0) continue
            val x = m + (i % 4) * g; val y = m + (i / 4) * g
            c.drawRoundRect(x + 5, y + 5, x + g - 5, y + g - 5, 12f, 12f, ink(4f))
            c.drawText(nums[i].toString(), x + g / 2, y + g * 0.66f, t)
        }
    }

    fun g2048(a: Activity) = make { c, s ->
        val m = s * 0.12f
        c.drawRoundRect(m, m, s - m, s - m, 30f, 30f, ink(fill = true))
        c.drawText("2048", s / 2, s / 2 + s * 0.08f, text(s * 0.22f, Color.WHITE))
    }
}
