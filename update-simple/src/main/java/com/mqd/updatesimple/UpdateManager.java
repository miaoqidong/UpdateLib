package com.mqd.updatesimple;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.mqd.updatesimple.core.UpdateChecker;
import com.mqd.updatesimple.core.UpdateLibContext;
import com.mqd.updatesimple.core.UpdateRepository;

/**
 * 更新库公开 API 入口（纯 Java，无下载/安装功能）。
 *
 * 使用流程：
 * 1. 在 Application 中调用 init() 初始化
 * 2. 调用 checkAndShowUpdateDialog() 一键完成检查+弹窗，用户点击"立即更新"会跳转到网站下载
 */
public class UpdateManager {

    private static String currentVersion = "";

    public static void init(Context context,
                             String githubOwner, String githubRepo,
                             String currentVersion,
                             boolean compareByTag,
                             long currentVersionCode,
                             String fallbackUrl,
                             boolean fallbackOnly) {
        UpdateLibContext.init(context);

        UpdateRepository.fallbackUrl = fallbackUrl != null ? fallbackUrl : "";
        UpdateRepository.useFallbackOnly = fallbackOnly;

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

            // Always configure (even with empty owner/repo) to set currentVersionCode and RELEASES_PAGE_URL
            if (githubOwner != null && !githubOwner.isEmpty() &&
                    githubRepo != null && !githubRepo.isEmpty()) {
                UpdateChecker.configure(githubOwner, githubRepo, detectedVersion,
                        compareByTag, detectedVersionCode);
            } else {
                UpdateChecker.configure("", "", detectedVersion, compareByTag, detectedVersionCode);
            }
        } catch (Exception e) {
            UpdateManager.currentVersion = "0.0.0";
        }
    }

    public static void init(Context context, String githubOwner, String githubRepo) {
        init(context, githubOwner, githubRepo, "", true, 0L, "", false);
    }

    public static void init(Context context, String fallbackUrl, boolean fallbackOnly) {
        init(context, "", "", "", true, 0L,
                fallbackUrl != null ? fallbackUrl : "", fallbackOnly);
    }

    public static String getCurrentVersion() {
        return currentVersion;
    }

    public static String getReleasesPageUrl() {
        String cached = UpdateRepository.getDetailsUrl();
        return (cached != null && !cached.isEmpty()) ? cached : UpdateChecker.RELEASES_PAGE_URL;
    }
}
