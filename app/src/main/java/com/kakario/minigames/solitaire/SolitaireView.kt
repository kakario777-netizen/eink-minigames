package com.kakario.minigames.solitaire

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 솔리테어 판 그리기 + 터치.
 * 이잉크라서 끌기 대신 '눌러서 집고 → 놓을 곳 누르기'. 같은 카드를 빨리 두 번 누르면 올리는 칸으로 (윈도우의 더블클릭).
 */
@SuppressLint("ViewConstructor")
class SolitaireView(context: Context, var game: Klondike) : View(context) {

    var tapAuto = false
    var onChanged: () -> Unit = {}
    var onWin: () -> Unit = {}

    private data class Sel(val loc: Loc, val index: Int)
    private data class Hit(val loc: Loc, val index: Int)

    private var sel: Sel? = null
    private var hint: Klondike.Hint? = null

    // 카드 그림 (원본 → 지금 크기로 줄인 것)
    private val raw = HashMap<String, Bitmap>()
    private val scaled = HashMap<String, Bitmap>()
    private var scaledW = 0

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK }

    // 배치
    private var pad = 0f; private var gap = 0f; private var cw = 0f; private var ch = 0f
    private var topY = 0f; private var tabY = 0f

    private fun bitmap(name: String): Bitmap {
        val w = cw.toInt().coerceAtLeast(1)
        if (scaledW != w) { scaled.values.forEach { it.recycle() }; scaled.clear(); scaledW = w }
        return scaled.getOrPut(name) {
            val r = raw.getOrPut(name) { context.assets.open("cards/$name.png").use { BitmapFactory.decodeStream(it) } }
            Bitmap.createScaledBitmap(r, w, (r.height * w.toFloat() / r.width).toInt(), true)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pad = w * 0.012f
        val unit = (w - pad * 2) / (7f + 6f * 0.07f)
        gap = unit * 0.07f
        cw = unit
        ch = cw * 1.4f
        // 화면이 넓고 낮으면(가로) 높이에 맞춰 줄인다
        if (ch * 3.2f > h) { ch = h / 3.2f; cw = ch / 1.4f; gap = (w - pad * 2 - cw * 7) / 6f }
        topY = pad
        tabY = topY + ch + max(pad * 2, ch * 0.12f)
        selPaint.strokeWidth = max(4f, cw * 0.05f)
        hintPaint.strokeWidth = max(4f, cw * 0.05f)
        hintPaint.pathEffect = DashPathEffect(floatArrayOf(cw * 0.08f, cw * 0.06f), 0f)
        linePaint.strokeWidth = max(3f, cw * 0.03f)
    }

    private fun colX(i: Int) = pad + i * (cw + gap)

    /** 바닥 줄 카드들의 y 위치 (넘치면 간격을 줄여 화면 안에 맞춘다). */
    private fun tabYs(t: Int): FloatArray {
        val p = game.tab[t]
        val n = p.size
        if (n == 0) return FloatArray(0)
        var down = ch * 0.11f; var up = ch * 0.27f
        val downs = p.dropLast(1).count { !it.up }; val ups = (n - 1) - downs
        val avail = height - tabY - pad - ch
        val need = downs * down + ups * up
        if (need > avail && need > 0) {
            if (ups > 0) up = max(ch * 0.10f, (avail - downs * down) / ups)
            if (downs * down + ups * up > avail) { down = ch * 0.05f; if (ups > 0) up = max(ch * 0.08f, (avail - downs * down) / ups) }
        }
        val ys = FloatArray(n); var y = tabY
        for (i in 0 until n) { ys[i] = y; y += if (p[i].up) up else down }
        return ys
    }

    private fun wasteVisible(): Int = if (game.drawThree) min(game.wasteFan.coerceAtLeast(1), min(3, game.waste.size)) else min(1, game.waste.size)

    private fun wasteX(k: Int) = colX(1) + k * cw * 0.30f

    private fun cardRect(l: Loc, index: Int): RectF? {
        return when (l.pile) {
            Pile.STOCK -> RectF(colX(0), topY, colX(0) + cw, topY + ch)
            Pile.WASTE -> {
                val v = wasteVisible(); if (v == 0) return null
                val k = index - (game.waste.size - v); if (k < 0) return null
                RectF(wasteX(k), topY, wasteX(k) + cw, topY + ch)
            }
            Pile.FOUND -> RectF(colX(3 + l.index), topY, colX(3 + l.index) + cw, topY + ch)
            Pile.TAB -> {
                val ys = tabYs(l.index); val x = colX(l.index)
                if (index in ys.indices) RectF(x, ys[index], x + cw, ys[index] + ch) else RectF(x, tabY, x + cw, tabY + ch)
            }
        }
    }

    // ───── 그리기 ─────
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        if (cw <= 0f) return
        // 덱
        val sx = colX(0)
        if (game.stock.isNotEmpty()) {
            if (game.stock.size > 1) canvas.drawBitmap(bitmap("back"), sx + cw * 0.03f, topY + cw * 0.03f, bmpPaint)
            canvas.drawBitmap(bitmap("back"), sx, topY, bmpPaint)
        } else {
            canvas.drawBitmap(bitmap("slot"), sx, topY, bmpPaint)
            val cx = sx + cw / 2; val cy = topY + ch / 2; val r = cw * 0.22f
            if (game.canRecycle) canvas.drawCircle(cx, cy, r, linePaint)
            else { canvas.drawLine(cx - r, cy - r, cx + r, cy + r, linePaint); canvas.drawLine(cx - r, cy + r, cx + r, cy - r, linePaint) }
        }
        // 버린 더미
        val v = wasteVisible()
        if (game.waste.size > v) canvas.drawBitmap(bitmap(game.waste[game.waste.size - v - 1].asset), wasteX(0), topY, bmpPaint)
        for (k in 0 until v) canvas.drawBitmap(bitmap(game.waste[game.waste.size - v + k].asset), wasteX(k), topY, bmpPaint)
        // 올리는 칸
        for (f in 0 until 4) {
            val x = colX(3 + f); val p = game.found[f]
            canvas.drawBitmap(bitmap(if (p.isEmpty()) "slot" else p.last().asset), x, topY, bmpPaint)
            if (p.isEmpty()) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = cw * 0.4f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
                canvas.drawText("A", x + cw / 2, topY + ch / 2 + tp.textSize * 0.35f, tp)
            }
        }
        // 바닥 줄
        for (t in 0 until 7) {
            val p = game.tab[t]; val x = colX(t)
            if (p.isEmpty()) { canvas.drawBitmap(bitmap("slot"), x, tabY, bmpPaint); continue }
            val ys = tabYs(t)
            for (i in p.indices) canvas.drawBitmap(bitmap(if (p[i].up) p[i].asset else "back"), x, ys[i], bmpPaint)
        }
        // 고른 카드 표시 (집은 카드 묶음 전체를 굵은 테두리로)
        sel?.let { s -> runRect(s.loc, s.index)?.let { canvas.drawRoundRect(it, cw * 0.08f, cw * 0.08f, selPaint) } }
        // 힌트 (점선 테두리)
        hint?.let { h ->
            runRect(h.from, h.index)?.let { canvas.drawRoundRect(it, cw * 0.08f, cw * 0.08f, hintPaint) }
            h.to?.let { to -> cardRect(to, game.pile(to).lastIndex)?.let { canvas.drawRoundRect(inset(it, -cw * 0.06f), cw * 0.1f, cw * 0.1f, hintPaint) } }
        }
    }

    private fun inset(r: RectF, d: Float) = RectF(r.left + d, r.top + d, r.right - d, r.bottom - d)

    /** 집은 카드 묶음의 테두리 영역. */
    private fun runRect(l: Loc, index: Int): RectF? {
        val first = cardRect(l, index) ?: return null
        val last = cardRect(l, game.pile(l).lastIndex) ?: first
        val d = selPaint.strokeWidth / 2
        return RectF(first.left - d, first.top - d, last.right + d, last.bottom + d)
    }

    // ───── 터치 ─────
    private var downX = 0f; private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var lastTap: Hit? = null; private var lastTapAt = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            MotionEvent.ACTION_UP -> if (abs(e.x - downX) < slop && abs(e.y - downY) < slop) hitTest(e.x, e.y)?.let { tap(it) } ?: run {
                if (sel != null || hint != null) { sel = null; hint = null; invalidate() }
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Hit? {
        if (y in topY..topY + ch) {
            if (x in colX(0)..colX(0) + cw) return Hit(Loc(Pile.STOCK), 0)
            val v = wasteVisible()
            if (v > 0 && x in wasteX(0)..wasteX(v - 1) + cw) return Hit(Loc(Pile.WASTE), if (x >= wasteX(v - 1)) game.waste.lastIndex else -1)
            for (f in 0 until 4) if (x in colX(3 + f)..colX(3 + f) + cw) return Hit(Loc(Pile.FOUND, f), game.found[f].lastIndex)
            return null
        }
        if (y >= tabY) for (t in 0 until 7) {
            val x0 = colX(t) - gap / 2
            if (x in x0..x0 + cw + gap) {
                val ys = tabYs(t)
                for (i in ys.indices.reversed()) if (y >= ys[i] && y <= ys[i] + ch) return Hit(Loc(Pile.TAB, t), i)
                return Hit(Loc(Pile.TAB, t), -1)
            }
        }
        return null
    }

    private fun tap(h: Hit) {
        val now = SystemClock.uptimeMillis()
        val dbl = lastTap == h && now - lastTapAt < 450
        lastTap = h; lastTapAt = now
        hint = null
        val s = sel
        if (h.loc.pile == Pile.STOCK) {
            sel = null
            if (game.drawFromStock()) changed() else invalidate()
            return
        }
        if (s != null) {
            if (h.loc == s.loc) {
                if (dbl && s.index == game.pile(s.loc).lastIndex && toFoundation(s.loc)) return
                sel = if (h.index >= 0 && h.index != s.index && game.canPick(h.loc, h.index)) Sel(h.loc, h.index) else null
                invalidate(); return
            }
            if (game.move(s.loc, s.index, h.loc)) { sel = null; changed(); return }
            sel = if (h.index >= 0 && game.canPick(h.loc, h.index)) Sel(h.loc, h.index) else null
            invalidate(); return
        }
        if (h.index < 0 || !game.canPick(h.loc, h.index)) { invalidate(); return }
        if (tapAuto && autoMove(h.loc, h.index)) return
        sel = Sel(h.loc, h.index)
        invalidate()
    }

    private fun toFoundation(l: Loc): Boolean {
        val p = game.pile(l); if (p.isEmpty()) return false
        val f = game.foundationFor(p.last())
        if (f >= 0 && l.pile != Pile.FOUND && game.move(l, p.lastIndex, Loc(Pile.FOUND, f))) { sel = null; changed(); return true }
        return false
    }

    /** '누르면 자동 이동': 올리는 칸 → 카드가 있는 줄 → 빈 줄 순서로 갈 수 있는 곳에 옮긴다. */
    private fun autoMove(l: Loc, index: Int): Boolean {
        if (index == game.pile(l).lastIndex && l.pile != Pile.FOUND && toFoundation(l)) return true
        val order = (0 until 7).sortedBy { if (game.tab[it].isEmpty()) 1 else 0 }
        for (t in order) {
            val to = Loc(Pile.TAB, t)
            if (to != l && game.move(l, index, to)) { sel = null; changed(); return true }
        }
        return false
    }

    fun showHint() {
        sel = null
        hint = game.hint()
        invalidate()
    }

    fun clearMarks() { sel = null; hint = null }

    private fun changed() {
        invalidate()
        onChanged()
        if (game.won) onWin()
    }
}
