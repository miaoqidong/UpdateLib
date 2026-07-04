package com.mqd.updatejava.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.mqd.updatejava.core.UpdateChecker;

/**
 * 更新通知（不依赖 androidx.core）。
 */
public class UpdateNotifications {

    private static final String CHANNEL_UPDATE = "updatejava_update";
    private static final String CHANNEL_DOWNLOAD = "updatejava_download";

    public static final int NOTIFICATION_ID_NEW_VERSION = 1001;
    public static final int NOTIFICATION_ID_DOWNLOAD = 1002;

    private static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_UPDATE) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_UPDATE, "更新提醒", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_DOWNLOAD) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_DOWNLOAD, "下载进度", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
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

    public static void showNewVersion(Context context, String version) {
        ensureChannels(context);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_UPDATE);
        } else {
            builder = new Notification.Builder(context);
        }
        String contentText = version != null ? UpdateChecker.displayVersion(version) + " 可更新，点击查看" : "发现新版本";
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
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_DOWNLOAD);
        } else {
            builder = new Notification.Builder(context);
        }
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
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_DOWNLOAD);
        } else {
            builder = new Notification.Builder(context);
        }
        String contentText = "点击安装 " + UpdateChecker.displayVersion(version);
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
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_DOWNLOAD);
        } else {
            builder = new Notification.Builder(context);
        }
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

    private static void notifySafely(Context context, int id, Notification notification) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, notification);
        } catch (Exception ignored) {
        }
    }

    private static int getNotificationIcon(Context context) {
        return context.getResources().getIdentifier(
                "ic_updatelib_notification", "drawable", context.getPackageName());
    }
}
