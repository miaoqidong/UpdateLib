package com.mqd.updatelib

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.mqd.updatelib.core.UpdateChecker
import com.mqd.updatelib.core.UpdateLibContext
import com.mqd.updatelib.core.UpdateRepository
import com.mqd.updatelib.core.UpdateState
import com.mqd.updatelib.data.DataStoreHolder
import com.mqd.updatelib.download.ApkInstaller
import com.mqd.updatelib.download.DownloadController
import com.mqd.updatelib.download.DownloadService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 更新库公开 API 入口。
 *
 * 使用流程：
 * 1. 在 Application 中调用 [init] 初始化
 * 2. 通过 [updateStateFlow] 观察更新状态
 * 3. 调用 [checkForUpdate] 手动检查更新
 * 4. 调用 [downloadUpdate] 下载更新
 * 5. 调用 [installUpdate] 安装更新
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/29
 */
object UpdateManager {

    private var githubOwner: String = ""
    private var githubRepo: String = ""
    private var currentVersion: String = ""
    private var fileProviderAuthority: String = ""

    /**
     * 初始化更新库。
     *
     * 最简用法（仅备用源）：`UpdateManager.init(this, fallbackUrl = "https://...")`
     *
     * 使用 GitHub Releases 时需传入仓库信息：
     * `UpdateManager.init(this, "owner", "repo")`
     *
     * @param context 应用上下文
     * @param githubOwner GitHub 仓库所有者，仅备用源模式可省略
     * @param githubRepo GitHub 仓库名称，仅备用源模式可省略
     * @param currentVersion 当前应用版本号，默认自动从 PackageInfo 读取
     * @param fileProviderAuthority FileProvider authority，默认为 "${packageName}.updatelib.fileprovider"
     * @param compareByTag 版本比较模式：true（默认）比较 GitHub tag name，false 比较 APK 文件名中的版本号
     * @param currentVersionCode 当前应用 versionCode，默认自动从 PackageInfo 读取
     * @param fallbackUrl 备用更新源 JSON 地址，传空串则禁用备用通道
     * @param fallbackOnly true 表示仅使用备用源（跳过 GitHub），false（默认）表示优先 GitHub
     */
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        githubOwner: String = "",
        githubRepo: String = "",
        currentVersion: String = "",
        fileProviderAuthority: String = "${context.packageName}.updatelib.fileprovider",
        compareByTag: Boolean = true,
        currentVersionCode: Long = 0L,
        fallbackUrl: String = "",
        fallbackOnly: Boolean = false
    ) {
        UpdateLibContext.init(context)
        this.githubOwner = githubOwner
        this.githubRepo = githubRepo
        this.fileProviderAuthority = fileProviderAuthority

        // 配置备用源
        UpdateRepository.fallbackUrl = fallbackUrl
        UpdateRepository.useFallbackOnly = fallbackOnly

        // 自动检测版本信息
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val detectedVersion = currentVersion.ifBlank { pkgInfo.versionName ?: "" }
        val detectedVersionCode = if (currentVersionCode > 0) currentVersionCode
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
            else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()

        this.currentVersion = detectedVersion

        // 配置各组件
        if (githubOwner.isNotBlank() && githubRepo.isNotBlank()) {
            UpdateChecker.configure(githubOwner, githubRepo, detectedVersion, compareByTag, detectedVersionCode)
        } else {
            // 仅备用源模式也需要配置版本比较所需的参数
            UpdateChecker.configure("", "", detectedVersion, compareByTag, detectedVersionCode)
        }
        ApkInstaller.configure(fileProviderAuthority)
    }

    /**
     * 检查是否已初始化
     */
    @JvmStatic
    fun isInitialized(): Boolean {
        return UpdateLibContext.isInitialized
    }

    /**
     * 获取当前版本号
     */
    @JvmStatic
    fun getCurrentVersion(): String = currentVersion

    /**
     * 获取 GitHub 仓库所有者
     */
    @JvmStatic
    fun getGithubOwner(): String = githubOwner

    /**
     * 获取 GitHub 仓库名称
     */
    @JvmStatic
    fun getGithubRepo(): String = githubRepo

    /**
     * 获取升级弹窗右上角按钮的跳转链接。
     *
     * 优先返回最近一次检查写入的 detailsUrl（备用源 desUrl 或 GitHub releases），
     * 若尚未检查过则回退到 GitHub releases 页面。
     */
    @JvmStatic
    fun getReleasesPageUrl(): String {
        val cached = UpdateRepository.detailsUrl
        return if (cached.isNotBlank()) cached else UpdateChecker.RELEASES_PAGE_URL
    }

    /**
     * 更新状态 Flow（来自 DataStore，跨进程共享）
     */
    @JvmStatic
    fun updateStateFlow(): Flow<UpdateState> {
        return DataStoreHolder.updateState.data
    }

    /**
     * 下载状态 Flow（内存单例，仅主进程）
     */
    @JvmStatic
    fun downloadStateFlow(): StateFlow<DownloadController.DownloadUiState> {
        return DownloadController.flow
    }

    /**
     * 检查是否有新版本。
     *
     * @return true 表示有可用的新版本
     */
    @JvmStatic
    suspend fun hasNewVersion(): Boolean {
        val state = DataStoreHolder.updateState.data.first()
        return state.latestVersion.isNotBlank() &&
                UpdateChecker.isRemoteNewer(state.latestVersion, currentVersion)
    }

    /**
     * 手动检查更新。
     *
     * @param force 是否强制检查（绕过缓存 TTL）
     * @return 检查结果
     */
    @JvmStatic
    suspend fun checkForUpdate(force: Boolean = false): UpdateRepository.CheckResult {
        return UpdateRepository.checkAndCache(force, currentVersion)
    }

    /**
     * 后台懒检查（仅在距上次检查超过 24h 时才检查）。
     *
     * @return true 表示执行了检查
     */
    @JvmStatic
    suspend fun checkOnLaunch(): Boolean {
        return if (UpdateRepository.shouldCheck()) {
            checkForUpdate(force = false)
            true
        } else {
            false
        }
    }

    /**
     * 开始下载更新。
     *
     * @param context 上下文
     * @param version 目标版本号
     * @param url 下载 URL
     * @param size 文件大小（字节）
     */
    @JvmStatic
    fun downloadUpdate(context: Context, version: String, url: String, size: Long) {
        DownloadService.start(context, version, url, size)
    }

    /**
     * 安装已下载的 APK。
     *
     * @param context 上下文
     * @param version 版本号
     * @return true 表示成功拉起安装界面
     */
    @JvmStatic
    fun installUpdate(context: Context, version: String): Boolean {
        val file = ApkInstaller.apkFile(context, version)
        return ApkInstaller.installApk(context, file)
    }

    /**
     * 检查是否可以安装未知来源应用。
     */
    @JvmStatic
    fun canInstall(context: Context): Boolean {
        return ApkInstaller.canInstall(context)
    }

    /**
     * 跳转到未知来源设置页面。
     */
    @JvmStatic
    fun gotoUnknownSourceSetting(context: Context) {
        ApkInstaller.gotoUnknownSourceSetting(context)
    }

    /**
     * 检查是否已有通知权限（Android 13 以下始终返回 true）。
     */
    @JvmStatic
    fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 跳转到应用通知设置页面。
     */
    @JvmStatic
    fun gotoNotificationSetting(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }

    /**
     * 检查 APK 是否已下载完成。
     *
     * @param context 上下文
     * @param version 版本号
     * @param expectedSize 期望的文件大小
     * @return true 表示已下载且大小匹配
     */
    @JvmStatic
    fun isDownloaded(context: Context, version: String, expectedSize: Long): Boolean {
        val file = ApkInstaller.apkFile(context, version)
        return ApkInstaller.isDownloaded(file, expectedSize)
    }

    /**
     * 重置下载状态（仅重置内存状态，不删除文件）。
     */
    @JvmStatic
    fun resetDownloadState() {
        DownloadController.reset()
    }

    /**
     * 获取最新版本号（从缓存读取）。
     */
    @JvmStatic
    suspend fun getLatestVersion(): String {
        val state = DataStoreHolder.updateState.data.first()
        return state.latestVersion
    }

    /**
     * 获取更新说明（从缓存读取）。
     */
    @JvmStatic
    suspend fun getReleaseNotes(): String {
        val state = DataStoreHolder.updateState.data.first()
        return state.notes
    }

    /**
     * 获取 APK 下载 URL（从缓存读取）。
     */
    @JvmStatic
    suspend fun getApkUrl(): String {
        val state = DataStoreHolder.updateState.data.first()
        return state.apkUrl
    }

    /**
     * 获取 APK 文件大小（从缓存读取）。
     */
    @JvmStatic
    suspend fun getApkSize(): Long {
        val state = DataStoreHolder.updateState.data.first()
        return state.apkSize
    }

    // ────────────────────────────────────────
    // Java 回调式 API（纯 Java 项目无需协程）
    // ────────────────────────────────────────

    private val javaScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )

    /**
     * 检查更新（Java 回调版）。
     *
     * @param force 是否强制检查（绕过缓存 TTL）
     * @param callback 检查完成后的回调
     */
    @JvmStatic
    fun checkForUpdate(
        force: Boolean,
        callback: (UpdateRepository.CheckResult) -> Unit
    ) {
        javaScope.launch {
            val result = checkForUpdate(force)
            callback(result)
        }
    }

    /**
     * 启动时懒检查（Java 回调版）。
     *
     * @param callback 是否执行了检查
     */
    @JvmStatic
    fun checkOnLaunch(callback: (Boolean) -> Unit) {
        javaScope.launch {
            callback(checkOnLaunch())
        }
    }

    /**
     * 观察下载状态（Java 回调版）。
     *
     * 回调在状态变化时持续触发，包括进度更新。
     * 返回 Job 可用于取消观察（如 Activity 销毁时）。
     */
    @JvmStatic
    fun observeDownloadState(
        callback: (DownloadController.DownloadUiState) -> Unit
    ): kotlinx.coroutines.Job {
        return javaScope.launch {
            downloadStateFlow().collect { callback(it) }
        }
    }

    /**
     * 观察更新状态（Java 回调版）。
     *
     * 返回 Job 可用于取消观察。
     */
    @JvmStatic
    fun observeUpdateState(
        callback: (UpdateState) -> Unit
    ): kotlinx.coroutines.Job {
        return javaScope.launch {
            updateStateFlow().collect { callback(it) }
        }
    }
}
