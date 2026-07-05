package com.mqd.updatejava.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.mqd.updatejava.UpdateManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 前台下载 Service + HTTP 断点续传下载（合并自 DownloadService + ApkInstaller 下载逻辑）。
 */
public class DownloadService extends Service {

    private static final String EXTRA_VERSION = "extra_version";
    private static final String EXTRA_URL = "extra_url";
    private static final String EXTRA_SIZE = "extra_size";

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final String CHANNEL_ID = "updatelib_dl";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isDownloading = false;
    private int lastStartId = -1;

    public static void start(Context context, String version, String url, long size) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(EXTRA_VERSION, version);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_SIZE, size);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        startForegroundCompat(0);

        if (isDownloading) return START_NOT_STICKY;

        String version = intent != null ? intent.getStringExtra(EXTRA_VERSION) : null;
        String url = intent != null ? intent.getStringExtra(EXTRA_URL) : null;
        long size = intent != null ? intent.getLongExtra(EXTRA_SIZE, 0L) : 0L;

        if (url == null || url.isEmpty() || version == null || version.isEmpty()) {
            stopSelfResult(lastStartId);
            return START_NOT_STICKY;
        }

        isDownloading = true;
        UpdateManager.onDownloadStart(version);

        final String fVersion = version;
        final String fUrl = url;
        final long fSize = size;

        executor.execute(() -> {
            try {
                File dir = updateDir();
                File dest = apkFile(fVersion);
                UpdateManager.clearOutdatedApks(dir, dest.getName());

                final int[] lastNotified = {-1};
                boolean success = downloadApk(fUrl, dest, fSize, percent -> {
                    mainHandler.post(() -> {
                        UpdateManager.onDownloadProgress(percent);
                        if (percent >= 100 || percent - lastNotified[0] >= 2) {
                            lastNotified[0] = percent;
                            updateForegroundNotification(percent);
                        }
                    });
                });

                mainHandler.post(() -> {
                    if (success) {
                        UpdateManager.onDownloadFinish();
                    } else {
                        UpdateManager.onDownloadFailed(fVersion);
                    }
                    stopForegroundCompat();
                    isDownloading = false;
                    stopSelfResult(lastStartId);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    UpdateManager.onDownloadFailed(fVersion);
                    stopForegroundCompat();
                    isDownloading = false;
                    stopSelfResult(lastStartId);
                });
            }
        });

        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ════════════════════ 文件路径 ════════════════════

    private File updateDir() {
        File base = getExternalCacheDir();
        if (base == null) base = getCacheDir();
        return new File(base, "update");
    }

    private File apkFile(String version) {
        String safe = (version != null && !version.trim().isEmpty()) ? version.trim() : "update";
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(updateDir(), safe + ".apk");
    }

    // ════════════════════ HTTP 断点续传下载 ════════════════════

    private interface ProgressCallback {
        void onProgress(int percent);
    }

    private static boolean downloadApk(String url, File destFile, long expectedSize,
                                        ProgressCallback onProgress) {
        File partFile = new File(destFile.getParentFile(), destFile.getName() + ".part");
        HttpURLConnection conn = null;
        try {
            destFile.getParentFile().mkdirs();

            if (isDownloaded(destFile, expectedSize)) {
                if (onProgress != null) onProgress.onProgress(100);
                return true;
            }

            long existing = 0L;
            if (partFile.exists()) existing = partFile.length();
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
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                /* append */
            } else if (code == HttpURLConnection.HTTP_OK) {
                existing = 0L;
                partFile.delete();
            } else {
                return false;
            }
            boolean append = (code == HttpURLConnection.HTTP_PARTIAL);

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

    private static boolean finalizePart(File partFile, File destFile, long expectedSize,
                                         ProgressCallback onProgress) {
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

    private static boolean isDownloaded(File file, long expectedSize) {
        if (!file.exists() || file.length() <= 0) return false;
        return expectedSize <= 0 || file.length() == expectedSize;
    }

    // ════════════════════ 最小化前台通知 ════════════════════

    private void startForegroundCompat(int progress) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(new NotificationChannel(
                            CHANNEL_ID, "更新下载", NotificationManager.IMPORTANCE_LOW));
                }
            }
            int p = Math.max(0, Math.min(100, progress));
            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(getApplicationInfo().icon)
                    .setContentTitle("正在下载更新")
                    .setContentText(p + "%")
                    .setProgress(100, p, false)
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, n);
            }
        } catch (Exception ignored) {}
    }

    private void updateForegroundNotification(int progress) {
        try {
            int p = Math.max(0, Math.min(100, progress));
            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(getApplicationInfo().icon)
                    .setContentTitle("正在下载更新")
                    .setContentText(p + "%")
                    .setProgress(100, p, false)
                    .setOngoing(true)
                    .build();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(1, n);
        } catch (Exception ignored) {}
    }

    private void stopForegroundCompat() {
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
    }
}
