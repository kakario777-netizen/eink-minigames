package com.kakario.minigames.g2048

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.kakario.minigames.GameScreen
import kotlin.math.abs
import kotlin.math.min

/** 2048 — 밀기 대신 판 둘레의 얇은 방향 버튼(또는 판의 그쪽 부분)을 누른다. 숫자가 클수록 칸이 진해진다. */
class G2048Activity : Activity() {
    private val prefs by lazy { getSharedPreferences("g2048", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private var g = IntArray(16); private var score = 0
    private var prev: IntArray? = null; private var prevScore = 0
    private var wonShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = GameScreen(this, "2048")
        board = Board(this); s.board(board)
        s.row("되돌리기" to { prev?.let { g = it; score = prevScore; prev = null; board.invalidate(); status() } }, "새 게임" to { confirmNew() })
        s.show()
        val saved = prefs.getString("state", null)
        if (saved != null) runCatching {
            val p = saved.split(";"); g = p[0].split(",").map { it.toInt() }.toIntArray(); score = p[1].toInt(); wonShown = p[2] == "1"
        }.onFailure { newGame() } else newGame()
        status()
    }

    override fun onPause() { prefs.edit().putString("state", g.joinToString(",") + ";$score;" + (if (wonShown) "1" else "0")).apply(); super.onPause() }

    private fun newGame() { g = IntArray(16); score = 0; prev = null; wonShown = false; add(); add(); board.invalidate(); status(); s.refresh() }
    private fun confirmNew() = AlertDialog.Builder(this).setMessage("새로 시작할까요?").setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("취소", null).show()

    private fun status() {
        val best = maxOf(score, prefs.getInt("best", 0)); if (best > prefs.getInt("best", 0)) prefs.edit().putInt("best", best).apply()
        s.status.text = "점수 $score   최고 $best"
    }

    private fun add() {
        val empty = g.indices.filter { g[it] == 0 }; if (empty.isEmpty()) return
        g[empty.random()] = if (Math.random() < 0.9) 2 else 4
    }

    /** dir: 0 위 1 아래 2 왼쪽 3 오른쪽 */
    private fun move(dir: Int) {
        val before = g.copyOf(); val beforeScore = score
        for (k in 0 until 4) {
            val idx = IntArray(4) { j ->
                when (dir) { 0 -> j * 4 + k; 1 -> (3 - j) * 4 + k; 2 -> k * 4 + j; else -> k * 4 + (3 - j) }
            }
            val vals = idx.map { g[it] }.filter { it != 0 }.toMutableList()
            val out = ArrayList<Int>()
            var i = 0
            while (i < vals.size) {
                if (i + 1 < vals.size && vals[i] == vals[i + 1]) { out += vals[i] * 2; score += vals[i] * 2; i += 2 } else { out += vals[i]; i++ }
            }
            for (j in 0 until 4) g[idx[j]] = if (j < out.size) out[j] else 0
        }
        if (g.contentEquals(before)) return
        prev = before; prevScore = beforeScore
        add(); board.invalidate(); status(); s.tick(15)
        if (!wonShown && g.any { it >= 2048 }) {
            wonShown = true; s.refresh()
            AlertDialog.Builder(this).setTitle("2048 달성!").setMessage("계속해서 더 큰 수에 도전할 수 있어요").setPositiveButton("계속", null).show()
        } else if (!canMove()) {
            s.refresh()
            AlertDialog.Builder(this).setTitle("더 움직일 수 없어요").setMessage("점수 $score")
                .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("되돌리기") { _, _ -> prev?.let { g = it; score = prevScore; prev = null; board.invalidate(); status() } }.show()
        }
    }

    private fun canMove(): Boolean {
        if (g.any { it == 0 }) return true
        for (i in 0 until 16) { if (i % 4 < 3 && g[i] == g[i + 1]) return true; if (i < 12 && g[i] == g[i + 4]) return true }
        return false
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
        private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 150, 150); textAlign = Paint.Align.CENTER }
        private fun geo(): Triple<Float, Float, Float> { val size = min(width, height) * 0.80f; return Triple(size / 4, (width - size) / 2, (height - size) / 2) }
        /** 판 둘레 방향 버튼 4개 (위·아래·왼쪽·오른쪽). */
        private fun bars(): List<android.graphics.RectF> {
            val (cell, ox, oy) = geo(); val s4 = cell * 4; val t = cell * 0.32f; val gap = cell * 0.1f
            return listOf(
                android.graphics.RectF(ox, oy - gap - t, ox + s4, oy - gap),
                android.graphics.RectF(ox, oy + s4 + gap, ox + s4, oy + s4 + gap + t),
                android.graphics.RectF(ox - gap - t, oy, ox - gap, oy + s4),
                android.graphics.RectF(ox + s4 + gap, oy, ox + s4 + gap + t, oy + s4),
            )
        }
        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val (cell, ox, oy) = geo()
            hint.color = Color.BLACK; hint.textSize = cell * 0.24f
            val arrows = arrayOf("▲", "▼", "◀", "▶")
            for ((k, r) in bars().withIndex()) {
                cv.drawRoundRect(r, cell * 0.08f, cell * 0.08f, box)
                cv.drawText(arrows[k], r.centerX(), r.centerY() + hint.textSize * 0.36f, hint)
            }
            for (i in 0 until 16) {
                val x = ox + (i % 4) * cell; val y = oy + (i / 4) * cell; val m = cell * 0.05f
                val v = g[i]
                if (v != 0) {
                    // 흑백 화면: 2·4 흰 칸 → 큰 수일수록 회색 → 128 이상은 검은 칸에 흰 글씨
                    val lvl = Integer.numberOfTrailingZeros(v)
                    val shade = when { lvl <= 2 -> 255; lvl >= 7 -> 0; else -> 255 - (lvl - 2) * 40 }
                    fill.color = Color.rgb(shade, shade, shade)
                    cv.drawRoundRect(x + m, y + m, x + cell - m, y + cell - m, cell * 0.1f, cell * 0.1f, fill)
                    txt.color = if (shade < 120) Color.WHITE else Color.BLACK
                    txt.textSize = cell * when { v < 100 -> 0.45f; v < 1000 -> 0.38f; v < 10000 -> 0.3f; else -> 0.24f }
                    cv.drawText(v.toString(), x + cell / 2, y + cell / 2 + txt.textSize * 0.36f, txt)
                }
                cv.drawRoundRect(x + m, y + m, x + cell - m, y + cell - m, cell * 0.1f, cell * 0.1f, box)
            }
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP) return true
            // 방향 버튼을 누르면 그쪽으로, 판 안을 누르면 가운데에서 누른 쪽 방향으로
            val (cell, _, _) = geo(); val slack = cell * 0.12f
            for ((k, r) in bars().withIndex()) if (e.x in r.left - slack..r.right + slack && e.y in r.top - slack..r.bottom + slack) { move(k); return true }
            val dx = e.x - width / 2f; val dy = e.y - height / 2f
            if (abs(dx) < width * 0.04f && abs(dy) < width * 0.04f) return true
            move(if (abs(dy) > abs(dx)) (if (dy < 0) 0 else 1) else (if (dx < 0) 2 else 3))
            return true
        }
    }
}
