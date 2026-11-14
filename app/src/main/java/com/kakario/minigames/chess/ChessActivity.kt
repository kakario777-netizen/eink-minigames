package com.kakario.minigames.chess

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

/** 체스. 혼자(컴퓨터: 쉬움·중간·어려움, 백/흑 선택) 또는 둘이서. */
class ChessActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("chess", Context.MODE_PRIVATE) }
    private val game = Chess()
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private var twoPlayer = false; private var human = 0; private var level = 1
    private var thinking = false; private var over = false
    private var sel = -1; private var targets: List<Int> = emptyList()
    private val main = Handler(Looper.getMainLooper())
    private val levels = arrayOf("쉬움", "중간", "어려움")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        twoPlayer = prefs.getBoolean("two", false); human = prefs.getInt("human", 0); level = prefs.getInt("level", 1)
        s = GameScreen(this, "체스")
        board = Board(this); s.board(board)
        s.row("새 게임" to { setup() }, "무르기" to { undo() }, "기록" to { records() })
        s.show()
        newGame()
    }

    private fun newGame() {
        game.reset(); over = false; sel = -1; targets = emptyList()
        s.title.text = "  체스 · " + if (twoPlayer) "둘이서" else "컴퓨터(${levels[level]})"
        status(); board.invalidate(); s.refresh()
        if (!twoPlayer && game.turn != human) ai()
    }

    private fun setup() {
        val items = arrayOf("혼자 · 백(먼저)으로", "혼자 · 흑으로", "둘이서", "난이도: ${levels[level]}")
        AlertDialog.Builder(this).setTitle("새 게임").setItems(items) { _, w ->
            when (w) {
                0 -> { twoPlayer = false; human = 0 }
                1 -> { twoPlayer = false; human = 1 }
                2 -> twoPlayer = true
                3 -> { AlertDialog.Builder(this).setItems(levels) { _, l -> level = l; save(); setup() }.show(); return@setItems }
            }
            save(); newGame()
        }.show()
    }
    private fun save() = prefs.edit().putBoolean("two", twoPlayer).putInt("human", human).putInt("level", level).apply()

    private fun records() {
        val msg = levels.withIndex().joinToString("\n") { (i, n) -> "$n: ${prefs.getInt("win$i", 0)}승 ${prefs.getInt("lose$i", 0)}패 ${prefs.getInt("draw$i", 0)}무" }
        AlertDialog.Builder(this).setTitle("컴퓨터 상대 기록").setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun name(c: Int) = if (c == 0) "백" else "흑"
    private fun status() {
        if (over) return
        s.status.text = when { thinking -> "생각 중…"; game.inCheck(game.turn) -> "${name(game.turn)} 체크!"; else -> "${name(game.turn)} 차례" }
    }

    private fun after() {
        sel = -1; targets = emptyList(); board.invalidate(); s.tick(12)
        val c = game.turn
        if (game.legal(c).isEmpty()) {
            over = true
            val mate = game.inCheck(c)
            val msg = if (mate) "체크메이트! ${name(1 - c)} 승리" else "스테일메이트 — 무승부"
            s.status.text = msg
            if (!twoPlayer) {
                val k = when { !mate -> "draw$level"; 1 - c == human -> "win$level"; else -> "lose$level" }
                prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).apply()
            }
            s.refresh()
            AlertDialog.Builder(this).setTitle(msg).setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
            return
        }
        status()
        if (!twoPlayer && game.turn != human) ai()
    }

    private fun ai() {
        thinking = true; status()
        val c = game.turn
        val depth = when (level) { 0 -> 1; 1 -> 2; else -> 3 }
        val noise = when (level) { 0 -> 80; 1 -> 15; else -> 0 }
        Thread {
            val m = game.best(c, depth, noise)
            main.post { thinking = false; if (m >= 0) game.make(m); after() }
        }.start()
    }

    private fun undo() {
        if (thinking || game.history.isEmpty()) return
        game.unmake()
        if (!twoPlayer && game.turn != human && game.history.isNotEmpty()) game.unmake()
        over = false; sel = -1; targets = emptyList(); status(); board.invalidate()
    }

    private fun flip() = !twoPlayer && human == 1

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val dark = Paint().apply { color = Color.rgb(185, 185, 185) }
        private val frame = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f }
        private val selP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 8f }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
        private val coord = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
        private val glyph = arrayOf("", "♟", "♞", "♝", "♜", "♛", "♚")
        private val glyphW = arrayOf("", "♙", "♘", "♗", "♖", "♕", "♔")
        private fun geo(): Triple<Float, Float, Float> { val cell = min(width, height) * 0.92f / 8; return Triple(cell, (width - cell * 8) / 2, (height - cell * 8) / 2) }
        private fun view(sq: Int) = if (flip()) 63 - sq else sq

        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val (cell, ox, oy) = geo()
            txt.textSize = cell * 0.82f; coord.textSize = cell * 0.22f
            for (sq in 0 until 64) {
                val v = view(sq); val x = ox + (v % 8) * cell; val y = oy + (v / 8) * cell
                if ((sq % 8 + sq / 8) % 2 == 1) cv.drawRect(x, y, x + cell, y + cell, dark)
                val p = game.b[sq]
                if (p != 0) {
                    // 백: 테두리 모양, 흑: 꽉 찬 모양 (︎ = 글자 모양으로)
                    val t = (if (Chess.color(p) == 0) glyphW else glyph)[Chess.type(p)] + "︎"
                    val upside = twoPlayer && Chess.color(p) == 1
                    if (upside) { cv.save(); cv.rotate(180f, x + cell / 2, y + cell / 2) }
                    cv.drawText(t, x + cell / 2, y + cell * 0.8f, txt)
                    if (upside) cv.restore()
                }
                if (sq == sel) cv.drawRect(x + 4, y + 4, x + cell - 4, y + cell - 4, selP)
                if (sq in targets) { if (p == 0) cv.drawCircle(x + cell / 2, y + cell / 2, cell * 0.13f, dot) else cv.drawCircle(x + cell / 2, y + cell / 2, cell * 0.46f, selP) }
            }
            game.history.lastOrNull()?.let { u ->
                for (sq in intArrayOf(u.move and 63, (u.move shr 6) and 63)) {
                    val v = view(sq); val x = ox + (v % 8) * cell; val y = oy + (v / 8) * cell
                    cv.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, frame)
                }
            }
            cv.drawRect(ox, oy, ox + cell * 8, oy + cell * 8, frame)
            for (k in 0..7) {
                val file = if (flip()) 7 - k else k; val rank = if (flip()) k + 1 else 8 - k
                cv.drawText(('a' + file).toString(), ox + (k + 0.5f) * cell, oy + cell * 8 + coord.textSize * 1.2f, coord)
                cv.drawText(rank.toString(), ox - coord.textSize * 0.8f, oy + (k + 0.6f) * cell, coord)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP || over || thinking) return true
            if (!twoPlayer && game.turn != human) return true
            val (cell, ox, oy) = geo()
            val cx = ((e.x - ox) / cell).toInt(); val cy = ((e.y - oy) / cell).toInt()
            if (e.x < ox || e.y < oy || cx !in 0..7 || cy !in 0..7) return true
            val sq = view(cy * 8 + cx)
            if (sq in targets) {
                val moves = game.legal(game.turn).filter { (it and 63) == sel && ((it shr 6) and 63) == sq }
                if (moves.size > 1) {
                    val names = arrayOf("퀸", "나이트", "룩", "비숍"); val types = intArrayOf(Chess.Q, Chess.N, Chess.R, Chess.B)
                    AlertDialog.Builder(this@ChessActivity).setTitle("승진할 말").setItems(names) { _, w ->
                        game.make(moves.first { (it shr 12) == types[w] }); after()
                    }.show()
                } else { game.make(moves.first()); after() }
                return true
            }
            val p = game.b[sq]
            if (p != 0 && Chess.color(p) == game.turn) { sel = sq; targets = game.legal(game.turn).filter { (it and 63) == sq }.map { (it shr 6) and 63 }.distinct() }
            else { sel = -1; targets = emptyList() }
            invalidate()
            return true
        }
    }
}
