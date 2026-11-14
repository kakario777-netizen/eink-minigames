package com.kakario.minigames.yut

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.kakario.minigames.GameScreen
import kotlin.math.min
import kotlin.random.Random

/**
 * 윷놀이. 말 4개, 2~4명(사람·컴퓨터 섞어서). 윷·모·잡기면 한 번 더. 빽도 있음. 모·뒷모·방에 멈추면 지름길.
 * 자리 번호: 바깥 0(참먹이)~19, 모(5)에서 방으로 20·21·방22·23·24 → 찌모(15), 뒷모(10)에서 25·26·방22·27·28 → 참먹이.
 */
class YutActivity : Activity() {
    companion object {
        const val HOME = -1; const val DONE = 99; const val CENTER = 22
        val NAMES = mapOf(-1 to "빽도", 1 to "도", 2 to "개", 3 to "걸", 4 to "윷", 5 to "모")
        val MARK = arrayOf("●", "○", "■", "▲")
    }
    private val prefs by lazy { getSharedPreferences("yut", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private val main = Handler(Looper.getMainLooper())

    private var players = 2
    private var human = BooleanArray(4)
    private var pos = Array(4) { IntArray(4) { HOME } }
    private var trail = Array(4) { Array(4) { ArrayList<Int>() } }
    private var cur = 0
    private val results = ArrayList<Int>()
    private var mustThrow = true
    private var selResult = 0
    private var over = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        players = prefs.getInt("players", 2)
        val hs = prefs.getString("human", "1000")!!
        human = BooleanArray(4) { hs.getOrNull(it) == '1' }
        s = GameScreen(this, "윷놀이")
        board = Board(this); s.board(board)
        s.row("윷 던지기" to { if (isHuman()) throwYut() }, "새 게임" to { setup() }, "기록" to { records() })
        s.show()
        newGame()
    }

    private fun isHuman() = !over && human[cur]
    private fun pname(p: Int) = "${MARK[p]} " + if (human[p]) (if (human.take(players).count { it } > 1) "${p + 1}번" else "나") else "컴퓨터"

    private fun setup() {
        val items = arrayOf("나 + 컴퓨터 1명", "나 + 컴퓨터 2명", "나 + 컴퓨터 3명", "사람 2명", "사람 3명", "사람 4명")
        AlertDialog.Builder(this).setTitle("새 게임").setItems(items) { _, w ->
            when (w) {
                0, 1, 2 -> { players = w + 2; human = BooleanArray(4) { it == 0 } }
                else -> { players = w - 1; human = BooleanArray(4) { it < players } }
            }
            prefs.edit().putInt("players", players).putString("human", human.joinToString("") { if (it) "1" else "0" }).apply()
            newGame()
        }.show()
    }
    private fun records() { AlertDialog.Builder(this).setTitle("컴퓨터 상대 기록").setMessage("이긴 판 ${prefs.getInt("win", 0)} / ${prefs.getInt("played", 0)}판").setPositiveButton("확인", null).show() }

    private fun newGame() {
        pos = Array(4) { IntArray(4) { HOME } }; trail = Array(4) { Array(4) { ArrayList<Int>() } }
        cur = 0; results.clear(); mustThrow = true; selResult = 0; over = false
        if (human.take(players).count { it } == 1) prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).apply()
        s.title.text = "  윷놀이 · ${players}명"
        turnStatus(); board.invalidate(); s.refresh()
        if (!human[cur]) main.postDelayed({ aiStep() }, 600)
    }

    private fun turnStatus(extra: String = "") {
        s.status.text = "${pname(cur)} 차례" + if (mustThrow) " · 던지기" else " · 말 옮기기" + extra
    }

    // ───── 윷 ─────
    private fun roll(): Int {
        val flats = BooleanArray(4) { Random.nextDouble() < 0.6 }
        return when (flats.count { it }) {
            0 -> 5; 4 -> 4; 3 -> 3; 2 -> 2
            else -> if (flats[0]) -1 else 1 // 표시된 윷짝 하나만 뒤집히면 빽도
        }
    }

