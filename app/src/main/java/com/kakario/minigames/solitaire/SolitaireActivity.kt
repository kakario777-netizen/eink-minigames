package com.kakario.minigames.solitaire

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.kakario.minigames.Ui
import com.kakario.minigames.eink.EinkDevice
import org.json.JSONObject

/** 윈도우 카드놀이(솔리테어·클론다이크). */
class SolitaireActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("solitaire", Context.MODE_PRIVATE) }
    private val game = Klondike()
    private lateinit var board: SolitaireView
    private lateinit var root: LinearLayout
    private lateinit var scoreText: TextView
    private lateinit var timeText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var changes = 0

    // 옵션
    private var vegasCumulative: Boolean
        get() = prefs.getBoolean("vegasCum", false)
        set(v) { prefs.edit().putBoolean("vegasCum", v).apply() }
    private var refreshEvery: Int
        get() = prefs.getInt("refreshEvery", 10)
        set(v) { prefs.edit().putInt("refreshEvery", v).apply() }
    private var showTime: Boolean
        get() = prefs.getBoolean("showTime", true)
        set(v) { prefs.edit().putBoolean("showTime", v).apply() }

    private val ticker = object : Runnable {
        override fun run() {
            val before = game.score
            game.tick()
            if (showTime && game.started && !game.won) updateStatus() else if (before != game.score) updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = prefs.getString("game", null)
        if (saved != null && runCatching { game.load(JSONObject(saved)) }.isSuccess && !game.won) {
            // 이어하기
        } else {
            game.drawThree = prefs.getBoolean("draw3", false)
            game.scoring = runCatching { Scoring.valueOf(prefs.getString("scoring", "STANDARD")!!) }.getOrDefault(Scoring.STANDARD)
            game.timed = prefs.getBoolean("timed", true)
            dealNew(countLoss = false)
        }

        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = Ui.dp(this@SolitaireActivity, 6f); setPadding(p, p, p, p)
        }
        fun add(text: String, f: () -> Unit) = bar.addView(Ui.button(this, text, 16f, f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                val m = Ui.dp(this@SolitaireActivity, 3f); setMargins(m, 0, m, 0)
            })
        add("새 게임") { confirmNewGame() }
        add("되돌리기") { if (game.undo()) { board.clearMarks(); afterChange() } }
        add("힌트") { board.showHint() }
        add("자동 올리기") { if (game.autoToFoundation() > 0) { board.clearMarks(); afterChange(); if (game.won) win() } }
        add("메뉴") { menu() }
        root.addView(bar)

        board = SolitaireView(this, game).apply {
            tapAuto = prefs.getBoolean("tapAuto", false)
            onChanged = { afterChange() }
            onWin = { win() }
        }
        root.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = Ui.dp(this@SolitaireActivity, 8f); setPadding(p * 2, p, p * 2, p)
        }
        scoreText = Ui.label(this, "", 18f, bold = true)
        timeText = Ui.label(this, "", 18f, bold = true).apply { gravity = Gravity.END }
        status.addView(scoreText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        status.addView(timeText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(View(this).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1.5f)))
        root.addView(status)
        setContentView(root)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(ticker, 1000)
        root.post { EinkDevice.applyMode(root); EinkDevice.fullRefresh(root) }
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        save()
        super.onPause()
    }

    private fun save() { prefs.edit().putString("game", game.toJson().toString()).apply() }

    private fun afterChange() {
        updateStatus()
        board.invalidate()
        changes++
        val every = refreshEvery
        if (every > 0 && changes >= every) { changes = 0; root.post { EinkDevice.fullRefresh(root) } }
        if (game.moves % 5 == 0) save()
    }

    private fun updateStatus() {
        scoreText.text = when (game.scoring) {
            Scoring.NONE -> ""
            Scoring.VEGAS -> "점수: ${if (game.score < 0) "-$" + (-game.score) else "$" + game.score}"
            Scoring.STANDARD -> "점수: ${game.score}"
        }
        timeText.text = if (showTime && game.timed) "시간: %d:%02d".format(game.seconds / 60, game.seconds % 60) else ""
    }

    // ───── 새 게임 / 승리 ─────
    private fun dealNew(countLoss: Boolean = true) {
        if (countLoss && game.started && !game.won) prefs.edit().putInt("streak", 0).apply()
        if (game.scoring == Scoring.VEGAS && vegasCumulative && game.started) prefs.edit().putInt("vegasBank", game.score).apply()
        val carry = if (game.scoring == Scoring.VEGAS && vegasCumulative) prefs.getInt("vegasBank", 0) else null
        game.deal(carry)
        if (game.scoring == Scoring.VEGAS && vegasCumulative) prefs.edit().putInt("vegasBank", game.score).apply()
        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).apply()
        changes = 0
        save()
    }

    private fun confirmNewGame() {
        if (!game.started || game.won) { startNew(); return }
        AlertDialog.Builder(this).setMessage("이번 판을 그만두고 새로 시작할까요?")
            .setPositiveButton("새 게임") { _, _ -> startNew() }
            .setNegativeButton("취소", null).show()
    }

    private fun startNew() {
        dealNew()
        board.clearMarks()
        board.invalidate()
        updateStatus()
        root.post { EinkDevice.fullRefresh(root) }
    }

    private fun win() {
        val won = prefs.getInt("won", 0) + 1
        val streak = prefs.getInt("streak", 0) + 1
        prefs.edit().putInt("won", won).putInt("streak", streak)
            .putInt("bestStreak", maxOf(streak, prefs.getInt("bestStreak", 0)))
            .putInt("bestScore", if (game.scoring == Scoring.STANDARD) maxOf(game.score, prefs.getInt("bestScore", 0)) else prefs.getInt("bestScore", 0))
            .apply()
        if (game.scoring == Scoring.VEGAS && vegasCumulative) prefs.edit().putInt("vegasBank", game.score).apply()
        updateStatus()
        save()
        val sb = StringBuilder("카드를 모두 올렸어요!\n\n")
        if (game.scoring != Scoring.NONE) sb.append("점수: ${scoreText.text.removePrefix("점수: ")}\n")
        if (game.timed) sb.append("시간: %d:%02d\n".format(game.seconds / 60, game.seconds % 60))
        sb.append("이동: ${game.moves}번")
        root.post { EinkDevice.fullRefresh(root) }
        AlertDialog.Builder(this).setTitle("축하합니다").setMessage(sb)
            .setPositiveButton("새 게임") { _, _ -> startNew() }
            .setNegativeButton("닫기", null).show()
    }

    // ───── 메뉴 ─────
    private fun menu() {
        val items = arrayOf("옵션", "통계", "화면 새로고침", "처음 화면으로")
        AlertDialog.Builder(this).setItems(items) { _, w ->
            when (w) {
                0 -> options()
                1 -> stats()
                2 -> EinkDevice.fullRefresh(root)
                3 -> finish()
            }
        }.show()
    }

    private fun options() {
        val pad = Ui.dp(this, 18f)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        fun title(t: String) = col.addView(Ui.label(this, t, 17f, bold = true).apply { setPadding(0, pad / 2, 0, 0) })
        fun group(vararg labels: String, checked: Int): RadioGroup {
            val g = RadioGroup(this)
            labels.forEachIndexed { i, l -> g.addView(RadioButton(this).apply { text = l; textSize = 17f; id = 1000 + i; setTextColor(Color.BLACK) }) }
            g.check(1000 + checked); col.addView(g); return g
        }
        fun check(label: String, v: Boolean) = CheckBox(this).apply { text = label; textSize = 17f; isChecked = v; setTextColor(Color.BLACK) }.also { col.addView(it) }

        title("카드 넘기기")
        val gDraw = group("한 장씩", "세 장씩", checked = if (game.drawThree) 1 else 0)
        title("점수")
        val gScore = group("표준", "베가스", "없음", checked = game.scoring.ordinal)
        val cTimed = check("시간 기록 (표준 점수: 10초마다 -2, 빨리 끝내면 보너스)", game.timed)
        val cShow = check("시간 표시", showTime)
        val cCum = check("베가스 점수 누적", vegasCumulative)
        val page1 = col
        val page2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun title2(t: String) = page2.addView(Ui.label(this, t, 17f, bold = true).apply { setPadding(0, pad / 2, 0, 0) })
        fun group2(vararg labels: String, checked: Int): RadioGroup {
            val g = RadioGroup(this)
            labels.forEachIndexed { i, l -> g.addView(RadioButton(this).apply { text = l; textSize = 17f; id = 1000 + i; setTextColor(Color.BLACK) }) }
            g.check(1000 + checked); page2.addView(g); return g
        }
        title2("누르기 방식")
        val gTap = group2("눌러서 집고 → 놓을 곳 누르기", "누르면 갈 수 있는 곳으로 바로 이동", checked = if (board.tapAuto) 1 else 0)
        title2("이잉크 전체 새로고침")
        val steps = intArrayOf(0, 5, 10, 20)
        val gRef = group2("안 함", "5번 움직일 때마다", "10번마다", "20번마다", checked = steps.indexOf(refreshEvery).coerceAtLeast(0))
        // 스크롤 없이 두 페이지로 나눠 보여 준다
        val switch = Ui.button(this, "다음 페이지 ▶", 17f) {}
        holder.addView(page1); holder.addView(page2); page2.visibility = View.GONE
        switch.setOnClickListener {
            val first = page1.visibility == View.VISIBLE
            page1.visibility = if (first) View.GONE else View.VISIBLE
            page2.visibility = if (first) View.VISIBLE else View.GONE
            switch.text = if (first) "◀ 이전 페이지" else "다음 페이지 ▶"
        }
        holder.addView(switch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            val m = Ui.dp(this@SolitaireActivity, 12f); setMargins(m, m, m, 0)
        })

        AlertDialog.Builder(this).setTitle("옵션").setView(holder)
            .setPositiveButton("확인") { _, _ ->
                val draw3 = gDraw.checkedRadioButtonId == 1001
                val scoring = Scoring.values()[gScore.checkedRadioButtonId - 1000]
                showTime = cShow.isChecked
                vegasCumulative = cCum.isChecked
                board.tapAuto = gTap.checkedRadioButtonId == 1001
                refreshEvery = steps[gRef.checkedRadioButtonId - 1000]
                prefs.edit().putBoolean("tapAuto", board.tapAuto).putBoolean("draw3", draw3)
                    .putString("scoring", scoring.name).putBoolean("timed", cTimed.isChecked).apply()
                val ruleChanged = draw3 != game.drawThree || scoring != game.scoring || cTimed.isChecked != game.timed
                if (ruleChanged) {
                    // 윈도우처럼 규칙이 바뀌면 새 판으로
                    game.drawThree = draw3; game.scoring = scoring; game.timed = cTimed.isChecked
                    startNew()
                } else updateStatus()
            }
            .setNegativeButton("취소", null).show()
    }

    private fun stats() {
        val played = prefs.getInt("played", 0); val won = prefs.getInt("won", 0)
        val pct = if (played > 0) won * 100 / played else 0
        val msg = "한 판 수: $played\n이긴 판: $won ($pct%)\n지금 연승: ${prefs.getInt("streak", 0)}\n최고 연승: ${prefs.getInt("bestStreak", 0)}\n" +
            "최고 점수(표준): ${prefs.getInt("bestScore", 0)}\n베가스 누적: $${prefs.getInt("vegasBank", 0)}"
        AlertDialog.Builder(this).setTitle("통계").setMessage(msg)
            .setPositiveButton("확인", null)
            .setNegativeButton("초기화") { _, _ ->
                prefs.edit().remove("played").remove("won").remove("streak").remove("bestStreak").remove("bestScore").remove("vegasBank").apply()
            }.show()
    }
}
