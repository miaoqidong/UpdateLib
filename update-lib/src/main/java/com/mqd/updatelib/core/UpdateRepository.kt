package com.mqd.updatelib.core

import android.util.Log
import com.mqd.updatelib.data.DataStoreHolder
import kotlinx.coroutines.flow.first

/**
 * 检查更新逻辑（进程无关）：拉取 GitHub release → 写入跨进程缓存 [UpdateState]。
 *
 * 主进程启动 / 关于页手动检查 / 后台 ticker 都走这里，结果统一进缓存，
 * 由各自的呈现层读取。
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/19
 */
object UpdateRepository {

    private const val TAG = "UpdateRepository"

    /** 后台懒触发间隔：距上次「成功」检查超过 24h 才需要再查。 */
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** 软限流：距上次「尝试」不足 60s 且非强制，跳过本次（仅防瞬时重复请求）。 */
    private const val ATTEMPT_TTL_MS = 60 * 1000L

    /** 失败/限流退避：网络不通或被限流时，至少再等 2h，避免空打 GitHub。 */
    private const val FAILED_RETRY_BACKOFF_MS = 2 * 60 * 60 * 1000L

    /** 备用更新源 JSON URL，为空则不启用备用通道。 */
    var fallbackUrl: String = ""

    /** 仅使用备用更新源，完全跳过 GitHub。 */
    var useFallbackOnly: Boolean = false

    /** 最近一次检查写入的详情链接（内存缓存，供非 suspend 场景读取）。 */
    @Volatile
    var detailsUrl: String = ""
        private set

    sealed interface CheckResult {
        /** 命中软 TTL，未发起请求。 */
        data object Skipped : CheckResult

        /** 请求失败（网络/解析），缓存的版本信息未更新。 */
        data object Failed : CheckResult

        /** 被 GitHub 限流（403 额度耗尽或 429）；[resetEpochSeconds] 为恢复时间（0 未知）。 */
        data class RateLimited(val resetEpochSeconds: Long) : CheckResult

        /** 远端有新版但暂无可下载 APK（可能正在上传）；不写成功缓存以便尽快重试。 */
        data class NoApk(val version: String) : CheckResult

        /** 检查成功，远端有新版。 */
        data class NewVersion(val state: UpdateState) : CheckResult

        /** 检查成功，当前已是最新。 */
        data class UpToDate(val state: UpdateState) : CheckResult
    }

    /** 后台懒触发判据：距上次成功超过 24h 且已过退避点。 */
    suspend fun shouldCheck(): Boolean {
        val state = DataStoreHolder.updateState.data.first()
        val now = System.currentTimeMillis()
        if (now - state.lastCheckSuccessTime < CHECK_INTERVAL_MS) return false
        return now >= state.nextRetryTime
    }