    private fun throwYut() {
        if (!mustThrow) { s.status.text = "먼저 말을 옮겨요"; return }
        val r = roll(); results += r
        mustThrow = r == 4 || r == 5
        val name = NAMES[r]!!
        if (!mustThrow) {
            // 빽도인데 판에 말이 없으면 버림
            if (results.all { it == -1 } && pos[cur].none { it != HOME && it != DONE }) {
                results.clear(); s.status.text = "${pname(cur)}: 빽도 — 옮길 말이 없어요"
                board.invalidate(); nextPlayer(); return
            }
            selResult = 0
        }
        s.status.text = "${pname(cur)}: $name!" + if (mustThrow) " 한 번 더 던져요" else ""
        board.invalidate(); s.tick(12)
        if (!human[cur]) main.postDelayed({ aiStep() }, 700)
    }

    // ───── 길 ─────
    private fun nextNode(node: Int, start: Int, prev: Int): Int = when (node) {
        5 -> if (start == 5) 20 else 6
        10 -> if (start == 10) 25 else 11
        19 -> DONE
        20 -> 21; 21 -> CENTER
        CENTER -> if (start == CENTER || prev == 26) 27 else 23
        23 -> 24; 24 -> 15
        25 -> 26; 26 -> CENTER
        27 -> 28; 28 -> DONE
        0 -> if (start == 0 && prev != HOME) DONE else 1
        else -> node + 1
    }

    /** 말 하나(또는 업힌 무리)를 steps만큼 옮긴 뒤의 자리와 지나온 길. */
    private fun simulate(p: Int, t: Int, steps: Int): Pair<Int, ArrayList<Int>> {
        val tr = ArrayList(trail[p][t])
        var node = pos[p][t]
        if (steps == -1) {
            if (node == HOME || node == DONE) return node to tr
            if (tr.size >= 2) { tr.removeAt(tr.lastIndex); return tr.last() to tr }
            return 19 to arrayListOf(19)
        }
        val start: Int
        var prev: Int
        if (node == HOME) { node = 0; start = 0; prev = HOME; tr.clear(); tr += 0 } else { start = node; prev = if (tr.size >= 2) tr[tr.size - 2] else -2 }
        repeat(steps) {
            if (node == DONE) return@repeat
            val nx = nextNode(node, start, prev)
            prev = node; node = nx
            if (node != DONE) tr += node
        }
        return node to tr
    }

    private fun group(p: Int, t: Int): List<Int> {
        val n = pos[p][t]
        return if (n == HOME || n == DONE) listOf(t) else (0 until 4).filter { pos[p][it] == n }
    }

    private fun canUse(p: Int, t: Int, r: Int) = pos[p][t] != DONE && !(r == -1 && pos[p][t] == HOME)

    /** 결과 r로 말 t를 옮긴다. 잡으면 true. */
    private fun apply(p: Int, t: Int, r: Int): Boolean {
        val (dest, tr) = simulate(p, t, r)
        val g = group(p, t)
        for (k in g) { pos[p][k] = dest; trail[p][k] = ArrayList(tr) }
        var caught = false
        if (dest != DONE && dest != HOME) for (q in 0 until players) if (q != p) for (k in 0 until 4) if (pos[q][k] == dest) {
            pos[q][k] = HOME; trail[q][k].clear(); caught = true
        }
        return caught
    }

    private fun useResult(t: Int) {
        val r = results[selResult]
        if (!canUse(cur, t, r)) { s.status.text = "그 말은 ${NAMES[r]}로 움직일 수 없어요"; return }
        results.removeAt(selResult); selResult = 0
        val caught = apply(cur, t, r)
        board.invalidate(); s.tick(12)
        if (pos[cur].all { it == DONE }) { finish(cur); return }
        var msg = "${pname(cur)}: ${NAMES[r]}"
        if (caught) { mustThrow = true; msg += " — 잡았다! 한 번 더" }
        // 남은 결과가 모두 쓸 수 없으면(빽도만 남았는데 판에 말 없음) 버림
        if (results.isNotEmpty() && results.all { it == -1 } && pos[cur].none { it != HOME && it != DONE }) results.clear()
        s.status.text = msg
        if (results.isEmpty() && !mustThrow) { nextPlayer(); return }
        if (!human[cur]) main.postDelayed({ aiStep() }, 700)
    }

    private fun nextPlayer() {
        cur = (cur + 1) % players; mustThrow = true; results.clear(); selResult = 0
        board.invalidate()
        main.postDelayed({ turnStatus(); if (!human[cur] && !over) aiStep() }, if (human[cur]) 0 else 700)
    }

