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

import com.mqd.updatejava.R;
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

    // ──────────────── 公开方法 ────────────────

    public static void openReleasesPage(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(UpdateChecker.RELEASES_PAGE_URL)));
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

    public static AlertDialog showNoApkDialog(Context context, Runnable onConfirm) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_check_no_apk_title)
                .setMessage(R.string.updatelib_update_check_no_apk_message)
                .setPositiveButton(R.string.updatelib_confirm, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (d, w) -> d.dismiss())
                .show();
    }

    public static AlertDialog showNotificationPermissionDialog(Context context,
                                                                Runnable onConfirm, Runnable onCancel) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_notification_permission_title)
                .setMessage(R.string.updatelib_notification_permission_rationale)
                .setPositiveButton(R.string.updatelib_confirm, (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    d.dismiss();
                })
                .setNegativeButton(R.string.updatelib_deny, (d, w) -> {
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

        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.updatelib_dialog_update_with_progress, null);

        LinearLayout layoutProgress = dialogView.findViewById(R.id.layout_progress);
        TextView tvVersion = dialogView.findViewById(R.id.tv_version);
        TextView tvReleaseNotes = dialogView.findViewById(R.id.tv_release_notes);
        WebView webView = dialogView.findViewById(R.id.webview_release_notes);
        TextView tvStatus = dialogView.findViewById(R.id.tv_status);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        TextView tvProgress = dialogView.findViewById(R.id.tv_progress);

        // Version info
        String currentVersion = com.mqd.updatejava.UpdateManager.getCurrentVersion();
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

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.updatelib_update_available_title)
                .setView(dialogView)
                .setCancelable(true)
                .create();

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

        final Runnable[] onConfirmRef = new Runnable[]{onConfirm};
        final Runnable[] onDismissRef = new Runnable[]{onDismiss};
        final Runnable[] onInstallRef = new Runnable[]{onInstall};

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
                    DownloadController.addListener(listener);
                    dialog.setOnDismissListener(d -> DownloadController.removeListener(listener));
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
