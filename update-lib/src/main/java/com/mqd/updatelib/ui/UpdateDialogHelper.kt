package com.mqd.updatelib.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.mqd.updatelib.R
import com.mqd.updatelib.UpdateManager
import com.mqd.updatelib.core.FallbackChecker
import com.mqd.updatelib.core.UpdateChecker
import com.mqd.updatelib.core.UpdateRepository
import com.mqd.updatelib.core.UpdateState
import com.mqd.updatelib.download.ApkInstaller
import com.mqd.updatelib.download.DownloadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * View-based 更新对话框辅助类。
 *
 * 为非 Compose 项目提供传统的 AlertDialog 界面。
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/29
 */
object UpdateDialogHelper {

    /**
     * 构建带详情跳转按钮的自定义标题视图。
     */
    private fun buildCustomTitleView(context: Context, titleRes: Int): View {
        val titleView = LayoutInflater.from(context).inflate(R.layout.updatelib_dialog_title_with_link, null)
        titleView.findViewById<TextView>(R.id.tv_dialog_title).setText(titleRes)
        titleView.findViewById<LinearLayout>(R.id.btn_open_link).setOnClickListener {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.getReleasesPageUrl())))
        }
        return titleView
    }

    /**
     * 显示新版本可用对话框。
     *
     * @param context 上下文
     * @param version 新版本号
     * @param releaseNotes 更新说明
     * @param apkUrl 下载 URL
     * @param apkSize 文件大小
     * @param onConfirm 点击"立即更新"回调
     * @param onCancel 点击"取消"回调
     */
    @JvmStatic
    fun showUpdateAvailableDialog(
        context: Context,
        version: String,
        releaseNotes: String,
        apkUrl: String,
        apkSize: Long,
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ): AlertDialog {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.updatelib_dialog_update, null)
        val tvVersion = dialogView.findViewById<TextView>(R.id.tv_version)
        val tvReleaseNotes = dialogView.findViewById<TextView>(R.id.tv_release_notes)
        val webView = dialogView.findViewById<WebView>(R.id.webview_release_notes)

        tvVersion.text = context.getString(
            R.string.updatelib_update_version_compare,
            UpdateManager.getCurrentVersion(),
            UpdateChecker.displayVersion(version)
        )

        if (!FallbackChecker.isHtmlContent(releaseNotes)) {
            // 纯文本内容：用 TextView 显示
            tvReleaseNotes.visibility = View.VISIBLE
            webView.visibility = View.GONE
            tvReleaseNotes.movementMethod = ScrollingMovementMethod.getInstance()
            if (isPlainUrl(releaseNotes)) {
                tvReleaseNotes.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                tvReleaseNotes.text = android.text.Html.fromHtml(
                    "<a href=\"$releaseNotes\">$releaseNotes</a>",
                    android.text.Html.FROM_HTML_MODE_LEGACY
                )
            } else {
                tvReleaseNotes.text = releaseNotes
            }
        } else {
            // HTML 内容：用 WebView 渲染
            tvReleaseNotes.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.settings.javaScriptEnabled = false
            // 限制 WebView 最大高度为屏幕高度的 1/3，超出部分可滚动查看
            val maxHeight = (context.resources.displayMetrics.heightPixels / 3)
            val lp = webView.layoutParams
            lp.height = maxHeight
            webView.layoutParams = lp
            webView.loadDataWithBaseURL(null, releaseNotes, "text/html", "UTF-8", null)
        }

        return AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_available_title))
            .setView(dialogView)
            .setPositiveButton(R.string.updatelib_update_now) { dialog, _ ->
                onConfirm?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.updatelib_cancel) { dialog, _ ->
                onCancel?.invoke()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 显示下载进度对话框。
     *
     * @param context 上下文
     * @param onDismiss 对话框关闭回调
     * @return 对话框实例和 Job（用于取消监听）
     */
    @JvmStatic
    fun showDownloadProgressDialog(
        context: Context,
        onDismiss: (() -> Unit)? = null
    ): Pair<AlertDialog, Job> {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.updatelib_dialog_download, null)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tv_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)
        val tvProgress = dialogView.findViewById<TextView>(R.id.tv_progress)

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_downloading_label))
            .setView(dialogView)
            .setNegativeButton(R.string.updatelib_update_move_to_background) { d, _ ->
                d.dismiss()
                onDismiss?.invoke()
            }
            .setCancelable(false)
            .show()

        // 监听下载进度
        val job = CoroutineScope(Dispatchers.Main).launch {
            DownloadController.flow.collect { state ->
                when (state.status) {
                    DownloadController.DownloadStatus.DOWNLOADING -> {
                        progressBar.progress = state.progress
                        tvProgress.text = "${state.progress}%"
                        tvStatus.text = context.getString(R.string.updatelib_update_downloading_label)
                    }
                    DownloadController.DownloadStatus.FAILED -> {
                        dialog.dismiss()
                        showDownloadFailedDialog(context, null)
                    }
                    DownloadController.DownloadStatus.IDLE -> {
                        // 下载完成
                        dialog.dismiss()
                    }
                }
            }
        }

        return Pair(dialog, job)
    }

    /**
     * 显示下载失败对话框。
     *
     * @param context 上下文
     * @param onRetry 点击"重试"回调
     */
    @JvmStatic
    fun showDownloadFailedDialog(
        context: Context,
        onRetry: (() -> Unit)? = null
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_download_failed_title))
            .setMessage(R.string.updatelib_update_download_failed)
            .setPositiveButton(R.string.updatelib_update_retry) { dialog, _ ->
                onRetry?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.updatelib_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示已是最新版本对话框。
     *
     * @param context 上下文
     */
    @JvmStatic
    fun showAlreadyLatestDialog(context: Context): AlertDialog {
        return AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_already_latest_title))
            .setPositiveButton(R.string.updatelib_confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示检查失败对话框。
     *
     * @param context 上下文
     * @param onConfirm 点击"确认"回调（通常用于跳转到 GitHub）
     */
    @JvmStatic
    fun showCheckFailedDialog(
        context: Context,
        onConfirm: (() -> Unit)? = null
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_check_failed_title))
            .setMessage(R.string.updatelib_update_check_failed_message)
            .setPositiveButton(R.string.updatelib_confirm) { dialog, _ ->
                onConfirm?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.updatelib_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示限流对话框。
     *
     * @param context 上下文
     * @param onConfirm 点击"确认"回调
     */
    @JvmStatic
    fun showRateLimitedDialog(
        context: Context,
        onConfirm: (() -> Unit)? = null
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_check_rate_limited_title))
            .setMessage(R.string.updatelib_update_check_rate_limited_message)
            .setPositiveButton(R.string.updatelib_confirm) { dialog, _ ->
                onConfirm?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.updatelib_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示新版本准备中对话框。
     *
     * @param context 上下文
     * @param onConfirm 点击"确认"回调
     */
    @JvmStatic
    fun showNoApkDialog(
        context: Context,
        onConfirm: (() -> Unit)? = null
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_check_no_apk_title))
            .setMessage(R.string.updatelib_update_check_no_apk_message)
            .setPositiveButton(R.string.updatelib_confirm) { dialog, _ ->
                onConfirm?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.updatelib_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示通知权限说明弹窗。
     *
     * 在请求 [android.Manifest.permission.POST_NOTIFICATIONS] 之前调用，
     * 向用户解释为什么需要通知权限。
     *
     * @param context 上下文
     * @param onConfirm 点击"去开启"回调（通常用于发起权限请求）
     * @param onCancel 点击"拒绝"回调
     */
    @JvmStatic
    fun showNotificationPermissionDialog(
        context: Context,
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(R.string.updatelib_notification_permission_title)
            .setMessage(R.string.updatelib_notification_permission_rationale)
            .setPositiveButton(R.string.updatelib_confirm) { dialog, _ ->
                onConfirm?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.updatelib_deny) { dialog, _ ->
                onCancel?.invoke()
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示统一更新对话框（版本信息 + 内联进度条）。
     *
     * 将版本说明和下载进度整合在同一个对话框中，类似 Compose 版的多阶段切换：
     * - 已下载：正按钮显示"点击安装"
     * - 未下载：显示版本信息，"忽略此版本"和"立即更新"按钮
     * - 点击"立即更新"后内联切换到进度条，"后台下载"按钮
     * - 下载失败时显示错误信息，"重试"按钮
     * - 下载完成自动关闭对话框
     *
     * @param context 上下文
     * @param version 新版本号
     * @param releaseNotes 更新说明
     * @param apkUrl 下载 URL
     * @param apkSize 文件大小
     * @param onConfirm 点击"立即更新"或"重试"回调
     * @param onIgnore 点击"忽略此版本"回调
     * @param onDismiss 对话框关闭回调
     * @param onInstall 点击"点击安装"回调（非 null 表示 APK 已下载，正按钮改为安装）
     * @return 控制器，可用于取消监听
     */
    @JvmStatic
    fun showUpdateDialog(
        context: Context,
        version: String,
        releaseNotes: String,
        apkUrl: String,
        apkSize: Long,
        onConfirm: (() -> Unit)? = null,
        onIgnore: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
        onInstall: (() -> Unit)? = null
    ): UpdateDialogController {
        val dialogView = LayoutInflater.from(context).inflate(
            R.layout.updatelib_dialog_update_with_progress, null
        )
        val layoutProgress = dialogView.findViewById<LinearLayout>(R.id.layout_progress)
        val tvVersion = dialogView.findViewById<TextView>(R.id.tv_version)
        val tvReleaseNotes = dialogView.findViewById<TextView>(R.id.tv_release_notes)
        val webView = dialogView.findViewById<WebView>(R.id.webview_release_notes)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tv_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)
        val tvProgress = dialogView.findViewById<TextView>(R.id.tv_progress)

        // 版本信息
        tvVersion.text = context.getString(
            R.string.updatelib_update_version_compare,
            UpdateManager.getCurrentVersion(),
            UpdateChecker.displayVersion(version)
        )

        if (!FallbackChecker.isHtmlContent(releaseNotes)) {
            tvReleaseNotes.visibility = View.VISIBLE
            webView.visibility = View.GONE
            tvReleaseNotes.movementMethod = ScrollingMovementMethod.getInstance()
            if (isPlainUrl(releaseNotes)) {
                tvReleaseNotes.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                tvReleaseNotes.text = android.text.Html.fromHtml(
                    "<a href=\"$releaseNotes\">$releaseNotes</a>",
                    android.text.Html.FROM_HTML_MODE_LEGACY
                )
            } else {
                tvReleaseNotes.text = releaseNotes
            }
        } else {
            tvReleaseNotes.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.settings.javaScriptEnabled = false
            val maxHeight = (context.resources.displayMetrics.heightPixels / 3)
            val lp = webView.layoutParams
            lp.height = maxHeight
            webView.layoutParams = lp
            webView.loadDataWithBaseURL(null, releaseNotes, "text/html", "UTF-8", null)
        }

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_available_title))
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val scope = CoroutineScope(Dispatchers.Main)
        var observeJob: Job? = null

        // 只设按钮文字，不设点击监听（AlertDialog 默认点击后自动 dismiss）
        val isDownloaded = onInstall != null
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
            context.getString(if (isDownloaded) R.string.updatelib_update_install_now else R.string.updatelib_update_now),
            { _, _ -> }
        )
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
            context.getString(R.string.updatelib_update_ignore_version),
            { _, _ -> }
        )
        dialog.show()

        if (isDownloaded) {
            // APK 已下载：正按钮改为安装，隐藏负按钮
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).visibility = View.GONE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                onDismiss?.invoke()
                onInstall?.invoke()
            }
        } else {
            // 正常下载流程

            // 用 View.setOnClickListener 覆盖默认行为，阻止自动 dismiss
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!UpdateManager.canInstall(context)) {
                    UpdateManager.gotoUnknownSourceSetting(context)
                    return@setOnClickListener
                }
                // 在版本信息下方展开进度条
                layoutProgress.visibility = View.VISIBLE
                it.visibility = View.GONE // 隐藏「立即更新」
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                    setText(R.string.updatelib_update_move_to_background)
                    setOnClickListener {
                        dialog.dismiss()
                        onDismiss?.invoke()
                    }
                }
                onConfirm?.invoke()
                var downloadStarted = false
                observeJob = scope.launch {
                    DownloadController.flow.collect { state ->
                        when (state.status) {
                            DownloadController.DownloadStatus.DOWNLOADING -> {
                                downloadStarted = true
                                progressBar.progress = state.progress
                                tvProgress.text = "${state.progress}%"
                                tvStatus.text = context.getString(R.string.updatelib_update_downloading_label)
                            }
                            DownloadController.DownloadStatus.FAILED -> {
                                tvStatus.text = context.getString(R.string.updatelib_update_download_failed)
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                                    visibility = View.VISIBLE
                                    setText(R.string.updatelib_update_retry)
                                    setOnClickListener {
                                        layoutProgress.visibility = View.GONE
                                        progressBar.progress = 0
                                        tvProgress.text = ""
                                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
                                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                                            setText(R.string.updatelib_update_move_to_background)
                                            setOnClickListener {
                                                dialog.dismiss()
                                                onDismiss?.invoke()
                                            }
                                        }
                                        onConfirm?.invoke()
                                    }
                                }
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                                    setText(R.string.updatelib_cancel)
                                    setOnClickListener {
                                        dialog.dismiss()
                                        onDismiss?.invoke()
                                    }
                                }
                            }
                            DownloadController.DownloadStatus.IDLE -> {
                                // 只有下载真正启动过的 IDLE 才算结束，忽略初始的空闲状态
                                if (downloadStarted) {
                                    dialog.dismiss()
                                    onDismiss?.invoke()
                                }
                            }
                        }
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                onIgnore?.invoke()
                dialog.dismiss()
                onDismiss?.invoke()
            }
        }

        dialog.setOnDismissListener {
            observeJob?.cancel()
        }

        return UpdateDialogController(dialog, observeJob)
    }

    /**
     * 打开 Releases 页面。
     *
     * 跳转到 GitHub Releases 页面或备用源详情页，用于在检查失败等场景下
     * 让用户手动查看更新。
     *
     * @param context 上下文
     */
    @JvmStatic
    fun openReleasesPage(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.getReleasesPageUrl())))
        } catch (_: Exception) {
        }
    }

    /** 判断内容是否为纯 URL（http/https 开头且不含空格/换行）。 */
    private fun isPlainUrl(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        val s = content.trim()
        return (s.startsWith("http://") || s.startsWith("https://"))
                && !s.contains(" ") && !s.contains("\n")
    }

    /** 获取 URL 指向的页面内容（纯文本或 HTML），失败返回 null。 */
    private suspend fun fetchUrlContent(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                return@withContext body
            }
            conn.disconnect()
        } catch (_: Exception) {}
        null
    }

    /**
     * 一键检查更新并自动展示对应对话框。
     *
     * 封装了"检查 → 展示 → 下载 → 安装"的完整流程，调用方只需一行代码即可接入。
     *
     * 内部自动处理：
     * - 新版本：展示统一更新对话框（含下载进度），如 APK 已下载则显示安装按钮
     * - 已是最新：展示「已是最新版本」提示
     * - 检查失败 / 限流 / 无 APK：展示对应错误对话框，确认按钮跳转到 Releases 页面
     * - 未知来源安装权限：未开启时自动跳转系统设置
     *
     * Kotlin 用法：
     * ```
     * UpdateDialogHelper.checkAndShowUpdateDialog(this)
     * ```
     *
     * Java 用法：
     * ```
     * UpdateDialogHelper.checkAndShowUpdateDialog(this, null);
     * ```
     *
     * @param activity 当前 Activity
     * @param onDismiss 对话框关闭回调（可选）
     */
    @JvmStatic
    @JvmOverloads
    fun checkAndShowUpdateDialog(
        activity: Activity,
        onDismiss: (() -> Unit)? = null
    ) {
        UpdateManager.checkForUpdate(true) { result ->
            when (result) {
                is UpdateRepository.CheckResult.NewVersion -> {
                    val notes = result.state.notes
                    if (isPlainUrl(notes)) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val fetched = fetchUrlContent(notes)
                            val state = if (!fetched.isNullOrBlank()) {
                                result.state.copy(notes = fetched)
                            } else {
                                result.state
                            }
                            showNewVersionDialog(activity, state, onDismiss)
                        }
                    } else {
                        showNewVersionDialog(activity, result.state, onDismiss)
                    }
                }
                is UpdateRepository.CheckResult.UpToDate -> {
                    showAlreadyLatestDialog(activity)
                }
                is UpdateRepository.CheckResult.Failed -> {
                    showCheckFailedDialog(activity) {
                        openReleasesPage(activity)
                    }
                }
                is UpdateRepository.CheckResult.RateLimited -> {
                    showRateLimitedDialog(activity) {
                        openReleasesPage(activity)
                    }
                }
                is UpdateRepository.CheckResult.NoApk -> {
                    showNoApkDialog(activity) {
                        openReleasesPage(activity)
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * 展示新版本对话框的内部实现。
     *
     * 判断 APK 是否已下载，决定展示「安装」还是「下载」按钮。
     * 点击下载前会检查未知来源安装权限。
     */
    private fun showNewVersionDialog(
        activity: Activity,
        state: UpdateState,
        onDismiss: (() -> Unit)?
    ) {
        val apkFile = ApkInstaller.apkFile(activity, state.latestVersion)
        val alreadyDownloaded = ApkInstaller.isDownloaded(apkFile, state.apkSize)

        showUpdateDialog(
            context = activity,
            version = state.latestVersion,
            releaseNotes = state.notes,
            apkUrl = state.apkUrl,
            apkSize = state.apkSize,
            onConfirm = {
                if (UpdateManager.canInstall(activity)) {
                    UpdateManager.downloadUpdate(
                        activity, state.latestVersion, state.apkUrl, state.apkSize
                    )
                } else {
                    UpdateManager.gotoUnknownSourceSetting(activity)
                }
            },
            onIgnore = { /* 暂不支持忽略版本 */ },
            onDismiss = {
                onDismiss?.invoke()
                if (ApkInstaller.isDownloaded(apkFile, state.apkSize)) {
                    UpdateManager.installUpdate(activity, state.latestVersion)
                }
            },
            onInstall = if (alreadyDownloaded) {
                { UpdateManager.installUpdate(activity, state.latestVersion) }
            } else null
        )
    }
}

/**
 * 统一更新对话框控制器。
 *
 * 用于取消下载状态监听或在外部关闭对话框。
 */
class UpdateDialogController(
    private val dialog: AlertDialog,
    private val observeJob: Job?
) {
    /** 关闭对话框并取消监听。 */
    fun dismiss() {
        observeJob?.cancel()
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    /** 取消下载状态监听（对话框关闭时自动调用）。 */
    fun cancel() {
        observeJob?.cancel()
    }
}
