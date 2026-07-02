package xiaofeixia.gesture

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.mqd.updatelib.UpdateManager
import com.mqd.updatelib.core.UpdateRepository
import com.mqd.updatelib.core.UpdateState
import com.mqd.updatelib.download.DownloadController
import com.mqd.updatelib.ui.UpdateDialogHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 非 Compose 项目接入示例。
 *
 * 演示如何用传统 View 体系调用 update-lib：
 * 1. 手动检查更新 → 根据 CheckResult 弹对应对话框
 * 2. 确认下载 → 弹进度弹窗，后台下载
 * 3. 下载完成 → 安装 APK
 *
 * 使用时复制此文件即可，不需要 update-compose 模块。
 */
class SampleViewActivity : ComponentActivity() {

    private lateinit var btnCheckUpdate: Button
    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    /** 下载进度监听 Job，用于在 Activity 销毁时取消 */
    private var downloadCollectJob: Job? = null

    /** 通知权限请求器（仅提示，不阻塞下载） */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果不影响下载，通知会由 UpdateNotifications 自行判断 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---- 纯 View 布局（也可用 XML，这里用代码方便演示） ----
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        tvCurrentVersion = TextView(this).apply {
            text = "当前版本：${UpdateManager.getCurrentVersion()}"
            textSize = 18f
        }
        root.addView(tvCurrentVersion)

        tvStatus = TextView(this).apply {
            text = ""
            textSize = 14f
            setPadding(0, 24, 0, 24)
        }
        root.addView(tvStatus)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar)

        btnCheckUpdate = Button(this).apply {
            text = "检查更新"
        }
        root.addView(btnCheckUpdate)

        setContentView(root)

        // ---- 检查更新按钮 ----
        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }

        // ---- 常驻监听下载状态（回到前台时恢复进度） ----
        observeDownloadState()

        // ---- 打开时自动检查更新 ----
        checkForUpdate()
    }

    /**
     * 手动检查更新。
     */
    private fun checkForUpdate() {
        btnCheckUpdate.isEnabled = false
        tvStatus.text = "正在检查更新…"

        lifecycleScope.launch {
            val result = UpdateManager.checkForUpdate(force = true)

            btnCheckUpdate.isEnabled = true
            tvStatus.text = ""

            when (result) {
                is UpdateRepository.CheckResult.NewVersion -> {
                    showNewVersionDialog(result.state)
                }

                is UpdateRepository.CheckResult.UpToDate -> {
                    UpdateDialogHelper.showAlreadyLatestDialog(this@SampleViewActivity)
                }

                is UpdateRepository.CheckResult.Failed -> {
                    UpdateDialogHelper.showCheckFailedDialog(
                        this@SampleViewActivity,
                        onConfirm = {
                            // 点「确认」跳转 GitHub Releases 页
                            openReleasesPage()
                        }
                    )
                }

                is UpdateRepository.CheckResult.RateLimited -> {
                    UpdateDialogHelper.showRateLimitedDialog(
                        this@SampleViewActivity,
                        onConfirm = { openReleasesPage() }
                    )
                }

                is UpdateRepository.CheckResult.NoApk -> {
                    UpdateDialogHelper.showNoApkDialog(
                        this@SampleViewActivity,
                        onConfirm = { openReleasesPage() }
                    )
                }

                UpdateRepository.CheckResult.Skipped -> {
                    // 缓存未过期，跳过检查（可读取缓存状态）
                    tvStatus.text = "检查间隔未到，已使用缓存"
                }
            }
        }
    }

    /**
     * 发现新版本 → 弹更新说明弹窗 → 确认后开始下载。
     */
    private fun showNewVersionDialog(state: UpdateState) {
        UpdateDialogHelper.showUpdateAvailableDialog(
            context = this,
            version = state.latestVersion,
            releaseNotes = state.notes,
            apkUrl = state.apkUrl,
            apkSize = state.apkSize,
            onConfirm = { startDownload(state) }
        )
    }

    /**
     * 开始下载并弹出进度对话框。
     * 缺少通知权限时弹出提示，但不阻塞下载。
     */
    private fun startDownload(state: UpdateState) {
        // 先检查安装权限
        if (!UpdateManager.canInstall(this)) {
            UpdateManager.gotoUnknownSourceSetting(this)
            return
        }

        // 通知权限提示：不阻塞下载，仅提醒用户可以开启通知
        if (!UpdateManager.canNotify(this)) {
            UpdateDialogHelper.showNotificationPermissionDialog(
                this,
                onConfirm = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        UpdateManager.downloadUpdate(this, state.latestVersion, state.apkUrl, state.apkSize)

        // 弹出下载进度对话框
        val (dialog, job) = UpdateDialogHelper.showDownloadProgressDialog(
            this,
            onDismiss = {
                // 用户点了「后台下载」，对话框关闭，下载继续
                tvStatus.text = "正在后台下载…"
            }
        )
        downloadCollectJob = job

        // 对话框关闭后，根据下载结果决定下一步
        lifecycleScope.launch {
            // 等待进度对话框关闭（内部 flow 监听 IDLE/FAILED 时会自动 dismiss）
            while (dialog.isShowing) {
                kotlinx.coroutines.delay(200)
            }

            // 检查磁盘上的 APK 文件 → 下载完成则拉起安装
            val apkFile = com.mqd.updatelib.download.ApkInstaller
                .apkFile(this@SampleViewActivity, state.latestVersion)
            if (com.mqd.updatelib.download.ApkInstaller
                .isDownloaded(apkFile, state.apkSize)
            ) {
                tvStatus.text = "下载完成，正在安装…"
                UpdateManager.installUpdate(this@SampleViewActivity, state.latestVersion)
            }
        }
    }

    /**
     * 常驻监听下载状态，用于恢复 UI（如从后台回来时下载已完成）。
     */
    private fun observeDownloadState() {
        lifecycleScope.launch {
            DownloadController.flow.collect { uiState ->
                when (uiState.status) {
                    DownloadController.DownloadStatus.DOWNLOADING -> {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = uiState.progress
                        tvStatus.text = "下载中 ${uiState.progress}%"
                    }

                    DownloadController.DownloadStatus.FAILED -> {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "下载失败"
                    }

                    DownloadController.DownloadStatus.IDLE -> {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    /**
     * 打开 GitHub Releases 页面。
     */
    private fun openReleasesPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse(UpdateManager.getReleasesPageUrl())))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        downloadCollectJob?.cancel()
        super.onDestroy()
    }
}
