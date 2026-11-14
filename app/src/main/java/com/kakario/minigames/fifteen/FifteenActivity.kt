package com.kakario.minigames.fifteen

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
import com.kakario.minigames.GameScreen
import kotlin.math.min

/** 15퍼즐 (숫자). 빈칸과 같은 줄의 칸을 누르면 그쪽으로 한꺼번에 민다. 3×3 · 4×4 · 5×5. */
class FifteenActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("fifteen", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private var n = 4
    private var moves = 0; private var seconds = 0; private var started = false; private var solved = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { if (started && !solved) { seconds++; status() }; handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        n = prefs.getInt("n", 4)
        s = GameScreen(this, "15퍼즐")
        board = Board(this); s.board(board)
        s.row("섞기 (새 게임)" to { newGame() }, "크기" to { pickSize() }, "기록" to { records() })
        s.show()
        val saved = prefs.getString("state$n", null)
        if (saved != null) runCatching {
            val p = saved.split(";"); board.tiles = p[0].split(",").map { it.toInt() }.toIntArray()
            moves = p[1].toInt(); seconds = p[2].toInt(); started = moves > 0
        }.onFailure { newGame() } else newGame()
        status()
    }

    override fun onResume() { super.onResume(); handler.postDelayed(ticker, 1000) }
    override fun onPause() {
        handler.removeCallbacks(ticker)
        if (!solved) prefs.edit().putString("state$n", board.tiles.joinToString(",") + ";$moves;$seconds").apply()
        super.onPause()
    }

    private fun newGame() {
        val t = IntArray(n * n) { (it + 1) % (n * n) }
        // 빈칸을 무작위로 많이 움직여 섞는다 → 항상 풀 수 있는 배치
        var blank = n * n - 1; var prev = -1
        repeat(n * n * 60) {
            val bx = blank % n; val by = blank / n
            val opts = listOf(blank - 1 to (bx > 0), blank + 1 to (bx < n - 1), blank - n to (by > 0), blank + n to (by < n - 1))
                .filter { it.second && it.first != prev }.map { it.first }
            val nx = opts.random(); t[blank] = t[nx]; t[nx] = 0; prev = blank; blank = nx
        }
        board.tiles = t; moves = 0; seconds = 0; started = false; solved = false
        prefs.edit().remove("state$n").apply()
        s.title.text = "  15퍼즐 · ${n}×$n"; status(); board.invalidate(); s.refresh()
    }

    private fun status() { s.status.text = "이동 $moves   ${seconds}초" }

    private fun pickSize() {
        val sizes = intArrayOf(3, 4, 5)
        AlertDialog.Builder(this).setItems(sizes.map { "${it}×$it" }.toTypedArray()) { _, w ->
            n = sizes[w]; prefs.edit().putInt("n", n).apply(); newGame()
        }.show()
    }

    private fun records() {
        val msg = (3..5).joinToString("\n") { k ->
            val m = prefs.getInt("bestM$k", -1); val t = prefs.getInt("bestT$k", -1)
            "${k}×$k: " + if (m < 0) "기록 없음" else "최소 이동 ${m}번 · 최단 ${t}초"
        }
        AlertDialog.Builder(this).setTitle("기록").setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun checkSolved() {
        if ((0 until n * n - 1).all { board.tiles[it] == it + 1 }) {
            solved = true
            val bm = prefs.getInt("bestM$n", -1); val bt = prefs.getInt("bestT$n", -1)
            prefs.edit().putInt("bestM$n", if (bm < 0) moves else min(bm, moves))
                .putInt("bestT$n", if (bt < 0) seconds else min(bt, seconds)).remove("state$n").apply()
            s.refresh()
            AlertDialog.Builder(this).setTitle("완성!").setMessage("이동 ${moves}번 · ${seconds}초")
                .setPositiveButton("다시 섞기") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
        }
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        var tiles = IntArray(16)
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val frame = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 8f }
        private fun geo(): Triple<Float, Float, Float> {
            val size = min(width, height) * 0.94f
            return Triple(size / n, (width - size) / 2, (height - size) / 2)
        }
        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val (cell, ox, oy) = geo()
            txt.textSize = cell * 0.45f
            cv.drawRect(ox - 6, oy - 6, ox + cell * n + 6, oy + cell * n + 6, frame)
            for (i in tiles.indices) {
                val v = tiles[i]; if (v == 0) continue
                val x = ox + (i % n) * cell; val y = oy + (i / n) * cell; val m = cell * 0.05f
                cv.drawRoundRect(x + m, y + m, x + cell - m, y + cell - m, cell * 0.1f, cell * 0.1f, box)
                cv.drawText(v.toString(), x + cell / 2, y + cell * 0.66f, txt)
            }
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP || solved) return true
            val (cell, ox, oy) = geo()
            val cx = ((e.x - ox) / cell).toInt(); val cy = ((e.y - oy) / cell).toInt()
            if (e.x < ox || e.y < oy || cx !in 0 until n || cy !in 0 until n) return true
            val b = tiles.indexOf(0); val bx = b % n; val by = b / n
            if (cx != bx && cy != by) return true
            // 빈칸 쪽으로 한 칸씩 당겨온다 (같은 줄 여러 칸 한꺼번에)
            var cur = b
            val target = cy * n + cx
            val step = when { cy == by && cx < bx -> -1; cy == by && cx > bx -> 1; cx == bx && cy < by -> -n; cx == bx && cy > by -> n; else -> 0 }
            if (step == 0) return true
            while (cur != target) { val nx = cur + step; tiles[cur] = tiles[nx]; tiles[nx] = 0; cur = nx; moves++ }
            started = true
            invalidate(); status(); s.tick(20); checkSolved()
            return true
        }
    }
}
