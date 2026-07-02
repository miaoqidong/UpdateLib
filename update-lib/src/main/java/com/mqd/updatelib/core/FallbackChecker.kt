package com.mqd.updatelib.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 备用更新源检查器。
 *
 * 当 GitHub 不可达时，从备用 JSON URL 获取版本信息：
 * 1. 拉取 JSON → 解析 [FallbackRelease]
 * 2. 从 [FallbackRelease.desContent] 抓取更新说明内容（HTML 或纯文本）
 * 3. 版本比较：先比 versionName，相等时再比 versionCode
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/30
 */
object FallbackChecker {

    private const val TAG = "FallbackChecker"
    private const val TIMEOUT_MS = 8000

    /**
     * 拉取并解析备用 JSON。
     */
    suspend fun fetchFallbackRelease(jsonUrl: String): Result<FallbackRelease> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(jsonUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "JSON body=$body")
                    Result.success(JsonHelper.decodeFromString(body))
                } else {
                    Log.w(TAG, "HTTP ${conn.responseCode}")
                    Result.failure(Exception("HTTP ${conn.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchFallbackRelease failed", e)
                Result.failure(e)
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * 从 [desUrl] 抓取更新说明内容。
     * 返回原始字符串（可能是 HTML 也可能是纯文本）。
     */
    suspend fun fetchDescription(desUrl: String): String = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(desUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w(TAG, "fetchDescription HTTP ${conn.responseCode}")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchDescription failed", e)
            ""
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 备用源版本比较：先比 versionName，相等时再比 versionCode。
     *
     * @return true 表示远端更新
     */
    fun isNewer(remoteName: String, localName: String, remoteCode: Long, localCode: Long): Boolean {
        return if (UpdateChecker.isRemoteNewer(remoteName, localName)) {
            true
        } else if (remoteName == localName) {
            remoteCode > localCode
        } else {
            false
        }
    }

    /**
     * 判断字符串是否包含 HTML 标签。
     */
    fun isHtmlContent(content: String): Boolean {
        return content.trimStart().let { it.startsWith("<") || it.contains("<html") || it.contains("<div") || it.contains("<p>") || it.contains("<br") }
    }
}
