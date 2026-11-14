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
 * 훌라. 7장씩 받고, 차례마다 ① 덱에서 1장 뽑거나 버린 패를 가져와(땡큐 — 이번 차례에 꼭 써야 함)
 * ② 같은 숫자 3장 이상 / 같은 무늬 연속 3장 이상 / 7 한 장을 '등록', 바닥 패에 '붙이기' ③ 1장 버린다.
 * 패를 다 털면 승리 (한 번도 등록 안 하고 한꺼번에 털면 '훌라!'). 덱이 떨어지면 남은 점수가 적은 사람이 이긴다.
 */
class HulaActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hula", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private lateinit var art: CardArt
    private val main = Handler(Looper.getMainLooper())

    private var n = 3
    private var hands = List(3) { mutableListOf<PCard>() }
    private var deck = mutableListOf<PCard>()
    private val discard = mutableListOf<PCard>()
    private val melds = mutableListOf<Pair<Int, MutableList<PCard>>>() // (주인, 카드들)
    private var cur = 0
    private var drew = false
    private var mustUse: PCard? = null
    private var registered = BooleanArray(3)
    private var registeredThisTurn = false
    private val selected = mutableSetOf<PCard>()
    private var over = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        art = CardArt(this)
        n = prefs.getInt("n", 3)
        s = GameScreen(this, "훌라")
        board = Board(this); s.board(board)
        s.row("덱에서 뽑기" to { myDraw(false) }, "버린 패 가져오기" to { myDraw(true) }, "등록" to { myRegister() }, "버리기" to { myDiscard() })
        s.row("새 게임" to { newGame() }, "인원" to { pickN() }, "규칙" to { rules() }, "기록" to { records() })
        s.show()
        newGame()
    }

    private fun name(p: Int) = if (p == 0) "나" else "컴퓨터$p"
    private fun points(c: PCard) = if (c.rank >= 10) 10 else c.rank
    private fun handPoints(p: Int) = hands[p].sumOf { points(it) }

    private fun pickN() { AlertDialog.Builder(this).setItems(arrayOf("나 + 컴퓨터 1명", "나 + 컴퓨터 2명", "나 + 컴퓨터 3명")) { _, w -> n = w + 2; prefs.edit().putInt("n", n).apply(); newGame() }.show() }
    private fun records() { AlertDialog.Builder(this).setTitle("기록").setMessage("이긴 판 ${prefs.getInt("win", 0)} / ${prefs.getInt("played", 0)}판\n훌라 ${prefs.getInt("hula", 0)}번").setPositiveButton("확인", null).show() }
    private fun rules() {
        AlertDialog.Builder(this).setTitle("훌라 규칙").setMessage(
            "· 덱에서 1장 뽑거나, 버린 패 맨 위를 가져와요(가져온 패는 이번 차례에 등록·붙이기에 꼭 써야 해요)\n" +
            "· 카드를 눌러 고른 뒤 '등록': 같은 숫자 3~4장, 같은 무늬 연속 3장 이상, 또는 7 한 장\n" +
            "· 카드를 고르고 바닥의 패 묶음을 누르면 '붙이기'\n· 마지막에 1장을 골라 '버리기'\n" +
            "· 다 털면 승리, 한 번에 다 털면 훌라!\n· 덱이 떨어지면 남은 점수(A=1, J·Q·K=10)가 적은 사람이 승리").setPositiveButton("확인", null).show()
    }

    private fun newGame() {
        deck = PCard.deck().also { it.shuffle() }
        hands = List(n) { mutableListOf<PCard>() }
        repeat(7) { for (p in 0 until n) hands[p] += deck.removeAt(deck.lastIndex) }
        discard.clear(); discard += deck.removeAt(deck.lastIndex)
        melds.clear(); registered = BooleanArray(n); selected.clear()
        cur = 0; drew = false; mustUse = null; registeredThisTurn = false; over = false
        sortHand()
        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).apply()
        s.title.text = "  훌라 · ${n}명"; s.status.text = "내 차례 — 먼저 뽑아요"
        board.invalidate(); s.refresh()
    }

    private fun sortHand() = hands[0].sortWith(compareBy({ it.suit }, { it.rank }))

    // ───── 규칙 ─────
    private fun isSet(c: List<PCard>) = c.size >= 3 && c.all { it.rank == c[0].rank }
    private fun isRun(c: List<PCard>): Boolean {
        if (c.size < 3 || c.any { it.suit != c[0].suit }) return false
        val r = c.map { it.rank }.sorted()
        return r.zipWithNext().all { (a, b) -> b == a + 1 }
    }
    private fun validMeld(c: List<PCard>) = isSet(c) || isRun(c) || (c.size == 1 && c[0].rank == 7)
    private fun canAttach(m: List<PCard>, add: List<PCard>): Boolean {
        val all = m + add
        if (m.size == 1 && m[0].rank == 7) return isSet(all) || isRun(all)
        return isSet(all) || isRun(all)
    }

    private fun drawFrom(p: Int, fromDiscard: Boolean) {
        if (fromDiscard) { val c = discard.removeAt(discard.lastIndex); hands[p] += c; mustUse = c }
        else { hands[p] += deck.removeAt(deck.lastIndex); mustUse = null }
        drew = true
    }

    private fun register(p: Int, cards: List<PCard>) {
        hands[p].removeAll(cards)
        melds += p to cards.sortedWith(compareBy({ it.rank }, { it.suit })).toMutableList()
        if (mustUse in cards) mustUse = null
        registeredThisTurn = true
    }

    private fun attach(p: Int, meld: Int, cards: List<PCard>) {
        hands[p].removeAll(cards)
        val m = melds[meld].second; m.addAll(cards); m.sortWith(compareBy({ it.rank }, { it.suit }))
        if (mustUse in cards) mustUse = null
    }

    private fun endTurnAfterDiscard(p: Int, c: PCard?) {
        if (c != null) { hands[p].remove(c); discard += c }
        if (registeredThisTurn) registered[p] = true
        if (hands[p].isEmpty()) { win(p); return }
        if (deck.isEmpty()) { deckOut(); return }
        cur = (cur + 1) % n; drew = false; mustUse = null; registeredThisTurn = false; selected.clear()
        board.invalidate(); s.tick(10)
        if (cur != 0) main.postDelayed({ aiTurn(cur) }, 700) else s.status.text = "${s.status.text}  ·  내 차례 — 먼저 뽑아요"
    }

    private fun win(p: Int) {
        over = true
        val hula = !registered[p] && registeredThisTurn
        if (p == 0) { prefs.edit().putInt("win", prefs.getInt("win", 0) + 1).apply(); if (hula) prefs.edit().putInt("hula", prefs.getInt("hula", 0) + 1).apply() }
        board.invalidate(); s.refresh()
        val others = (0 until n).filter { it != p }.joinToString("\n") { "${name(it)}: ${handPoints(it)}점 남음" }
        AlertDialog.Builder(this).setTitle((if (hula) "훌라! " else "") + "${name(p)} 승리").setMessage(others)
            .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
    }

    private fun deckOut() {
        over = true
        val w = (0 until n).minByOrNull { handPoints(it) }!!
        if (w == 0) prefs.edit().putInt("win", prefs.getInt("win", 0) + 1).apply()
        board.invalidate(); s.refresh()
        AlertDialog.Builder(this).setTitle("덱이 떨어졌어요 — ${name(w)} 승리").setMessage((0 until n).joinToString("\n") { "${name(it)}: ${handPoints(it)}점" })
            .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
    }

    // ───── 내 차례 ─────
    private fun myTurn() = !over && cur == 0

    private fun myDraw(fromDiscard: Boolean) {
        if (!myTurn()) return
        if (drew) { s.status.text = "이미 뽑았어요"; return }
        if (fromDiscard && discard.isEmpty()) return
        drawFrom(0, fromDiscard); sortHand(); selected.clear()
        s.status.text = if (fromDiscard) "가져온 ${mustUse?.label}는 이번에 꼭 써야 해요" else "등록·붙이기 후 1장 버려요"
        board.invalidate()
    }

    private fun myRegister() {
        if (!myTurn()) return
        if (!drew) { s.status.text = "먼저 뽑아요"; return }
        val sel = selected.toList()
        if (!validMeld(sel)) { s.status.text = "등록할 수 없는 조합이에요"; return }
        register(0, sel); selected.clear()
        s.status.text = "등록했어요"
        if (hands[0].isEmpty()) { endTurnAfterDiscard(0, null); return }
        board.invalidate()
    }

    private fun myAttach(meld: Int) {
        if (!myTurn() || !drew) return
        val sel = selected.toList(); if (sel.isEmpty()) return
        if (!canAttach(melds[meld].second, sel)) { s.status.text = "그 묶음에는 붙일 수 없어요"; return }
        attach(0, meld, sel); selected.clear()
        s.status.text = "붙였어요"
        if (hands[0].isEmpty()) { endTurnAfterDiscard(0, null); return }
        board.invalidate()
    }

    private fun myDiscard() {
        if (!myTurn()) return
        if (!drew) { s.status.text = "먼저 뽑아요"; return }
        if (mustUse != null) { s.status.text = "가져온 ${mustUse?.label}를 먼저 써야 해요"; return }
        if (selected.size != 1) { s.status.text = "버릴 카드 1장을 골라요"; return }
        val c = selected.first()
        s.status.text = "나: ${c.label} 버림"
        endTurnAfterDiscard(0, c)
    }

    // ───── 컴퓨터 ─────
    private fun findMelds(h: List<PCard>): List<List<PCard>> {
        val out = ArrayList<List<PCard>>()
        val used = HashSet<PCard>()
        for ((_, g) in h.groupBy { it.rank }) if (g.size >= 3) { out += g; used += g }
        for ((_, g) in h.filter { it !in used }.groupBy { it.suit }) {
            val sorted = g.sortedBy { it.rank }; var run = mutableListOf<PCard>()
            for (c in sorted) {
                if (run.isEmpty() || c.rank == run.last().rank + 1) run.add(c) else { if (run.size >= 3) out += run.toList(); run = mutableListOf(c) }
            }
            if (run.size >= 3) out += run.toList()
        }
        for (c in h) if (c.rank == 7 && out.none { c in it }) out += listOf(c)
        return out
    }

    private fun aiTurn(p: Int) {
        if (over || cur != p) return
        val top = discard.lastOrNull()
        val take = top != null && (findMelds(hands[p] + top).any { top in it } || melds.any { canAttach(it.second, listOf(top)) })
        drawFrom(p, take)
        var log = "${name(p)}: " + if (take) "${top!!.label} 가져옴" else "뽑음"
        // 다 털 수 있으면 털고, 아니면 등록 가능한 것 등록
        for (m in findMelds(hands[p])) if (m.all { it in hands[p] }) { register(p, m); log += " · 등록 ${m.joinToString(" ") { it.label }}" }
        var again = true
        while (again) {
            again = false
            for (c in hands[p].toList()) for (i in melds.indices) if (c in hands[p] && canAttach(melds[i].second, listOf(c))) { attach(p, i, listOf(c)); log += " · 붙임 ${c.label}"; again = true }
        }
        val mu = mustUse
        if (mu != null && mu in hands[p]) { mustUse = null } // 못 썼으면(드묾) 그냥 진행
        s.status.text = log
        if (hands[p].isEmpty()) { endTurnAfterDiscard(p, null); return }
        // 버릴 카드: 짝이 될 만한 게 적고 점수 높은 카드
        val h = hands[p]
        fun keep(c: PCard) = h.count { it != c && (it.rank == c.rank || (it.suit == c.suit && kotlin.math.abs(it.rank - c.rank) <= 2)) } * 10 + (if (c.rank == 7) 30 else 0)
        val d = h.maxByOrNull { points(it) - keep(it) }!!
        s.status.text = "$log · ${d.label} 버림"
        endTurnAfterDiscard(p, d)
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; isFakeBoldText = true }
        private val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 7f }
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        private val handRects = ArrayList<Pair<RectF, PCard>>()
        private val meldRects = ArrayList<Pair<RectF, Int>>()

        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val cw = min(width / 8f, height / 11f)
            txt.textSize = cw * 0.24f
            // 상대 + 덱 + 버린 패
            var y = cw * 0.3f
            val opp = (1 until n).joinToString("   ") { "${name(it)} ${hands[it].size}장" + (if (cur == it && !over) " ◀" else "") }
            cv.drawText(opp, cw * 0.3f, y + txt.textSize, txt)
            y += txt.textSize * 1.8f
            cv.drawBitmap(art.get("back", cw.toInt()), cw * 0.3f, y, null)
            cv.drawText("덱 ${deck.size}", cw * 0.3f, y + cw * 1.4f + txt.textSize * 1.2f, txt)
            discard.lastOrNull()?.let { cv.drawBitmap(art.get(it.asset, cw.toInt()), cw * 1.6f, y, null) }
            cv.drawText("버린 패", cw * 1.6f, y + cw * 1.4f + txt.textSize * 1.2f, txt)
            // 바닥 묶음들 (오른쪽부터 여러 줄)
            meldRects.clear()
            val mw = cw * 0.62f; val mh = mw * 1.4f; val step = mw * 0.42f
            var mx = cw * 3.0f; var my = y
            for ((i, m) in melds.withIndex()) {
                val wdt = mw + step * (m.second.size - 1)
                if (mx + wdt > width - cw * 0.2f) { mx = cw * 3.0f; my += mh + txt.textSize * 1.4f }
                for ((k, cd) in m.second.withIndex()) cv.drawBitmap(art.get(cd.asset, mw.toInt()), mx + k * step, my, null)
                val r = RectF(mx - 4, my - 4, mx + wdt + 4, my + mh + 4)
                cv.drawRoundRect(r, 10f, 10f, box)
                meldRects += r to i
                mx += wdt + cw * 0.3f
            }
            // 내 패
            handRects.clear()
            val hand = hands[0]
            val perRow = 8; val hw = min(width / (perRow + 0.6f), cw * 1.15f); val hh = hw * 1.4f
            val rowsN = (hand.size + perRow - 1) / perRow
            for ((i, card) in hand.withIndex()) {
                val r = i / perRow; val k = i % perRow
                val inRow = min(perRow, hand.size - r * perRow)
                val left = (width - inRow * hw) / 2 + k * hw
                val top = height - (rowsN - r) * (hh + 10) - (if (card in selected) hh * 0.18f else 0f)
                cv.drawBitmap(art.get(card.asset, (hw - 6).toInt()), left + 3, top, null)
                val rect = RectF(left + 3, top, left + hw - 3, top + hh)
                if (card in selected) cv.drawRoundRect(rect, 12f, 12f, hl)
                if (card == mustUse) cv.drawText("가져옴", left + 6, top - 6, txt)
                handRects += rect to card
            }
            val mine = (if (myTurn()) "◀ 내 차례 · " else "") + "내 패 ${hand.size}장 · ${handPoints(0)}점  (눌러서 고르기, 바닥 묶음을 누르면 붙이기)"
            cv.drawText(mine, cw * 0.3f, height - rowsN * (hh + 10) - hh * 0.25f, txt)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP || !myTurn()) return true
            for ((r, c) in handRects.reversed()) if (r.contains(e.x, e.y)) {
                if (c in selected) selected.remove(c) else selected.add(c)
                invalidate(); return true
            }
            for ((r, i) in meldRects) if (r.contains(e.x, e.y)) { myAttach(i); return true }
            return true
        }
    }
}
