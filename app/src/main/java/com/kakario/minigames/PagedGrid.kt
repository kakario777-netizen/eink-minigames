package com.kakario.minigames

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kakario.minigames.eink.EinkDevice

/**
 * 이잉크용 페이지 격자 — 스크롤 없이 화면 높이에 딱 맞는 줄 수만큼 칸을 놓고, 아래 ◀ ▶ 로 페이지를 넘긴다.
 * aspect = 칸 높이 / 칸 너비 (최소 비율, 남는 높이는 칸에 고르게 나눔).
 */
class PagedGrid(
    c: Context,
    private val count: Int,
    private val columns: Int,
    private val aspect: Float,
    private val tile: (index: Int, w: Int, h: Int) -> View,
    private val onClick: (Int) -> Unit,
    focus: Int = 0,
) : LinearLayout(c) {
    private var pendingIndex = focus
    var page = 0
        private set
    private var perPage = columns
    private val area = object : FrameLayout(c) {
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            if (w > 0 && h > 0) post { build() }
        }
    }
    private val pageText: TextView
    private val nav: LinearLayout

    init {
        orientation = VERTICAL
        addView(area, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        nav = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, Ui.dp(c, 6f), 0, 0) }
        nav.addView(Ui.button(c, "◀ 이전", 18f) { go(page - 1) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        pageText = Ui.label(c, "", 18f, bold = true).apply { gravity = Gravity.CENTER }
        nav.addView(pageText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(Ui.button(c, "다음 ▶", 18f) { go(page + 1) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(nav)
    }

    val perPageCount get() = perPage
    val pages get() = maxOf(1, (count + perPage - 1) / perPage)

    fun go(p: Int) {
        if (p !in 0 until pages || p == page) return
        page = p; build()
        EinkDevice.fullRefresh(this)
    }

    /** 이 칸이 있는 페이지로 */

    private fun build() {
        val w = area.width; val h = area.height
        if (w <= 0 || h <= 0) return
        val tw = w / columns
        val rows = maxOf(1, (h / (tw * aspect)).toInt())
        perPage = rows * columns
        if (pendingIndex >= 0) { page = pendingIndex / perPage; pendingIndex = -1 }
        if (page >= pages) page = pages - 1
        val th = h / rows
        val grid = GridLayout(context).apply { columnCount = columns }
        val start = page * perPage
        for (i in start until minOf(count, start + perPage)) {
            val v = tile(i, tw, th)
            v.isClickable = true
            v.setOnClickListener { onClick(i) }
            grid.addView(v, GridLayout.LayoutParams().apply { width = tw; height = th })
        }
        area.removeAllViews()
        area.addView(grid)
        pageText.text = "${page + 1} / $pages"
        nav.visibility = if (pages > 1) View.VISIBLE else View.GONE
        setBackgroundColor(Color.WHITE)
    }
}
