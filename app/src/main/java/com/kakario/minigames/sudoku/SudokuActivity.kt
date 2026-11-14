package com.kakario.minigames.sudoku

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.kakario.minigames.Ui
import com.kakario.minigames.eink.EinkDevice
import com.kakario.minigames.puzzle.PuzzleList
import org.json.JSONObject

/**
 * 스도쿠 — 모든 판은 답이 하나뿐인지 검사했고, 풀 때 필요한 기법으로 난이도를 나눴다.
 *  쉬움: 빈칸 하나 남은 칸만 / 중간: 숨은 한 자리까지 / 어려움: 쌍·줄 가두기 필요 / 챌린지: 세 짝·X-윙 이상 필요
 */
class SudokuActivity : Activity() {

    class Puzzle(val given: IntArray, val solution: IntArray)

    companion object {
        private var cache: Map<String, List<Puzzle>>? = null
        fun load(c: Context): Map<String, List<Puzzle>> = cache ?: run {
            val o = JSONObject(c.assets.open("sudoku.json").bufferedReader().use { it.readText() })
            PuzzleList.LEVELS.associate { (id, _) ->
                val a = o.getJSONArray(id)
                id to List(a.length()) { i ->
                    val s = a.getString(i)
                    Puzzle(IntArray(81) { s[it] - '0' }, IntArray(81) { s[81 + it] - '0' })
                }
            }.also { cache = it }
        }
    }

    private val prefs by lazy { getSharedPreferences("sudoku", Context.MODE_PRIVATE) }
    private lateinit var all: Map<String, List<Puzzle>>
    private var level = "easy"
    private var current = -1
    private var board: SudokuView? = null
    private var seconds = 0
    private var changes = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timeText: TextView? = null
    private val ticker = object : Runnable {
        override fun run() {
            val b = board
            if (b != null && !b.locked) { seconds++; timeText?.text = fmt(seconds) }
            handler.postDelayed(this, 1000)
        }
    }

    private fun fmt(s: Int) = "%d:%02d".format(s / 60, s % 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        all = load(this)
        level = prefs.getString("lv", "easy")!!
        showList()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(ticker, 1000)
        window.decorView.post { EinkDevice.applyMode(window.decorView); EinkDevice.fullRefresh(window.decorView) }
    }

    override fun onPause() { handler.removeCallbacks(ticker); saveProgress(); super.onPause() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (current >= 0) { saveProgress(); showList() } else super.onBackPressed()
    }

    private fun key(i: Int) = "${level}_$i"
    private fun refresh() { window.decorView.post { EinkDevice.fullRefresh(window.decorView) } }

    private fun showList() {
        current = -1; board = null; timeText = null
        val list = all[level]!!
        val cleared = list.indices.count { prefs.contains("c_" + key(it)) }
        setContentView(PuzzleList.build(this, "스도쿠", level, list.size, cleared, 5,
            onLevel = { level = it; prefs.edit().putString("lv", it).apply(); showList() },
            tile = { i, _ -> tile(i) },
            onTile = { open(it) },
            onBack = { finish() }))
        refresh()
    }

