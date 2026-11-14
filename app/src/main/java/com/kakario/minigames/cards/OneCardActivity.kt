package com.kakario.minigames.cards

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

/**
 * 원카드. 같은 무늬나 같은 숫자를 낸다. 먼저 다 털면 승리, 카드가 16장이 되면 파산.
 * 공격: 2(+2) · A(+3) · ♠A(+5) · 흑백 조커(+5) · 컬러 조커(+7) — 같거나 센 공격 카드로 막거나, 못 막으면 쌓인 만큼 먹는다.
 * 특수: 7 무늬 바꾸기 · J 다음 사람 건너뛰기 · Q 방향 바꾸기 · K 한 번 더.
 */
class OneCardActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("onecard", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private lateinit var art: CardArt
    private val main = Handler(Looper.getMainLooper())

    private var n = 2
    private var hands = List(2) { mutableListOf<PCard>() }
    private var deck = mutableListOf<PCard>()
    private val pile = mutableListOf<PCard>()
    private var suit = 0              // 지금 내야 하는 무늬 (7로 바뀔 수 있음)
    private var free = false          // 조커 다음: 아무거나
    private var attack = 0            // 쌓인 공격 장수
    private var attackPower = 0       // 지금 공격의 세기
    private var cur = 0; private var dir = 1
    private var over = false
    private val out = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        art = CardArt(this)
        n = prefs.getInt("n", 2)
        s = GameScreen(this, "원카드")
        board = Board(this); s.board(board)
        s.row("뽑기 / 먹기" to { if (cur == 0 && !over) drawTurn(0) }, "새 게임" to { newGame() }, "인원" to { pickN() }, "기록" to { records() })
        s.show()
        newGame()
    }

    private fun pickN() {
        AlertDialog.Builder(this).setItems(arrayOf("나 + 컴퓨터 1명", "나 + 컴퓨터 2명", "나 + 컴퓨터 3명")) { _, w -> n = w + 2; prefs.edit().putInt("n", n).apply(); newGame() }.show()
    }
    private fun records() {
        AlertDialog.Builder(this).setTitle("기록").setMessage("이긴 판 ${prefs.getInt("win", 0)} / ${prefs.getInt("played", 0)}판").setPositiveButton("확인", null).show()
    }

    private fun name(p: Int) = if (p == 0) "나" else "컴퓨터$p"

    private fun newGame() {
        deck = PCard.deck(jokers = true).also { it.shuffle() }
        hands = List(n) { mutableListOf<PCard>() }
        repeat(7) { for (p in 0 until n) hands[p] += deck.removeAt(deck.lastIndex) }
        pile.clear()
        var first = deck.removeAt(deck.lastIndex)
        while (power(first) > 0 || first.joker) { deck.add(0, first); first = deck.removeAt(deck.lastIndex) }
        pile += first; suit = first.suit; free = false; attack = 0; attackPower = 0
        cur = 0; dir = 1; over = false; out.clear()
        hands[0].sortWith(compareBy({ it.suit }, { it.rank }))
        s.title.text = "  원카드 · ${n}명"
        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).apply()
        s.status.text = "내 차례"; board.invalidate(); s.refresh()
    }

    /** 공격 세기 (0 = 공격 아님) */
    private fun power(c: PCard) = when {
        c.joker && c.rank == 1 -> 7
        c.joker -> 5
        c.rank == 1 && c.suit == 0 -> 5
        c.rank == 1 -> 3
        c.rank == 2 -> 2
        else -> 0
    }

    private fun canPlay(c: PCard): Boolean {
        val top = pile.last()
        if (attack > 0) {
            val p = power(c); if (p == 0 || p < attackPower) return false
            return c.joker || top.joker || c.suit == suit || c.rank == top.rank
        }
        if (c.joker || free) return true
        return c.suit == suit || (!top.joker && c.rank == top.rank)
    }

    private fun refill() {
        if (deck.isNotEmpty()) return
        val top = pile.removeAt(pile.lastIndex)
        deck = pile.toMutableList().also { it.shuffle() }; pile.clear(); pile += top
    }

    private fun draw(p: Int, k: Int) { repeat(k) { refill(); if (deck.isNotEmpty()) hands[p] += deck.removeAt(deck.lastIndex) } }

    private fun next(skip: Int = 1) {
        var c = cur
        repeat(skip) { do { c = (c + dir + n) % n } while (c in out) }
        cur = c
    }

    private fun play(p: Int, c: PCard, chosenSuit: Int = -1) {
        hands[p].remove(c); pile += c
        free = c.joker
        if (!c.joker) suit = c.suit
        val pw = power(c)
        if (pw > 0) { attack += pw; attackPower = pw }
        var msg = "${name(p)}: ${c.label}"
        var skip = 1; var again = false
        when {
            c.joker -> {}
            c.rank == 7 -> { suit = if (chosenSuit >= 0) chosenSuit else c.suit; msg += " → ${"♠♥♦♣"[suit]}로" }
            c.rank == 11 -> { skip = 2; msg += " (건너뛰기)" }
            c.rank == 12 -> { if (n > 2) dir = -dir else skip = 2; msg += " (방향 바꾸기)" }
            c.rank == 13 -> { again = true; msg += " (한 번 더)" }
        }
        if (hands[p].size == 1) msg += " — 원카드!"
        s.status.text = msg
        if (hands[p].isEmpty()) { finish(p); return }
        if (!again) next(skip)
        board.invalidate(); s.tick(10)
        continueTurn()
    }

    private fun drawTurn(p: Int) {
        val k = if (attack > 0) attack else 1
        draw(p, k)
        s.status.text = "${name(p)}: ${k}장 ${if (attack > 0) "먹음" else "뽑음"}"
        attack = 0; attackPower = 0; free = false
        if (p == 0) hands[0].sortWith(compareBy({ it.suit }, { it.rank }))
        if (hands[p].size >= 16) {
            out += p; s.status.text = "${name(p)} 파산! (16장)"
            for (c in hands[p]) deck.add(0, c); hands[p].clear()
            if (p == 0 || out.size >= n - 1) { finish((0 until n).first { it !in out }); return }
        }
        next(); board.invalidate(); s.tick(10)
        continueTurn()
    }

    private fun continueTurn() {
        if (over) return
        if (cur != 0) main.postDelayed({ aiTurn(cur) }, 700)
        else if (attack > 0 && hands[0].none { canPlay(it) }) s.status.text = "${s.status.text}  ·  막을 카드가 없어요 — 먹기를 눌러요"
    }

    private fun aiTurn(p: Int) {
        if (over || cur != p) return
        val opts = hands[p].filter { canPlay(it) }
        if (opts.isEmpty()) { drawTurn(p); return }
        // 다음 사람이 거의 다 털었으면 공격 우선, 아니면 일반 카드부터 아낀다
        val nextHand = hands[(p + dir + n) % n].size
        val c = if (nextHand <= 2) opts.maxByOrNull { power(it) * 10 + it.rank }!! else opts.minByOrNull { power(it) * 10 + (if (it.joker) 50 else 0) }!!
        val chosen = if (c.rank == 7 && !c.joker) hands[p].filter { it != c && !it.joker }.groupBy { it.suit }.maxByOrNull { it.value.size }?.key ?: c.suit else -1
        play(p, c, chosen)
    }

    private fun tapCard(c: PCard) {
        if (over || cur != 0) return
        if (!canPlay(c)) { s.status.text = "그 카드는 낼 수 없어요"; return }
        if (c.rank == 7 && !c.joker) {
            AlertDialog.Builder(this).setTitle("바꿀 무늬").setItems(arrayOf("♠ 스페이드", "♥ 하트", "♦ 다이아", "♣ 클로버")) { _, w -> play(0, c, w) }.show()
        } else play(0, c)
    }

    private fun finish(winner: Int) {
        over = true
        if (winner == 0) prefs.edit().putInt("win", prefs.getInt("win", 0) + 1).apply()
        board.invalidate(); s.refresh()
        AlertDialog.Builder(this).setTitle(if (winner == 0) "이겼어요!" else "${name(winner)} 승리")
            .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; isFakeBoldText = true }
        private val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 6f }
        private val handRects = ArrayList<Pair<RectF, PCard>>()

        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val cw = min(width / 6.5f, height / 9f); val ch = cw * 1.4f
            txt.textSize = cw * 0.22f
            // 상대들
            var x = width * 0.04f
            for (p in 1 until n) {
                val back = art.get("back", (cw * 0.55f).toInt())
                cv.drawBitmap(back, x, height * 0.03f, null)
                val tag = name(p) + " ${hands[p].size}장" + (if (p in out) " (파산)" else "") + (if (cur == p && !over) " ◀" else "")
                cv.drawText(tag, x + back.width + 10, height * 0.03f + back.height * 0.6f, txt)
                x += width / (n - 1).toFloat().coerceAtLeast(1f) * 0.98f
            }
            // 가운데: 덱 · 낸 카드
            val cy = height * 0.2f
            cv.drawBitmap(art.get("back", cw.toInt()), width / 2f - cw * 1.2f, cy, null)
            cv.drawBitmap(art.get(pile.last().asset, cw.toInt()), width / 2f + cw * 0.2f, cy, null)
            txt.textSize = cw * 0.24f
            val info = buildString {
                append("무늬: ${"♠♥♦♣"[suit.coerceIn(0, 3)]}")
                if (free) append("  (아무거나)")
                if (attack > 0) append("   공격 ${attack}장!")
                append("   ${if (dir == 1) "→" else "←"}")
            }
            cv.drawText(info, width / 2f - txt.measureText(info) / 2, cy + ch + cw * 0.4f, txt)
            // 내 패 (여러 줄)
            handRects.clear()
            val hand = hands[0]
            val perRow = 8; val hw = min(width / (perRow + 0.6f), cw * 1.1f); val hh = hw * 1.4f
            val rowsN = (hand.size + perRow - 1) / perRow
            for ((i, card) in hand.withIndex()) {
                val r = i / perRow; val k = i % perRow
                val inRow = min(perRow, hand.size - r * perRow)
                val left = (width - inRow * hw) / 2 + k * hw
                val top = height - (rowsN - r) * (hh + 8) - 8
                cv.drawBitmap(art.get(card.asset, (hw - 6).toInt()), left + 3, top, null)
                val rect = RectF(left + 3, top, left + hw - 3, top + hh)
                if (cur == 0 && !over && canPlay(card)) cv.drawRoundRect(RectF(rect.left - 2, rect.top - 2, rect.right + 2, rect.bottom + 2), 12f, 12f, hl)
                handRects += rect to card
            }
            txt.textSize = cw * 0.22f
            val mine = (if (cur == 0 && !over) "◀ 내 차례 · " else "") + "내 패 ${hand.size}장 (굵은 테두리 = 낼 수 있는 카드)"
            cv.drawText(mine, width / 2f - txt.measureText(mine) / 2, height - rowsN * (hh + 8) - 24, txt)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP) return true
            for ((r, c) in handRects.reversed()) if (r.contains(e.x, e.y)) { tapCard(c); return true }
            return true
        }
    }
}
