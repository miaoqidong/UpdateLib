package com.mqd.updatejava;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import com.mqd.updatejava.core.UpdateCore;
import com.mqd.updatejava.download.ApkInstaller;
import com.mqd.updatejava.download.DownloadService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 更新库公开 API 入口（纯 Java 版）。
 *
 * 使用流程：
 * 1. 在 Application 中调用 init() 初始化
 * 2. 调用 checkAndShowUpdateDialog() 一键完成检查+弹窗+下载+安装
 */
public class UpdateManager {

    private static Context appContext;
    private static String currentVersion = "";
    private static String fileProviderAuthority = "";

    // ── 下载状态（原 DownloadController） ──

    public enum DownloadStatus { IDLE, DOWNLOADING, FAILED }

    public static class DownloadState {
        public DownloadStatus status = DownloadStatus.IDLE;
        public String version = "";
        public int progress = 0;
    }

    public interface DownloadListener {
        void onStateChanged(DownloadState state);
    }

    private static final DownloadState downloadState = new DownloadState();
    private static final List<DownloadListener> downloadListeners = new CopyOnWriteArrayList<>();

    // ════════════════════ 初始化 ════════════════════

    /**
     * 初始化更新库。
     */
    public static void init(Context context,
                             String githubOwner, String githubRepo,
                             String currentVersion,
                             String fileProviderAuthority,
                             boolean compareByTag,
                             long currentVersionCode,
                             String fallbackUrl,
                             boolean fallbackOnly) {
        appContext = context.getApplicationContext();
        UpdateManager.fileProviderAuthority = fileProviderAuthority;

        UpdateCore.fallbackUrl = fallbackUrl != null ? fallbackUrl : "";
        UpdateCore.useFallbackOnly = fallbackOnly;

        try {
            PackageInfo pkgInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgInfo = context.getPackageManager().getPackageInfo(
                        context.getPackageName(),
                        PackageManager.PackageInfoFlags.of(0));
            } else {
                pkgInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            }
            String detectedVersion = (currentVersion != null && !currentVersion.isEmpty())
                    ? currentVersion : (pkgInfo.versionName != null ? pkgInfo.versionName : "");
            long detectedVersionCode = currentVersionCode > 0 ? currentVersionCode :
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                            pkgInfo.getLongVersionCode() : pkgInfo.versionCode);

            UpdateManager.currentVersion = detectedVersion;

            if (githubOwner != null && !githubOwner.isEmpty() &&
                    githubRepo != null && !githubRepo.isEmpty()) {
                UpdateCore.configure(githubOwner, githubRepo, detectedVersion,
                        compareByTag, detectedVersionCode);
            } else {
                UpdateCore.configure("", "", detectedVersion, compareByTag, detectedVersionCode);
            }

            String authority = (fileProviderAuthority != null && !fileProviderAuthority.isEmpty())
                    ? fileProviderAuthority
                    : context.getPackageName() + ".updatejava.fileprovider";
            ApkInstaller.configure(authority);
        } catch (Exception e) {
            UpdateManager.currentVersion = "0.0.0";
        }
    }

    public static void init(Context context, String githubOwner, String githubRepo) {
        init(context, githubOwner, githubRepo, "", "", true, 0L, "", false);
    }

    public static void init(Context context, String fallbackUrl, boolean fallbackOnly) {
        init(context, "", "", "", "", true, 0L,
                fallbackUrl != null ? fallbackUrl : "", fallbackOnly);
    }

    public static Context getContext() {
        if (appContext == null) throw new IllegalStateException("UpdateManager not initialized. Call init() first.");
        return appContext;
    }

    public static String getCurrentVersion() { return currentVersion; }

    public static String getReleasesPageUrl() {
        String cached = UpdateCore.getDetailsUrl();
        return (cached != null && !cached.isEmpty()) ? cached : UpdateCore.RELEASES_PAGE_URL;
    }

    // ════════════════════ 下载与安装 ════════════════════

    public static void downloadUpdate(Context context, String version, String url, long size) {
        DownloadService.start(context, version, url, size);
    }

    public static boolean installUpdate(Context context, String version) {
        java.io.File file = ApkInstaller.apkFile(context, version);
        return ApkInstaller.installApk(context, file);
    }

    public static boolean canInstall(Context context) {
        return ApkInstaller.canInstall(context);
    }

    public static void gotoUnknownSourceSetting(Context context) {
        ApkInstaller.gotoUnknownSourceSetting(context);
    }

    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void gotoNotificationSetting(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.fromParts("package", context.getPackageName(), null));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); }
        catch (Exception e) {
            context.startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    public static boolean isDownloaded(Context context, String version, long expectedSize) {
        java.io.File file = ApkInstaller.apkFile(context, version);
        return ApkInstaller.isDownloaded(file, expectedSize);
    }

    // ════════════════════ 下载状态监听 ════════════════════

    public static void addDownloadListener(DownloadListener listener) {
        if (listener != null) downloadListeners.add(listener);
    }

    public static void removeDownloadListener(DownloadListener listener) {
        downloadListeners.remove(listener);
    }

    public static DownloadState getDownloadState() {
        synchronized (downloadState) {
            DownloadState s = new DownloadState();
            s.status = downloadState.status;
            s.version = downloadState.version;
            s.progress = downloadState.progress;
            return s;
        }
    }

    public static void resetDownloadState() {
        synchronized (downloadState) {
            downloadState.status = DownloadStatus.IDLE;
            downloadState.version = "";
            downloadState.progress = 0;
        }
        notifyDownloadListeners();
    }

    // ════════════════════ 下载状态管理（供 DownloadService 调用） ════════════════════

    public static void onDownloadStart(String version) {
        synchronized (downloadState) {
            downloadState.status = DownloadStatus.DOWNLOADING;
            downloadState.version = version;
            downloadState.progress = 0;
        }
        notifyDownloadListeners();
    }

    public static void onDownloadProgress(int percent) {
        synchronized (downloadState) {
            downloadState.progress = Math.max(0, Math.min(100, percent));
        }
        notifyDownloadListeners();
    }

    public static void onDownloadFinish() {
        synchronized (downloadState) {
            downloadState.status = DownloadStatus.IDLE;
            downloadState.version = "";
            downloadState.progress = 0;
        }
        notifyDownloadListeners();
    }

    public static void onDownloadFailed(String version) {
        synchronized (downloadState) {
            downloadState.status = DownloadStatus.FAILED;
            downloadState.version = version;
        }
        notifyDownloadListeners();
    }

    private static void notifyDownloadListeners() {
        DownloadState snapshot = getDownloadState();
        for (DownloadListener listener : downloadListeners) {
            listener.onStateChanged(snapshot);
        }
    }
}
