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
    fun chess(a: Activity) = make { c, s ->
        c.drawText("♞\uFE0E", s / 2, s * 0.78f, text(s * 0.75f))
    }

    fun ladder(a: Activity) = make { c, s ->
        val xs = floatArrayOf(s * 0.2f, s * 0.4f, s * 0.6f, s * 0.8f)
        for (x in xs) c.drawLine(x, s * 0.12f, x, s * 0.88f, ink(9f))
        val rungs = listOf(0 to 0.25f, 2 to 0.32f, 1 to 0.45f, 0 to 0.6f, 2 to 0.66f, 1 to 0.78f)
        for ((k, y) in rungs) c.drawLine(xs[k], s * y, xs[k + 1], s * y, ink(9f))
    }

    fun yut(a: Activity) = make { c, s ->
        for (k in 0 until 4) {
            val x = s * (0.2f + k * 0.17f); val r = android.graphics.RectF(x, s * 0.15f, x + s * 0.12f, s * 0.85f)
            if (k % 2 == 0) c.drawRoundRect(r, 40f, 40f, ink(fill = true)) else c.drawRoundRect(r, 40f, 40f, ink(7f))
            if (k % 2 == 1) for (j in 0 until 3) { val cy = s * (0.32f + j * 0.18f); c.drawLine(x + s * 0.03f, cy - 12, x + s * 0.09f, cy + 12, ink(5f)); c.drawLine(x + s * 0.09f, cy - 12, x + s * 0.03f, cy + 12, ink(5f)) }
        }
    }

    private fun cardShape(c: Canvas, x: Float, y: Float, w: Float, label: String, s: Float) {
        val r = android.graphics.RectF(x, y, x + w, y + w * 1.4f)
        c.drawRoundRect(r, 20f, 20f, Paint().apply { color = Color.WHITE })
        c.drawRoundRect(r, 20f, 20f, ink(6f))
        c.drawText(label, r.centerX(), r.centerY() + s * 0.07f, text(s * 0.2f))
    }

    fun hula(a: Activity) = make { c, s ->
        cardShape(c, s * 0.1f, s * 0.14f, s * 0.36f, "7", s)
        cardShape(c, s * 0.32f, s * 0.2f, s * 0.36f, "7", s)
        cardShape(c, s * 0.54f, s * 0.26f, s * 0.36f, "7", s)
    }

    fun onecard(a: Activity) = make { c, s ->
        cardShape(c, s * 0.18f, s * 0.12f, s * 0.4f, "♠", s)
        cardShape(c, s * 0.42f, s * 0.22f, s * 0.4f, "1", s)
    }

    fun memory(a: Activity) = make { c, s ->
        val w = s * 0.32f
        for (k in 0 until 4) {
            val x = s * 0.12f + (k % 2) * s * 0.42f; val y = s * 0.04f + (k / 2) * s * 0.47f
            val r = android.graphics.RectF(x, y, x + w, y + w * 1.35f)
            if (k == 0 || k == 3) { c.drawRoundRect(r, 16f, 16f, ink(6f)); c.drawText("★", r.centerX(), r.centerY() + s * 0.06f, text(s * 0.18f)) }
            else c.drawRoundRect(r, 16f, 16f, ink(fill = true))
        }
    }

    fun shisen(a: Activity) = make { c, s ->
        for ((k, ch) in listOf("中", "中").withIndex()) {
            val x = s * (0.1f + k * 0.46f); val r = android.graphics.RectF(x, s * 0.2f, x + s * 0.34f, s * 0.68f)
            c.drawRoundRect(r, 18f, 18f, ink(7f)); c.drawText(ch, r.centerX(), r.centerY() + s * 0.08f, text(s * 0.24f))
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 6f; style = Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f) }
        val path = Path().apply { moveTo(s * 0.27f, s * 0.68f); lineTo(s * 0.27f, s * 0.85f); lineTo(s * 0.73f, s * 0.85f); lineTo(s * 0.73f, s * 0.68f) }
        c.drawPath(path, p)
    }
}
