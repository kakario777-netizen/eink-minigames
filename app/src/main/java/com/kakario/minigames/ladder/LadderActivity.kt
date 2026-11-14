package com.kakario.minigames.ladder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.kakario.minigames.GameScreen
import com.kakario.minigames.Ui
import kotlin.random.Random

/** 사다리게임. 위 이름을 누르면 그 줄을 따라 내려간 길이 굵게 보인다. '모두 보기'로 전체 결과. */
class LadderActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ladder", Context.MODE_PRIVATE) }
    private lateinit var s: GameScreen
    private lateinit var board: Board
    private var names = mutableListOf<String>()
    private var results = mutableListOf<String>()
    private var rungs = Array(0) { BooleanArray(0) }   // [행][왼쪽 줄 번호]
    private val rows = 14
    private val shown = mutableSetOf<Int>()
    private var hideResults = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        names = prefs.getString("names", "1,2,3,4")!!.split("\n").let { if (it.size > 1) it else prefs.getString("names", "1,2,3,4")!!.split(",") }.toMutableList()
        results = prefs.getString("results", "당첨\n꽝\n꽝\n꽝")!!.split("\n").toMutableList()
        fit()
        s = GameScreen(this, "사다리게임")
        board = Board(this); s.board(board)
        s.row("새 사다리" to { make() }, "모두 보기" to { shown.addAll(names.indices); hideResults = false; board.invalidate() },
            "이름·결과" to { edit() }, "사람 수" to { count() })
        s.show()
        make()
    }

    private fun fit() { while (results.size < names.size) results += "꽝"; while (results.size > names.size) results.removeAt(results.lastIndex) }

    private fun save() = prefs.edit().putString("names", names.joinToString("\n")).putString("results", results.joinToString("\n")).apply()

    private fun make() {
        val n = names.size
        rungs = Array(rows) { BooleanArray(n - 1) }
        for (r in 0 until rows) {
            var c = 0
            while (c < n - 1) { if (Random.nextDouble() < 0.42) { rungs[r][c] = true; c += 2 } else c++ }
        }
        // 이웃한 두 줄 사이에 가로줄이 하나도 없으면 하나 넣는다
        for (c in 0 until n - 1) if ((0 until rows).none { rungs[it][c] }) {
            val r = (0 until rows).firstOrNull { (c == 0 || !rungs[it][c - 1]) && (c == n - 2 || !rungs[it][c + 1]) }
            if (r != null) rungs[r][c] = true
        }
        results.shuffle()
        shown.clear(); hideResults = true
        s.title.text = "  사다리게임 · ${n}명"; s.status.text = ""
        board.invalidate(); s.refresh()
    }

    /** start 줄에서 내려간 길: (행 경계마다의 줄 번호) */
    private fun path(start: Int): IntArray {
        val out = IntArray(rows + 1); var c = start; out[0] = c
        for (r in 0 until rows) {
            if (c < names.size - 1 && rungs[r][c]) c++ else if (c > 0 && rungs[r][c - 1]) c--
            out[r + 1] = c
        }
        return out
    }

    private fun count() {
        val opts = (2..8).map { "${it}명" }.toTypedArray()
        AlertDialog.Builder(this).setItems(opts) { _, w ->
            val n = w + 2
            while (names.size < n) names += "${names.size + 1}"
            while (names.size > n) names.removeAt(names.lastIndex)
            fit(); if (results.none { it != "꽝" }) results[0] = "당첨"
            save(); make()
        }.show()
    }

    private fun edit() {
        // 스크롤 없이 한 화면: 한 줄에 '이름 | 결과' (최대 8줄)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = Ui.dp(this@LadderActivity, 14f); setPadding(p, p, p, p) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(Ui.label(this, "이름 (위)", 16f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.label(this, "결과 (섞여서 놓여요)", 16f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(head)
        val nameEds = ArrayList<EditText>(); val resEds = ArrayList<EditText>()
        for (i in names.indices) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val ne = EditText(this).apply { setText(names[i]); textSize = 17f; inputType = InputType.TYPE_CLASS_TEXT; hint = "이름"; maxLines = 1 }
            val re = EditText(this).apply { setText(results[i]); textSize = 17f; inputType = InputType.TYPE_CLASS_TEXT; hint = "결과"; maxLines = 1 }
            row.addView(ne, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(re, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            col.addView(row); nameEds += ne; resEds += re
        }
        AlertDialog.Builder(this).setTitle("이름·결과").setView(col)
            .setPositiveButton("저장") { _, _ ->
                names = nameEds.mapIndexed { i, e -> e.text.toString().ifBlank { "${i + 1}" } }.toMutableList()
                results = resEds.map { it.text.toString().ifBlank { "꽝" } }.toMutableList()
                save(); make()
            }.setNegativeButton("취소", null).show()
    }

    @SuppressLint("ViewConstructor")
    inner class Board(c: Context) : View(c) {
        private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 3f }
        private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 12f; strokeCap = Paint.Cap.ROUND }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }
        private fun colX(c: Int): Float { val n = names.size; val gap = width / n.toFloat(); return gap * (c + 0.5f) }
        private val top get() = height * 0.1f
        private val bottom get() = height * 0.88f
        private fun rowY(r: Int) = top + (bottom - top) * r / rows

        override fun onDraw(cv: Canvas) {
            cv.drawColor(Color.WHITE)
            val n = names.size; val gap = width / n.toFloat()
            txt.textSize = minOf(gap * 0.3f, height * 0.035f)
            for (c in 0 until n) {
                cv.drawLine(colX(c), top, colX(c), bottom, thin)
                cv.drawRoundRect(colX(c) - gap * 0.45f, top - txt.textSize * 2.1f, colX(c) + gap * 0.45f, top - txt.textSize * 0.4f, 12f, 12f, box)
                cv.drawText(names[c].take(6), colX(c), top - txt.textSize * 0.9f, txt)
            }
            for (r in 0 until rows) for (c in 0 until n - 1) if (rungs[r][c]) {
                val y = (rowY(r) + rowY(r + 1)) / 2
                cv.drawLine(colX(c), y, colX(c + 1), y, thin)
            }
            for (st in shown) {
                val p = path(st)
                for (r in 0 until rows) {
                    val y0 = rowY(r); val y1 = rowY(r + 1); val mid = (y0 + y1) / 2
                    cv.drawLine(colX(p[r]), y0, colX(p[r]), mid, bold)
                    if (p[r + 1] != p[r]) cv.drawLine(colX(p[r]), mid, colX(p[r + 1]), mid, bold)
                    cv.drawLine(colX(p[r + 1]), mid, colX(p[r + 1]), y1, bold)
                }
            }
            val revealed = shown.map { path(it)[rows] }.toSet()
            for (c in 0 until n) {
                val y = bottom + txt.textSize * 1.6f
                val label = if (!hideResults || c in revealed) results[c].take(6) else "?"
                cv.drawText(label, colX(c), y, txt)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP) return true
            if (e.y > top + 40) return true
            val c = (e.x / (width / names.size.toFloat())).toInt().coerceIn(0, names.size - 1)
            shown.clear(); shown.add(c)
            s.status.text = "${names[c]} → ${results[path(c)[rows]]}"
            invalidate(); s.tick(10)
            return true
        }
    }
}
