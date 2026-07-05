package com.mqd.updatejava.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
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

    private static View buildCustomTitleView(Context context, int titleRes) {
        View titleView = LayoutInflater.from(context)
                .inflate(R.layout.updatelib_dialog_title_with_link, null);
        ((TextView) titleView.findViewById(R.id.tv_dialog_title)).setText(titleRes);
        titleView.findViewById(R.id.btn_open_link)
                .setOnClickListener(v -> openReleasesPage(context));
        return titleView;
    }

    // ──────────────── 公开方法 ────────────────

    public static void openReleasesPage(Context context) {
        String url = UpdateRepository.getDetailsUrl();
        if (url == null || url.isEmpty()) url = UpdateChecker.RELEASES_PAGE_URL;
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    public static AlertDialog showAlreadyLatestDialog(Context context) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_already_latest_title));
        d.show();
        return d;
    }

    public static AlertDialog showCheckFailedDialog(Context context, Runnable onConfirm) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_update_check_failed_message)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_check_failed_title));
        d.show();
        return d;
    }

    public static AlertDialog showRateLimitedDialog(Context context, Runnable onConfirm) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_update_check_rate_limited_message)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_check_rate_limited_title));
        d.show();
        return d;
    }

    public static AlertDialog showNoApkDialog(Context context, Runnable onConfirm) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_update_check_no_apk_message)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_cancel, (dd, w) -> dd.dismiss())
                .create();
        d.setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_check_no_apk_title));
        d.show();
        return d;
    }

    public static AlertDialog showNotificationPermissionDialog(Context context,
                                                                Runnable onConfirm, Runnable onCancel) {
        AlertDialog d = new AlertDialog.Builder(context)
                .setMessage(R.string.updatelib_notification_permission_rationale)
                .setPositiveButton(R.string.updatelib_confirm, (dd, w) -> {
                    if (onConfirm != null) onConfirm.run();
                    dd.dismiss();
                })
                .setNegativeButton(R.string.updatelib_deny, (dd, w) -> {
                    if (onCancel != null) onCancel.run();
                    dd.dismiss();
                })
                .create();
        d.setCustomTitle(buildCustomTitleView(context, R.string.updatelib_notification_permission_title));
        d.show();
        return d;
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
        TextView tvStatus = dialogView.findViewById(R.id.tv_status);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        TextView tvProgress = dialogView.findViewById(R.id.tv_progress);

        // Version info
        String currentVersion = com.mqd.updatejava.UpdateManager.getCurrentVersion();
        if (tvVersion != null) {
            tvVersion.setText(context.getString(R.string.updatelib_update_version_compare,
                    currentVersion, UpdateChecker.displayVersion(version)));
        }

        if (releaseNotes != null && !releaseNotes.trim().isEmpty()) {
            if (tvReleaseNotes != null) {
                tvReleaseNotes.setVisibility(View.VISIBLE);
                if (FallbackChecker.isHtmlContent(releaseNotes)) {
                    tvReleaseNotes.setMovementMethod(LinkMovementMethod.getInstance());
                    tvReleaseNotes.setText(android.text.Html.fromHtml(
                            releaseNotes, android.text.Html.FROM_HTML_MODE_LEGACY));
                } else {
                    tvReleaseNotes.setMovementMethod(ScrollingMovementMethod.getInstance());
                    tvReleaseNotes.setText(releaseNotes);
                }
            }
        } else {
            if (tvReleaseNotes != null) tvReleaseNotes.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        dialog.setCustomTitle(buildCustomTitleView(context, R.string.updatelib_update_available_title));

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
