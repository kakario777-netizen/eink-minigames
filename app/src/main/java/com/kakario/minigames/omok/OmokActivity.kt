package com.kakario.minigames.omok

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
import kotlin.math.min

/**
 * 오목 (15줄). 혼자(컴퓨터 상대: 쉬움·중간·어려움) 또는 둘이서.
 * 다섯 개(이상)를 먼저 이으면 승리. '쌍삼 금지' 옵션 켜면 열린 삼이 두 개 생기는 수는 둘 수 없다.
 */
class OmokActivity : Activity() {
    companion object { const val N = 15 }
    private val prefs by lazy { getSharedPreferences("omok", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private var cells = IntArray(N * N)      // 0 빈칸, 1 흑, 2 백
    private val history = ArrayList<Int>()
    private var turn = 1
    private var over = false
    private var twoPlayer = false
    private var humanColor = 1
    private var level = 1                    // 0 쉬움 1 중간 2 어려움
    private var ban33 = true
    private var thinking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        twoPlayer = prefs.getBoolean("two", false); humanColor = prefs.getInt("human", 1)
        level = prefs.getInt("level", 1); ban33 = prefs.getBoolean("ban33", true)
        s = GameScreen(this, "오목")
        board = Board(this); s.board(board)
        s.row("새 게임" to { setup() }, "무르기" to { undo() }, "기록" to { records() })
        s.show()
        newGame()
    }

    private fun newGame() {
        cells = IntArray(N * N); history.clear(); turn = 1; over = false
        s.title.text = "  오목 · " + if (twoPlayer) "둘이서" else "컴퓨터(${arrayOf("쉬움", "중간", "어려움")[level]})"
        status(); board.invalidate(); s.refresh()
        if (!twoPlayer && humanColor == 2) aiMove()
    }

    private fun status() {
        s.status.text = when {
            over -> s.status.text
            thinking -> "생각 중…"
            else -> (if (turn == 1) "● 흑" else "○ 백") + " 차례"
        }
    }

    private fun setup() {
        val items = arrayOf("혼자 · 흑(먼저)으로", "혼자 · 백(나중)으로", "둘이서", "난이도: " + arrayOf("쉬움", "중간", "어려움")[level], "쌍삼 금지: " + if (ban33) "켬" else "끔")
        AlertDialog.Builder(this).setTitle("새 게임").setItems(items) { _, w ->
            when (w) {
                0 -> { twoPlayer = false; humanColor = 1 }
                1 -> { twoPlayer = false; humanColor = 2 }
                2 -> twoPlayer = true
                3 -> { AlertDialog.Builder(this).setItems(arrayOf("쉬움", "중간", "어려움")) { _, l -> level = l; save(); setup() }.show(); return@setItems }
                4 -> { ban33 = !ban33; save(); setup(); return@setItems }
            }
            save(); newGame()
        }.show()
    }

    private fun save() { prefs.edit().putBoolean("two", twoPlayer).putInt("human", humanColor).putInt("level", level).putBoolean("ban33", ban33).apply() }

    private fun records() {
        val msg = arrayOf("쉬움", "중간", "어려움").withIndex().joinToString("\n") { (i, n) -> "$n: ${prefs.getInt("win$i", 0)}승 ${prefs.getInt("lose$i", 0)}패" }
        AlertDialog.Builder(this).setTitle("컴퓨터 상대 기록").setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun undo() {
        if (thinking || history.isEmpty()) return
        val n = if (twoPlayer) 1 else (if (history.size >= 2) 2 else 1)
        repeat(n) { if (history.isNotEmpty()) { cells[history.removeAt(history.lastIndex)] = 0; turn = 3 - turn } }
        over = false; status(); board.invalidate()
    }

    // ───── 규칙 ─────
    private val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))

    private fun at(x: Int, y: Int, b: IntArray) = if (x in 0 until N && y in 0 until N) b[y * N + x] else -1

