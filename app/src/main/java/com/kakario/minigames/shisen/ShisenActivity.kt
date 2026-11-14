package com.kakario.minigames.shisen

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
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
 * 사천성. 같은 마작패 두 개를 '꺾임 두 번 이하'의 빈 길로 이을 수 있으면 없앤다.
 * 판은 거꾸로 쌓는 방식으로 만들어 항상 끝까지 풀 수 있다. 섞기도 풀 수 있게 다시 쌓는다.
 */
class ShisenActivity : Activity() {
    data class Level(val name: String, val cols: Int, val rows: Int)
    private val levels = listOf(Level("쉬움", 6, 8), Level("보통", 8, 10), Level("어려움", 10, 12))
    private val prefs by lazy { getSharedPreferences("shisen", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private var lv = 1
    private var W = 8; private var H = 10          // 안쪽 칸 수
    private var g = IntArray(0)                    // (H+2)×(W+2), -1 빈칸
    private var sel = -1
    private var lastPath: List<Int> = emptyList()
    private var hint: Pair<Int, Int>? = null
    private var seconds = 0; private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { if (running) { seconds++; status() }; handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lv = prefs.getInt("lv", 1)
        s = GameScreen(this, "사천성")
        board = Board(this); s.board(board)
        s.row("새 게임" to { newGame() }, "힌트" to { showHint() }, "섞기" to { reshuffle() }, "난이도" to { pickLevel() }, "기록" to { records() })
        s.show()
        newGame()
    }

    override fun onResume() { super.onResume(); handler.postDelayed(ticker, 1000) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    private fun idx(x: Int, y: Int) = y * (W + 2) + x
    private fun status() { s.status.text = "남은 패 ${g.count { it >= 0 }}   ${seconds}초" }

    private fun newGame() {
        W = levels[lv].cols; H = levels[lv].rows
        val kinds = W * H / 4
        val pool = (0 until 34).shuffled().take(kinds)
        val list = pool.flatMap { listOf(it, it, it, it) }
        g = IntArray((W + 2) * (H + 2)) { -1 }
        val cells = (1..H).flatMap { y -> (1..W).map { x -> idx(x, y) } }
        build(cells, list)
        sel = -1; lastPath = emptyList(); hint = null; seconds = 0; running = true
        s.title.text = "  사천성 · ${levels[lv].name}"
        status(); board.invalidate(); s.refresh()
    }

    /** cells 자리에 list 패들을 '거꾸로 쌓기'로 놓는다 → 항상 풀 수 있다. */
    private fun build(cells: List<Int>, list: List<Int>) {
        val pairs = list.groupBy { it }.flatMap { (k, v) -> List(v.size / 2) { k } }
        repeat(200) {
            for (c in cells) g[c] = -1
            val empty = cells.toMutableSet()
            var ok = true
            for (k in pairs.shuffled()) {
                var placed = false
                for (a in empty.shuffled().take(6)) {
                    val reach = empty.filter { it != a && connect(a, it, treatEmpty = empty) != null }
                    if (reach.isNotEmpty()) {
                        val b = reach.random()
                        g[a] = k; g[b] = k; empty.remove(a); empty.remove(b); placed = true; break
                    }
                }
                if (!placed) { ok = false; break }
            }
            if (ok) return
        }
        // 아주 드물게 실패하면 그냥 섞는다
        val sh = list.shuffled(); for ((i, c) in cells.withIndex()) g[c] = sh[i]
    }

    /** a→b 를 꺾임 2번 이하로 잇는 길(꼭짓점 목록). treatEmpty: 아직 안 놓인(빈) 칸으로 볼 칸들. */
    private fun connect(a: Int, b: Int, treatEmpty: Set<Int>? = null): List<Int>? {
        val cw = W + 2
        fun free(i: Int) = i == a || i == b || g[i] < 0 || (treatEmpty != null && i in treatEmpty)
        fun clearLine(p: Int, q: Int): Boolean {
            val px = p % cw; val py = p / cw; val qx = q % cw; val qy = q / cw
            if (px != qx && py != qy) return false
            val dx = Integer.signum(qx - px); val dy = Integer.signum(qy - py)
            var x = px + dx; var y = py + dy
            while (x != qx || y != qy) { if (!free(y * cw + x)) return false; x += dx; y += dy }
            return true
        }
        val ax = a % cw; val ay = a / cw; val bx = b % cw; val by = b / cw
        if (clearLine(a, b)) return listOf(a, b)
        val c1 = by * cw + ax; val c2 = ay * cw + bx
        if (free(c1) && clearLine(a, c1) && clearLine(c1, b)) return listOf(a, c1, b)
        if (free(c2) && clearLine(a, c2) && clearLine(c2, b)) return listOf(a, c2, b)
        // 두 번 꺾임: a에서 가로/세로로 뻗은 빈 칸들 중 b와 한 번 꺾임으로 이어지는 곳
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            var x = ax + dx; var y = ay + dy
            while (x in 0 until cw && y in 0..H + 1) {
                val p = y * cw + x
                if (!free(p) || p == b) break
                val q1 = by * cw + x; val q2 = y * cw + bx
                if (free(q1) && clearLine(p, q1) && clearLine(q1, b) && q1 != a) return listOf(a, p, q1, b)
                if (free(q2) && clearLine(p, q2) && clearLine(q2, b) && q2 != a) return listOf(a, p, q2, b)
                x += dx; y += dy
            }
        }
        return null
    }

    private fun findPair(): Pair<Int, Int>? {
        val tiles = g.indices.filter { g[it] >= 0 }
        for (i in tiles.indices) for (j in i + 1 until tiles.size) {
            val a = tiles[i]; val b = tiles[j]
            if (g[a] == g[b] && connect(a, b) != null) return a to b
        }
        return null
    }

    private fun showHint() { hint = findPair(); if (hint == null) s.status.text = "이을 패가 없어요 — 섞기를 눌러요"; board.invalidate() }

    private fun reshuffle() {
        val cells = g.indices.filter { g[it] >= 0 }
        val list = cells.map { g[it] }
        build(cells, list)
        sel = -1; hint = null; lastPath = emptyList(); seconds += 30
        board.invalidate(); status(); s.refresh()
    }

    private fun pickLevel() {
        AlertDialog.Builder(this).setItems(levels.map { "${it.name} (${it.cols}×${it.rows})" }.toTypedArray()) { _, w -> lv = w; prefs.edit().putInt("lv", w).apply(); newGame() }.show()
    }

    private fun records() {
        val msg = levels.withIndex().joinToString("\n") { (i, l) -> val b = prefs.getInt("best$i", -1); "${l.name}: " + if (b < 0) "기록 없음" else "${b}초" }
        AlertDialog.Builder(this).setTitle("최고 기록").setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun tap(i: Int) {
        hint = null; lastPath = emptyList()
        if (g[i] < 0) { sel = -1; board.invalidate(); return }
        if (sel < 0 || sel == i) { sel = if (sel == i) -1 else i; board.invalidate(); return }
        if (g[sel] == g[i]) {
            val p = connect(sel, i)
            if (p != null) {
                g[sel] = -1; g[i] = -1; lastPath = p; sel = -1
                status(); board.invalidate(); s.tick(14)
                if (g.all { it < 0 }) {
                    running = false
                    val b = prefs.getInt("best$lv", -1)
                    if (b < 0 || seconds < b) prefs.edit().putInt("best$lv", seconds).apply()
                    s.refresh()
                    AlertDialog.Builder(this).setTitle("다 없앴어요!").setMessage("${seconds}초" + if (b < 0 || seconds < b) " — 최고 기록!" else "")
                        .setPositiveButton("새 게임") { _, _ -> newGame() }.setNegativeButton("닫기", null).show()
                } else if (findPair() == null) s.status.text = "이을 패가 없어요 — 섞기를 눌러요"
                return
            }
        }
        sel = i; board.invalidate()
    }

    // ───── 패 그림 ─────
    private val tileCache = HashMap<Int, Bitmap>()
    private var tileW = 0

    private fun tileBitmap(k: Int, w: Int, h: Int): Bitmap {
        if (w != tileW) { tileCache.clear(); tileW = w }
        return tileCache.getOrPut(k) {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
            c.drawColor(Color.WHITE)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = w * 0.06f }
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
            val nums = "一二三四五六七八九"
            when {
                k < 9 -> { // 만수
                    t.textSize = h * 0.36f; c.drawText(nums[k].toString(), w / 2f, h * 0.42f, t)
                    t.textSize = h * 0.36f; c.drawText("萬", w / 2f, h * 0.86f, t)
                }
                k < 18 -> drawPips(c, k - 9 + 1, w, h) { cx, cy, r -> c.drawCircle(cx, cy, r, stroke); c.drawCircle(cx, cy, r * 0.35f, ink) }
                k < 27 -> drawPips(c, k - 18 + 1, w, h) { cx, cy, r ->
                    stroke.strokeWidth = w * 0.09f
                    c.drawLine(cx, cy - r * 1.1f, cx, cy + r * 1.1f, stroke)
                    c.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, Paint(stroke).apply { strokeWidth = w * 0.04f })
                }
                else -> {
                    val ch = "東南西北中發白"[k - 27].toString()
                    if (ch == "白") { stroke.strokeWidth = w * 0.06f; c.drawRect(w * 0.22f, h * 0.2f, w * 0.78f, h * 0.8f, stroke) }
                    else { t.textSize = h * 0.55f; c.drawText(ch, w / 2f, h * 0.7f, t) }
                }
            }
            b
        }
    }

    private fun drawPips(c: Canvas, n: Int, w: Int, h: Int, pip: (Float, Float, Float) -> Unit) {
        val layout: List<Pair<Float, Float>> = when (n) {
            1 -> listOf(0.5f to 0.5f)
            2 -> listOf(0.5f to 0.28f, 0.5f to 0.72f)
            3 -> listOf(0.25f to 0.22f, 0.5f to 0.5f, 0.75f to 0.78f)
            4 -> listOf(0.3f to 0.28f, 0.7f to 0.28f, 0.3f to 0.72f, 0.7f to 0.72f)
            5 -> listOf(0.28f to 0.24f, 0.72f to 0.24f, 0.5f to 0.5f, 0.28f to 0.76f, 0.72f to 0.76f)
            6 -> listOf(0.3f to 0.2f, 0.7f to 0.2f, 0.3f to 0.5f, 0.7f to 0.5f, 0.3f to 0.8f, 0.7f to 0.8f)
            7 -> listOf(0.22f to 0.16f, 0.5f to 0.26f, 0.78f to 0.36f, 0.3f to 0.6f, 0.7f to 0.6f, 0.3f to 0.85f, 0.7f to 0.85f)
            8 -> listOf(0.3f to 0.15f, 0.7f to 0.15f, 0.3f to 0.38f, 0.7f to 0.38f, 0.3f to 0.62f, 0.7f to 0.62f, 0.3f to 0.85f, 0.7f to 0.85f)
            else -> (0 until 9).map { (0.22f + (it % 3) * 0.28f) to (0.18f + (it / 3) * 0.32f) }
        }
        val r = min(w, h) * when { n <= 2 -> 0.2f; n <= 4 -> 0.15f; n <= 6 -> 0.12f; else -> 0.1f }
        for ((x, y) in layout) pip(w * x, h * y, r)
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }
        private val selP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 9f }
        private val pathP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 5f; style = Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f) }
        private fun geo(): Triple<Float, Float, Float> {
            val cw = min(width / (W + 1f), height / ((H + 1f) * 1.3f)); val ch = cw * 1.3f
            return Triple(cw, (width - cw * (W + 2)) / 2, (height - ch * (H + 2)) / 2)
        }
        private fun rect(i: Int): RectF {
            val (cw, ox, oy) = geo(); val ch = cw * 1.3f; val cwid = W + 2
            val x = ox + (i % cwid) * cw; val y = oy + (i / cwid) * ch
            return RectF(x + 2, y + 2, x + cw - 2, y + ch - 2)
        }
        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            if (g.isEmpty()) return
            val (cw, _, _) = geo()
            for (i in g.indices) {
                if (g[i] < 0) continue
                val r = rect(i)
                cv.drawBitmap(tileBitmap(g[i], r.width().toInt().coerceAtLeast(4), r.height().toInt().coerceAtLeast(4)), r.left, r.top, null)
                cv.drawRoundRect(r, 8f, 8f, box)
                if (i == sel) cv.drawRoundRect(RectF(r.left - 4, r.top - 4, r.right + 4, r.bottom + 4), 10f, 10f, selP)
                hint?.let { (a, b) -> if (i == a || i == b) cv.drawRoundRect(RectF(r.left + 6, r.top + 6, r.right - 6, r.bottom - 6), 6f, 6f, pathP) }
            }
            if (lastPath.size >= 2) {
                for (k in 0 until lastPath.size - 1) {
                    val p = rect(lastPath[k]); val q = rect(lastPath[k + 1])
                    cv.drawLine(p.centerX(), p.centerY(), q.centerX(), q.centerY(), pathP)
                }
            }
            if (cw <= 0) return
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP) return true
            for (i in g.indices) if (g[i] >= 0 && rect(i).contains(e.x, e.y)) { tap(i); return true }
            return true
        }
    }
}
