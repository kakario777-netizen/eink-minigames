package com.kakario.minigames.nonogram

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kakario.minigames.Ui
import com.kakario.minigames.eink.EinkDevice
import com.kakario.minigames.puzzle.PuzzleList
import org.json.JSONObject

/** 네모네모 로직 — 모든 판은 미리 '추측 없이 줄 논리만으로 끝까지 풀리는지' 검사를 통과한 것만 들어 있다. */
class NonogramActivity : Activity() {

    class Puzzle(val title: String, val cat: String, val n: Int, val sol: BooleanArray)

    companion object {
        private var cache: Map<String, List<Puzzle>>? = null
        fun load(c: Context): Map<String, List<Puzzle>> = cache ?: run {
            val o = JSONObject(c.assets.open("nonogram.json").bufferedReader().use { it.readText() })
            PuzzleList.LEVELS.associate { (id, _) ->
                val a = o.getJSONArray(id)
                id to List(a.length()) { i ->
                    val p = a.getJSONObject(i)
                    val r = p.getString("r")
                    Puzzle(p.getString("t"), p.getString("c"), p.getInt("n"), BooleanArray(r.length) { r[it] == '1' })
                }
            }.also { cache = it }
        }

        fun picture(p: Puzzle, size: Int): Bitmap {
            val b = Bitmap.createBitmap(p.n, p.n, Bitmap.Config.ARGB_8888)
            for (i in p.sol.indices) b.setPixel(i % p.n, i / p.n, if (p.sol[i]) Color.BLACK else Color.WHITE)
            return Bitmap.createScaledBitmap(b, size, size, false)
        }
    }