    private fun fiveAt(i: Int, c: Int, b: IntArray): Boolean {
        val x = i % N; val y = i / N
        for (d in dirs) {
            var cnt = 1
            var k = 1; while (at(x + d[0] * k, y + d[1] * k, b) == c) { cnt++; k++ }
            k = 1; while (at(x - d[0] * k, y - d[1] * k, b) == c) { cnt++; k++ }
            if (cnt >= 5) return true
        }
        return false
    }

    /** 이 수를 두면 열린 삼이 두 줄 이상 생기는지 (쌍삼). */
    private fun isDouble3(i: Int, c: Int, b: IntArray): Boolean {
        if (b[i] != 0) return false
        b[i] = c
        if (fiveAt(i, c, b)) { b[i] = 0; return false }
        val x = i % N; val y = i / N; var threes = 0
        for (d in dirs) {
            val sb = StringBuilder()
            for (k in -5..5) sb.append(when (at(x + d[0] * k, y + d[1] * k, b)) { c -> 'X'; 0 -> '_'; else -> 'O' })
            val line = sb.toString()
            val pats = arrayOf("_XXX_", "_XX_X_", "_X_XX_")
            var found = false
            for (p in pats) {
                var from = 0
                while (true) {
                    val idx = line.indexOf(p, from); if (idx < 0) break
                    // 이번 수(가운데 5번 칸)가 포함된 열린 삼만
                    if (5 in idx until idx + p.length && line[5] == 'X') {
                        // 양 끝 중 한쪽이라도 더 이어져 막힌 육목 모양이 아니면 인정
                        found = true
                    }
                    from = idx + 1
                }
            }
            if (found) threes++
        }
        b[i] = 0
        return threes >= 2
    }

    private fun legal(i: Int, c: Int) = cells[i] == 0 && !(ban33 && isDouble3(i, c, cells))

