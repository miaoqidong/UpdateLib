package com.mqd.updatelib.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.mqd.updatelib.R
import com.mqd.updatelib.UpdateManager
import com.mqd.updatelib.core.FallbackChecker
import com.mqd.updatelib.core.UpdateChecker
import android.webkit.WebView
import com.mqd.updatelib.download.DownloadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
            tvReleaseNotes.text = releaseNotes
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
}
