package com.kakario.minigames.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 앱 안 업데이트: GitHub 최신 빌드를 확인해서 받고 바로 설치한다.
 * 두 번째 업데이트부터(안드로이드 12+)는 묻지 않고 바로 바뀌고 앱이 다시 켜진다.
 */
class Updater private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    data class Release(val build: Int, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val build: Int) : State
        data class Available(val release: Release, val current: Int) : State
        data class Downloading(val release: Release, val percent: Int) : State
        data class Ready(val release: Release, val file: File) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    @Volatile var state: State = State.Idle
        private set
    private val listeners = mutableSetOf<(State) -> Unit>()
    private var job: Job? = null

    fun addListener(l: (State) -> Unit) { listeners += l }
    fun removeListener(l: (State) -> Unit) { listeners -= l }

    private fun set(s: State) {
        state = s
        main.post { listeners.toList().forEach { it(s) } }
    }

    /** 지금 설치된 빌드 번호 (GitHub 자동 빌드 번호와 같음). */
    val currentBuild: Int
        get() = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
            else @Suppress("DEPRECATION") info.versionCode
        }.getOrDefault(0)

    /** 새 버전 확인. quiet = true면 최신일 때·실패했을 때 아무것도 안 띄움 (앱 켤 때 자동 확인용). */
    fun check(quiet: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!quiet) set(State.Checking)
            try {
                val r = fetchLatest()
                val cur = currentBuild
                set(
                    when {
                        r == null -> if (quiet) State.Idle else State.Failed("최신 빌드 정보를 찾지 못했어요")
                        r.build > cur -> State.Available(r, cur)
                        quiet -> State.Idle
                        else -> State.UpToDate(cur)
                    }
                )
            } catch (e: Exception) {
                set(if (quiet) State.Idle else State.Failed("업데이트를 확인하지 못했어요.\n${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    /** 받기 → 다 받으면 바로 설치. */
    fun download(release: Release) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val dir = File(context.cacheDir, "update").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "minigames-${release.build}.apk")
                set(State.Downloading(release, 0))
                Downloader.fetch(release.apkUrl, file, release.size) { done, total ->
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    if ((state as? State.Downloading)?.percent != pct) set(State.Downloading(release, pct))
                }
                set(State.Ready(release, file))
                install(file)
            } catch (e: Exception) {
                set(State.Failed("받다가 끊겼어요. 다시 시도해 주세요. (${e.message ?: e.javaClass.simpleName})"))
            }
        }
    }

    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val release = (state as? State.Ready)?.release
        scope.launch {
            try {
                set(State.Installing)
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(file.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setRequestUpdateOwnership(true)
                    }
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("minigames.apk", 0, file.length()).use { out ->
                            input.copyTo(out, 256 * 1024)
                            session.fsync(out)
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val pi = PendingIntent.getBroadcast(
                        context, id,
                        Intent(context, InstallReceiver::class.java).setPackage(context.packageName),
                        flags,
                    )
                    session.commit(pi.intentSender)
                }
            } catch (e: Exception) {
                set(
                    if (release != null) State.Ready(release, file)
                    else State.Failed("설치하지 못했어요. (${e.message ?: e.javaClass.simpleName})")
                )
            }
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        if (status == PackageInstaller.STATUS_SUCCESS) return
        set(State.Failed("설치하지 못했어요.\n${message ?: "코드 $status"}"))
    }

    internal fun onUserAction() = set(State.Idle)

    fun dismiss() {
        if (job?.isActive != true) set(State.Idle)
    }

    private fun fetchLatest(): Release? {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "minigames-app")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException(if (code == 404) "업데이트 저장소에 접근할 수 없어요 (저장소가 공개인지 확인)" else "응답 $code")
        }
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        // 릴리스 이름 '최신 빌드 #25' 의 25 = 빌드 번호 = 앱 versionCode
        val build = Regex("#(\\d+)").find(json.optString("name"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return Release(build, a.getString("browser_download_url"), a.optLong("size"))
            }
        }
        return null
    }

    companion object {
        private const val API = "https://api.github.com/repos/kakario777-netizen/eink-minigames/releases/tags/latest"

        @Volatile private var instance: Updater? = null
        fun get(context: Context): Updater =
            instance ?: synchronized(this) { instance ?: Updater(context.applicationContext).also { instance = it } }
    }
}
