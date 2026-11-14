package com.kakario.minigames.puzzle

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kakario.minigames.Ui

/** 퍼즐 게임 공통: 난이도 탭 + 판 목록 화면. */
object PuzzleList {
    val LEVELS = listOf("easy" to "쉬움", "medium" to "중간", "hard" to "어려움", "challenge" to "챌린지")

    fun build(
        a: Activity,
        title: String,
        level: String,
        count: Int,
        cleared: Int,
        columns: Int,
        onLevel: (String) -> Unit,
        tile: (index: Int, size: Int) -> View,
        onTile: (Int) -> Unit,
        onBack: () -> Unit,
    ): View {
        val pad = Ui.dp(a, 10f)
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(pad, pad, pad, pad) }
        val top = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(Ui.button(a, "←", 18f) { onBack() })
        top.addView(Ui.label(a, "  $title", 24f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Ui.label(a, "완료 $cleared / $count", 16f))
        col.addView(top)

        val tabs = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, pad, 0, pad) }
        for ((id, name) in LEVELS) {
            val b = Ui.button(a, if (id == level) "[ $name ]" else name, 17f) { onLevel(id) }
            if (id == level) { b.setBackgroundColor(Color.BLACK); b.setTextColor(Color.WHITE) }
            tabs.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                val m = Ui.dp(a, 3f); setMargins(m, 0, m, 0)
            })
        }
        col.addView(tabs)

        val width = a.resources.displayMetrics.widthPixels - pad * 2
        val size = width / columns
        val grid = GridLayout(a).apply { columnCount = columns }
        for (i in 0 until count) {
            val t = tile(i, size)
            t.isClickable = true
            t.setOnClickListener { onTile(i) }
            grid.addView(t, GridLayout.LayoutParams().apply { width = size; height = size })
        }
        col.addView(ScrollView(a).apply { addView(grid) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return col
    }
}
