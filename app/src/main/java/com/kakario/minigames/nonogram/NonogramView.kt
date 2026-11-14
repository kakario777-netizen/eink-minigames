package com.kakario.minigames.nonogram

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 네모네모 로직 판.
 *  - 누르기: 지금 고른 도구(칠하기 / X)로 칸을 바꾼다
 *  - 꾸욱 누르기: X(빈칸 확정)
 *  - 누른 채 밀기: 한 줄로 여러 칸을 같은 상태로
 */
@SuppressLint("ViewConstructor")
class NonogramView(context: Context, val n: Int, val solution: BooleanArray, val cells: IntArray) : View(context) {
    companion object { const val EMPTY = 0; const val FILL = 1; const val CROSS = 2 }

    var markMode = false          // false = 칠하기, true = X
    var locked = false            // 다 풀면 잠금
    var onChanged: () -> Unit = {}

    val rowClues: List<IntArray> = List(n) { r -> clue { solution[r * n + it] } }
    val colClues: List<IntArray> = List(n) { c -> clue { solution[it * n + c] } }

    private fun clue(get: (Int) -> Boolean): IntArray {
        val out = ArrayList<Int>(); var run = 0
        for (i in 0 until n) { if (get(i)) run++ else if (run > 0) { out += run; run = 0 } }
        if (run > 0) out += run
        return out.toIntArray()
    }

    private fun lineDone(clue: IntArray, get: (Int) -> Int): Boolean {
        val cur = ArrayList<Int>(); var run = 0
        for (i in 0 until n) { if (get(i) == FILL) run++ else if (run > 0) { cur += run; run = 0 } }
        if (run > 0) cur += run
        return cur.toIntArray().contentEquals(clue)
    }

    fun rowDone(r: Int) = lineDone(rowClues[r]) { cells[r * n + it] }
    fun colDone(c: Int) = lineDone(colClues[c]) { cells[it * n + c] }
    fun solved() = (0 until n).all { rowDone(it) && colDone(it) }

    // 되돌리기: 한 번의 손동작마다 (칸, 이전 상태) 묶음
    private val undo = ArrayDeque<List<Pair<Int, Int>>>()
    private var batch = ArrayList<Pair<Int, Int>>()
    fun undo(): Boolean {
        val b = undo.removeLastOrNull() ?: return false
        for ((i, old) in b.reversed()) cells[i] = old
        invalidate(); onChanged(); return true
    }

