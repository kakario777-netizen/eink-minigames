package com.kakario.minigames.janggi

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.kakario.minigames.GameScreen
import com.kakario.minigames.janggi.Janggi.Companion.CHO
import com.kakario.minigames.janggi.Janggi.Companion.HAN
import kotlin.math.min

/** 장기. 혼자(컴퓨터 상대 쉬움·중간·어려움) 또는 둘이서(위쪽 말 글자는 마주 앉은 사람 쪽으로 뒤집어 보임). */
class JanggiActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("janggi", Context.MODE_PRIVATE) }
    private val game = Janggi()
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private var twoPlayer = false
    private var human = CHO
    private var level = 1
    private var hanja = true
    private var mySetup = 0
    private var thinking = false
    private var over = false
    private var selected = -1
    private var targets: List<Int> = emptyList()
    private val main = Handler(Looper.getMainLooper())

    private val levels = arrayOf("쉬움", "중간", "어려움")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        twoPlayer = prefs.getBoolean("two", false); human = prefs.getInt("human", CHO); level = prefs.getInt("level", 1)
        hanja = prefs.getBoolean("hanja", true); mySetup = prefs.getInt("setup", 0)
        s = GameScreen(this, "장기")
        board = Board(this); s.board(board)
        s.row("새 게임" to { setupDialog() }, "무르기" to { undo() }, "한수 쉼" to { passTurn() }, "글자" to {
            hanja = !hanja; prefs.edit().putBoolean("hanja", hanja).apply(); board.invalidate()
        }, "기록" to { records() })
        s.show()
        newGame()
    }

    private fun flip() = !twoPlayer && human == HAN

    private fun newGame() {
        val mine = Janggi.SETUPS[mySetup].second
        val other = if (twoPlayer) mine else Janggi.SETUPS.random().second
        if (human == CHO || twoPlayer) game.setup(mine, other) else game.setup(other, mine)
        over = false; selected = -1; targets = emptyList()
        s.title.text = "  장기 · " + if (twoPlayer) "둘이서" else "컴퓨터(${levels[level]})"
        status(); board.invalidate(); s.refresh()
        if (!twoPlayer && game.turn != human) ai()
    }

    private fun setupDialog() {
        val items = arrayOf("혼자 · 초(먼저)로", "혼자 · 한(나중)으로", "둘이서", "난이도: ${levels[level]}", "내 상차림: ${Janggi.SETUPS[mySetup].first}")
        AlertDialog.Builder(this).setTitle("새 게임").setItems(items) { _, w ->
            when (w) {
                0 -> { twoPlayer = false; human = CHO }
                1 -> { twoPlayer = false; human = HAN }
                2 -> twoPlayer = true
                3 -> { AlertDialog.Builder(this).setItems(levels) { _, l -> level = l; save(); setupDialog() }.show(); return@setItems }
                4 -> { AlertDialog.Builder(this).setItems(Janggi.SETUPS.map { it.first }.toTypedArray()) { _, l -> mySetup = l; save(); setupDialog() }.show(); return@setItems }
            }
            save(); newGame()
        }.show()
    }

    private fun save() = prefs.edit().putBoolean("two", twoPlayer).putInt("human", human).putInt("level", level).putInt("setup", mySetup).apply()

    private fun records() {
        val msg = levels.withIndex().joinToString("\n") { (i, n) -> "$n: ${prefs.getInt("win$i", 0)}승 ${prefs.getInt("lose$i", 0)}패" }
        AlertDialog.Builder(this).setTitle("컴퓨터 상대 기록").setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun sideName(sd: Int) = if (sd == CHO) "초" else "한"

    private fun status() {
        if (over) return
        s.status.text = when {
            thinking -> "생각 중…"
            game.inCheck(game.turn) -> "${sideName(game.turn)} 장군!"
            else -> "${sideName(game.turn)} 차례"
        }
    }

    private fun afterMove() {
        selected = -1; targets = emptyList()
        board.invalidate(); s.tick(12)
        val t = game.turn
        if (game.legal(t).isEmpty() && game.inCheck(t)) {
            over = true
            val winner = 1 - t
            s.status.text = "외통! ${sideName(winner)} 승리"
            if (!twoPlayer) { val k = if (winner == human) "win$level" else "lose$level"; prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).apply() }
            s.refresh()
            AlertDialog.Builder(this).setTitle("${sideName(winner)} 승리!").setMessage(if (!twoPlayer) (if (winner == human) "이겼어요!" else "컴퓨터가 이겼어요") else "외통수예요")
                .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
            return
        }
        status()
        if (!twoPlayer && game.turn != human) ai()
    }

    private fun ai() {
        thinking = true; status()
        val side = game.turn
        val depth = when (level) { 0 -> 1; 1 -> 2; else -> 3 }
        val noise = when (level) { 0 -> 40; 1 -> 6; else -> 0 }
        Thread {
            val m = game.bestMove(side, depth, noise)
            main.post {
                thinking = false
                if (m < 0) { if (!game.inCheck(side)) { game.pass(); afterMove() } else status() }
                else { game.doMove(m); afterMove() }
            }
        }.start()
    }

    private fun undo() {
        if (thinking) return
        val n = if (twoPlayer) 1 else 2
        repeat(n) { game.undo() }
        if (!twoPlayer && game.turn != human) game.undo()
        over = false; selected = -1; targets = emptyList(); status(); board.invalidate()
    }

    private fun passTurn() {
        if (thinking || over) return
        if (!twoPlayer && game.turn != human) return
        if (game.inCheck(game.turn)) { s.status.text = "장군일 때는 쉴 수 없어요"; return }
        game.pass(); afterMove()
    }

    // ───── 판 ─────
    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 2f }
        private val fillW = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val fillB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }

        private fun geo(): Triple<Float, Float, Float> {
            val g = min(width / 9.4f, height / 10.4f)
            return Triple(g, (width - g * 8) / 2, (height - g * 9) / 2)
        }
        private fun sx(x: Int) = if (flip()) 8 - x else x
        private fun sy(y: Int) = if (flip()) 9 - y else y

        private fun label(v: Int): String {
            val t = game.type(v); val sd = game.side(v)
            return if (hanja) when (t) { Janggi.K -> if (sd == CHO) "楚" else "漢"; Janggi.A -> "士"; Janggi.E -> "象"; Janggi.H -> "馬"; Janggi.R -> "車"; Janggi.C -> "包"; else -> if (sd == CHO) "卒" else "兵" }
            else when (t) { Janggi.K -> if (sd == CHO) "초" else "한"; Janggi.A -> "사"; Janggi.E -> "상"; Janggi.H -> "마"; Janggi.R -> "차"; Janggi.C -> "포"; else -> if (sd == CHO) "졸" else "병" }
        }

        /** 팔각형 말 */
        private fun octagon(cx: Float, cy: Float, r: Float): Path {
            val p = Path(); val k = r * 0.414f
            p.moveTo(cx - k, cy - r); p.lineTo(cx + k, cy - r); p.lineTo(cx + r, cy - k); p.lineTo(cx + r, cy + k)
            p.lineTo(cx + k, cy + r); p.lineTo(cx - k, cy + r); p.lineTo(cx - r, cy + k); p.lineTo(cx - r, cy - k); p.close()
            return p
        }

        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val (g, ox, oy) = geo()
            for (y in 0..9) cv.drawLine(ox, oy + y * g, ox + 8 * g, oy + y * g, line)
            for (x in 0..8) cv.drawLine(ox + x * g, oy, ox + x * g, oy + 9 * g, line)
            for (top in intArrayOf(0, 7)) {
                cv.drawLine(ox + 3 * g, oy + top * g, ox + 5 * g, oy + (top + 2) * g, line)
                cv.drawLine(ox + 5 * g, oy + top * g, ox + 3 * g, oy + (top + 2) * g, line)
            }
            // 직전 수 표시
            game.history.lastOrNull()?.takeIf { !it.pass }?.let { mv ->
                for (sq in intArrayOf(mv.from, mv.to)) {
                    val cx = ox + sx(sq % 9) * g; val cy = oy + sy(sq / 9) * g; val r = g * 0.48f
                    cv.drawRect(cx - r, cy - r, cx + r, cy + r, mark)
                }
            }
            for (i in 0 until 90) {
                val v = game.b[i]; if (v == 0) continue
                val cx = ox + sx(i % 9) * g; val cy = oy + sy(i / 9) * g
                val t = game.type(v)
                val r = g * when (t) { Janggi.K -> 0.47f; Janggi.A, Janggi.P -> 0.34f; else -> 0.41f }
                val han = game.side(v) == HAN
                val path = octagon(cx, cy, r)
                cv.drawPath(path, if (han) fillB else fillW)
                edge.strokeWidth = if (i == selected) g * 0.1f else g * 0.04f
                cv.drawPath(path, edge)
                if (i == selected) cv.drawPath(octagon(cx, cy, r + g * 0.08f), edge)
                txt.color = if (han) Color.WHITE else Color.BLACK
                txt.textSize = r * 1.15f
                val upside = twoPlayer && han
                if (upside) { cv.save(); cv.rotate(180f, cx, cy) }
                cv.drawText(label(v), cx, cy + txt.textSize * 0.36f, txt)
                if (upside) cv.restore()
            }
            for (sq in targets) {
                val cx = ox + sx(sq % 9) * g; val cy = oy + sy(sq / 9) * g
                if (game.b[sq] == 0) cv.drawCircle(cx, cy, g * 0.13f, dot)
                else cv.drawCircle(cx, cy, g * 0.5f, mark)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP || over || thinking) return true
            if (!twoPlayer && game.turn != human) return true
            val (g, ox, oy) = geo()
            val gx = Math.round((e.x - ox) / g); val gy = Math.round((e.y - oy) / g)
            if (gx !in 0..8 || gy !in 0..9) return true
            val sq = Janggi.idx(sx(gx), sy(gy))
            if (sq in targets) {
                game.doMove(selected * 100 + sq)
                afterMove(); return true
            }
            val v = game.b[sq]
            if (v != 0 && game.side(v) == game.turn) {
                selected = sq
                targets = game.legal(game.turn).filter { it / 100 == sq }.map { it % 100 }
            } else { selected = -1; targets = emptyList() }
            invalidate()
            return true
        }
    }
}
