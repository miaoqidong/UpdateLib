package com.mqd.updatejava.core;

/**
 * 更新状态缓存。
 */
public class UpdateState {
    public String latestVersion = "";
    public String notes = "";
    public String apkUrl = "";
    public long apkSize = 0L;
    public long lastCheckSuccessTime = 0L;
    public long lastCheckAttemptTime = 0L;
    public long nextRetryTime = 0L;
    public String detailsUrl = "";
}
