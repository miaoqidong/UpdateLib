package com.mqd.updatejava.download;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 前台下载 Service（无 coroutines，用 ExecutorService）。
 */
public class DownloadService extends Service {

    private static final String EXTRA_VERSION = "extra_version";
    private static final String EXTRA_URL = "extra_url";
    private static final String EXTRA_SIZE = "extra_size";
    private static final int NOTIFY_STEP = 2;

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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        startForegroundCompat(DownloadController.getCurrentState().progress);

        if (isDownloading) {
            return START_NOT_STICKY;
        }

        String version = intent != null ? intent.getStringExtra(EXTRA_VERSION) : null;
        String url = intent != null ? intent.getStringExtra(EXTRA_URL) : null;
        long size = intent != null ? intent.getLongExtra(EXTRA_SIZE, 0L) : 0L;

        if (url == null || url.isEmpty() || version == null || version.isEmpty()) {
            stopSelfResult(lastStartId);
            return START_NOT_STICKY;
        }

        isDownloading = true;
        DownloadController.onStart(version);
        UpdateNotifications.cancelNewVersion(this);

        final String fVersion = version;
        final String fUrl = url;
        final long fSize = size;

        executor.execute(() -> {
            try {
                java.io.File dir = ApkInstaller.updateDir(DownloadService.this);
                java.io.File dest = ApkInstaller.apkFile(DownloadService.this, fVersion);
                ApkInstaller.clearOutdatedApks(dir, dest.getName());

                final int[] lastNotified = {-1};
                boolean success = ApkInstaller.download(fUrl, dest, fSize, percent -> {
                    mainHandler.post(() -> {
                        DownloadController.onProgress(percent);
                        if (percent >= 100 || percent - lastNotified[0] >= NOTIFY_STEP) {
                            lastNotified[0] = percent;
                            UpdateNotifications.notifyDownloadProgress(DownloadService.this, percent);
                        }
                    });
                });

                mainHandler.post(() -> {
                    if (success) {
                        DownloadController.onFinish();
                        stopForegroundCompat();
                        UpdateNotifications.showDownloadDone(DownloadService.this, fVersion);
                    } else {
                        DownloadController.onFailed(fVersion);
                        stopForegroundCompat();
                        UpdateNotifications.showDownloadFailed(DownloadService.this);
                    }
                    isDownloading = false;
                    stopSelfResult(lastStartId);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    DownloadController.onFailed(fVersion);
                    stopForegroundCompat();
                    UpdateNotifications.showDownloadFailed(DownloadService.this);
                    isDownloading = false;
                    stopSelfResult(lastStartId);
                });
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void startForegroundCompat(int progress) {
        try {
            android.app.Notification notification = UpdateNotifications.buildDownloadNotification(this, progress);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(UpdateNotifications.NOTIFICATION_ID_DOWNLOAD, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(UpdateNotifications.NOTIFICATION_ID_DOWNLOAD, notification);
            }
        } catch (Exception e) {
            // If startForeground fails, the service will be killed by the system
        }
    }

    private void stopForegroundCompat() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
    }
}