    /**
     * 拉取最新 release 并写入缓存。
     *
     * @param force 为 true 时绕过软 TTL（手动检查），仍只读、零副作用。
     * @param currentVersion 当前应用版本号
     */
    suspend fun checkAndCache(force: Boolean, currentVersion: String): CheckResult {
        val current = DataStoreHolder.updateState.data.first()
        val now = System.currentTimeMillis()
        if (!force && now - current.lastCheckAttemptTime < ATTEMPT_TTL_MS) {
            return CheckResult.Skipped
        }
        DataStoreHolder.updateState.updateData { it.copy(lastCheckAttemptTime = now) }

        // 仅备用源模式：完全跳过 GitHub
        if (useFallbackOnly) {
            val fallbackResult = tryFallback(currentVersion)
            if (fallbackResult != null) return fallbackResult
            setNextRetry(now + FAILED_RETRY_BACKOFF_MS)
            return CheckResult.Failed
        }

        val release = when (val result = UpdateChecker.fetchLatestRelease()) {
            is UpdateChecker.FetchResult.Success -> result.release
            is UpdateChecker.FetchResult.RateLimited -> {
                Log.w(TAG, "GitHub rate limited, trying fallback...")
                val fallbackResult = tryFallback(currentVersion)
                if (fallbackResult != null) return fallbackResult
                setNextRetry(now + FAILED_RETRY_BACKOFF_MS)
                return CheckResult.RateLimited(result.resetEpochSeconds)
            }
            UpdateChecker.FetchResult.Failed -> {
                Log.w(TAG, "GitHub failed, trying fallback...")
                val fallbackResult = tryFallback(currentVersion)
                if (fallbackResult != null) return fallbackResult
                setNextRetry(now + FAILED_RETRY_BACKOFF_MS)
                return CheckResult.Failed
            }
        }
        val asset = UpdateChecker.pickApkAsset(release)

        // 根据 compareByTag 决定用于版本比较（及展示）的字符串
        val compareVersion = if (UpdateChecker.compareByTag) {
            release.tagName
        } else {
            val extracted = asset?.let { UpdateChecker.extractVersionFromFileName(it.name) }
            Log.d("UpdateRepository", "APK fileName=${asset?.name}, extracted=$extracted, fallback to tagName=${release.tagName}")
            extracted ?: release.tagName
        }
        Log.d("UpdateRepository", "compareByTag=${UpdateChecker.compareByTag}, compareVersion=$compareVersion, currentVersion=$currentVersion")       
        val isNewer = UpdateChecker.isRemoteNewer(compareVersion, currentVersion)
        val hasApk = asset != null && asset.size > 0

        // 有新版但暂无可下载 APK：不写成功缓存，也不返回 NewVersion
        if (isNewer && !hasApk) {
            setNextRetry(0L)
            return CheckResult.NoApk(compareVersion)
        }

        val newState = DataStoreHolder.updateState.updateData {
            it.copy(
                latestVersion = compareVersion,
                notes = release.body.ifBlank { release.name },
                apkUrl = asset?.browserDownloadUrl.orEmpty(),
                apkSize = asset?.size ?: 0L,
                lastCheckSuccessTime = System.currentTimeMillis(),
                nextRetryTime = 0L,
                detailsUrl = UpdateChecker.RELEASES_PAGE_URL
            )
        }
        detailsUrl = UpdateChecker.RELEASES_PAGE_URL
        return if (isNewer) {
            CheckResult.NewVersion(newState)
        } else {
            CheckResult.UpToDate(newState)
        }
    }

    private suspend fun setNextRetry(time: Long) {
        DataStoreHolder.updateState.updateData { it.copy(nextRetryTime = time) }
    }

    /**
     * 尝试从备用源获取更新信息。
     *
     * @return 检查结果（NewVersion / UpToDate / Failed），备用源未配置或也失败时返回 null
     */
    private suspend fun tryFallback(currentVersion: String): CheckResult? {
        if (fallbackUrl.isBlank()) return null

        val fallbackRelease = FallbackChecker.fetchFallbackRelease(fallbackUrl).getOrElse {
            Log.e(TAG, "Fallback also failed", it)
            return null
        }

        val remoteName = fallbackRelease.versionName
        val remoteCode = fallbackRelease.versionCode
        Log.d(TAG, "Fallback: versionName=$remoteName, versionCode=$remoteCode, currentVersion=$currentVersion")

        // 抓取更新说明内容
        val notes = if (fallbackRelease.desContent.isNotBlank()) {
            FallbackChecker.fetchDescription(fallbackRelease.desContent)
        } else {
            ""
        }

        val isNewer = FallbackChecker.isNewer(
            remoteName, currentVersion,
            remoteCode, UpdateChecker.currentVersionCode
        )

        val downloadUrl = fallbackRelease.downloadUrl
        val hasApk = downloadUrl.isNotBlank()

        if (isNewer && !hasApk) {
            setNextRetry(0L)
            return CheckResult.NoApk(remoteName)
        }

        val fallbackDetailsUrl = fallbackRelease.desUrl
        val newState = DataStoreHolder.updateState.updateData {
            it.copy(
                latestVersion = remoteName,
                notes = notes,
                apkUrl = downloadUrl,
                apkSize = 0L,
                lastCheckSuccessTime = System.currentTimeMillis(),
                nextRetryTime = 0L,
                detailsUrl = fallbackDetailsUrl
            )
        }
        detailsUrl = fallbackDetailsUrl
        return if (isNewer) {
            CheckResult.NewVersion(newState)
        } else {
            CheckResult.UpToDate(newState)
        }
    }
}
