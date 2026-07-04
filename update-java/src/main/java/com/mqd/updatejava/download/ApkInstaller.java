package com.mqd.updatejava.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.core.content.FileProvider;

/**
 * APK 下载 + 安装器。
 */
public class ApkInstaller {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final String UPDATE_DIR_NAME = "update";

    private static String fileProviderAuthority = "";

    public static void configure(String authority) {
        fileProviderAuthority = authority;
    }

    public static File updateDir(Context context) {
        File base = context.getExternalCacheDir();
        if (base == null) base = context.getCacheDir();
        return new File(base, UPDATE_DIR_NAME);
    }

    public static String apkFileName(String version) {
        String safe = (version != null && !version.trim().isEmpty()) ? version.trim() : "update";
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe + ".apk";
    }

    public static File apkFile(Context context, String version) {
        return new File(updateDir(context), apkFileName(version));
    }

    public interface ProgressCallback {
        void onProgress(int percent);
    }

    /**
     * 断点续传下载（同步方法，需在后台线程调用）。
     */
    public static boolean download(String url, File destFile, long expectedSize, ProgressCallback onProgress) {
        File partFile = new File(destFile.getParentFile(), destFile.getName() + ".part");
        HttpURLConnection conn = null;
        try {
            destFile.getParentFile().mkdirs();

            if (isDownloaded(destFile, expectedSize)) {
                if (onProgress != null) onProgress.onProgress(100);
                return true;
            }

            long existing = 0L;
            if (partFile.exists()) {
                existing = partFile.length();
            }
            if (expectedSize > 0 && existing > expectedSize) {
                partFile.delete();
                existing = 0L;
            }
            if (expectedSize > 0 && existing == expectedSize) {
                return finalizePart(partFile, destFile, expectedSize, onProgress);
            }

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            if (existing > 0) {
                conn.setRequestProperty("Range", "bytes=" + existing + "-");
            }

            boolean append;
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                append = true;
            } else if (code == HttpURLConnection.HTTP_OK) {
                append = false;
                existing = 0L;
                partFile.delete();
            } else {
                return false;
            }

            long remaining = conn.getContentLength();
            long total = expectedSize > 0 ? expectedSize :
                    (remaining > 0 ? existing + remaining : -1L);
            long downloaded = existing;
            int lastPercent = -1;

            InputStream input = conn.getInputStream();
            FileOutputStream output = new FileOutputStream(partFile, append);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (total > 0) {
                    int percent = (int) ((downloaded * 100) / total);
                    if (percent > 100) percent = 100;
                    if (percent != lastPercent && onProgress != null) {
                        lastPercent = percent;
                        onProgress.onProgress(percent);
                    }
                }
            }
            output.flush();
            output.close();
            input.close();

            return finalizePart(partFile, destFile, expectedSize, onProgress);
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean finalizePart(File partFile, File destFile, long expectedSize, ProgressCallback onProgress) {
        if (expectedSize > 0 && partFile.length() != expectedSize) {
            partFile.delete();
            return false;
        }
        if (destFile.exists()) destFile.delete();
        if (!partFile.renameTo(destFile)) {
            try {
                FileInputStream fis = new FileInputStream(partFile);
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buf = new byte[BUFFER_SIZE];
                int r;
                while ((r = fis.read(buf)) != -1) fos.write(buf, 0, r);
                fis.close();
                fos.close();
                partFile.delete();
            } catch (Exception e) {
                return false;
            }
        }
        if (onProgress != null) onProgress.onProgress(100);
        return true;
    }

    public static boolean isDownloaded(File file, long expectedSize) {
        if (!file.exists() || file.length() <= 0) return false;
        if (expectedSize > 0) return file.length() == expectedSize;
        return true;
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

    /**
     * 拉起系统安装器。使用自实现的 FileProvider 提供 content URI。
     */
    public static boolean installApk(Context context, File file) {
        try {
            String authority = !fileProviderAuthority.isEmpty() ?
                    fileProviderAuthority : context.getPackageName() + ".updatejava.fileprovider";
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
            } catch (Exception ignored) {
            }
        }
    }
}
