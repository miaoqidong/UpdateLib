package com.mqd.updatejava;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.mqd.updatejava.core.UpdateCore;
import com.mqd.updatejava.download.DownloadService;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 更新库公开 API 入口（纯 Java 版）。
 *
 * 合并了 Context 持有、下载状态管理、FileProvider、APK 安装/权限等逻辑。
 * 使用流程：
 * 1. Application.onCreate() 中调用 init()
 * 2. UpdateDialogHelper.checkAndShowUpdateDialog(activity)
 */
public class UpdateManager {

    private static Context appContext;
    private static String currentVersion = "";
    private static String fileProviderAuthority = "";

    // ── 下载状态 ──

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

    public static void init(Context context,
                             String githubOwner, String githubRepo,
                             boolean compareByTag,
                             String fallbackUrl,
                             boolean fallbackOnly,
                             String fileProviderAuthority) {
        appContext = context.getApplicationContext();
        UpdateManager.fileProviderAuthority = fileProviderAuthority;

        UpdateCore.fallbackUrl = fallbackUrl != null ? fallbackUrl : "";
        UpdateCore.useFallbackOnly = fallbackOnly;

        try {
            PackageInfo pkgInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgInfo = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                pkgInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            }
            String detectedVersion = pkgInfo.versionName != null ? pkgInfo.versionName : "";
            long detectedVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                    pkgInfo.getLongVersionCode() : pkgInfo.versionCode;

            UpdateManager.currentVersion = detectedVersion;

            if (githubOwner != null && !githubOwner.isEmpty() &&
                    githubRepo != null && !githubRepo.isEmpty()) {
                UpdateCore.configure(githubOwner, githubRepo, detectedVersion, compareByTag, detectedVersionCode);
            } else {
                UpdateCore.configure("", "", detectedVersion, compareByTag, detectedVersionCode);
            }

            String authority = (fileProviderAuthority != null && !fileProviderAuthority.isEmpty())
                    ? fileProviderAuthority
                    : context.getPackageName() + ".updatejava.fileprovider";
            UpdateManager.fileProviderAuthority = authority;
        } catch (Exception e) {
            UpdateManager.currentVersion = "0.0.0";
        }
    }

    public static void init(Context context, String githubOwner, String githubRepo) {
        init(context, githubOwner, githubRepo, true, "", false, "");
    }

    public static void init(Context context, String fallbackUrl, boolean fallbackOnly) {
        init(context, "", "", true, fallbackUrl != null ? fallbackUrl : "", fallbackOnly, "");
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

    public static boolean isDownloaded(Context context, String version, long expectedSize) {
        File file = apkFile(context, version);
        if (!file.exists() || file.length() <= 0) return false;
        return expectedSize <= 0 || file.length() == expectedSize;
    }

    public static boolean installUpdate(Context context, String version) {
        File file = apkFile(context, version);
        try {
            String authority = !fileProviderAuthority.isEmpty()
                    ? fileProviderAuthority : context.getPackageName() + ".updatejava.fileprovider";
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(context, authority, file);
            } else {
                uri = Uri.fromFile(file);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ════════════════════ 文件工具 ════════════════════

    private static final String UPDATE_DIR_NAME = "update";

    private static File updateDir(Context context) {
        File base = context.getExternalCacheDir();
        if (base == null) base = context.getCacheDir();
        return new File(base, UPDATE_DIR_NAME);
    }

    private static String apkFileName(String version) {
        String safe = (version != null && !version.trim().isEmpty()) ? version.trim() : "update";
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe + ".apk";
    }

    public static File apkFile(Context context, String version) {
        return new File(updateDir(context), apkFileName(version));
    }

    public static void clearOutdatedApks(File dir, String keepFileName) {
        File[] files = dir.listFiles();
        if (files == null) return;
        String keepPart = keepFileName + ".part";
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            boolean isApk = name.toLowerCase().endsWith(".apk");
            boolean isPart = name.toLowerCase().endsWith(".apk.part");
            if ((isApk || isPart) && !name.equals(keepFileName) && !name.equals(keepPart)) {
                file.delete();
            }
        }
    }

    // ════════════════════ 权限 ════════════════════

    public static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    public static void gotoUnknownSourceSetting(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
        }
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
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); }
        catch (Exception e) {
            context.startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    // ════════════════════ 下载状态管理 ════════════════════

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
        for (DownloadListener l : downloadListeners) l.onStateChanged(snapshot);
    }

    // ════════════════════ FileProvider ════════════════════

    public static class LibFileProvider extends FileProvider {}
}
