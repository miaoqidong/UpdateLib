package com.mqd.updatejava.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mqd.updatejava.R;
import com.mqd.updatejava.UpdateManager;
import com.mqd.updatejava.core.UpdateCore;
import com.mqd.updatejava.download.ApkInstaller;

/**
 * View-based 更新对话框辅助类（纯 Java，布局全用代码构建，无 XML 布局依赖）。
 */
public class UpdateDialogHelper {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 跨方法引用的 view ID（布局 XML 已移除，用 generateViewId）
    private static final int ID_BTN_LINK = View.generateViewId();
    private static final int ID_LAYOUT_PROGRESS = View.generateViewId();
    private static final int ID_TV_STATUS = View.generateViewId();
    private static final int ID_PROGRESS_BAR = View.generateViewId();
    private static final int ID_TV_PROGRESS = View.generateViewId();

    private UpdateDialogHelper() {}

    // ════════════════════ 对话框控制器 ════════════════════

    public static class Controller {
        private final AlertDialog dialog;
        Controller(AlertDialog dialog) { this.dialog = dialog; }
        public void dismiss() { if (dialog != null && dialog.isShowing()) dialog.dismiss(); }
        public boolean isShowing() { return dialog != null && dialog.isShowing(); }
    }

    // ════════════════════ 工具方法 ════════════════════

    private static int dp(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int resolveColorAccent(Context ctx) {
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true);
        return tv.data;
    }

    // ════════════════════ 标题栏（代码构建） ════════════════════

    private static View buildTitleBar(Context context, int titleRes) {
        RelativeLayout root = new RelativeLayout(context);
        root.setClipToPadding(false);
        root.setClipChildren(false);
        root.setPadding(dp(context, 24), dp(context, 20), dp(context, 8), dp(context, 8));
        root.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setId(android.R.id.title);
        tvTitle.setText(titleRes);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvTitle.setTypeface(tvTitle.getTypeface(), Typeface.BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        RelativeLayout.LayoutParams titleLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.addRule(RelativeLayout.ALIGN_PARENT_START);
        titleLp.addRule(RelativeLayout.CENTER_VERTICAL);

        LinearLayout btnLink = new LinearLayout(context);
        btnLink.setId(ID_BTN_LINK);
        btnLink.setOrientation(LinearLayout.HORIZONTAL);
        btnLink.setGravity(Gravity.CENTER_VERTICAL);
        btnLink.setClickable(true);
        btnLink.setFocusable(true);
        TypedValue bgTv = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, bgTv, true);
        btnLink.setBackgroundResource(bgTv.resourceId);
        btnLink.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        btnLink.setOnClickListener(v -> openReleasesPage(context));

        TextView tvLink = new TextView(context);
        tvLink.setText("↗ " + context.getString(R.string.updatelib_update_view_details));
        tvLink.setTextColor(resolveColorAccent(context));
        tvLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnLink.addView(tvLink);

        RelativeLayout.LayoutParams btnLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        btnLp.addRule(RelativeLayout.CENTER_VERTICAL);
        root.addView(btnLink, btnLp);

        titleLp.addRule(RelativeLayout.START_OF, ID_BTN_LINK);
        root.addView(tvTitle, titleLp);

        return root;
    }

    // ════════════════════ 更新内容视图（代码构建） ════════════════════

    private static View buildUpdateContentView(Context context, String version, String releaseNotes,
                                                String currentVersion) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        root.setPadding(pad, pad, pad, pad);

        // Version comparison
        TextView tvVersion = new TextView(context);
        tvVersion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvVersion.setTypeface(tvVersion.getTypeface(), Typeface.BOLD);
        tvVersion.setText(context.getString(R.string.updatelib_update_version_compare,
                currentVersion, UpdateCore.displayVersion(version)));
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verLp.bottomMargin = dp(context, 8);
        root.addView(tvVersion, verLp);