    private fun tile(i: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply { setStroke(2, Color.BLACK); setColor(Color.WHITE) }
        }
        val done = prefs.getInt("c_" + key(i), -1)
        box.addView(Ui.label(this, "${i + 1}", 22f, bold = true).apply { gravity = Gravity.CENTER })
        val sub = when {
            done >= 0 -> "완료 ${fmt(done)}"
            prefs.contains("p_" + key(i)) -> "진행 중"
            else -> ""
        }
        if (sub.isNotEmpty()) box.addView(Ui.label(this, sub, 13f).apply { gravity = Gravity.CENTER })
        if (done >= 0) { box.setBackgroundColor(Color.BLACK); (box.getChildAt(0) as TextView).setTextColor(Color.WHITE); (box.getChildAt(1) as TextView).setTextColor(Color.WHITE) }
        return box
    }

    private fun open(i: Int) {
        current = i
        val p = all[level]!![i]
        val values = IntArray(81); val notes = IntArray(81)
        seconds = 0
        prefs.getString("p_" + key(i), null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                val v = o.getString("v"); for (k in 0 until 81) values[k] = v[k] - '0'
                val n = o.getJSONArray("n"); for (k in 0 until 81) notes[k] = n.getInt(k)
                seconds = o.optInt("t")
            }
        }
        val cleared = prefs.getInt("c_" + key(i), -1)
        if (cleared >= 0) { for (k in 0 until 81) values[k] = if (p.given[k] == 0) p.solution[k] else 0; seconds = cleared }

        val v = SudokuView(this, p.given, values, notes)
        board = v
        val undo = ArrayDeque<Triple<Int, Int, Int>>() // 칸, 이전 값, 이전 메모
        var noteMode = false

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val pad = Ui.dp(this, 8f)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(pad, pad, pad, pad) }
        top.addView(Ui.button(this, "← 목록", 16f) { saveProgress(); showList() })
        val levelName = PuzzleList.LEVELS.first { it.first == level }.second
        val title = Ui.label(this, "  $levelName ${i + 1}", 22f, bold = true)
        top.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        timeText = Ui.label(this, fmt(seconds), 20f, bold = true)
        top.addView(timeText)
        root.addView(top)
        root.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        fun lp() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { val m = Ui.dp(this@SudokuActivity, 3f); setMargins(m, m, m, m) }
        val pad1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, 0, pad, 0) }
        val pad2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, 0, pad, pad) }
        lateinit var bNote: TextView

        fun finishIfDone() {
            if ((0 until 81).all { v.value(it) == p.solution[it] }) {
                v.locked = true; v.selected = -1
                prefs.edit().putInt("c_" + key(i), seconds).remove("p_" + key(i)).apply()
                title.text = "  완성! $levelName ${i + 1}"
                pad1.removeAllViews(); pad2.removeAllViews()
                pad2.addView(Ui.button(this, "목록", 18f) { showList() }, lp())
                if (i + 1 < all[level]!!.size) pad2.addView(Ui.button(this, "다음 판", 18f) { open(i + 1) }, lp())
                v.invalidate(); refresh()
            }
        }
        fun changed() {
            v.invalidate(); changes++
            if (changes % 20 == 0) refresh()
            finishIfDone()
        }
        fun input(n: Int) {
            val s = v.selected
            if (s < 0 || p.given[s] != 0 || v.locked) return
            undo.addLast(Triple(s, values[s], notes[s]))
            if (noteMode && n > 0) {
                if (values[s] == 0) notes[s] = notes[s] xor (1 shl n)
            } else {
                values[s] = if (values[s] == n) 0 else n
                if (n > 0) {
                    // 같은 줄·칸·상자의 메모에서 이 숫자를 지운다
                    val r = s / 9; val c = s % 9
                    for (j in 0 until 81) if ((j / 9 == r || j % 9 == c || (j / 27 == s / 27 && j % 9 / 3 == c / 3)) && notes[j] and (1 shl n) != 0) {
                        undo.addLast(Triple(j, values[j], notes[j])); notes[j] = notes[j] and (1 shl n).inv()
                    }
                } else notes[s] = 0
            }
            changed()
        }
        for (n in 1..9) pad1.addView(Ui.button(this, n.toString(), 24f) { input(n) }, lp())
        bNote = Ui.button(this, "메모 끔", 17f) {
            noteMode = !noteMode
            bNote.text = if (noteMode) "메모 켬" else "메모 끔"
            if (noteMode) { bNote.setBackgroundColor(Color.BLACK); bNote.setTextColor(Color.WHITE) }
            else { bNote.background = Ui.button(this, "") {}.background; bNote.setTextColor(Color.BLACK) }
        }
        pad2.addView(bNote, lp())
        pad2.addView(Ui.button(this, "지우기", 17f) { input(0) }, lp())
        pad2.addView(Ui.button(this, "되돌리기", 17f) {
            undo.removeLastOrNull()?.let { (s, ov, on) -> values[s] = ov; notes[s] = on; v.invalidate() }
        }, lp())
        pad2.addView(Ui.button(this, "힌트", 17f) {
            val s = v.selected.takeIf { it >= 0 && p.given[it] == 0 && values[it] != p.solution[it] }
                ?: (0 until 81).firstOrNull { p.given[it] == 0 && values[it] != p.solution[it] }
            if (s != null) {
                undo.addLast(Triple(s, values[s], notes[s]))
                values[s] = p.solution[s]; notes[s] = 0; v.selected = s; changed()
            }
        }, lp())
        pad2.addView(Ui.button(this, "확인", 17f) {
            val wrong = (0 until 81).count { p.given[it] == 0 && values[it] != 0 && values[it] != p.solution[it] }
            AlertDialog.Builder(this).setMessage(if (wrong == 0) "지금까지 넣은 숫자는 모두 맞아요" else "틀린 숫자가 ${wrong}개 있어요")
                .setPositiveButton("확인", null).show()
        }, lp())
        root.addView(pad1); root.addView(pad2)
        setContentView(root)
        if (cleared >= 0) finishIfDone()
        refresh()
    }

    private fun saveProgress() {
        val v = board ?: return
        if (current < 0 || v.locked) return
        val k = "p_" + key(current)
        if (v.values.all { it == 0 } && v.notes.all { it == 0 }) { prefs.edit().remove(k).apply(); return }
        val o = JSONObject().put("v", v.values.joinToString("")).put("t", seconds)
            .put("n", org.json.JSONArray().apply { v.notes.forEach { put(it) } })
        prefs.edit().putString(k, o.toString()).apply()
    }
}
