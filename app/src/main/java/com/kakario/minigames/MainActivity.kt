package com.kakario.minigames

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.kakario.minigames.eink.EinkDevice
import com.kakario.minigames.nonogram.NonogramActivity
import com.kakario.minigames.solitaire.SolitaireActivity
import com.kakario.minigames.sudoku.SudokuActivity
import com.kakario.minigames.update.UpdateDialogs
import com.kakario.minigames.update.Updater

/**
 * 미니게임 플랫폼 첫 화면 — 게임 아이콘이 격자로 놓이고, 누르면 그 게임이 켜진다.
 * 새 게임을 만들면 GAMES 목록에 한 줄만 추가하면 아이콘이 생긴다.
 */
class MainActivity : Activity() {

    private class Game(val id: String, val name: String, val icon: (Activity) -> Bitmap, val open: Class<out Activity>)

    private val games = listOf(
        Game("solitaire", "솔리테어", ::solitaireIcon, SolitaireActivity::class.java),
        Game("nonogram", "네모네모 로직", ::nonogramIcon, NonogramActivity::class.java),
        Game("sudoku", "스도쿠", ::sudokuIcon, SudokuActivity::class.java),
        Game("mines", "지뢰찾기", Icons::mines, com.kakario.minigames.mines.MinesActivity::class.java),
        Game("janggi", "장기", Icons::janggi, com.kakario.minigames.janggi.JanggiActivity::class.java),
        Game("omok", "오목", Icons::omok, com.kakario.minigames.omok.OmokActivity::class.java),
        Game("fifteen", "15퍼즐", Icons::fifteen, com.kakario.minigames.fifteen.FifteenActivity::class.java),
        Game("2048", "2048", Icons::g2048, com.kakario.minigames.g2048.G2048Activity::class.java),
        Game("chess", "체스", Icons::chess, com.kakario.minigames.chess.ChessActivity::class.java),
        Game("ladder", "사다리게임", Icons::ladder, com.kakario.minigames.ladder.LadderActivity::class.java),
        Game("yut", "윷놀이", Icons::yut, com.kakario.minigames.yut.YutActivity::class.java),
        Game("hula", "훌라", Icons::hula, com.kakario.minigames.cards.HulaActivity::class.java),
        Game("shisen", "사천성", Icons::shisen, com.kakario.minigames.shisen.ShisenActivity::class.java),
        Game("memory", "짝 맞추기", Icons::memory, com.kakario.minigames.memory.MemoryActivity::class.java),
        Game("onecard", "원카드", Icons::onecard, com.kakario.minigames.cards.OneCardActivity::class.java),
    )

    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }
    private lateinit var updates: UpdateDialogs
    private lateinit var root: View
    private lateinit var gridBox: LinearLayout
    private val iconCache = HashMap<String, Bitmap>()

    /** 저장된 순서(없는 새 게임은 뒤에 붙임). */
    private fun order(): MutableList<String> {
        val saved = prefs.getString("order", "")!!.split(",").filter { id -> games.any { it.id == id } }.toMutableList()
        for (g in games) if (g.id !in saved) saved += g.id
        return saved
    }
    private fun hidden(): MutableSet<String> = prefs.getString("hidden", "")!!.split(",").filter { it.isNotEmpty() }.toMutableSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = UpdateDialogs(this)
        val pad = Ui.dp(this, 20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.label(this, "미니게임", 30f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.button(this, "설정") { settings() })
        col.addView(head)
        col.addView(View(this).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 2f)).apply {
                topMargin = Ui.dp(this@MainActivity, 10f); bottomMargin = Ui.dp(this@MainActivity, 20f)
            })

        gridBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(gridBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        buildGrid()

        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        foot.addView(Ui.label(this, "빌드 #${Updater.get(this).currentBuild}", 16f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        foot.addView(Ui.button(this, "업데이트 확인") { Updater.get(this).check(quiet = false) })
        col.addView(foot)

        root = col
        setContentView(root)
        Updater.get(this).check(quiet = true)
    }

    private fun buildGrid() {
        val hid = hidden()
        val visible = order().filter { it !in hid }.map { id -> games.first { it.id == id } }
        val focus = (gridBox.getChildAt(0) as? PagedGrid)?.let { it.page * it.perPageCount } ?: 0
        gridBox.removeAllViews()
        val pg = PagedGrid(this, visible.size, 3, 1.2f, { i, w, h ->
            val g = visible[i]
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val label = Ui.label(this@MainActivity, g.name, 18f, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 }
                val iconSize = minOf((w * 0.72f).toInt(), (h * 0.7f).toInt())
                addView(ImageView(this@MainActivity).apply { setImageBitmap(iconCache.getOrPut(g.id) { g.icon(this@MainActivity) }) },
                    LinearLayout.LayoutParams(iconSize, iconSize))
                addView(label)
            }
        }, { i -> startActivity(Intent(this, visible[i].open)) }, focus = focus)
        gridBox.addView(pg, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
    }

    /** 설정: 게임 숨기기(체크 해제) · 순서 바꾸기(▲▼). */
    private fun settings() {
        val ord = order(); val hid = hidden()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = Ui.dp(this@MainActivity, 12f); setPadding(p, p, p, p) }
        val per = 8
        var pg = 0
        fun render() {
            list.removeAllViews()
            list.addView(Ui.label(this, "체크를 끄면 첫 화면에서 숨겨요. ▲▼로 순서를 바꿔요.", 15f).apply { setPadding(0, 0, 0, Ui.dp(this@MainActivity, 8f)) })
            val pages = (ord.size + per - 1) / per
            pg = pg.coerceIn(0, pages - 1)
            for (i in pg * per until minOf(ord.size, pg * per + per)) {
                val id = ord[i]
                val g = games.first { it.id == id }
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(android.widget.CheckBox(this).apply {
                    text = g.name; textSize = 18f; setTextColor(Color.BLACK); isChecked = id !in hid
                    setOnCheckedChangeListener { _, on -> if (on) hid.remove(id) else hid.add(id) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.button(this, "▲", 16f) { if (i > 0) { ord.add(i - 1, ord.removeAt(i)); if (i - 1 < pg * per) pg--; render() } })
                row.addView(View(this), LinearLayout.LayoutParams(Ui.dp(this, 6f), 1))
                row.addView(Ui.button(this, "▼", 16f) { if (i < ord.size - 1) { ord.add(i + 1, ord.removeAt(i)); if (i + 1 >= pg * per + per) pg++; render() } })
                list.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = Ui.dp(this@MainActivity, 4f) })
            }
            if (pages > 1) {
                val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, Ui.dp(this@MainActivity, 8f), 0, 0) }
                nav.addView(Ui.button(this, "◀ 이전", 17f) { if (pg > 0) { pg--; render() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                nav.addView(Ui.label(this, "${pg + 1} / $pages", 17f, bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                nav.addView(Ui.button(this, "다음 ▶", 17f) { if (pg < pages - 1) { pg++; render() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                list.addView(nav)
            }
        }
        render()
        android.app.AlertDialog.Builder(this).setTitle("게임 보이기 · 순서").setView(list)
            .setPositiveButton("저장") { _, _ ->
                prefs.edit().putString("order", ord.joinToString(",")).putString("hidden", hid.joinToString(",")).apply()
                buildGrid(); EinkDevice.fullRefresh(root)
            }
            .setNegativeButton("취소", null)
            .setNeutralButton("모두 보이기") { _, _ -> prefs.edit().putString("hidden", "").apply(); buildGrid() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        updates.attach()
        root.post { EinkDevice.applyMode(root); EinkDevice.fullRefresh(root) }
    }

    override fun onPause() {
        updates.detach()
        super.onPause()
    }

    companion object {
        /** 네모네모 로직 아이콘: 하트 퍼즐 그림 + 칸 선. */
        fun nonogramIcon(a: Activity): Bitmap {
            val size = 360
            val p = NonogramActivity.load(a)["medium"]!!.let { l -> l.firstOrNull { it.title == "하트" } ?: l.first() }
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(Color.WHITE)
            val m = size * 0.08f; val cell = (size - 2 * m) / p.n
            val fill = Paint(); fill.color = Color.BLACK
            val line = Paint(); line.color = Color.BLACK; line.strokeWidth = 2f
            for (i in p.sol.indices) if (p.sol[i]) {
                val x = m + (i % p.n) * cell; val y = m + (i / p.n) * cell
                c.drawRect(x, y, x + cell, y + cell, fill)
            }
            for (k in 0..p.n) {
                line.strokeWidth = if (k % 5 == 0) 5f else 1.5f
                c.drawLine(m + k * cell, m, m + k * cell, size - m, line)
                c.drawLine(m, m + k * cell, size - m, m + k * cell, line)
            }
            return bmp
        }

        /** 스도쿠 아이콘: 3×3 칸에 숫자. */
        fun sudokuIcon(a: Activity): Bitmap {
            val size = 360
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(Color.WHITE)
            val m = size * 0.1f; val cell = (size - 2 * m) / 3
            val line = Paint(); line.color = Color.BLACK
            val t = Paint(Paint.ANTI_ALIAS_FLAG); t.color = Color.BLACK; t.textSize = cell * 0.6f; t.textAlign = Paint.Align.CENTER; t.isFakeBoldText = true
            val nums = intArrayOf(5, 0, 7, 0, 3, 0, 9, 0, 1)
            for (k in 0..3) {
                line.strokeWidth = if (k == 0 || k == 3) 9f else 3f
                c.drawLine(m + k * cell, m, m + k * cell, size - m, line)
                c.drawLine(m, m + k * cell, size - m, m + k * cell, line)
            }
            for (i in 0 until 9) if (nums[i] != 0) c.drawText(nums[i].toString(), m + (i % 3 + 0.5f) * cell, m + (i / 3 + 0.72f) * cell, t)
            return bmp
        }

        /** 솔리테어 아이콘: 카드 두 장을 겹쳐 그린다. */
        fun solitaireIcon(a: Activity): Bitmap {
            val size = 360
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            fun card(name: String) = a.assets.open("cards/$name.png").use { BitmapFactory.decodeStream(it) }
            val back = card("SA"); val front = card("HK")
            val h = size * 0.86f; val sc = h / back.height
            c.save(); c.rotate(-12f, size / 2f, size / 2f)
            c.drawBitmap(back, Matrix().apply { setScale(sc, sc); postTranslate(size * 0.10f, size * 0.08f) }, p)
            c.restore()
            c.save(); c.rotate(8f, size / 2f, size / 2f)
            c.drawBitmap(front, Matrix().apply { setScale(sc, sc); postTranslate(size * 0.36f, size * 0.06f) }, p)
            c.restore()
            return bmp
        }
    }
}
