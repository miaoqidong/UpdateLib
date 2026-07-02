package com.mqd.updatelib.compose.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mqd.updatelib.UpdateManager
import com.mqd.updatelib.core.UpdateChecker
import com.mqd.updatelib.core.UpdateRepository
import com.mqd.updatelib.download.ApkInstaller
import com.mqd.updatelib.download.DownloadController
import com.mqd.updatelib.download.DownloadController.DownloadStatus
import com.mqd.updatelib.download.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 更新交互的 ViewModel（Compose 项目使用）。
 *
 * 合成三处状态决定弹窗：
 * 1. UpdateState 跨进程缓存（检查结果）
 * 2. DownloadController 主进程内存（下载进行中 / 失败）
 * 3. apk 文件态（ApkInstaller.isDownloaded，「已下完」唯一判据，进程重启不丢）
 *
 * 优先级：下载中 > 下载失败 > 已下完 > 有新版未下 > 已最新。
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/29
 */
class UpdateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _checkingFlow = MutableStateFlow(false)
    val checkingFlow: StateFlow<Boolean> = _checkingFlow.asStateFlow()

    // 会话级：用户在「已下完」弹窗点关闭后，切回前台不再反复弹安装提示
    private var installPromptDismissed = false

    init {
        // 下载进度 / 缓存变化时刷新状态
        viewModelScope.launch {
            var prevStatus: DownloadStatus? = null
            DownloadController.flow.collect { download ->
                val justEnded = prevStatus == DownloadStatus.DOWNLOADING &&
                        download.status != DownloadStatus.DOWNLOADING
                // 兜底：从任何非 IDLE 状态变为 IDLE，都视为下载刚结束
                val justFinished = prevStatus != null &&
                        prevStatus != DownloadStatus.IDLE &&
                        download.status == DownloadStatus.IDLE
                prevStatus = download.status
                when {
                    justEnded -> recompute(OpenMode.DownloadEnded)
                    justFinished -> {
                        // 稍等一下让文件系统和 DataStore 稳定
                        kotlinx.coroutines.delay(300)
                        recompute(OpenMode.DownloadEnded)
                    }
                    else -> recompute(OpenMode.None)
                }
            }
        }
        viewModelScope.launch {
            UpdateManager.updateStateFlow().collect { recompute(OpenMode.None) }
        }
    }

    /** App 启动：开启自动检查、缓存不新鲜时懒触发检查。 */
    fun checkOnLaunch() {
        viewModelScope.launch {
            UpdateManager.checkOnLaunch()
            recompute(OpenMode.Auto)
        }
    }

    /** 启动入口 / 通知点击：按状态决定弹窗。 */
    fun onEntry() {
        viewModelScope.launch {
            recompute(OpenMode.Auto)
        }
    }

    /** 回到前台：只浮出下载相关态。 */
    fun onForeground() {
        viewModelScope.launch {
            recompute(OpenMode.Resume)
        }
    }

    /** 手动检查更新。 */
    fun checkManually() {
        viewModelScope.launch {
            if (_checkingFlow.value) return@launch
            _checkingFlow.value = true
            _uiState.value = _uiState.value.copy(checking = true)

            val result = UpdateManager.checkForUpdate(force = true)

            _checkingFlow.value = false
            _uiState.value = _uiState.value.copy(checking = false)

            when (result) {
                is UpdateRepository.CheckResult.Failed -> showCheckFailed(CheckFailedReason.Generic)
                is UpdateRepository.CheckResult.RateLimited -> showCheckFailed(CheckFailedReason.RateLimited)
                is UpdateRepository.CheckResult.NoApk -> showCheckFailed(CheckFailedReason.NoApk)
                else -> {
                    // 主动检查到结果：清掉残留的下载失败态
                    if (DownloadController.flow.value.status == DownloadStatus.FAILED) {
                        DownloadController.reset()
                    }
                    recompute(OpenMode.Force)
                }
            }
        }
    }

    private fun showCheckFailed(reason: CheckFailedReason) {
        _uiState.value = _uiState.value.copy(
            showCheckFailedDialog = true,
            checkFailedReason = reason
        )
    }

    fun dismissCheckFailedDialog() {
        _uiState.value = _uiState.value.copy(showCheckFailedDialog = false)
    }

    /** 开始下载更新。缺少通知权限时弹出提示，但不阻塞下载。 */
    fun onConfirmUpdate(context: Context) {
        if (!ApkInstaller.canInstall(context)) {
            ApkInstaller.gotoUnknownSourceSetting(context)
            return
        }
        val state = _uiState.value
        if (state.apkUrl.isBlank()) return

        installPromptDismissed = false

        // 通知权限提示：不阻塞下载，仅提醒用户可以开启通知
        if (!UpdateManager.canNotify(context)) {
            _uiState.value = state.copy(showNotificationPermissionDialog = true)
        }

        DownloadService.start(context, state.version, state.apkUrl, state.apkSize)
    }

    /** 关闭通知权限提示弹窗。 */
    fun dismissNotificationPermissionDialog() {
        _uiState.value = _uiState.value.copy(showNotificationPermissionDialog = false)
    }

    /** 安装已下载的 APK。 */
    fun onInstall(context: Context) {
        val state = _uiState.value
        val file = ApkInstaller.apkFile(context, state.version)
        if (!ApkInstaller.isDownloaded(file, state.apkSize)) {
            onConfirmUpdate(context)
            return
        }
        if (!ApkInstaller.canInstall(context)) {
            ApkInstaller.gotoUnknownSourceSetting(context)
            return
        }
        _uiState.value = _uiState.value.copy(showDialog = false)
        ApkInstaller.installApk(context, file)
    }

    /** 转后台下载。 */
    fun onMoveToBackground() {
        _uiState.value = _uiState.value.copy(showDialog = false)
    }

    /** 关闭弹窗。 */
    fun dismiss() {
        when (_uiState.value.phase) {
            UpdatePhase.Failed -> DownloadController.reset()
            UpdatePhase.Downloaded -> installPromptDismissed = true
            else -> Unit
        }
        _uiState.value = _uiState.value.copy(showDialog = false)
    }

    private suspend fun recompute(openMode: OpenMode) {
        val cache = UpdateManager.updateStateFlow().first()
        val download = DownloadController.flow.value
        val currentVersion = UpdateManager.getCurrentVersion()

        val downloaded = UpdateManager.isDownloaded(
            com.mqd.updatelib.core.UpdateLibContext.getContext(),
            cache.latestVersion,
            cache.apkSize
        )
        val isNewer = cache.latestVersion.isNotBlank() &&
                UpdateChecker.isRemoteNewer(cache.latestVersion, currentVersion)

        val phase = when {
            download.status == DownloadStatus.DOWNLOADING -> UpdatePhase.Downloading
            download.status == DownloadStatus.FAILED -> UpdatePhase.Failed
            downloaded && isNewer -> UpdatePhase.Downloaded
            isNewer -> UpdatePhase.NewVersion
            else -> UpdatePhase.UpToDate
        }

        val version = if (phase == UpdatePhase.Downloading) download.version else cache.latestVersion

        val show = when (openMode) {
            OpenMode.None -> _uiState.value.showDialog
            OpenMode.Force -> true
            OpenMode.Auto -> when (phase) {
                UpdatePhase.Downloading, UpdatePhase.Downloaded, UpdatePhase.Failed -> true
                UpdatePhase.NewVersion -> true
                UpdatePhase.UpToDate -> _uiState.value.showDialog
            }
            OpenMode.Resume -> when (phase) {
                UpdatePhase.Downloaded -> !installPromptDismissed
                UpdatePhase.Failed -> true
                else -> _uiState.value.showDialog
            }
            OpenMode.DownloadEnded -> when (phase) {
                UpdatePhase.Downloaded, UpdatePhase.Failed -> true
                else -> _uiState.value.showDialog
            }
        }

        _uiState.value = _uiState.value.copy(
            showDialog = show,
            phase = phase,
            version = version,
            notes = cache.notes,
            apkUrl = cache.apkUrl,
            apkSize = cache.apkSize,
            progress = download.progress
        )
    }

    private enum class OpenMode {
        None,
        Auto,
        Resume,
        DownloadEnded,
        Force
    }

    enum class UpdatePhase {
        Downloading,
        Downloaded,
        NewVersion,
        UpToDate,
        Failed
    }

    enum class CheckFailedReason {
        Generic,
        RateLimited,
        NoApk
    }

    data class UiState(
        val showDialog: Boolean = false,
        val phase: UpdatePhase = UpdatePhase.NewVersion,
        val version: String = "",
        val notes: String = "",
        val apkUrl: String = "",
        val apkSize: Long = 0L,
        val progress: Int = 0,
        val checking: Boolean = false,
        val showCheckFailedDialog: Boolean = false,
        val checkFailedReason: CheckFailedReason = CheckFailedReason.Generic,
        val showNotificationPermissionDialog: Boolean = false
    )
}
