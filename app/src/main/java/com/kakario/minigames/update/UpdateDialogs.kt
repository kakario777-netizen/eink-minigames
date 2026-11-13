package com.kakario.minigames.update

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** 업데이트 창 (확인 → 받기 진행 → 설치). 액티비티가 켜져 있는 동안만 붙어 있다. */
class UpdateDialogs(private val activity: Activity) {
    private val updater = Updater.get(activity)
    private var dialog: AlertDialog? = null
    private var progress: ProgressBar? = null
    private var progressText: TextView? = null
    private val listener: (Updater.State) -> Unit = { show(it) }

    fun attach() { updater.addListener(listener); show(updater.state) }
    fun detach() { updater.removeListener(listener); close() }

    private fun close() { dialog?.dismiss(); dialog = null; progress = null; progressText = null }

    private fun show(s: Updater.State) {
        if (activity.isFinishing) return
        when (s) {
            Updater.State.Idle -> close()
            Updater.State.Checking -> simple("업데이트 확인 중…", null)
            is Updater.State.UpToDate -> simple("지금이 최신 버전이에요 (빌드 #${s.build})", "확인")
            is Updater.State.Failed -> simple(s.message, "확인")
            is Updater.State.Available -> {
                close()
                dialog = AlertDialog.Builder(activity)
                    .setTitle("새 버전이 있어요")
                    .setMessage("빌드 #${s.release.build} (지금 #${s.current})\n받아서 바로 업데이트할까요?")
                    .setPositiveButton("업데이트") { _, _ -> updater.download(s.release) }
                    .setNegativeButton("나중에") { _, _ -> updater.dismiss() }
                    .setOnCancelListener { updater.dismiss() }
                    .show()
            }
            is Updater.State.Downloading -> {
                if (progress == null) {
                    close()
                    val pad = (20 * activity.resources.displayMetrics.density).toInt()
                    val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
                    progressText = TextView(activity).apply { textSize = 18f }
                    progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
                    box.addView(progressText); box.addView(progress)
                    dialog = AlertDialog.Builder(activity).setTitle("새 버전 받는 중").setView(box).setCancelable(false).show()
                }
                progress?.progress = s.percent
                progressText?.text = "${s.percent}%"
            }
            is Updater.State.Ready -> {
                close()
                dialog = AlertDialog.Builder(activity)
                    .setMessage("다 받았어요. 설치할까요?")
                    .setPositiveButton("설치") { _, _ -> updater.install(s.file) }
                    .setNegativeButton("닫기") { _, _ -> updater.dismiss() }
                    .show()
            }
            Updater.State.Installing -> simple("설치 중… 끝나면 앱이 다시 켜져요", null)
        }
    }

    private fun simple(msg: String, button: String?) {
        close()
        val b = AlertDialog.Builder(activity).setMessage(msg)
        if (button != null) b.setPositiveButton(button) { _, _ -> updater.dismiss() }.setOnCancelListener { updater.dismiss() }
        else b.setCancelable(false)
        dialog = b.show()
    }
}
