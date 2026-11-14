package com.kakario.minigames.chess

/** 체스 규칙 (캐슬링·앙파상·승진 포함). 칸 0=a8 … 63=h1. 말 = 색*8 + 종류(1 폰 2 나이트 3 비숍 4 룩 5 퀸 6 킹), 색 0 백 1 흑. */
class Chess {
    companion object {
        const val P = 1; const val N = 2; const val B = 3; const val R = 4; const val Q = 5; const val K = 6
        val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 0)
        private val KN = arrayOf(intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2), intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1))
        private val KG = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1))
        private val ROOK = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        private val BISH = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
        fun type(v: Int) = v and 7
        fun color(v: Int) = v shr 3
    }

    var b = IntArray(64)
    var turn = 0
    var castle = 15   // 1 백 킹쪽, 2 백 퀸쪽, 4 흑 킹쪽, 8 흑 퀸쪽
    var ep = -1
    /** 수 = from | to<<6 | promo<<12 */
    class Undo(val move: Int, val b: IntArray, val castle: Int, val ep: Int)
    val history = ArrayList<Undo>()

    fun reset() {
        val back = intArrayOf(R, N, B, Q, K, B, N, R)
        b = IntArray(64)
        for (x in 0..7) { b[x] = 8 + back[x]; b[8 + x] = 8 + P; b[48 + x] = P; b[56 + x] = back[x] }
        turn = 0; castle = 15; ep = -1; history.clear()
    }

    private fun on(x: Int, y: Int) = x in 0..7 && y in 0..7

    fun attacked(sq: Int, by: Int): Boolean {
        val x = sq % 8; val y = sq / 8
        // 폰
        val py = if (by == 0) y + 1 else y - 1
        for (dx in intArrayOf(-1, 1)) if (on(x + dx, py) && b[py * 8 + x + dx] == by * 8 + P) return true
        for (d in KN) if (on(x + d[0], y + d[1]) && b[(y + d[1]) * 8 + x + d[0]] == by * 8 + N) return true
        for (d in KG) if (on(x + d[0], y + d[1]) && b[(y + d[1]) * 8 + x + d[0]] == by * 8 + K) return true
        for ((dirs, t1) in listOf(ROOK to R, BISH to B)) for (d in dirs) {
            var nx = x + d[0]; var ny = y + d[1]
            while (on(nx, ny)) {
                val v = b[ny * 8 + nx]
                if (v != 0) { if (color(v) == by && (type(v) == t1 || type(v) == Q)) return true; break }
                nx += d[0]; ny += d[1]
            }
        }
        return false
    }

    fun kingSq(c: Int) = b.indexOf(c * 8 + K)
    fun inCheck(c: Int): Boolean { val k = kingSq(c); return k < 0 || attacked(k, 1 - c) }

    fun pseudo(c: Int): MutableList<Int> {
        val out = ArrayList<Int>(48)
        for (sq in 0 until 64) {
            val v = b[sq]; if (v == 0 || color(v) != c) continue
            val x = sq % 8; val y = sq / 8
            fun add(to: Int, promo: Int = 0) { out += sq or (to shl 6) or (promo shl 12) }
            fun addP(to: Int) { if (to / 8 == 0 || to / 8 == 7) for (pr in intArrayOf(Q, N, R, B)) add(to, pr) else add(to) }
            when (type(v)) {
                P -> {
                    val dir = if (c == 0) -1 else 1; val ny = y + dir
                    if (on(x, ny) && b[ny * 8 + x] == 0) {
                        addP(ny * 8 + x)
                        val start = if (c == 0) 6 else 1
                        if (y == start && b[(y + 2 * dir) * 8 + x] == 0) add((y + 2 * dir) * 8 + x)
                    }
                    for (dx in intArrayOf(-1, 1)) if (on(x + dx, ny)) {
                        val t = ny * 8 + x + dx
                        if ((b[t] != 0 && color(b[t]) != c) || t == ep) addP(t)
                    }
                }
                N, K -> {
                    for (d in if (type(v) == N) KN else KG) if (on(x + d[0], y + d[1])) {
                        val t = (y + d[1]) * 8 + x + d[0]; if (b[t] == 0 || color(b[t]) != c) add(t)
                    }
                    if (type(v) == K) {
                        val row = if (c == 0) 56 else 0
                        if (sq == row + 4 && !attacked(sq, 1 - c)) {
                            if (castle and (if (c == 0) 1 else 4) != 0 && b[row + 5] == 0 && b[row + 6] == 0 && b[row + 7] == c * 8 + R && !attacked(row + 5, 1 - c) && !attacked(row + 6, 1 - c)) add(row + 6)
                            if (castle and (if (c == 0) 2 else 8) != 0 && b[row + 3] == 0 && b[row + 2] == 0 && b[row + 1] == 0 && b[row] == c * 8 + R && !attacked(row + 3, 1 - c) && !attacked(row + 2, 1 - c)) add(row + 2)
                        }
                    }
                }
                else -> {
                    val dirs = when (type(v)) { B -> BISH; R -> ROOK; else -> ROOK + BISH }
                    for (d in dirs) {
                        var nx = x + d[0]; var ny = y + d[1]
                        while (on(nx, ny)) {
                            val t = ny * 8 + nx
                            if (b[t] == 0) add(t) else { if (color(b[t]) != c) add(t); break }
                            nx += d[0]; ny += d[1]
                        }
                    }
                }
            }
        }
        return out
    }

    fun make(m: Int) {
        val from = m and 63; val to = (m shr 6) and 63; val promo = m shr 12
        history += Undo(m, b.copyOf(), castle, ep)
        val v = b[from]; val c = color(v)
        if (type(v) == P && to == ep) b[to + (if (c == 0) 8 else -8)] = 0
        if (type(v) == K && kotlin.math.abs(to - from) == 2) {
            if (to > from) { b[from + 1] = b[from + 3]; b[from + 3] = 0 } else { b[from - 1] = b[from - 4]; b[from - 4] = 0 }
        }
        b[to] = if (promo != 0) c * 8 + promo else v; b[from] = 0
        ep = if (type(v) == P && kotlin.math.abs(to - from) == 16) (from + to) / 2 else -1
        for (sq in intArrayOf(from, to)) when (sq) { 63 -> castle = castle and 1.inv(); 56 -> castle = castle and 2.inv(); 7 -> castle = castle and 4.inv(); 0 -> castle = castle and 8.inv(); 60 -> castle = castle and 3.inv(); 4 -> castle = castle and 12.inv() }
        turn = 1 - turn
    }

    fun unmake() { val u = history.removeAt(history.lastIndex); b = u.b; castle = u.castle; ep = u.ep; turn = 1 - turn }

    fun legal(c: Int): List<Int> = pseudo(c).filter { m -> make(m); val ok = !inCheck(c); unmake(); ok }

    // ───── 컴퓨터 ─────
    private fun pst(t: Int, sq: Int, c: Int): Int {
        val x = sq % 8; val y = if (c == 0) sq / 8 else 7 - sq / 8
        val center = 3 - (kotlin.math.abs(3.5f - x) + kotlin.math.abs(3.5f - y)).toInt() / 2
        return when (t) { P -> (6 - y) * 8 + if (x in 2..5) 4 else 0; N, B -> center * 6; Q -> center * 2; K -> if (y == 7) 10 else -10; else -> 0 }
    }

    fun eval(c: Int): Int {
        var s = 0
        for (sq in 0 until 64) { val v = b[sq]; if (v == 0) continue; val w = VAL[type(v)] + pst(type(v), sq, color(v)); s += if (color(v) == c) w else -w }
        return s
    }

    private fun search(c: Int, depth: Int, alphaIn: Int, beta: Int): Int {
        if (depth == 0) return quiesce(c, alphaIn, beta, 2)
        var alpha = alphaIn
        val moves = pseudo(c)
        moves.sortByDescending { val t = b[(it shr 6) and 63]; if (t == 0) 0 else VAL[type(t)] * 10 - VAL[type(b[it and 63])] }
        var any = false; var best = -100000
        for (m in moves) {
            make(m)
            if (inCheck(c)) { unmake(); continue }
            any = true
            val v = -search(1 - c, depth - 1, -beta, -alpha)
            unmake()
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        if (!any) return if (inCheck(c)) -90000 - depth else 0
        return best
    }

    private fun quiesce(c: Int, alphaIn: Int, beta: Int, d: Int): Int {
        val stand = eval(c)
        if (d == 0 || stand >= beta) return stand
        var alpha = maxOf(alphaIn, stand)
        for (m in pseudo(c)) {
            if (b[(m shr 6) and 63] == 0) continue
            make(m)
            if (inCheck(c)) { unmake(); continue }
            val v = -quiesce(1 - c, -beta, -alpha, d - 1)
            unmake()
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    fun best(c: Int, depth: Int, noise: Int): Int {
        val moves = legal(c).shuffled(); if (moves.isEmpty()) return -1
        var bm = moves.first(); var bv = Int.MIN_VALUE
        for (m in moves) {
            make(m)
            val v = -search(1 - c, depth - 1, -100000, 100000) + if (noise > 0) (0..noise).random() else 0
            unmake()
            if (v > bv) { bv = v; bm = m }
        }
        return bm
    }
}
