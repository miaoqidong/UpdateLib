package com.mqd.updatejava.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.mqd.updatejava.UpdateManager;
import com.mqd.updatejava.core.UpdateCore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 前台下载 Service + 通知管理（合并自 DownloadService + UpdateNotifications）。
 */
public class DownloadService extends Service {

    private static final String EXTRA_VERSION = "extra_version";
    private static final String EXTRA_URL = "extra_url";
    private static final String EXTRA_SIZE = "extra_size";
    private static final int NOTIFY_STEP = 2;

    private static final String CHANNEL_UPDATE = "updatejava_update";
    private static final String CHANNEL_DOWNLOAD = "updatejava_download";

    public static final int NOTIFICATION_ID_NEW_VERSION = 1001;
    public static final int NOTIFICATION_ID_DOWNLOAD = 1002;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isDownloading = false;
    private int lastStartId = -1;

    // ════════════════════ 启动 ════════════════════

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
        startForegroundCompat(UpdateManager.getDownloadState().progress);

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
        cancelNewVersion(this);

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
                        UpdateManager.onDownloadProgress(percent);
                        if (percent >= 100 || percent - lastNotified[0] >= NOTIFY_STEP) {
                            lastNotified[0] = percent;
                            notifyDownloadProgress(DownloadService.this, percent);
                        }
                    });
                });

                mainHandler.post(() -> {
                    if (success) {
                        UpdateManager.onDownloadFinish();
                        stopForegroundCompat();
                        showDownloadDone(DownloadService.this, fVersion);
                    } else {
                        UpdateManager.onDownloadFailed(fVersion);
                        stopForegroundCompat();
                        showDownloadFailed(DownloadService.this);
                    }
                    isDownloading = false;
                    stopSelfResult(lastStartId);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    UpdateManager.onDownloadFailed(fVersion);
                    stopForegroundCompat();
                    showDownloadFailed(DownloadService.this);
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

    // ════════════════════ 前台通知 ════════════════════

    private void startForegroundCompat(int progress) {
        try {
            Notification notification = buildDownloadNotification(this, progress);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_DOWNLOAD, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID_DOWNLOAD, notification);
            }
        } catch (Exception ignored) {}
    }

    private void stopForegroundCompat() {
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
    }

    // ════════════════════ 通知渠道 ════════════════════

    private static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_UPDATE) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_UPDATE, "更新提醒", NotificationManager.IMPORTANCE_LOW));
        }
        if (nm.getNotificationChannel(CHANNEL_DOWNLOAD) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_DOWNLOAD, "下载进度", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private static PendingIntent contentIntent(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else {
            intent = new Intent();
        }
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    private static int getNotificationIcon(Context context) {
        return context.getApplicationInfo().icon;
    }

    private static void notifySafely(Context context, int id, Notification notification) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, notification);
        } catch (Exception ignored) {}
    }

    // ════════════════════ 公开通知 API ════════════════════

    public static void showNewVersion(Context context, String version) {
        ensureChannels(context);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_UPDATE)
                : new Notification.Builder(context);
        String contentText = version != null
                ? UpdateCore.displayVersion(version) + " 可更新，点击查看" : "发现新版本";
        Notification notification = builder
                .setSmallIcon(getNotificationIcon(context))
                .setContentTitle("发现新版本")
                .setContentText(contentText)
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .build();
        notifySafely(context, NOTIFICATION_ID_NEW_VERSION, notification);
    }

    public static void cancelNewVersion(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID_NEW_VERSION);
    }

    public static Notification buildDownloadNotification(Context context, int progress) {
        ensureChannels(context);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_DOWNLOAD)
                : new Notification.Builder(context);
        int p = Math.max(0, Math.min(100, progress));
        return builder
                .setSmallIcon(getNotificationIcon(context))
                .setContentTitle("正在下载更新")
                .setContentText(p + "%")
                .setProgress(100, p, false)
                .setOngoing(true)
                .setContentIntent(contentIntent(context))
                .build();
    }

    public static void notifyDownloadProgress(Context context, int progress) {
        notifySafely(context, NOTIFICATION_ID_DOWNLOAD, buildDownloadNotification(context, progress));
    }

    public static void showDownloadDone(Context context, String version) {
        ensureChannels(context);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_DOWNLOAD)
                : new Notification.Builder(context);
        String contentText = "点击安装 " + UpdateCore.displayVersion(version);
        Notification notification = builder
                .setSmallIcon(getNotificationIcon(context))
                .setContentTitle("下载完成")
                .setContentText(contentText)
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .setOngoing(false)
                .build();
        notifySafely(context, NOTIFICATION_ID_DOWNLOAD, notification);
    }

    public static void showDownloadFailed(Context context) {
        ensureChannels(context);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_DOWNLOAD)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(getNotificationIcon(context))
                .setContentTitle("下载失败")
                .setContentText("点击重试")
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .setOngoing(false)
                .build();
        notifySafely(context, NOTIFICATION_ID_DOWNLOAD, notification);
    }
}