        // Release notes
        TextView tvNotes = new TextView(context);
        tvNotes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvNotes.setMaxLines(10);
        tvNotes.setVerticalScrollBarEnabled(true);
        if (releaseNotes != null && !releaseNotes.trim().isEmpty()) {
            tvNotes.setVisibility(View.VISIBLE);
            if (UpdateCore.isHtmlContent(releaseNotes)) {
                tvNotes.setMovementMethod(LinkMovementMethod.getInstance());
                tvNotes.setText(Html.fromHtml(releaseNotes, Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvNotes.setMovementMethod(ScrollingMovementMethod.getInstance());
                tvNotes.setText(releaseNotes);
            }
        } else {
            tvNotes.setVisibility(View.GONE);
        }
        root.addView(tvNotes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Progress section (hidden initially)
        LinearLayout layoutProgress = new LinearLayout(context);
        layoutProgress.setId(ID_LAYOUT_PROGRESS);
        layoutProgress.setOrientation(LinearLayout.VERTICAL);
        layoutProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progLp.topMargin = dp(context, 12);
        root.addView(layoutProgress, progLp);

        TextView tvStatus = new TextView(context);
        tvStatus.setId(ID_TV_STATUS);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        layoutProgress.addView(tvStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setId(ID_PROGRESS_BAR);
        progressBar.setMax(100);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = dp(context, 8);
        layoutProgress.addView(progressBar, barLp);

        TextView tvProgress = new TextView(context);
        tvProgress.setId(ID_TV_PROGRESS);
        tvProgress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvProgress.setGravity(Gravity.END);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pctLp.topMargin = dp(context, 4);
        layoutProgress.addView(tvProgress, pctLp);

        return root;
    }

    // ════════════════════ 公开方法 ════════════════════

    public static void openReleasesPage(Context context) {
        String url = UpdateCore.getDetailsUrl();
        if (url == null || url.isEmpty()) url = UpdateCore.RELEASES_PAGE_URL;
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    public static AlertDialog showAlreadyLatestDialog(Context context) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildTitleBar(context, R.string.updatelib_update_already_latest_title));
        d.show();
        return d;
    }

    public static AlertDialog showCheckFailedDialog(Context context, Runnable onConfirm) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_update_check_failed_message)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run(); dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildTitleBar(context, R.string.updatelib_update_check_failed_title));
        d.show();
        return d;
    }

    public static AlertDialog showRateLimitedDialog(Context context, Runnable onConfirm) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_update_check_rate_limited_message)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run(); dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildTitleBar(context, R.string.updatelib_update_check_rate_limited_title));
        d.show();
        return d;
    }

    public static AlertDialog showNoApkDialog(Context context, Runnable onConfirm) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_update_check_no_apk_message)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run(); dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildTitleBar(context, R.string.updatelib_update_check_no_apk_title));
        d.show();
        return d;
    }

    public static AlertDialog showNotificationPermissionDialog(Context context,
                                                                Runnable onConfirm, Runnable onCancel) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_notification_permission_rationale)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run(); dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_deny, (dd, w) -> {
                    if (onCancel != null) onCancel.run(); dd.dismiss();
                })
                .create();
        d.setCustomTitle(buildTitleBar(context, R.string.updatelib_notification_permission_title));
        d.show();
        return d;
    }

    // ════════════════════ 统一更新弹窗 ════════════════════

    public static Controller showUpdateDialog(
            Context context, String version, String releaseNotes,
            String apkUrl, long apkSize,
            Runnable onConfirm, Runnable onIgnore, Runnable onDismiss, Runnable onInstall) {

        String currentVersion = UpdateManager.getCurrentVersion();
        View dialogView = buildUpdateContentView(context, version, releaseNotes, currentVersion);

        LinearLayout layoutProgress = dialogView.findViewById(ID_LAYOUT_PROGRESS);
        TextView tvStatus = dialogView.findViewById(ID_TV_STATUS);
        ProgressBar progressBar = dialogView.findViewById(ID_PROGRESS_BAR);
        TextView tvProgress = dialogView.findViewById(ID_TV_PROGRESS);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        dialog.setCustomTitle(buildTitleBar(context, R.string.updatelib_update_available_title));

        boolean isDownloaded = onInstall != null;

        String installText = context.getString(R.string.updatelib_update_install_now);
        String updateText = context.getString(R.string.updatelib_update_now);
        String ignoreText = context.getString(R.string.updatelib_update_ignore_version);
        String bgText = context.getString(R.string.updatelib_update_move_to_background);
        String downloadingText = context.getString(R.string.updatelib_update_downloading_label);
        String failedText = context.getString(R.string.updatelib_update_download_failed);
        String retryText = context.getString(R.string.updatelib_update_retry);
        String cancelText = context.getString(R.string.updatelib_cancel);

        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                isDownloaded ? installText : updateText, (d, w) -> {});
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, ignoreText, (d, w) -> {});
        dialog.show();

        final Runnable[] onConfirmRef = {onConfirm};
        final Runnable[] onDismissRef = {onDismiss};
        final Runnable[] onInstallRef = {onInstall};

        if (isDownloaded) {
            Button negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negBtn != null) negBtn.setVisibility(View.GONE);
            Button posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (posBtn != null) {
                posBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onDismissRef[0] != null) onDismissRef[0].run();
                    if (onInstallRef[0] != null) onInstallRef[0].run();
                });
            }
        } else {
            Button posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (posBtn != null) {
                posBtn.setOnClickListener(v -> {
                    if (!ApkInstaller.canInstall(context)) {
                        ApkInstaller.gotoUnknownSourceSetting(context);
                        return;
                    }
                    if (layoutProgress != null) layoutProgress.setVisibility(View.VISIBLE);
                    posBtn.setVisibility(View.GONE);
                    if (negBtn != null) {
                        negBtn.setText(bgText);
                        negBtn.setOnClickListener(v2 -> {
                            dialog.dismiss();
                            if (onDismissRef[0] != null) onDismissRef[0].run();
                        });
                    }
                    if (onConfirmRef[0] != null) onConfirmRef[0].run();

                    final boolean[] downloadStarted = {false};
                    UpdateManager.DownloadListener listener = state -> {
                        mainHandler.post(() -> {
                            if (!dialog.isShowing()) return;
                            switch (state.status) {
                                case DOWNLOADING:
                                    downloadStarted[0] = true;
                                    if (progressBar != null) progressBar.setProgress(state.progress);
                                    if (tvProgress != null) tvProgress.setText(state.progress + "%");
                                    if (tvStatus != null) tvStatus.setText(downloadingText);
                                    break;
                                case FAILED:
                                    if (tvStatus != null) tvStatus.setText(failedText);
                                    Button pb = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                                    if (pb != null) {
                                        pb.setVisibility(View.VISIBLE);
                                        pb.setText(retryText);
                                        pb.setOnClickListener(v2 -> {
                                            if (layoutProgress != null) layoutProgress.setVisibility(View.GONE);
                                            if (progressBar != null) progressBar.setProgress(0);
                                            if (tvProgress != null) tvProgress.setText("");
                                            pb.setVisibility(View.GONE);
                                            Button nb2 = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                                            if (nb2 != null) {
                                                nb2.setText(bgText);
                                                nb2.setOnClickListener(v3 -> {
                                                    dialog.dismiss();
                                                    if (onDismissRef[0] != null) onDismissRef[0].run();
                                                });
                                            }
                                            if (onConfirmRef[0] != null) onConfirmRef[0].run();
                                        });
                                    }
                                    Button nb = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                                    if (nb != null) {
                                        nb.setText(cancelText);
                                        nb.setOnClickListener(v2 -> {
                                            dialog.dismiss();
                                            if (onDismissRef[0] != null) onDismissRef[0].run();
                                        });
                                    }
                                    break;
                                case IDLE:
                                    if (downloadStarted[0]) {
                                        dialog.dismiss();
                                        if (onDismissRef[0] != null) onDismissRef[0].run();
                                    }
                                    break;
                            }
                        });
                    };
                    UpdateManager.addDownloadListener(listener);
                    dialog.setOnDismissListener(d -> UpdateManager.removeDownloadListener(listener));
                });
            }

            if (negBtn != null) {
                negBtn.setOnClickListener(v -> {
                    if (onIgnore != null) onIgnore.run();
                    dialog.dismiss();
                    if (onDismissRef[0] != null) onDismissRef[0].run();
                });
            }
        }

        return new Controller(dialog);
    }

    // ════════════════════ 一键检查并弹窗 ════════════════════

    public static void checkAndShowUpdateDialog(Activity activity) {
        checkAndShowUpdateDialog(activity, null);
    }

    public static void checkAndShowUpdateDialog(Activity activity, Runnable onDismiss) {
        UpdateCore.checkAndCache(activity, true,
                UpdateManager.getCurrentVersion(),
                result -> mainHandler.post(() -> {
                    switch (result.type) {
                        case NEW_VERSION:
                            showNewVersionDialogInternal(activity, result.state, onDismiss);
                            break;
                        case UP_TO_DATE:
                            showAlreadyLatestDialog(activity);
                            break;
                        case FAILED:
                            showCheckFailedDialog(activity, () -> openReleasesPage(activity));
                            break;
                        case RATE_LIMITED:
                            showRateLimitedDialog(activity, () -> openReleasesPage(activity));
                            break;
                        case NO_APK:
                            showNoApkDialog(activity, () -> openReleasesPage(activity));
                            break;
                    }
                }));
    }

    private static void showNewVersionDialogInternal(Activity activity, UpdateCore.UpdateState state,
                                                      Runnable onDismiss) {
        java.io.File apkFile = ApkInstaller.apkFile(activity, state.latestVersion);
        boolean alreadyDownloaded = ApkInstaller.isDownloaded(apkFile, state.apkSize);

        showUpdateDialog(
                activity,
                state.latestVersion,
                state.notes,
                state.apkUrl,
                state.apkSize,
                () -> {
                    if (UpdateManager.canInstall(activity)) {
                        UpdateManager.downloadUpdate(activity, state.latestVersion, state.apkUrl, state.apkSize);
                    } else {
                        UpdateManager.gotoUnknownSourceSetting(activity);
                    }
                },
                () -> {},
                () -> {
                    if (onDismiss != null) onDismiss.run();
                    if (ApkInstaller.isDownloaded(apkFile, state.apkSize)) {
                        UpdateManager.installUpdate(activity, state.latestVersion);
                    }
                },
                alreadyDownloaded ? () -> UpdateManager.installUpdate(activity, state.latestVersion) : null
        );
    }
}