    private fun finish(p: Int) {
        over = true
        if (human[p] && human.take(players).count { it } == 1) prefs.edit().putInt("win", prefs.getInt("win", 0) + 1).apply()
        s.refresh()
        AlertDialog.Builder(this).setTitle("${pname(p)} 승리!").setMessage("말 네 개가 모두 났어요")
            .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
    }

    // ───── 컴퓨터 ─────
    private fun aiStep() {
        if (over || human[cur]) return
        if (mustThrow) { throwYut(); return }
        var best: Triple<Int, Int, Int>? = null; var bestV = Int.MIN_VALUE
        for ((ri, r) in results.withIndex()) for (t in 0 until 4) {
            if (!canUse(cur, t, r)) continue
            if (pos[cur][t] == HOME && (0 until t).any { pos[cur][it] == HOME }) continue
            val (dest, _) = simulate(cur, t, r)
            var v = 0
            if (dest == DONE) v += 60 * group(cur, t).size
            else {
                for (q in 0 until players) if (q != cur) v += 80 * (0 until 4).count { pos[q][it] == dest }
                if (dest == 5 || dest == 10 || dest == CENTER) v += 15
                if ((0 until 4).any { it != t && pos[cur][it] == dest }) v += 10
                v += progress(dest) - progress(pos[cur][t])
                // 바로 뒤에 상대가 있으면 위험
                for (q in 0 until players) if (q != cur) for (k in 0 until 4) {
                    val o = pos[q][k]; if (o == HOME || o == DONE) continue
                    if (progress(dest) - progress(o) in 1..3) v -= 12
                }
            }
            if (v > bestV) { bestV = v; best = Triple(ri, t, r) }
        }
        val b = best ?: run { results.clear(); nextPlayer(); return }
        selResult = b.first
        useResult(b.second)
    }

    private fun progress(n: Int) = when (n) {
        HOME -> 0; DONE -> 40
        in 0..19 -> n
        20 -> 6; 21 -> 7; CENTER -> 8; 23 -> 9; 24 -> 10
        25 -> 11; 26 -> 12; 27 -> 14; 28 -> 15
        else -> 0
    }

