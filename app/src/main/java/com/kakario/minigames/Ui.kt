package com.kakario.minigames

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.TextView

/** 이잉크용 공통 화면 도구: 흰 바탕 + 검은 선만. */
object Ui {
    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    private fun box(c: Context, fill: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(c, 2f), Color.BLACK)
        cornerRadius = dp(c, 8f).toFloat()
    }

    /** 테두리만 있는 버튼 (눌렀을 때만 검게). */
    fun button(c: Context, text: String, size: Float = 17f, onClick: () -> Unit): TextView =
        TextView(c).apply {
            this.text = text
            textSize = size
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val p = dp(c, 10f)
            setPadding(p, dp(c, 8f), p, dp(c, 8f))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), box(c, Color.LTGRAY))
                addState(intArrayOf(), box(c, Color.WHITE))
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun label(c: Context, text: String, size: Float = 17f, bold: Boolean = false): TextView =
        TextView(c).apply {
            this.text = text
            textSize = size
            setTextColor(Color.BLACK)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
}
