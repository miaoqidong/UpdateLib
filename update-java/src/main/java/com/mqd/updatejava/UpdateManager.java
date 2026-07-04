package com.mqd.updatejava;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import com.mqd.updatejava.core.UpdateChecker;
import com.mqd.updatejava.core.UpdateLibContext;
import com.mqd.updatejava.core.UpdateRepository;
import com.mqd.updatejava.core.UpdateState;
import com.mqd.updatejava.download.ApkInstaller;
import com.mqd.updatejava.download.DownloadController;
import com.mqd.updatejava.download.DownloadService;

/**
 * 更新库公开 API 入口（纯 Java 版）。
 *
 * 使用流程：
 * 1. 在 Application 中调用 init() 初始化
 * 2. 调用 checkAndShowUpdateDialog() 一键完成检查+弹窗+下载+安装
 *
 * @since 2026/7/4
 */
public class UpdateManager {

    private static String currentVersion = "";
    private static String fileProviderAuthority = "";

    /**
     * 初始化更新库。
     *
     * @param context 应用上下文
     * @param githubOwner GitHub 仓库所有者（可选）
     * @param githubRepo GitHub 仓库名称（可选）
     * @param currentVersion 当前版本号（传空则自动读取）
     * @param fileProviderAuthority FileProvider authority（传空则自动生成）
     * @param compareByTag 版本比较模式：true 比较 tag，false 比较 APK 文件名
     * @param currentVersionCode 当前 versionCode（传 0 则自动读取）
     * @param fallbackUrl 备用更新源 JSON 地址
     * @param fallbackOnly true 表示仅使用备用源
     */
    public static void init(Context context,
                             String githubOwner, String githubRepo,
                             String currentVersion,
                             String fileProviderAuthority,
                             boolean compareByTag,
                             long currentVersionCode,
                             String fallbackUrl,
                             boolean fallbackOnly) {
        UpdateLibContext.init(context);
        UpdateManager.fileProviderAuthority = fileProviderAuthority;

        // Set up fallback
        UpdateRepository.fallbackUrl = fallbackUrl != null ? fallbackUrl : "";
        UpdateRepository.useFallbackOnly = fallbackOnly;

        // Auto-detect version
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

            // Configure checker
            if (githubOwner != null && !githubOwner.isEmpty() &&
                    githubRepo != null && !githubRepo.isEmpty()) {
                UpdateChecker.configure(githubOwner, githubRepo, detectedVersion,
                        compareByTag, detectedVersionCode);
            } else {
                UpdateChecker.configure("", "", detectedVersion, compareByTag, detectedVersionCode);
            }

            // Configure FileProvider
            String authority = (fileProviderAuthority != null && !fileProviderAuthority.isEmpty())
                    ? fileProviderAuthority
                    : context.getPackageName() + ".updatejava.fileprovider";
            ApkInstaller.configure(authority);
        } catch (Exception e) {
            UpdateManager.currentVersion = "0.0.0";
        }
    }

    // Convenience overloads
    public static void init(Context context, String githubOwner, String githubRepo) {
        init(context, githubOwner, githubRepo, "", "", true, 0L, "", false);
    }

    public static void init(Context context, String fallbackUrl, boolean fallbackOnly) {
        init(context, "", "", "", "", true, 0L,
                fallbackUrl != null ? fallbackUrl : "", fallbackOnly);
    }

    public static String getCurrentVersion() {
        return currentVersion;
    }

    public static String getReleasesPageUrl() {
        String cached = UpdateRepository.getDetailsUrl();
        return (cached != null && !cached.isEmpty()) ? cached : UpdateChecker.RELEASES_PAGE_URL;
    }

    // ──────── 下载与安装 ────────

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
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            context.startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    public static boolean isDownloaded(Context context, String version, long expectedSize) {
        java.io.File file = ApkInstaller.apkFile(context, version);
        return ApkInstaller.isDownloaded(file, expectedSize);
    }

    public static void resetDownloadState() {
        DownloadController.reset();
    }

    // ──────── 下载状态监听 ────────

    public static void addDownloadListener(DownloadController.Listener listener) {
        DownloadController.addListener(listener);
    }

    public static void removeDownloadListener(DownloadController.Listener listener) {
        DownloadController.removeListener(listener);
    }

    public static DownloadController.State getDownloadState() {
        return DownloadController.getCurrentState();
    }
}