    // 배치
    private var cell = 0f; private var gx = 0f; private var gy = 0f
    private val rcMax get() = max(1, rowClues.maxOf { it.size })
    private val ccMax get() = max(1, colClues.maxOf { it.size })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val k = 0.62f
        cell = min(w / (n + rcMax * k + 0.5f), h / (n + ccMax * k + 0.5f))
        val total = cell * (n + rcMax * k)
        gx = (w - total) / 2 + rcMax * k * cell
        gy = ccMax * k * cell + cell * 0.2f
    }

    private val thin = Paint().apply { color = Color.BLACK; strokeWidth = 1.5f }
    private val thick = Paint().apply { color = Color.BLACK; strokeWidth = 4f }
    private val fill = Paint().apply { color = Color.BLACK }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val doneText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(170, 170, 170); textAlign = Paint.Align.CENTER }
    private val shade = Paint().apply { color = Color.rgb(232, 232, 232) }

    private var focusR = -1; private var focusC = -1

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        if (cell <= 0f) return
        val k = 0.62f * cell
        text.textSize = cell * 0.5f; doneText.textSize = cell * 0.5f
        cross.strokeWidth = max(2f, cell * 0.08f)
        // 지금 누르는 줄 표시 (연하게)
        if (!locked && focusR >= 0) {
            canvas.drawRect(gx - rcMax * k, gy + focusR * cell, gx + n * cell, gy + (focusR + 1) * cell, shade)
            canvas.drawRect(gx + focusC * cell, gy - ccMax * k, gx + (focusC + 1) * cell, gy + n * cell, shade)
        }
        // 숫자 (다 맞은 줄은 흐리게)
        for (r in 0 until n) {
            val cl = rowClues[r]; val p = if (rowDone(r)) doneText else text
            val list = if (cl.isEmpty()) intArrayOf(0) else cl
            for ((j, v) in list.withIndex()) {
                val x = gx - (list.size - j) * k + k / 2
                canvas.drawText(v.toString(), x, gy + r * cell + cell * 0.68f, p)
            }
        }
        for (c in 0 until n) {
            val cl = colClues[c]; val p = if (colDone(c)) doneText else text
            val list = if (cl.isEmpty()) intArrayOf(0) else cl
            for ((j, v) in list.withIndex()) {
                val y = gy - (list.size - j) * k + k * 0.75f
                canvas.drawText(v.toString(), gx + c * cell + cell / 2, y, p)
            }
        }
        // 칸
        for (r in 0 until n) for (c in 0 until n) {
            val x = gx + c * cell; val y = gy + r * cell
            when (cells[r * n + c]) {
                FILL -> canvas.drawRect(x + 1.5f, y + 1.5f, x + cell - 1.5f, y + cell - 1.5f, fill)
                CROSS -> if (!locked) {
                    val d = cell * 0.28f
                    canvas.drawLine(x + d, y + d, x + cell - d, y + cell - d, cross)
                    canvas.drawLine(x + d, y + cell - d, x + cell - d, y + d, cross)
                }
            }
        }
        // 선 (5칸마다 굵게)
        if (!locked) {
            for (i in 0..n) {
                val p = if (i % 5 == 0 || i == n) thick else thin
                canvas.drawLine(gx + i * cell, gy, gx + i * cell, gy + n * cell, p)
                canvas.drawLine(gx, gy + i * cell, gx + n * cell, gy + i * cell, p)
            }
        } else {
            canvas.drawRect(gx, gy, gx + n * cell, gy + n * cell, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.BLACK })
        }
    }

    // ───── 터치 ─────
    private val handler = Handler(Looper.getMainLooper())
    private var startR = -1; private var startC = -1
    private var dragging = false; private var longFired = false
    private var target = EMPTY
    private var axis = 0 // 0 미정, 1 가로, 2 세로
    private val longPress = Runnable {
        if (startR >= 0 && !dragging) {
            longFired = true
            target = if (cells[startR * n + startC] == CROSS) EMPTY else CROSS
            set(startR, startC, target)
            commit()
            invalidate(); onChanged()
        }
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val c = ((x - gx) / cell).toInt(); val r = ((y - gy) / cell).toInt()
        if (x < gx || y < gy || r !in 0 until n || c !in 0 until n) return null
        return r to c
    }

    private fun set(r: Int, c: Int, v: Int) {
        val i = r * n + c
        if (cells[i] != v) { batch += i to cells[i]; cells[i] = v }
    }

    private fun commit() { if (batch.isNotEmpty()) { undo.addLast(batch); if (undo.size > 500) undo.removeFirst() }; batch = ArrayList() }

    private fun tapResult(cur: Int) = if (markMode) (if (cur == CROSS) EMPTY else CROSS) else (if (cur == FILL) EMPTY else FILL)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (locked) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val rc = cellAt(e.x, e.y) ?: return true
                startR = rc.first; startC = rc.second; dragging = false; longFired = false; axis = 0
                focusR = startR; focusC = startC
                handler.postDelayed(longPress, 450)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (startR < 0 || longFired) return true
                val rc = cellAt(e.x, e.y) ?: return true
                if (rc.first == startR && rc.second == startC && !dragging) return true
                handler.removeCallbacks(longPress)
                if (!dragging) {
                    dragging = true
                    target = tapResult(cells[startR * n + startC])
                    set(startR, startC, target)
                    axis = if (abs(rc.first - startR) >= abs(rc.second - startC)) 2 else 1
                }
                if (axis == 1) {
                    val lo = min(startC, rc.second); val hi = max(startC, rc.second)
                    for (c in lo..hi) set(startR, c, target)
                    focusC = rc.second
                } else {
                    val lo = min(startR, rc.first); val hi = max(startR, rc.first)
                    for (r in lo..hi) set(r, startC, target)
                    focusR = rc.first
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (startR >= 0 && !dragging && !longFired && e.actionMasked == MotionEvent.ACTION_UP) {
                    set(startR, startC, tapResult(cells[startR * n + startC]))
                }
                val changed = batch.isNotEmpty()
                commit()
                startR = -1
                invalidate()
                if (changed || dragging) onChanged()
            }
        }
        return true
    }

    fun clearAll() {
        for (i in cells.indices) if (cells[i] != EMPTY) { batch += i to cells[i]; cells[i] = EMPTY }
        commit(); invalidate(); onChanged()
    }
}