    private fun play(i: Int) {
        cells[i] = turn; history += i
        board.invalidate(); s.tick(20)
        if (fiveAt(i, turn, cells)) {
            over = true
            val who = if (turn == 1) "흑" else "백"
            s.status.text = "$who 승리!"
            if (!twoPlayer) {
                val k = if (turn == humanColor) "win$level" else "lose$level"
                prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).apply()
            }
            s.refresh()
            AlertDialog.Builder(this).setTitle("$who 승리!").setMessage(if (!twoPlayer) (if (turn == humanColor) "이겼어요!" else "컴퓨터가 이겼어요") else "다섯 개를 이었어요")
                .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
            return
        }
        if (history.size == N * N) { over = true; s.status.text = "무승부"; return }
        turn = 3 - turn
        status()
        if (!twoPlayer && turn != humanColor) aiMove()
    }

    // ───── 컴퓨터 ─────
    private fun aiMove() {
        thinking = true; status()
        board.postDelayed({
            val m = choose(turn)
            thinking = false
            if (m >= 0) play(m) else status()
        }, 120)
    }

    /** 한 방향 모양 점수: 이어진 수와 양 끝이 열렸는지로. */
    private fun lineScore(b: IntArray, x: Int, y: Int, d: IntArray, c: Int): Int {
        var cnt = 1; var open = 0; var gaps = 0
        for (sgn in intArrayOf(1, -1)) {
            var k = 1; var usedGap = false
            while (true) {
                val v = at(x + d[0] * k * sgn, y + d[1] * k * sgn, b)
                if (v == c) { cnt++; k++ }
                else if (v == 0 && !usedGap && at(x + d[0] * (k + 1) * sgn, y + d[1] * (k + 1) * sgn, b) == c) { usedGap = true; gaps++; k++ }
                else { if (v == 0) open++; break }
            }
        }
        if (cnt >= 5 && gaps == 0) return 1_000_000
        if (cnt >= 5) return 20_000
        return when (cnt) {
            4 -> if (open == 2 && gaps == 0) 100_000 else if (open >= 1) 12_000 else 0
            3 -> if (open == 2) 6_000 else if (open == 1) 400 else 0
            2 -> if (open == 2) 300 else if (open == 1) 40 else 0
            else -> if (open == 2) 12 else if (open == 1) 3 else 0
        }
    }

    private fun cellScore(b: IntArray, i: Int, c: Int): Int {
        val x = i % N; val y = i / N; var sum = 0; var bigThreats = 0
        for (d in dirs) { val v = lineScore(b, x, y, d, c); sum += v; if (v >= 6_000) bigThreats++ }
        if (bigThreats >= 2) sum += 50_000 // 겹치는 공격 (사삼·삼삼 등)
        return sum
    }

    private fun candidates(b: IntArray): List<Int> {
        if (b.all { it == 0 }) return listOf((N / 2) * N + N / 2)
        val out = ArrayList<Int>()
        for (i in 0 until N * N) {
            if (b[i] != 0) continue
            val x = i % N; val y = i / N; var near = false
            loop@ for (dy in -2..2) for (dx in -2..2) if (at(x + dx, y + dy, b) > 0) { near = true; break@loop }
            if (near) out += i
        }
        return out
    }

    private fun choose(c: Int): Int {
        val opp = 3 - c
        val cand = candidates(cells).filter { legal(it, c) }
        if (cand.isEmpty()) return -1
        val defW = when (level) { 0 -> 0.6; 1 -> 0.9; else -> 1.0 }
        val scored = cand.map { i -> i to (cellScore(cells, i, c) + defW * cellScore(cells, i, opp)) }.sortedByDescending { it.second }
        if (level == 0) return scored.take(5).random().first
        if (level == 1) return scored.first().first
        // 어려움: 상위 후보마다 상대의 가장 좋은 응수까지 한 수 더 내다본다
        var best = scored.first().first; var bestV = Double.NEGATIVE_INFINITY
        for ((i, v) in scored.take(10)) {
            if (cellScore(cells, i, c) >= 1_000_000) return i
            cells[i] = c
            val reply = candidates(cells).maxOfOrNull { j -> cellScore(cells, j, opp).toDouble() } ?: 0.0
            cells[i] = 0
            val total = v - reply * 0.85
            if (total > bestV) { bestV = total; best = i }
        }
        return best
    }

    // ───── 판 ─────
    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val line = Paint().apply { color = Color.BLACK; strokeWidth = 2f }
        private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }
        private fun geo(): Triple<Float, Float, Float> { val size = min(width, height) * 0.96f; val g = size / N; return Triple(g, (width - size) / 2 + g / 2, (height - size) / 2 + g / 2) }
        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val (g, ox, oy) = geo()
            for (k in 0 until N) {
                cv.drawLine(ox, oy + k * g, ox + (N - 1) * g, oy + k * g, line)
                cv.drawLine(ox + k * g, oy, ox + k * g, oy + (N - 1) * g, line)
            }
            for (p in intArrayOf(3, 7, 11)) for (q in intArrayOf(3, 7, 11)) if ((p == 7) == (q == 7)) cv.drawCircle(ox + p * g, oy + q * g, g * 0.1f, black)
            for (i in cells.indices) {
                if (cells[i] == 0) continue
                val x = ox + (i % N) * g; val y = oy + (i / N) * g
                if (cells[i] == 1) cv.drawCircle(x, y, g * 0.44f, black)
                else { cv.drawCircle(x, y, g * 0.44f, white); cv.drawCircle(x, y, g * 0.44f, edge) }
            }
            history.lastOrNull()?.let { i ->
                val x = ox + (i % N) * g; val y = oy + (i / N) * g; val r = g * 0.13f
                cv.drawRect(x - r, y - r, x + r, y + r, if (cells[i] == 1) white else black)
            }
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP || over || thinking) return true
            if (!twoPlayer && turn != humanColor) return true
            val (g, ox, oy) = geo()
            val cx = Math.round((e.x - ox) / g); val cy = Math.round((e.y - oy) / g)
            if (cx !in 0 until N || cy !in 0 until N) return true
            val i = cy * N + cx
            if (cells[i] != 0) return true
            if (ban33 && isDouble3(i, turn, cells)) { s.status.text = "쌍삼 금지 자리예요"; return true }
            play(i)
            return true
        }
    }
}