    // ───── 판 ─────
    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 4f }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val chipRects = ArrayList<Pair<RectF, Int>>()
        private val homeRects = ArrayList<Pair<RectF, Int>>()

        private fun unit(n: Int): Pair<Float, Float> = when (n) {
            in 0..5 -> 1f to 1f - n * 0.2f
            in 6..10 -> 1f - (n - 5) * 0.2f to 0f
            in 11..15 -> 0f to (n - 10) * 0.2f
            in 16..19 -> (n - 15) * 0.2f to 1f
            20 -> 5 / 6f to 1 / 6f; 21 -> 4 / 6f to 2 / 6f; CENTER -> 0.5f to 0.5f; 23 -> 2 / 6f to 4 / 6f; 24 -> 1 / 6f to 5 / 6f
            25 -> 1 / 6f to 1 / 6f; 26 -> 2 / 6f to 2 / 6f; 27 -> 4 / 6f to 4 / 6f; 28 -> 5 / 6f to 5 / 6f
            else -> 0.5f to 0.5f
        }
        private fun geo(): Triple<Float, Float, Float> {
            val size = min(width * 0.86f, height * 0.62f)
            return Triple(size, (width - size) / 2, height * 0.12f)
        }
        private fun pt(n: Int): Pair<Float, Float> { val (sz, ox, oy) = geo(); val (u, v) = unit(n); return ox + u * sz to oy + v * sz }

        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val (sz, ox, oy) = geo()
            cv.drawRect(ox, oy, ox + sz, oy + sz, line.apply { style = Paint.Style.STROKE })
            line.style = Paint.Style.FILL
            cv.drawLine(ox, oy, ox + sz, oy + sz, line); cv.drawLine(ox + sz, oy, ox, oy + sz, line)
            for (n in 0..28) {
                val (x, y) = pt(n)
                val big = n % 5 == 0 || n == CENTER
                val r = sz * if (big) 0.045f else 0.03f
                cv.drawCircle(x, y, r, white); cv.drawCircle(x, y, r, ring)
                if (big && n != CENTER) cv.drawCircle(x, y, r * 0.6f, ring)
            }
            txt.textSize = sz * 0.035f
            val (sx, sy) = pt(0); cv.drawText("출발·도착", sx, sy + sz * 0.1f, txt)
            // 말
            txt.textSize = sz * 0.07f
            for (p in 0 until players) {
                val nodes = pos[p].filter { it != HOME && it != DONE }.distinct()
                for (n in nodes) {
                    val cnt = pos[p].count { it == n }
                    val (x, y) = pt(n)
                    val offs = (p - (players - 1) / 2f) * sz * 0.035f
                    piece(cv, p, x + offs, y - offs, sz * 0.04f)
                    if (cnt > 1) { txt.textSize = sz * 0.04f; cv.drawText("×$cnt", x + offs + sz * 0.06f, y - offs - sz * 0.03f, txt) }
                }
            }
            // 결과 (던진 윷)
            chipRects.clear()
            txt.textSize = sz * 0.05f
            var cx = width * 0.05f; val cy = height * 0.02f
            if (results.isNotEmpty()) {
                for ((i, r) in results.withIndex()) {
                    val w = sz * 0.16f
                    val rect = RectF(cx, cy, cx + w, cy + sz * 0.08f)
                    if (i == selResult && !mustThrow) cv.drawRoundRect(rect, 12f, 12f, Paint().apply { color = Color.BLACK })
                    cv.drawRoundRect(rect, 12f, 12f, ring)
                    txt.color = if (i == selResult && !mustThrow) Color.WHITE else Color.BLACK
                    cv.drawText(NAMES[r]!!, rect.centerX(), rect.centerY() + txt.textSize * 0.36f, txt)
                    txt.color = Color.BLACK
                    chipRects += rect to i
                    cx += w + 12
                }
            }
            // 대기 말 · 난 말
            homeRects.clear()
            txt.textSize = sz * 0.045f; txt.textAlign = Paint.Align.LEFT
            var hy = oy + sz + sz * 0.16f
            for (p in 0 until players) {
                val waiting = pos[p].count { it == HOME }; val done = pos[p].count { it == DONE }
                val label = (if (p == cur && !over) "▶ " else "   ") + pname(p) + "   대기 "
                cv.drawText(label, width * 0.05f, hy, txt)
                var hx = width * 0.05f + txt.measureText(label)
                val rect = RectF(hx - 6, hy - sz * 0.06f, hx + sz * 0.4f, hy + sz * 0.025f)
                for (k in 0 until waiting) piece(cv, p, hx + sz * 0.03f + k * sz * 0.08f, hy - sz * 0.016f, sz * 0.028f)
                if (p == cur && waiting > 0) { cv.drawRoundRect(rect, 10f, 10f, ring); homeRects += rect to p }
                hx += sz * 0.45f
                cv.drawText("났음 $done", hx, hy, txt)
                hy += sz * 0.1f
            }
            txt.textAlign = Paint.Align.CENTER
            if (isHuman() && !mustThrow) {
                txt.textSize = sz * 0.04f
                cv.drawText("위에서 윷 결과를 고르고, 옮길 말(또는 대기 말 칸)을 눌러요", width / 2f, hy + sz * 0.02f, txt)
            }
        }

        private fun piece(cv: Canvas, p: Int, x: Float, y: Float, r: Float) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            when (p) {
                0 -> cv.drawCircle(x, y, r, fill)
                1 -> { cv.drawCircle(x, y, r, white); cv.drawCircle(x, y, r, ring) }
                2 -> cv.drawRect(x - r, y - r, x + r, y + r, fill)
                else -> { val path = android.graphics.Path().apply { moveTo(x, y - r * 1.1f); lineTo(x + r, y + r * 0.8f); lineTo(x - r, y + r * 0.8f); close() }; cv.drawPath(path, fill) }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP || !isHuman()) return true
            for ((r, i) in chipRects) if (r.contains(e.x, e.y)) { selResult = i; invalidate(); return true }
            if (mustThrow) return true
            for ((r, _) in homeRects) if (r.contains(e.x, e.y)) {
                val t = (0 until 4).firstOrNull { pos[cur][it] == HOME } ?: return true
                useResult(t); return true
            }
            val (sz, _, _) = geo()
            var bestT = -1; var bestD = Float.MAX_VALUE
            for (t in 0 until 4) {
                val n = pos[cur][t]; if (n == HOME || n == DONE) continue
                val (x, y) = pt(n); val d = (x - e.x) * (x - e.x) + (y - e.y) * (y - e.y)
                if (d < bestD) { bestD = d; bestT = t }
            }
            if (bestT >= 0 && bestD < (sz * 0.08f) * (sz * 0.08f)) useResult(bestT)
            return true
        }
    }
}
