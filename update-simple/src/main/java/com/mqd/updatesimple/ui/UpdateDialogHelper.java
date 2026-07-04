package com.mqd.updatesimple.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.webkit.WebView;
import android.view.ViewGroup;

import com.mqd.updatesimple.R;
import com.mqd.updatesimple.UpdateManager;
import com.mqd.updatesimple.core.FallbackChecker;
import com.mqd.updatesimple.core.UpdateChecker;
import com.mqd.updatesimple.core.UpdateRepository;
import com.mqd.updatesimple.core.UpdateState;

/**
 * 简化版更新对话框——只展示版本信息和跳转链接，不含下载/安装功能。
 */
public class UpdateDialogHelper {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private UpdateDialogHelper() {}

    public static void openReleasesPage(Context context) {
        try {
            String url = UpdateManager.getReleasesPageUrl();
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    public static AlertDialog showAlreadyLatestDialog(Context context) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_already_latest_title)
                .setPositiveButton(R.string.updatelib_confirm, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showCheckFailedDialog(Context context, Runnable onConfirm) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_check_failed_title)
                .setMessage(R.string.updatelib_update_check_failed_message)
                .setPositiveButton(R.string.updatelib_confirm, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showRateLimitedDialog(Context context, Runnable onConfirm) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_check_rate_limited_title)
                .setMessage(R.string.updatelib_update_check_rate_limited_message)
                .setPositiveButton(R.string.updatelib_confirm, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showNewVersionDialog(Context context, String version,
                                                    String releaseNotes, String detailsUrl) {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.updatelib_dialog_update, null);

        TextView tvVersion = dialogView.findViewById(R.id.tv_version);
        TextView tvReleaseNotes = dialogView.findViewById(R.id.tv_release_notes);
        WebView webView = dialogView.findViewById(R.id.webview_release_notes);

        String currentVersion = UpdateManager.getCurrentVersion();
        String versionText = currentVersion + " \u2192 " + UpdateChecker.displayVersion(version);
        if (tvVersion != null) tvVersion.setText(versionText);

        if (releaseNotes != null && !FallbackChecker.isHtmlContent(releaseNotes)) {
            if (tvReleaseNotes != null) {
                tvReleaseNotes.setVisibility(View.VISIBLE);
                tvReleaseNotes.setMovementMethod(ScrollingMovementMethod.getInstance());
                tvReleaseNotes.setText(releaseNotes);
            }
            if (webView != null) webView.setVisibility(View.GONE);
        } else if (releaseNotes != null) {
            if (tvReleaseNotes != null) tvReleaseNotes.setVisibility(View.GONE);
            if (webView != null) {
                webView.setVisibility(View.VISIBLE);
                webView.getSettings().setJavaScriptEnabled(false);
                int maxHeight = context.getResources().getDisplayMetrics().heightPixels / 3;
                ViewGroup.LayoutParams lp = webView.getLayoutParams();
                lp.height = maxHeight;
                webView.setLayoutParams(lp);
                webView.loadDataWithBaseURL(null, releaseNotes, "text/html", "UTF-8", null);
            }
        }

        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_available_title)
                .setView(dialogView)
                .setPositiveButton(R.string.updatelib_update_to_site, (d, w) -> {
                    d.dismiss();
                    try {
                        String url = detailsUrl != null ? detailsUrl : UpdateManager.getReleasesPageUrl();
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.updatelib_cancel, (d, w) -> d.dismiss())
                .show();
    }

    // ──────────────── 一键检查并弹窗 ────────────────

    public static void checkAndShowUpdateDialog(Activity activity) {
        checkAndShowUpdateDialog(activity, null);
    }

    public static void checkAndShowUpdateDialog(Activity activity, Runnable onDismiss) {
        UpdateRepository.checkAndCache(activity, true,
                UpdateManager.getCurrentVersion(),
                result -> mainHandler.post(() -> {
                    switch (result.type) {
                        case NEW_VERSION:
                            showNewVersionDialog(activity,
                                    result.state.latestVersion,
                                    result.state.notes,
                                    result.state.detailsUrl);
                            break;
                        case UP_TO_DATE:
                            showAlreadyLatestDialog(activity);
                            break;
                        case FAILED:
                            showCheckFailedDialog(activity,
                                    () -> openReleasesPage(activity));
                            break;
                        case RATE_LIMITED:
                            showRateLimitedDialog(activity,
                                    () -> openReleasesPage(activity));
                            break;
                    }
                    if (onDismiss != null) onDismiss.run();
                }));
    }
}
