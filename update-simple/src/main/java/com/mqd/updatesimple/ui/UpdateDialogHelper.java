package com.mqd.updatesimple.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mqd.updatesimple.R;
import com.mqd.updatesimple.UpdateManager;
import com.mqd.updatesimple.core.Core;

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
        } catch (Exception ignored) {}
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
                    if (onConfirm != null) onConfirm.run(); d.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showRateLimitedDialog(Context context, Runnable onConfirm) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_check_rate_limited_title)
                .setMessage(R.string.updatelib_update_check_rate_limited_message)
                .setPositiveButton(R.string.updatelib_confirm, (d, w) -> {
                    if (onConfirm != null) onConfirm.run(); d.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showNewVersionDialog(Context context, String version,
                                                    String releaseNotes, String detailsUrl) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView tvVersion = new TextView(context);
        tvVersion.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvVersion.setTextSize(16);
        tvVersion.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ((LinearLayout.LayoutParams) tvVersion.getLayoutParams()).bottomMargin = (int) (8 * density);
        String verText = UpdateManager.getCurrentVersion()
                + " \u2192 " + Core.UpdateChecker.displayVersion(version);
        tvVersion.setText(verText);
        root.addView(tvVersion);

        TextView tvReleaseNotes = new TextView(context);
        tvReleaseNotes.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvReleaseNotes.setTextSize(14);
        tvReleaseNotes.setMaxLines(10);
        tvReleaseNotes.setMovementMethod(ScrollingMovementMethod.getInstance());
        root.addView(tvReleaseNotes);

        WebView webView = new WebView(context);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        webView.setVisibility(View.GONE);
        root.addView(webView);

        if (releaseNotes != null && !Core.FallbackChecker.isHtml(releaseNotes)) {
            tvReleaseNotes.setVisibility(View.VISIBLE);
            tvReleaseNotes.setText(releaseNotes);
        } else if (releaseNotes != null) {
            tvReleaseNotes.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.getSettings().setJavaScriptEnabled(false);
            int maxH = context.getResources().getDisplayMetrics().heightPixels / 3;
            ViewGroup.LayoutParams lp = webView.getLayoutParams();
            lp.height = maxH;
            webView.setLayoutParams(lp);
            webView.loadDataWithBaseURL(null, releaseNotes, "text/html", "UTF-8", null);
        }

        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_available_title)
                .setView(root)
                .setPositiveButton(R.string.updatelib_update_to_site, (d, w) -> {
                    d.dismiss();
                    try {
                        String url = detailsUrl != null ? detailsUrl : UpdateManager.getReleasesPageUrl();
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton(R.string.updatelib_cancel, (d, w) -> d.dismiss())
                .show();
    }

    // ─── 一键检查 ──────────────────────────────────────

    public static void checkAndShowUpdateDialog(Activity activity) {
        checkAndShowUpdateDialog(activity, null);
    }

    public static void checkAndShowUpdateDialog(Activity activity, Runnable onDismiss) {
        Core.UpdateRepository.checkAndCache(activity, true,
                UpdateManager.getCurrentVersion(),
                result -> mainHandler.post(() -> {
                    switch (result.type) {
                        case Core.UpdateRepository.CheckResult.NEW_VERSION:
                            showNewVersionDialog(activity,
                                    result.state.latestVersion,
                                    result.state.notes,
                                    result.state.detailsUrl);
                            break;
                        case Core.UpdateRepository.CheckResult.UP_TO_DATE:
                            showAlreadyLatestDialog(activity); break;
                        case Core.UpdateRepository.CheckResult.FAILED:
                            showCheckFailedDialog(activity, () -> openReleasesPage(activity)); break;
                        case Core.UpdateRepository.CheckResult.RATE_LIMITED:
                            showRateLimitedDialog(activity, () -> openReleasesPage(activity)); break;
                    }
                    if (onDismiss != null) onDismiss.run();
                }));
    }
}
