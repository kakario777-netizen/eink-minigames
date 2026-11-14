package com.kakario.minigames.sudoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** 스도쿠 판. 칸을 눌러 고르고 아래 숫자판으로 넣는다. */
@SuppressLint("ViewConstructor")
class SudokuView(context: Context, val given: IntArray, val values: IntArray, val notes: IntArray) : View(context) {
    var selected = -1
    var locked = false
    var showConflicts = true
    var onSelect: () -> Unit = {}

    private var cell = 0f; private var ox = 0f; private var oy = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = min(w, h) * 0.97f
        cell = s / 9f; ox = (w - s) / 2; oy = (h - s) / 2
    }

    private val thin = Paint().apply { color = Color.BLACK; strokeWidth = 1.5f }
    private val thick = Paint().apply { color = Color.BLACK; strokeWidth = 5f }
    private val givenP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val userP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
    private val noteP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
    private val selP = Paint().apply { color = Color.rgb(195, 195, 195) }
    private val peerP = Paint().apply { color = Color.rgb(238, 238, 238) }
    private val sameP = Paint().apply { color = Color.rgb(215, 215, 215) }
    private val badP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }

    fun value(i: Int) = if (given[i] != 0) given[i] else values[i]

    fun conflict(i: Int): Boolean {
        val v = value(i); if (v == 0) return false
        val r = i / 9; val c = i % 9
        for (j in 0 until 81) {
            if (j == i || value(j) != v) continue
            val rj = j / 9; val cj = j % 9
            if (rj == r || cj == c || (rj / 3 == r / 3 && cj / 3 == c / 3)) return true
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        if (cell <= 0f) return
        givenP.textSize = cell * 0.62f; userP.textSize = cell * 0.58f; noteP.textSize = cell * 0.27f
        badP.strokeWidth = cell * 0.06f
        val s = selected
        val sv = if (s >= 0) value(s) else 0
        for (i in 0 until 81) {
            val r = i / 9; val c = i % 9
            val x = ox + c * cell; val y = oy + r * cell
            if (!locked && s >= 0) {
                val sr = s / 9; val sc = s % 9
                when {
                    i == s -> canvas.drawRect(x, y, x + cell, y + cell, selP)
                    sv != 0 && value(i) == sv -> canvas.drawRect(x, y, x + cell, y + cell, sameP)
                    r == sr || c == sc || (r / 3 == sr / 3 && c / 3 == sc / 3) -> canvas.drawRect(x, y, x + cell, y + cell, peerP)
                }
            }
            val v = value(i)
            if (v != 0) {
                canvas.drawText(v.toString(), x + cell / 2, y + cell * 0.72f, if (given[i] != 0) givenP else userP)
                if (showConflicts && given[i] == 0 && conflict(i)) canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.38f, badP)
            } else if (notes[i] != 0) {
                for (n in 1..9) if (notes[i] and (1 shl n) != 0) {
                    val nx = x + ((n - 1) % 3 + 0.5f) * cell / 3; val ny = y + ((n - 1) / 3 + 0.82f) * cell / 3
                    canvas.drawText(n.toString(), nx, ny, noteP)
                }
            }
        }
        for (k in 0..9) {
            val p = if (k % 3 == 0) thick else thin
            canvas.drawLine(ox + k * cell, oy, ox + k * cell, oy + 9 * cell, p)
            canvas.drawLine(ox, oy + k * cell, ox + 9 * cell, oy + k * cell, p)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP && !locked) {
            val c = ((e.x - ox) / cell).toInt(); val r = ((e.y - oy) / cell).toInt()
            if (e.x >= ox && e.y >= oy && r in 0..8 && c in 0..8) { selected = r * 9 + c; invalidate(); onSelect() }
        }
        return true
    }
}