    private val prefs by lazy { getSharedPreferences("nonogram", Context.MODE_PRIVATE) }
    private lateinit var all: Map<String, List<Puzzle>>
    private var level = "easy"
    private var current = -1
    private var board: NonogramView? = null
    private var changes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        all = load(this)
        level = prefs.getString("lv", "easy")!!
        showList()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { EinkDevice.applyMode(window.decorView); EinkDevice.fullRefresh(window.decorView) }
    }

    override fun onPause() { saveProgress(); super.onPause() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (current >= 0) { saveProgress(); showList() } else super.onBackPressed()
    }

    private fun key(i: Int) = "${level}_$i"
    private fun isCleared(i: Int) = prefs.getBoolean("c_" + key(i), false)

    private fun refresh() { window.decorView.post { EinkDevice.fullRefresh(window.decorView) } }

    // ───── 목록 ─────
    private fun showList() {
        val focusIdx = maxOf(0, current); current = -1; board = null
        val list = all[level]!!
        val cleared = list.indices.count { isCleared(it) }
        setContentView(PuzzleList.build(this, "네모네모 로직", level, list.size, cleared, 4,
            onLevel = { level = it; prefs.edit().putString("lv", it).apply(); showList() },
            tile = { i, w, h -> tile(list[i], i, minOf(w, h)) },
            onTile = { open(it) },
            onBack = { finish() }, focus = focusIdx))
        refresh()
    }

    private fun tile(p: Puzzle, i: Int, size: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val m = Ui.dp(this@NonogramActivity, 4f); setPadding(m, m, m, m)
        }
        if (isCleared(i)) {
            val img = (size * 0.62f).toInt()
            box.addView(ImageView(this).apply { setImageBitmap(picture(p, img)) }, LinearLayout.LayoutParams(img, img))
            box.addView(Ui.label(this, p.title, 13f).apply { gravity = Gravity.CENTER; maxLines = 1 })
        } else {
            box.addView(Ui.label(this, "${i + 1}", 24f, bold = true).apply { gravity = Gravity.CENTER })
            val sub = if (prefs.contains("p_" + key(i))) "${p.n}×${p.n} · 진행 중" else "${p.n}×${p.n}"
            box.addView(Ui.label(this, sub, 13f).apply { gravity = Gravity.CENTER })
            box.background = android.graphics.drawable.GradientDrawable().apply { setStroke(2, Color.BLACK); setColor(Color.WHITE) }
        }
        return box
    }

    // ───── 풀기 ─────
    private fun open(i: Int) {
        current = i
        val p = all[level]!![i]
        val cells = IntArray(p.n * p.n)
        val cleared = isCleared(i)
        val saved = prefs.getString("p_" + key(i), null)
        if (cleared) for (k in cells.indices) cells[k] = if (p.sol[k]) NonogramView.FILL else NonogramView.EMPTY
        else if (saved != null && saved.length == cells.size) for (k in cells.indices) cells[k] = saved[k] - '0'

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val pad = Ui.dp(this, 8f)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(pad, pad, pad, pad) }
        top.addView(Ui.button(this, "← 목록", 16f) { saveProgress(); showList() })
        val title = Ui.label(this, "  주제: ${p.title}", 22f, bold = true)
        top.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val levelName = PuzzleList.LEVELS.first { it.first == level }.second
        top.addView(Ui.label(this, "${p.cat} · $levelName ${i + 1} · ${p.n}×${p.n}", 15f))
        root.addView(top)

        val v = NonogramView(this, p.n, p.sol, cells)
        board = v
        root.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, pad, pad, pad) }
        lateinit var bFill: TextView; lateinit var bCross: TextView
        fun paintMode() {
            for ((b, on) in listOf(bFill to !v.markMode, bCross to v.markMode)) {
                if (on) { b.setBackgroundColor(Color.BLACK); b.setTextColor(Color.WHITE) }
                else { b.background = Ui.button(this, "") {}.background; b.setTextColor(Color.BLACK) }
            }
        }
        fun lp() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { val m = Ui.dp(this@NonogramActivity, 3f); setMargins(m, 0, m, 0) }
        bFill = Ui.button(this, "■ 칠하기", 17f) { v.markMode = false; paintMode() }
        bCross = Ui.button(this, "✕ 빈칸 확정", 17f) { v.markMode = true; paintMode() }
        val bUndo = Ui.button(this, "되돌리기", 17f) { v.undo() }
        val bClear = Ui.button(this, "처음부터", 17f) {
            android.app.AlertDialog.Builder(this).setMessage("이 판을 처음부터 다시 할까요?")
                .setPositiveButton("다시") { _, _ -> v.clearAll() }.setNegativeButton("취소", null).show()
        }
        listOf(bFill, bCross, bUndo, bClear).forEach { bottom.addView(it, lp()) }
        root.addView(bottom)
        val help = Ui.label(this, "누르기: 고른 도구로 칠하기 · 꾸욱: 빈칸 확정(✕) · 누른 채 밀기: 한 줄 여러 칸", 13f).apply {
            gravity = Gravity.CENTER; setPadding(0, 0, 0, pad)
        }
        root.addView(help)
        paintMode()

        fun finished() {
            v.locked = true
            title.text = "  완성! 주제: ${p.title}"
            bottom.removeAllViews()
            bottom.addView(Ui.button(this, "목록", 17f) { showList() }, lp())
            if (i + 1 < all[level]!!.size) bottom.addView(Ui.button(this, "다음 판", 17f) { open(i + 1) }, lp())
            help.text = "완성한 그림은 목록에 저장돼요"
            v.invalidate()
            refresh()
        }
        if (cleared) finished()
        v.onChanged = {
            changes++
            if (changes % 15 == 0) refresh()
            if (!v.locked && v.solved()) {
                // 숫자만 맞으면 완성 (모든 판은 답이 하나뿐이라 그림과 같다)
                prefs.edit().putBoolean("c_" + key(i), true).remove("p_" + key(i)).apply()
                for (k in cells.indices) cells[k] = if (p.sol[k]) NonogramView.FILL else NonogramView.EMPTY
                finished()
            }
        }
        setContentView(root)
        refresh()
    }

    private fun saveProgress() {
        val v = board ?: return
        if (current < 0 || v.locked) return
        val k = "p_" + key(current)
        if (v.cells.all { it == 0 }) prefs.edit().remove(k).apply()
        else prefs.edit().putString(k, v.cells.joinToString("")).apply()
    }
}
