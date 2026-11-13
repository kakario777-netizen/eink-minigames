package com.kakario.minigames.solitaire

import org.json.JSONArray
import org.json.JSONObject

/** 카드 한 장. suit 0=♠ 1=♥ 2=♦ 3=♣, rank 1(A)..13(K). */
class Card(val suit: Int, val rank: Int, var up: Boolean = false) {
    val red get() = suit == 1 || suit == 2
    val asset: String get() = "SHDC"[suit] + when (rank) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString() }
    fun copy() = Card(suit, rank, up)
    fun toInt() = (suit * 13 + rank - 1) * 2 + if (up) 1 else 0
    companion object { fun of(i: Int) = Card((i / 2) / 13, (i / 2) % 13 + 1, i % 2 == 1) }
}

enum class Pile { STOCK, WASTE, FOUND, TAB }

/** 카드가 있는 자리: 덱, 버린 더미, 올리는 칸(0..3), 바닥 줄(0..6). */
data class Loc(val pile: Pile, val index: Int = 0)

enum class Scoring { STANDARD, VEGAS, NONE }

/** 윈도우 카드놀이(클론다이크) 규칙과 점수 계산. */
class Klondike(
    var drawThree: Boolean = false,
    var scoring: Scoring = Scoring.STANDARD,
    var timed: Boolean = true,
) {
    var stock = mutableListOf<Card>()
    var waste = mutableListOf<Card>()
    var found = List(4) { mutableListOf<Card>() }
    var tab = List(7) { mutableListOf<Card>() }
    /** draw-3일 때 버린 더미에서 펼쳐 보이는 장수. */
    var wasteFan = 0
    var score = 0
    var seconds = 0
    /** 덱을 몇 번째 도는 중인지 (처음 = 1). */
    var pass = 1
    var started = false
    var won = false
    var moves = 0

    private class Snap(val json: String)
    private val undoStack = ArrayDeque<Snap>()
    val canUndo get() = undoStack.isNotEmpty()

    fun deal(vegasCarry: Int? = null) {
        val deck = MutableList(52) { Card(it / 13, it % 13 + 1) }
        deck.shuffle()
        stock.clear(); waste.clear(); found.forEach { it.clear() }; tab.forEach { it.clear() }
        for (i in 0 until 7) for (j in i until 7) {
            val c = deck.removeAt(deck.lastIndex)
            c.up = (i == j)
            tab[j].add(c)
        }
        stock.addAll(deck)
        wasteFan = 0; pass = 1; seconds = 0; started = false; won = false; moves = 0
        score = when (scoring) {
            Scoring.VEGAS -> (vegasCarry ?: 0) - 52
            else -> 0
        }
        undoStack.clear()
    }

    fun pile(l: Loc): MutableList<Card> = when (l.pile) {
        Pile.STOCK -> stock
        Pile.WASTE -> waste
        Pile.FOUND -> found[l.index]
        Pile.TAB -> tab[l.index]
    }

    /** 이번 판에서 덱을 다시 돌릴 수 있는 최대 횟수 (null = 무제한). */
    val maxPasses: Int? get() = if (scoring == Scoring.VEGAS) (if (drawThree) 3 else 1) else null
    val canRecycle get() = waste.isNotEmpty() && (maxPasses == null || pass < maxPasses!!)

    // ───── 이동 규칙 ─────
    fun canPlaceOnFound(c: Card, f: Int): Boolean {
        val p = found[f]
        return if (p.isEmpty()) c.rank == 1 else p.last().suit == c.suit && p.last().rank + 1 == c.rank
    }

    fun canPlaceOnTab(c: Card, t: Int): Boolean {
        val p = tab[t]
        if (p.isEmpty()) return c.rank == 13
        val top = p.last()
        return top.up && top.red != c.red && top.rank == c.rank + 1
    }

    /** from의 index번째 카드부터 위를 집을 수 있는지. */
    fun canPick(from: Loc, index: Int): Boolean {
        val p = pile(from)
        if (index !in p.indices) return false
        return when (from.pile) {
            Pile.STOCK -> false
            Pile.WASTE, Pile.FOUND -> index == p.lastIndex
            Pile.TAB -> p[index].up
        }
    }

    fun canMove(from: Loc, index: Int, to: Loc): Boolean {
        if (from == to || !canPick(from, index)) return false
        val c = pile(from)[index]
        val count = pile(from).size - index
        return when (to.pile) {
            Pile.FOUND -> count == 1 && canPlaceOnFound(c, to.index)
            Pile.TAB -> canPlaceOnTab(c, to.index)
            else -> false
        }
    }

    /** 카드 옮기기. 성공하면 true. */
    fun move(from: Loc, index: Int, to: Loc): Boolean {
        if (!canMove(from, index, to)) return false
        save()
        val src = pile(from)
        val run = src.subList(index, src.size).toList()
        repeat(run.size) { src.removeAt(src.lastIndex) }
        pile(to).addAll(run)
        if (from.pile == Pile.WASTE) wasteFan = (wasteFan - 1).coerceAtLeast(if (waste.isEmpty()) 0 else 1)
        // 점수 (윈도우 표준/베가스 규칙)
        when (scoring) {
            Scoring.STANDARD -> {
                when {
                    from.pile == Pile.WASTE && to.pile == Pile.TAB -> score += 5
                    to.pile == Pile.FOUND && from.pile != Pile.FOUND -> score += 10
                    from.pile == Pile.FOUND && to.pile == Pile.TAB -> score -= 15
                }
            }
            Scoring.VEGAS -> {
                if (to.pile == Pile.FOUND && from.pile != Pile.FOUND) score += 5
                if (from.pile == Pile.FOUND && to.pile != Pile.FOUND) score -= 5
            }
            Scoring.NONE -> {}
        }
        // 바닥 줄의 뒤집힌 카드가 맨 위에 남으면 자동으로 뒤집는다 (+5)
        if (from.pile == Pile.TAB && src.isNotEmpty() && !src.last().up) {
            src.last().up = true
            if (scoring == Scoring.STANDARD) score += 5
        }
        afterAction()
        return true
    }

    /** 덱 누르기: 카드 넘기기 또는 다 넘겼으면 다시 돌리기. */
    fun drawFromStock(): Boolean {
        if (stock.isEmpty()) {
            if (!canRecycle) return false
            save()
            while (waste.isNotEmpty()) stock.add(waste.removeAt(waste.lastIndex).also { it.up = false })
            pass++
            wasteFan = 0
            if (scoring == Scoring.STANDARD) {
                // 윈도우 표준 점수: 한 장 넘기기는 다시 돌릴 때마다 -100, 세 장은 세 바퀴 넘어서부터 -20
                if (!drawThree) score -= 100 else if (pass > 3) score -= 20
            }
            afterAction()
            return true
        }
        save()
        val n = if (drawThree) 3 else 1
        var drawn = 0
        repeat(n) {
            if (stock.isNotEmpty()) { waste.add(stock.removeAt(stock.lastIndex).also { it.up = true }); drawn++ }
        }
        wasteFan = drawn
        afterAction()
        return true
    }

    private fun afterAction() {
        started = true
        moves++
        if (scoring == Scoring.STANDARD && score < 0) score = 0
        if (found.all { it.size == 13 }) {
            won = true
            if (scoring == Scoring.STANDARD && timed && seconds >= 30) score += 700000 / seconds
        }
    }

    /** 1초 지날 때마다 (게임 중일 때만). 표준 점수 + 시간 기록이면 10초마다 -2. */
    fun tick() {
        if (!started || won) return
        seconds++
        if (timed && scoring == Scoring.STANDARD && seconds % 10 == 0) score = (score - 2).coerceAtLeast(0)
    }

    /** 이 카드를 올릴 수 있는 올리는 칸 (없으면 -1). */
    fun foundationFor(c: Card): Int = (0 until 4).firstOrNull { canPlaceOnFound(c, it) } ?: -1

    /** 올릴 수 있는 카드를 전부 올리기 (윈도우의 오른쪽 클릭). 옮긴 장수를 돌려준다. */
    fun autoToFoundation(): Int {
        var count = 0
        while (true) {
            var moved = false
            val sources = listOf(Loc(Pile.WASTE)) + (0 until 7).map { Loc(Pile.TAB, it) }
            for (s in sources) {
                val p = pile(s)
                if (p.isEmpty() || !p.last().up) continue
                val f = foundationFor(p.last())
                if (f >= 0 && move(s, p.lastIndex, Loc(Pile.FOUND, f))) { moved = true; count++; break }
            }
            if (!moved) return count
        }
    }

    // ───── 힌트 ─────
    data class Hint(val from: Loc, val index: Int, val to: Loc?)

    /** 다음에 둘 만한 수 하나 (없으면 덱 넘기기를 권함 = to null, from STOCK). */
    fun hint(): Hint? {
        // 1) 올리는 칸으로
        val tops = listOf(Loc(Pile.WASTE)) + (0 until 7).map { Loc(Pile.TAB, it) }
        for (s in tops) {
            val p = pile(s); if (p.isEmpty() || !p.last().up) continue
            val f = foundationFor(p.last()); if (f >= 0) return Hint(s, p.lastIndex, Loc(Pile.FOUND, f))
        }
        // 2) 바닥 줄끼리 — 뒤집힌 카드를 드러내거나 빈 줄을 만드는 이동
        for (t in 0 until 7) {
            val p = tab[t]; val first = p.indexOfFirst { it.up }; if (first < 0) continue
            if (first == 0 && p[0].rank == 13) continue // 빈 줄의 K를 다른 빈 줄로 옮기는 건 의미 없음
            for (d in 0 until 7) if (d != t && canMove(Loc(Pile.TAB, t), first, Loc(Pile.TAB, d))) return Hint(Loc(Pile.TAB, t), first, Loc(Pile.TAB, d))
        }
        // 3) 버린 더미 → 바닥 줄
        if (waste.isNotEmpty()) for (d in 0 until 7) if (canMove(Loc(Pile.WASTE), waste.lastIndex, Loc(Pile.TAB, d))) return Hint(Loc(Pile.WASTE), waste.lastIndex, Loc(Pile.TAB, d))
        // 4) 덱 넘기기
        if (stock.isNotEmpty() || canRecycle) return Hint(Loc(Pile.STOCK), 0, null)
        return null
    }

    // ───── 되돌리기·저장 ─────
    private fun save() {
        undoStack.addLast(Snap(toJson(withUndo = false).toString()))
        if (undoStack.size > 300) undoStack.removeFirst()
    }

    fun undo(): Boolean {
        val s = undoStack.removeLastOrNull() ?: return false
        val keepSeconds = seconds
        load(JSONObject(s.json), withUndo = false)
        seconds = keepSeconds
        // 윈도우처럼 되돌리기는 점수도 되돌린다 (단, 표준 점수에선 되돌리기 자체로 -2 없음)
        return true
    }

    fun toJson(withUndo: Boolean = true): JSONObject = JSONObject().apply {
        fun arr(l: List<Card>) = JSONArray().apply { l.forEach { put(it.toInt()) } }
        put("stock", arr(stock)); put("waste", arr(waste))
        put("found", JSONArray().apply { found.forEach { put(arr(it)) } })
        put("tab", JSONArray().apply { tab.forEach { put(arr(it)) } })
        put("fan", wasteFan); put("score", score); put("sec", seconds); put("pass", pass)
        put("started", started); put("won", won); put("moves", moves)
        put("draw3", drawThree); put("scoring", scoring.name); put("timed", timed)
        if (withUndo) put("undo", JSONArray().apply { undoStack.forEach { put(it.json) } })
    }

    fun load(o: JSONObject, withUndo: Boolean = true) {
        fun arr(a: JSONArray) = MutableList(a.length()) { Card.of(a.getInt(it)) }
        stock = arr(o.getJSONArray("stock")); waste = arr(o.getJSONArray("waste"))
        val f = o.getJSONArray("found"); found = List(4) { arr(f.getJSONArray(it)) }
        val t = o.getJSONArray("tab"); tab = List(7) { arr(t.getJSONArray(it)) }
        wasteFan = o.optInt("fan"); score = o.optInt("score"); seconds = o.optInt("sec"); pass = o.optInt("pass", 1)
        started = o.optBoolean("started"); won = o.optBoolean("won"); moves = o.optInt("moves")
        drawThree = o.optBoolean("draw3"); scoring = runCatching { Scoring.valueOf(o.optString("scoring")) }.getOrDefault(Scoring.STANDARD)
        timed = o.optBoolean("timed", true)
        if (withUndo) {
            undoStack.clear()
            o.optJSONArray("undo")?.let { a -> for (i in 0 until a.length()) undoStack.addLast(Snap(a.getString(i))) }
        }
    }
}
