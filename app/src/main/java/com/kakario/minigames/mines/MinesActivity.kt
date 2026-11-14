package com.kakario.minigames.mines

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.kakario.minigames.GameScreen
import kotlin.math.min

/** 윈도우 지뢰찾기. 첫 칸은 항상 안전(주변까지). 누르기 = 열기(또는 깃발 도구), 꾸욱 = 깃발. 숫자 칸 누르기 = 주변 한꺼번에 열기. */
class MinesActivity : Activity() {
    data class Level(val name: String, val w: Int, val h: Int, val mines: Int)
    private val levels = listOf(Level("초급", 9, 9, 10), Level("중급", 16, 16, 40), Level("고급", 16, 30, 99))
    private val prefs by lazy { getSharedPreferences("mines", Context.MODE_PRIVATE) }
    private var lv = 0
    private lateinit var s: GameScreen
    private lateinit var view: Board
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private val ticker = object : Runnable {
        override fun run() { if (view.started && !view.over) { seconds++; status() }; handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lv = prefs.getInt("lv", 0)
        s = GameScreen(this, "지뢰찾기")
        view = Board(this)
        s.board(view)
        lateinit var bOpen: TextView; lateinit var bFlag: TextView
        val r = s.row("열기" to { view.flagMode = false; s.setOn(bOpen, true); s.setOn(bFlag, false) },
            "깃발" to { view.flagMode = true; s.setOn(bOpen, false); s.setOn(bFlag, true) },
            "새 게임" to { newGame() }, "난이도" to { pickLevel() }, "기록" to { records() })
        bOpen = r.getChildAt(0) as TextView; bFlag = r.getChildAt(1) as TextView
        s.setOn(bOpen, true)
        s.show()
        newGame()
    }

    override fun onResume() { super.onResume(); handler.postDelayed(ticker, 1000) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    private fun newGame() {
        val l = levels[lv]; seconds = 0
        view.reset(l.w, l.h, l.mines)
        s.title.text = "  지뢰찾기 · ${l.name}"
        status(); s.refresh()
    }

    private fun status() { s.status.text = "지뢰 ${view.mines - view.flags}   ${seconds}초" }

    private fun pickLevel() {
        AlertDialog.Builder(this).setItems(levels.map { "${it.name} (${it.w}×${it.h}, 지뢰 ${it.mines})" }.toTypedArray()) { _, w ->
            lv = w; prefs.edit().putInt("lv", w).apply(); newGame()
        }.show()
    }

    private fun records() {
        val msg = levels.indices.joinToString("\n") { i ->
            val b = prefs.getInt("best$i", -1)
            "${levels[i].name}: " + (if (b < 0) "기록 없음" else "${b}초") + "  (이긴 판 ${prefs.getInt("won$i", 0)} / ${prefs.getInt("played$i", 0)})"
        }
        AlertDialog.Builder(this).setTitle("최고 기록").setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun ended(win: Boolean) {
        prefs.edit().putInt("played$lv", prefs.getInt("played$lv", 0) + 1).apply()
        s.refresh()
        if (win) {
            val best = prefs.getInt("best$lv", -1)
            val rec = best < 0 || seconds < best
            prefs.edit().putInt("won$lv", prefs.getInt("won$lv", 0) + 1).apply()
            if (rec) prefs.edit().putInt("best$lv", seconds).apply()
            AlertDialog.Builder(this).setTitle("성공!").setMessage("${seconds}초" + if (rec) " — 새 최고 기록!" else "")
                .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
        } else {
            s.status.text = "펑! 지뢰를 밟았어요"
        }
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        var w = 9; var h = 9; var mines = 10
        var mine = BooleanArray(81); var open = BooleanArray(81); var flag = BooleanArray(81)
        var started = false; var over = false; var flags = 0; var flagMode = false; var boom = -1
        private var cell = 0f; private var ox = 0f; private var oy = 0f

        fun reset(w: Int, h: Int, m: Int) {
            this.w = w; this.h = h; mines = m
            mine = BooleanArray(w * h); open = BooleanArray(w * h); flag = BooleanArray(w * h)
            started = false; over = false; flags = 0; boom = -1
            layoutCells(); invalidate()
        }

        private fun layoutCells() {
            if (width == 0) return
            cell = min(width / w.toFloat(), height / h.toFloat()) * 0.98f
            ox = (width - cell * w) / 2; oy = (height - cell * h) / 2
        }
        override fun onSizeChanged(a: Int, b: Int, c: Int, d: Int) = layoutCells()

        private fun nb(i: Int): List<Int> {
            val x = i % w; val y = i / w; val out = ArrayList<Int>(8)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) out += ny * w + nx
            }
            return out
        }
        private fun count(i: Int) = nb(i).count { mine[it] }

        private fun place(safe: Int) {
            val ban = (nb(safe) + safe).toSet()
            val cand = (0 until w * h).filter { it !in ban }.shuffled()
            for (k in 0 until mines) mine[cand[k]] = true
            started = true
        }

        private fun reveal(i: Int) {
            val stack = ArrayDeque<Int>(); stack.add(i)
            while (stack.isNotEmpty()) {
                val j = stack.removeLast()
                if (open[j] || flag[j]) continue
                open[j] = true
                if (mine[j]) { boom = j; over = true; continue }
                if (count(j) == 0) nb(j).forEach { if (!open[it]) stack.add(it) }
            }
        }

        private fun act(i: Int, longPress: Boolean) {
            if (over) return
            val wantFlag = longPress || flagMode
            if (open[i]) {
                // 숫자 칸: 주변 깃발 수가 맞으면 나머지를 한꺼번에 연다
                if (count(i) > 0 && nb(i).count { flag[it] } == count(i)) nb(i).forEach { if (!flag[it]) reveal(it) }
            } else if (wantFlag) {
                flag[i] = !flag[i]; flags += if (flag[i]) 1 else -1
            } else if (!flag[i]) {
                if (!started) place(i)
                reveal(i)
            }
            invalidate(); s.tick(20)
            if (over) { for (k in mine.indices) if (mine[k]) open[k] = true; invalidate(); ended(false); return }
            if (started && (0 until w * h).all { open[it] || mine[it] }) {
                over = true
                for (k in mine.indices) if (mine[k] && !flag[k]) { flag[k] = true; flags++ }
                invalidate(); status(); ended(true)
            }
            status()
        }

        private val line = Paint().apply { color = Color.BLACK; strokeWidth = 1.5f }
        private val closed = Paint().apply { color = Color.rgb(205, 205, 205) }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 3f }

        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            txt.textSize = cell * 0.62f
            for (i in 0 until w * h) {
                val x = ox + (i % w) * cell; val y = oy + (i / w) * cell
                if (!open[i]) {
                    cv.drawRect(x, y, x + cell, y + cell, closed)
                    if (flag[i]) {
                        cv.drawLine(x + cell * 0.38f, y + cell * 0.2f, x + cell * 0.38f, y + cell * 0.82f, ink)
                        val p = android.graphics.Path().apply {
                            moveTo(x + cell * 0.38f, y + cell * 0.2f); lineTo(x + cell * 0.78f, y + cell * 0.35f); lineTo(x + cell * 0.38f, y + cell * 0.5f); close()
                        }
                        cv.drawPath(p, ink)
                        if (over && !mine[i]) { cv.drawLine(x, y, x + cell, y + cell, ink); cv.drawLine(x + cell, y, x, y + cell, ink) }
                    }
                } else if (mine[i]) {
                    if (i == boom) cv.drawRect(x, y, x + cell, y + cell, closed)
                    cv.drawCircle(x + cell / 2, y + cell / 2, cell * 0.28f, ink)
                    cv.drawLine(x + cell * 0.12f, y + cell / 2, x + cell * 0.88f, y + cell / 2, ink)
                    cv.drawLine(x + cell / 2, y + cell * 0.12f, x + cell / 2, y + cell * 0.88f, ink)
                } else {
                    val n = count(i)
                    if (n > 0) cv.drawText(n.toString(), x + cell / 2, y + cell * 0.72f, txt)
                }
            }
            for (k in 0..w) cv.drawLine(ox + k * cell, oy, ox + k * cell, oy + h * cell, line)
            for (k in 0..h) cv.drawLine(ox, oy + k * cell, ox + w * cell, oy + k * cell, line)
        }

        private var downCell = -1; private var longDone = false
        private val lp = Runnable { if (downCell >= 0) { longDone = true; act(downCell, true) } }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val cx = ((e.x - ox) / cell).toInt(); val cy = ((e.y - oy) / cell).toInt()
            val i = if (e.x >= ox && e.y >= oy && cx in 0 until w && cy in 0 until h) cy * w + cx else -1
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downCell = i; longDone = false; if (i >= 0) postDelayed(lp, 450) }
                MotionEvent.ACTION_MOVE -> if (i != downCell) { removeCallbacks(lp); downCell = -1 }
                MotionEvent.ACTION_UP -> { removeCallbacks(lp); if (downCell >= 0 && !longDone) act(downCell, false); downCell = -1 }
                MotionEvent.ACTION_CANCEL -> { removeCallbacks(lp); downCell = -1 }
            }
            return true
        }
    }
}
