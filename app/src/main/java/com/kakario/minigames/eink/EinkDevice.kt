package com.kakario.minigames.eink

import android.view.View

/**
 * 오닉스(BOOX) 계열 이잉크 기기 제어 — sam 10 Plus처럼 NeoReader가 들어 있는 기기.
 * 앱 화면을 'Regal(고화질)' 모드로 요청하고, 전체 새로고침은 기기 자체 GC(진하게 한 번 번쩍)로 한다.
 * 오닉스가 아닌 기기(일반 폰)에서는 요청이 거절되어 아무 일도 일어나지 않는다.
 */
object EinkDevice {
    @Volatile var active: Boolean = false
        private set

    private fun mode(vararg names: String): com.onyx.android.sdk.api.device.epd.UpdateMode? {
        val all = com.onyx.android.sdk.api.device.epd.UpdateMode.values()
        for (n in names) all.firstOrNull { it.name == n }?.let { return it }
        return null
    }

    private fun disableFastMode(view: View) {
        val c = com.onyx.android.sdk.api.device.epd.EpdController::class.java
        val pkg = view.context.packageName
        val z = java.lang.Boolean.TYPE
        runCatching { c.getMethod("clearApplicationFastMode").invoke(null) }
        runCatching { c.getMethod("applyApplicationFastMode", String::class.java, z, z).invoke(null, pkg, false, true) }
        runCatching { c.getMethod("disableA2ForSpecificView", View::class.java).invoke(null, view) }
        runCatching { c.getMethod("setAppScopeRefreshMode", String::class.java, Int::class.javaPrimitiveType).invoke(null, pkg, 0) }
    }

    /** 화면 전체를 고화질 모드로. 성공하면 true. */
    fun applyMode(view: View): Boolean {
        disableFastMode(view)
        val ok = runCatching {
            val m = mode("REGAL", "REGAL_D", "GU", "GC") ?: return@runCatching false
            com.onyx.android.sdk.api.device.epd.EpdController.setViewDefaultUpdateMode(view, m)
        }.getOrDefault(false)
        active = ok
        return ok
    }

    fun resetMode(view: View) {
        runCatching { com.onyx.android.sdk.api.device.epd.EpdController.resetViewUpdateMode(view) }
        active = false
    }

    /** 기기 자체 전체 새로고침 (진하게 한 번 번쩍여 잔상 지우기). */
    fun fullRefresh(view: View) {
        runCatching {
            val m = mode("GC", "GC4") ?: return@runCatching
            com.onyx.android.sdk.api.device.epd.EpdController.invalidate(view, m)
        }
    }
}
