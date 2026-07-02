package com.mqd.updatelib.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新：拉取 GitHub 最新 release 并比对版本。
 *
 * 用户群是国内用户，api.github.com 访问不稳，因此用短超时（5s）+ 静默失败：
 * 任何异常都返回 null，由上层决定「静默」还是「toast」。
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/18
 */
object UpdateChecker {

    private const val TIMEOUT_MS = 5000
    private const val HTTP_TOO_MANY_REQUESTS = 429

    /** GitHub 要求所有请求带 User-Agent，缺失会直接 403。 */
    private var USER_AGENT = "updatelib (Android)"

    /** release 列表页，html_url 缺失时的回退跳转地址。 */
    var RELEASES_PAGE_URL = "https://github.com"
        private set

    private var latestReleaseApi = ""

    /** 版本比较模式：true 比较 tag name，false 比较 APK 文件名中的版本号。 */
    var compareByTag = true
        private set

    /** 当前应用 versionCode，用于备用源版本比较。 */
    var currentVersionCode: Long = 0L
        private set

    /** 配置 GitHub 仓库信息 */
    fun configure(owner: String, repo: String, versionName: String, compareByTag: Boolean = true, currentVersionCode: Long = 0L) {
        latestReleaseApi = "https://api.github.com/repos/$owner/$repo/releases/latest"
        RELEASES_PAGE_URL = "https://github.com/$owner/$repo/releases"
        USER_AGENT = "$owner/$repo/${versionName} (Android)"
        this.compareByTag = compareByTag
        this.currentVersionCode = currentVersionCode
    }

    /** 拉取结果：成功 / 速率限流（403 额度耗尽或 429）/ 其它失败。 */
    sealed interface FetchResult {
        data class Success(val release: GithubRelease) : FetchResult

        /** GitHub 接口限流；[resetEpochSeconds] 为限流恢复的 Unix 秒（0 表示未知）。 */
        data class RateLimited(val resetEpochSeconds: Long) : FetchResult

        data object Failed : FetchResult
    }

    suspend fun fetchLatestRelease(): FetchResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(latestReleaseApi).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext FetchResult.Success(JsonHelper.decodeFromString(body))
            }
            // 403 且额度耗尽（X-RateLimit-Remaining=0），或 429 → 判定为限流
            if (code == HttpURLConnection.HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS) {
                val remaining = conn.getHeaderField("X-RateLimit-Remaining")
                val reset = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull() ?: 0L
                if (code == HTTP_TOO_MANY_REQUESTS || remaining == "0") {
                    return@withContext FetchResult.RateLimited(reset)
                }
            }
            FetchResult.Failed
        } catch (e: Exception) {
            FetchResult.Failed
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 比较远端 tag 与本地版本号，去掉前缀 v，按 x.y.z 逐段数值比较。
     */
    fun isRemoteNewer(remoteTag: String, localName: String): Boolean {
        val remote = parseVersion(remoteTag)
        val local = parseVersion(localName)
        val size = maxOf(remote.size, local.size)
        for (i in 0 until size) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(".")
            .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }

    /**
     * 从 APK 文件名中提取版本号。
     *
     * 支持的文件名格式示例：
     * - app-v1.5.3-release.apk → "1.5.3"
     * - myapp-1.2.0.apk → "1.2.0"
     * - app_2.0.0.apk → "2.0.0"
     *
     * @return 提取到的版本号（不含 v 前缀），未找到则返回 null
     */
    fun extractVersionFromFileName(fileName: String): String? {
        val nameWithoutExt = fileName.substringBeforeLast('.').let {
            if (fileName.contains('.')) it else fileName
        }
        val match = Regex("""(\d+(?:\.\d+){1,3})""").find(nameWithoutExt)
        return match?.groupValues?.get(1)
    }

    fun pickApkAsset(release: GithubRelease): GithubRelease.Asset? {
        return release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
    }

    /** 版本号展示：去掉 v/V 前缀后统一加 v，如 "1.5.3" → "v1.5.3"。 */
    fun displayVersion(raw: String): String {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        return "v$trimmed"
    }
}
