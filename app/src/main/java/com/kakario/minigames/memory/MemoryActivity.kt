package com.kakario.minigames.memory

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.kakario.minigames.GameScreen
import com.kakario.minigames.nonogram.NonogramActivity
import kotlin.math.min

/** 짝 맞추기. 두 장을 뒤집어 같은 그림이면 가져간다. 틀리면 다음에 누를 때 다시 덮인다(이잉크라 시간 대신 누르기로). 혼자 / 둘이서. */
class MemoryActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("memory", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private val sizes = listOf(4 to 4, 4 to 5, 5 to 6, 6 to 6)
    private var sizeIdx = 1
    private var twoPlayer = false
    private var cols = 4; private var rowsN = 5
    private var deck = IntArray(0)            // 그림 번호
    private var taken = IntArray(0)           // 0 안 가져감, 1 플레이어1, 2 플레이어2
    private val up = ArrayList<Int>()
    private var pics: List<NonogramActivity.Puzzle> = emptyList()
    private var moves = 0; private var player = 1; private val score = IntArray(3)
    private val bmps = HashMap<Int, Bitmap>()
    private var back: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sizeIdx = prefs.getInt("size", 1); twoPlayer = prefs.getBoolean("two", false)
        s = GameScreen(this, "짝 맞추기")
        board = Board(this); s.board(board)
        s.row("새 게임" to { newGame() }, "크기" to { pickSize() }, "인원" to {
            twoPlayer = !twoPlayer; prefs.edit().putBoolean("two", twoPlayer).apply(); newGame()
        }, "기록" to { records() })
        back = runCatching { assets.open("cards/back.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        s.show()
        newGame()
    }

    private fun newGame() {
        cols = sizes[sizeIdx].first; rowsN = sizes[sizeIdx].second
        val pairs = cols * rowsN / 2
        pics = NonogramActivity.load(this)["medium"]!!.shuffled().distinctBy { it.title }.take(pairs)
        deck = (0 until pairs).flatMap { listOf(it, it) }.shuffled().toIntArray()
        taken = IntArray(deck.size); up.clear(); bmps.clear()
        moves = 0; player = 1; score.fill(0)
        s.title.text = "  짝 맞추기 · ${cols}×$rowsN" + if (twoPlayer) " · 둘이서" else ""
        status(); board.invalidate(); s.refresh()
    }

    private fun status() {
        s.status.text = if (twoPlayer) "${if (player == 1) "▶" else ""}1번 ${score[1]}  ${if (player == 2) "▶" else ""}2번 ${score[2]}" else "뒤집기 ${moves}번"
    }

    private fun pickSize() {
        AlertDialog.Builder(this).setItems(sizes.map { "${it.first}×${it.second} (${it.first * it.second / 2}쌍)" }.toTypedArray()) { _, w ->
            sizeIdx = w; prefs.edit().putInt("size", w).apply(); newGame()
        }.show()
    }

    private fun records() {
        val msg = sizes.withIndex().joinToString("\n") { (i, p) -> val b = prefs.getInt("best$i", -1); "${p.first}×${p.second}: " + if (b < 0) "기록 없음" else "최소 ${b}번" }
        AlertDialog.Builder(this).setTitle("혼자 하기 최고 기록").setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun tap(i: Int) {
        if (up.size == 2) { // 틀린 두 장 덮기
            up.clear()
            if (twoPlayer) player = 3 - player
            status()
        }
        if (i < 0 || taken[i] != 0 || i in up) { board.invalidate(); return }
        up += i
        if (up.size == 2) {
            moves++
            if (deck[up[0]] == deck[up[1]]) {
                taken[up[0]] = player; taken[up[1]] = player; score[player]++
                up.clear()
                if (taken.all { it != 0 }) finish()
            }
            status()
        }
        board.invalidate(); s.tick(16)
    }

    private fun finish() {
        s.refresh()
        val msg = if (twoPlayer) {
            if (score[1] == score[2]) "비겼어요! (${score[1]} : ${score[2]})" else "${if (score[1] > score[2]) 1 else 2}번 승리! (${score[1]} : ${score[2]})"
        } else {
            val b = prefs.getInt("best$sizeIdx", -1)
            if (b < 0 || moves < b) prefs.edit().putInt("best$sizeIdx", moves).apply()
            "뒤집기 ${moves}번 만에 다 맞췄어요" + if (b < 0 || moves < b) " — 최고 기록!" else ""
        }
        AlertDialog.Builder(this).setTitle("끝!").setMessage(msg).setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private fun cellRect(i: Int): RectF {
            val cw = width / cols.toFloat(); val ch = height / rowsN.toFloat()
            val w = min(cw, ch / 1.4f) * 0.92f; val h = w * 1.4f
            val ox = (width - cols * cw) / 2; val x = ox + (i % cols) * cw + (cw - w) / 2; val y = (i / cols) * ch + (ch - h) / 2
            return RectF(x, y, x + w, y + h)
        }
        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            for (i in deck.indices) {
                val r = cellRect(i)
                if (taken[i] != 0 && i !in up) {
                    // 가져간 자리: 흐린 점선 테두리만
                    box.alpha = 70; cv.drawRoundRect(r, 14f, 14f, box); box.alpha = 255; continue
                }
                if (i in up) {
                    val p = pics[deck[i]]
                    val side = (min(r.width(), r.height() * 0.78f) * 0.86f).toInt().coerceAtLeast(8)
                    val b = bmps.getOrPut(deck[i] * 10000 + side) { NonogramActivity.picture(p, side) }
                    cv.drawBitmap(b, r.centerX() - side / 2f, r.top + r.height() * 0.06f, bmpPaint)
                    txt.textSize = r.height() * 0.11f
                    cv.drawText(p.title.take(6), r.centerX(), r.bottom - r.height() * 0.06f, txt)
                    cv.drawRoundRect(r, 14f, 14f, box)
                } else {
                    val bk = back
                    if (bk != null) cv.drawBitmap(bk, null, r, bmpPaint) else cv.drawRoundRect(r, 14f, 14f, box)
                }
            }
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP) return true
            for (i in deck.indices) if (cellRect(i).contains(e.x, e.y)) { tap(i); return true }
            if (up.size == 2) tap(-1) // 빈 곳 눌러도 덮기
            return true
        }
    }
}
