package com.kakario.minigames.janggi

/**
 * 장기 규칙. 판 9×10 (x 0..8, y 0..9). 한(1)은 위(y 0..3), 초(0)는 아래(y 6..9). 초가 먼저 둔다.
 * 말: K 궁, A 사, E 상, H 마, R 차, C 포, P 졸·병.
 */
class Janggi {
    companion object {
        const val K = 1; const val A = 2; const val E = 3; const val H = 4; const val R = 5; const val C = 6; const val P = 7
        const val CHO = 0; const val HAN = 1
        val VALUE = intArrayOf(0, 0, 3, 3, 5, 13, 7, 2)
        /** 상차림: 왼쪽부터 (1번, 2번, 6번, 7번 자리) */
        val SETUPS = listOf("마상상마" to intArrayOf(H, E, E, H), "상마마상" to intArrayOf(E, H, H, E), "마상마상" to intArrayOf(H, E, H, E), "상마상마" to intArrayOf(E, H, E, H))
        fun idx(x: Int, y: Int) = y * 9 + x
    }

    /** 칸마다: 0 빈칸, 아니면 side*8 + type */
    var b = IntArray(90)
    var turn = CHO
    class Move(val from: Int, val to: Int, val captured: Int) { val pass get() = from < 0 }
    val history = ArrayList<Move>()

    fun type(v: Int) = v and 7
    fun side(v: Int) = v shr 3

    fun setup(cho: IntArray, han: IntArray) {
        b = IntArray(90); history.clear(); turn = CHO
        fun put(x: Int, y: Int, s: Int, t: Int) { b[idx(x, y)] = s * 8 + t }
        for ((s, back, kingY, cy, py, sl) in listOf(Six(CHO, 9, 8, 7, 6, cho), Six(HAN, 0, 1, 2, 3, han))) {
            put(0, back, s, R); put(8, back, s, R); put(3, back, s, A); put(5, back, s, A); put(4, kingY, s, K)
            put(1, cy, s, C); put(7, cy, s, C)
            for (x in intArrayOf(0, 2, 4, 6, 8)) put(x, py, s, P)
            // 한은 마주 보고 앉아 있으므로 왼쪽·오른쪽이 반대
            val xs = if (s == CHO) intArrayOf(1, 2, 6, 7) else intArrayOf(7, 6, 2, 1)
            for (k in 0 until 4) put(xs[k], back, s, sl[k])
        }
    }
    private data class Six(val s: Int, val back: Int, val kingY: Int, val cy: Int, val py: Int, val sl: IntArray)

    private fun inPalace(x: Int, y: Int) = x in 3..5 && (y in 0..2 || y in 7..9)
    private fun palaceCenter(y: Int) = if (y <= 2) 1 else 8
    /** 궁성 대각선 위의 점인지 (네 귀퉁이와 가운데). */
    private fun onDiag(x: Int, y: Int) = inPalace(x, y) && ((x == 4 && y == palaceCenter(y)) || (x != 4 && y != palaceCenter(y)))

    /** 의사 합법수 (자기 궁이 잡히는지는 따지지 않음). */
    fun pseudo(s: Int, out: MutableList<Int>) {
        for (i in 0 until 90) {
            val v = b[i]; if (v == 0 || side(v) != s) continue
            val x = i % 9; val y = i / 9
            fun add(nx: Int, ny: Int): Boolean { // 놓을 수 있으면 추가, 계속 갈 수 있는지(빈칸) 반환
                if (nx !in 0..8 || ny !in 0..9) return false
                val t = b[idx(nx, ny)]
                if (t == 0) { out += i * 100 + idx(nx, ny); return true }
                if (side(t) != s) out += i * 100 + idx(nx, ny)
                return false
            }
            when (type(v)) {
                K, A -> {
                    for ((dx, dy) in ORTH) { val nx = x + dx; val ny = y + dy; if (inPalace(nx, ny) && palaceCenter(ny) == palaceCenter(y)) add(nx, ny) }
                    if (onDiag(x, y)) for ((dx, dy) in DIAG) { val nx = x + dx; val ny = y + dy; if (inPalace(nx, ny) && onDiag(nx, ny) && palaceCenter(ny) == palaceCenter(y)) add(nx, ny) }
                }
                R -> {
                    for ((dx, dy) in ORTH) { var k = 1; while (add(x + dx * k, y + dy * k)) k++ }
                    if (onDiag(x, y)) for ((dx, dy) in DIAG) {
                        var k = 1
                        while (true) {
                            val nx = x + dx * k; val ny = y + dy * k
                            if (!inPalace(nx, ny) || !onDiag(nx, ny) || palaceCenter(ny) != palaceCenter(y)) break
                            if (!add(nx, ny)) break; k++
                        }
                    }
                }
                C -> {
                    for ((dx, dy) in ORTH) cannonLine(i, s, x, y, dx, dy, false, out)
                    if (onDiag(x, y) && x != 4) for ((dx, dy) in DIAG) cannonLine(i, s, x, y, dx, dy, true, out)
                }
                H -> for ((dx, dy) in ORTH) {
                    if (at(x + dx, y + dy) != 0) continue
                    for (k in intArrayOf(-1, 1)) { val nx = x + dx * 2 + (if (dx == 0) k else 0); val ny = y + dy * 2 + (if (dy == 0) k else 0); add(nx, ny) }
                }
                E -> for ((dx, dy) in ORTH) {
                    if (at(x + dx, y + dy) != 0) continue
                    for (k in intArrayOf(-1, 1)) {
                        val ddx = if (dx == 0) k else dx; val ddy = if (dy == 0) k else dy
                        if (at(x + dx + ddx, y + dy + ddy) != 0) continue
                        add(x + dx + ddx * 2, y + dy + ddy * 2)
                    }
                }
                P -> {
                    val f = if (s == CHO) -1 else 1
                    add(x, y + f); add(x - 1, y); add(x + 1, y)
                    // 상대 궁성 안에서는 앞쪽 대각선으로도
                    if (onDiag(x, y)) for (dx in intArrayOf(-1, 1)) { val nx = x + dx; val ny = y + f; if (inPalace(nx, ny) && onDiag(nx, ny) && palaceCenter(ny) == palaceCenter(y)) add(nx, ny) }
                }
            }
        }
    }

