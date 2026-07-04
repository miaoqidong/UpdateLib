package com.mqd.updatejava.ui;

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
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mqd.updatejava.core.FallbackChecker;
import com.mqd.updatejava.core.UpdateChecker;
import com.mqd.updatejava.core.UpdateRepository;
import com.mqd.updatejava.core.UpdateState;
import com.mqd.updatejava.download.ApkInstaller;
import com.mqd.updatejava.download.DownloadController;

/**
 * View-based 更新对话框辅助类（纯 Java，不依赖 Kotlin/androidx.core）。
 */
public class UpdateDialogHelper {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private UpdateDialogHelper() {}

    // ──────────────── 资源 ID 动态获取 ────────────────

    private static int getLayoutId(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "layout", ctx.getPackageName());
    }

    private static int getId(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
    }

    private static int getStringId(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
    }

    private static String getStr(Context ctx, String name) {
        int id = getStringId(ctx, name);
        return id != 0 ? ctx.getString(id) : name;
    }

    // ──────────────── 标题构建 ────────────────

    private static View buildCustomTitleView(Context context, int titleRes) {
        int layoutId = getLayoutId(context, "updatelib_dialog_title_with_link");
        View titleView = LayoutInflater.from(context).inflate(layoutId, null);
        TextView tv = titleView.findViewById(getId(context, "tv_dialog_title"));
        if (tv != null) tv.setText(titleRes);
        LinearLayout btn = titleView.findViewById(getId(context, "btn_open_link"));
        if (btn != null) {
            btn.setOnClickListener(v -> openReleasesPage(context));
        }
        return titleView;
    }

    // ──────────────── 公开方法 ────────────────

    public static void openReleasesPage(Context context) {
        try {
            String url = "https://github.com";
            if (com.mqd.updatejava.core.UpdateLibContext.isInitialized()) {
                // Try to get releases URL from UpdateChecker
                url = UpdateChecker.RELEASES_PAGE_URL;
            }
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    public static AlertDialog showAlreadyLatestDialog(Context context) {
        int titleId = getStringId(context, "updatelib_update_already_latest_title");
        int confirmId = getStringId(context, "updatelib_confirm");
        return new AlertDialog.Builder(context)
                .setCustomTitle(buildCustomTitleView(context,
                        titleId != 0 ? titleId : android.R.string.ok))
                .setPositiveButton(confirmId != 0 ? confirmId : android.R.string.ok,
                        (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showCheckFailedDialog(Context context, Runnable onConfirm) {
        int titleId = getStringId(context, "updatelib_update_check_failed_title");
        int msgId = getStringId(context, "updatelib_update_check_failed_message");
        int confirmId = getStringId(context, "updatelib_confirm");
        int cancelId = getStringId(context, "updatelib_cancel");

        return new AlertDialog.Builder(context)
                .setCustomTitle(buildCustomTitleView(context, titleId))
                .setMessage(msgId)
                .setPositiveButton(confirmId, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(cancelId, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showRateLimitedDialog(Context context, Runnable onConfirm) {
        int titleId = getStringId(context, "updatelib_update_check_rate_limited_title");
        int msgId = getStringId(context, "updatelib_update_check_rate_limited_message");
        int confirmId = getStringId(context, "updatelib_confirm");
        int cancelId = getStringId(context, "updatelib_cancel");

        return new AlertDialog.Builder(context)
                .setCustomTitle(buildCustomTitleView(context, titleId))
                .setMessage(msgId)
                .setPositiveButton(confirmId, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(cancelId, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showNoApkDialog(Context context, Runnable onConfirm) {
        int titleId = getStringId(context, "updatelib_update_check_no_apk_title");
        int msgId = getStringId(context, "updatelib_update_check_no_apk_message");
        int confirmId = getStringId(context, "updatelib_confirm");
        int cancelId = getStringId(context, "updatelib_cancel");

        return new AlertDialog.Builder(context)
                .setCustomTitle(buildCustomTitleView(context, titleId))
                .setMessage(msgId)
                .setPositiveButton(confirmId, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(cancelId, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showNotificationPermissionDialog(Context context,
                                                                Runnable onConfirm, Runnable onCancel) {
        int titleId = getStringId(context, "updatelib_notification_permission_title");
        int msgId = getStringId(context, "updatelib_notification_permission_rationale");
        int confirmId = getStringId(context, "updatelib_confirm");
        int denyId = getStringId(context, "updatelib_deny");

        return new AlertDialog.Builder(context)
                .setTitle(titleId)
                .setMessage(msgId)
                .setPositiveButton(confirmId, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(denyId, (d, w) -> {
                    if (onCancel != null) onCancel.run();
                    d.dismiss();
                })
                .show();
    }

    // ──────────────── 统一更新弹窗 ────────────────

    public static UpdateDialogController showUpdateDialog(
            Context context, String version, String releaseNotes,
            String apkUrl, long apkSize,
            Runnable onConfirm, Runnable onIgnore, Runnable onDismiss, Runnable onInstall) {

        int layoutId = getLayoutId(context, "updatelib_dialog_update_with_progress");
        View dialogView = LayoutInflater.from(context).inflate(layoutId, null);

        LinearLayout layoutProgress = dialogView.findViewById(getId(context, "layout_progress"));
        TextView tvVersion = dialogView.findViewById(getId(context, "tv_version"));
        TextView tvReleaseNotes = dialogView.findViewById(getId(context, "tv_release_notes"));
        WebView webView = dialogView.findViewById(getId(context, "webview_release_notes"));
        TextView tvStatus = dialogView.findViewById(getId(context, "tv_status"));
        ProgressBar progressBar = dialogView.findViewById(getId(context, "progress_bar"));
        TextView tvProgress = dialogView.findViewById(getId(context, "tv_progress"));

        // Version info
        String currentVersion = "";
        try {
            if (com.mqd.updatejava.core.UpdateLibContext.isInitialized()) {
                currentVersion = com.mqd.updatejava.UpdateManager.getCurrentVersion();
            }
        } catch (Exception ignored) {}
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

        int titleId = getStringId(context, "updatelib_update_available_title");
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setCustomTitle(buildCustomTitleView(context, titleId))
                .setView(dialogView)
                .setCancelable(true)
                .create();

        boolean isDownloaded = onInstall != null;

        String installText = getStr(context, "updatelib_update_install_now");
        String updateText = getStr(context, "updatelib_update_now");
        String ignoreText = getStr(context, "updatelib_update_ignore_version");
        String bgText = getStr(context, "updatelib_update_move_to_background");
        String downloadingText = getStr(context, "updatelib_update_downloading_label");
        String failedText = getStr(context, "updatelib_update_download_failed");
        String retryText = getStr(context, "updatelib_update_retry");
        String cancelText = getStr(context, "updatelib_cancel");

        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                isDownloaded ? installText : updateText,
                (d, w) -> {});
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, ignoreText, (d, w) -> {});
        dialog.show();

        final Runnable[] onConfirmRef = new Runnable[]{onConfirm};
        final Runnable[] onDismissRef = new Runnable[]{onDismiss};
        final Runnable[] onInstallRef = new Runnable[]{onInstall};

        if (isDownloaded) {
            // APK already downloaded
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
            // Normal download flow
            Button posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (posBtn != null) {
                posBtn.setOnClickListener(v -> {
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

                    // Start observing download state
                    final boolean[] downloadStarted = {false};
                    DownloadController.Listener listener = state -> {
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
                                            Button nb = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                                            if (nb != null) {
                                                nb.setText(bgText);
                                                nb.setOnClickListener(v3 -> {
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
                    DownloadController.addListener(listener);

                    // Store listener for cleanup
                    dialog.setOnDismissListener(d -> {
                        DownloadController.removeListener(listener);
                    });
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

        return new UpdateDialogController(dialog);
    }

    // ──────────────── 一键检查并弹窗 ────────────────

    public static void checkAndShowUpdateDialog(Activity activity) {
        checkAndShowUpdateDialog(activity, null);
    }

    public static void checkAndShowUpdateDialog(Activity activity, Runnable onDismiss) {
        UpdateRepository.checkAndCache(activity, true,
                com.mqd.updatejava.UpdateManager.getCurrentVersion(),
                result -> mainHandler.post(() -> {
                    switch (result.type) {
                        case NEW_VERSION:
                            showNewVersionDialogInternal(activity, result.state, onDismiss);
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
                        case NO_APK:
                            showNoApkDialog(activity,
                                    () -> openReleasesPage(activity));
                            break;
                    }
                }));
    }

    private static void showNewVersionDialogInternal(Activity activity, UpdateState state,
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
                    if (com.mqd.updatejava.UpdateManager.canInstall(activity)) {
                        com.mqd.updatejava.UpdateManager.downloadUpdate(
                                activity, state.latestVersion, state.apkUrl, state.apkSize);
                    } else {
                        com.mqd.updatejava.UpdateManager.gotoUnknownSourceSetting(activity);
                    }
                },
                () -> {}, // onIgnore
                () -> {
                    if (onDismiss != null) onDismiss.run();
                    if (ApkInstaller.isDownloaded(apkFile, state.apkSize)) {
                        com.mqd.updatejava.UpdateManager.installUpdate(activity, state.latestVersion);
                    }
                },
                alreadyDownloaded ? () -> {
                    com.mqd.updatejava.UpdateManager.installUpdate(activity, state.latestVersion);
                } : null
        );
    }
}
