package com.kakario.minigames

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.kakario.minigames.eink.EinkDevice

/** 간단한 게임 화면 틀: 위(← 제목 상태) · 가운데 판 · 아래 버튼 줄들. */
class GameScreen(val a: Activity, title: String) {
    val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
    private val pad = Ui.dp(a, 8f)
    val title: TextView = Ui.label(a, "  $title", 22f, bold = true)
    val status: TextView = Ui.label(a, "", 18f, bold = true)
    private var changes = 0

    init {
        val top = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(pad, pad, pad, pad) }
        top.addView(Ui.button(a, "←", 18f) { a.finish() })
        top.addView(this.title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(status)
        root.addView(top)
    }

    fun board(v: View) = root.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

    fun row(vararg buttons: Pair<String, () -> Unit>): LinearLayout {
        val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, pad / 2, pad, pad / 2) }
        for ((t, f) in buttons) r.addView(Ui.button(a, t, 17f, f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = Ui.dp(a, 3f); setMargins(m, 0, m, 0)
        })
        root.addView(r)
        return r
    }

    /** 켜진 버튼(검게) 표시. */
    fun setOn(t: TextView, on: Boolean) {
        if (on) { t.setBackgroundColor(Color.BLACK); t.setTextColor(Color.WHITE) }
        else { t.background = Ui.button(a, "") {}.background; t.setTextColor(Color.BLACK) }
    }

    fun show() { a.setContentView(root); refresh() }
    fun refresh() { root.post { EinkDevice.applyMode(root); EinkDevice.fullRefresh(root) } }
    /** 움직일 때마다 부르면 n번마다 전체 새로고침. */
    fun tick(every: Int = 15) { if (++changes % every == 0) root.post { EinkDevice.fullRefresh(root) } }
}