    /** 포: 포가 아닌 말 하나를 넘어서 간다. 포는 포를 넘거나 잡을 수 없다. */
    private fun cannonLine(i: Int, s: Int, x: Int, y: Int, dx: Int, dy: Int, palace: Boolean, out: MutableList<Int>) {
        var k = 1; var jumped = false
        while (true) {
            val nx = x + dx * k; val ny = y + dy * k
            if (nx !in 0..8 || ny !in 0..9) return
            if (palace && (!inPalace(nx, ny) || !onDiag(nx, ny) || palaceCenter(ny) != palaceCenter(y))) return
            val t = b[idx(nx, ny)]
            if (!jumped) {
                if (t != 0) { if (type(t) == C) return; jumped = true }
            } else {
                if (t == 0) out += i * 100 + idx(nx, ny)
                else { if (side(t) != s && type(t) != C) out += i * 100 + idx(nx, ny); return }
            }
            k++
        }
    }

    private fun at(x: Int, y: Int) = if (x in 0..8 && y in 0..9) b[idx(x, y)] else -1

    fun kingPos(s: Int) = (0 until 90).firstOrNull { b[it] == s * 8 + K } ?: -1

    fun attacked(sq: Int, by: Int): Boolean {
        val l = ArrayList<Int>(64); pseudo(by, l)
        return l.any { it % 100 == sq }
    }

    fun inCheck(s: Int): Boolean { val k = kingPos(s); return k < 0 || attacked(k, 1 - s) }

    fun doMove(m: Int): Move {
        val from = m / 100; val to = m % 100
        val mv = Move(from, to, b[to])
        b[to] = b[from]; b[from] = 0
        history += mv; turn = 1 - turn
        return mv
    }

    fun pass() { history += Move(-1, -1, 0); turn = 1 - turn }

    fun undo(): Boolean {
        val mv = history.removeLastOrNull() ?: return false
        if (!mv.pass) { b[mv.from] = b[mv.to]; b[mv.to] = mv.captured }
        turn = 1 - turn
        return true
    }

    /** 진짜 둘 수 있는 수 (두고 나서 내 궁이 잡히지 않는 수). */
    fun legal(s: Int): List<Int> {
        val l = ArrayList<Int>(64); pseudo(s, l)
        return l.filter { m ->
            val from = m / 100; val to = m % 100; val cap = b[to]
            b[to] = b[from]; b[from] = 0
            val ok = !inCheck(s)
            b[from] = b[to]; b[to] = cap
            ok
        }
    }

    // ───── 컴퓨터 ─────
    fun eval(s: Int): Int {
        var sc = 0
        for (i in 0 until 90) {
            val v = b[i]; if (v == 0) continue
            val t = type(v)
            var w = if (t == K) 0 else VALUE[t] * 10
            if (t == P) { val y = i / 9; w += if (side(v) == CHO) (6 - y).coerceAtLeast(0) * 2 else (y - 3).coerceAtLeast(0) * 2 }
            sc += if (side(v) == s) w else -w
        }
        return sc
    }

    private fun search(s: Int, depth: Int, alphaIn: Int, beta: Int): Int {
        if (kingPos(s) < 0) return -100000 - depth
        if (depth == 0) return eval(s)
        var alpha = alphaIn
        val moves = ArrayList<Int>(64); pseudo(s, moves)
        moves.sortByDescending { val c = b[it % 100]; if (c == 0) 0 else if (type(c) == K) 1000 else VALUE[type(c)] }
        var best = -1_000_000
        for (m in moves) {
            val from = m / 100; val to = m % 100; val cap = b[to]
            if (cap != 0 && type(cap) == K) return 100000 + depth
            b[to] = b[from]; b[from] = 0
            val v = -search(1 - s, depth - 1, -beta, -alpha)
            b[from] = b[to]; b[to] = cap
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    fun bestMove(s: Int, depth: Int, noise: Int): Int {
        val moves = legal(s); if (moves.isEmpty()) return -1
        var best = moves.first(); var bestV = Int.MIN_VALUE
        for (m in moves.shuffled()) {
            val from = m / 100; val to = m % 100; val cap = b[to]
            b[to] = b[from]; b[from] = 0
            val v = -search(1 - s, depth - 1, -1_000_000, 1_000_000) + if (noise > 0) (0..noise).random() else 0
            b[from] = b[to]; b[to] = cap
            if (v > bestV) { bestV = v; best = m }
        }
        return best
    }
}

private val ORTH = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val DIAG = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
