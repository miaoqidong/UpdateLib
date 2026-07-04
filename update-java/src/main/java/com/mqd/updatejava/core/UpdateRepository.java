package com.mqd.updatejava.core;

import android.content.Context;
import android.util.Log;

/**
 * 检查更新逻辑：拉取 GitHub → 写入缓存。
 */
public class UpdateRepository {

    private static final String TAG = "UpdateRepository";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L;
    private static final long ATTEMPT_TTL_MS = 60 * 1000L;
    private static final long FAILED_RETRY_BACKOFF_MS = 2 * 60 * 60 * 1000L;

    public static String fallbackUrl = "";
    public static boolean useFallbackOnly = false;

    private static volatile String detailsUrl = "";

    public static String getDetailsUrl() { return detailsUrl; }

    public interface CheckCallback {
        void onResult(CheckResult result);
    }

    public static class CheckResult {
        public enum Type { SKIPPED, FAILED, RATE_LIMITED, NO_APK, NEW_VERSION, UP_TO_DATE }
        public Type type;
        public UpdateState state;
        public String version;
        public long resetEpochSeconds;

        public static CheckResult skipped() {
            CheckResult r = new CheckResult(); r.type = Type.SKIPPED; return r;
        }
        public static CheckResult failed() {
            CheckResult r = new CheckResult(); r.type = Type.FAILED; return r;
        }
        public static CheckResult rateLimited(long resetEpochSeconds) {
            CheckResult r = new CheckResult(); r.type = Type.RATE_LIMITED; r.resetEpochSeconds = resetEpochSeconds; return r;
        }
        public static CheckResult noApk(String version) {
            CheckResult r = new CheckResult(); r.type = Type.NO_APK; r.version = version; return r;
        }
        public static CheckResult newVersion(UpdateState state) {
            CheckResult r = new CheckResult(); r.type = Type.NEW_VERSION; r.state = state; return r;
        }
        public static CheckResult upToDate(UpdateState state) {
            CheckResult r = new CheckResult(); r.type = Type.UP_TO_DATE; r.state = state; return r;
        }

        public boolean isNewVersion() { return type == Type.NEW_VERSION; }
        public boolean isUpToDate() { return type == Type.UP_TO_DATE; }
        public boolean isFailed() { return type == Type.FAILED; }
        public boolean isRateLimited() { return type == Type.RATE_LIMITED; }
        public boolean isNoApk() { return type == Type.NO_APK; }
        public boolean isSkipped() { return type == Type.SKIPPED; }
    }

    public static boolean shouldCheck(Context context) {
        UpdateState state = StateStore.load(context);
        long now = System.currentTimeMillis();
        if (now - state.lastCheckSuccessTime < CHECK_INTERVAL_MS) return false;
        return now >= state.nextRetryTime;
    }

    public static void checkAndCache(Context context, boolean force, String currentVersion, CheckCallback callback) {
        UpdateState current = StateStore.load(context);
        long now = System.currentTimeMillis();
        if (!force && now - current.lastCheckAttemptTime < ATTEMPT_TTL_MS) {
            callback.onResult(CheckResult.skipped());
            return;
        }
        current.lastCheckAttemptTime = now;
        StateStore.save(context, current);

        if (useFallbackOnly) {
            tryFallback(context, currentVersion, callback);
            return;
        }

        UpdateChecker.fetchLatestRelease(new UpdateChecker.FetchCallback() {
            @Override
            public void onSuccess(GithubRelease release) {
                processRelease(context, currentVersion, release, callback);
            }

            @Override
            public void onRateLimited(long resetEpochSeconds) {
                Log.w(TAG, "GitHub rate limited, trying fallback...");
                tryFallback(context, currentVersion, new CheckCallback() {
                    @Override
                    public void onResult(CheckResult fallbackResult) {
                        if (fallbackResult.type != CheckResult.Type.FAILED) {
                            callback.onResult(fallbackResult);
                        } else {
                            setNextRetry(context, now + FAILED_RETRY_BACKOFF_MS);
                            callback.onResult(CheckResult.rateLimited(resetEpochSeconds));
                        }
                    }
                });
            }

            @Override
            public void onFailed() {
                Log.w(TAG, "GitHub failed, trying fallback...");
                tryFallback(context, currentVersion, new CheckCallback() {
                    @Override
                    public void onResult(CheckResult fallbackResult) {
                        if (fallbackResult.type != CheckResult.Type.FAILED) {
                            callback.onResult(fallbackResult);
                        } else {
                            setNextRetry(context, now + FAILED_RETRY_BACKOFF_MS);
                            callback.onResult(CheckResult.failed());
                        }
                    }
                });
            }
        });
    }

    private static void processRelease(Context context, String currentVersion, GithubRelease release, CheckCallback callback) {
        GithubRelease.Asset asset = UpdateChecker.pickApkAsset(release);

        String compareVersion;
        if (UpdateChecker.compareByTag) {
            compareVersion = release.tagName;
        } else {
            String extracted = asset != null ? UpdateChecker.extractVersionFromFileName(asset.name) : null;
            compareVersion = (extracted != null) ? extracted : release.tagName;
        }

        boolean isNewer = UpdateChecker.isRemoteNewer(compareVersion, currentVersion);
        boolean hasApk = asset != null && asset.size > 0;

        if (isNewer && !hasApk) {
            setNextRetry(context, 0L);
            callback.onResult(CheckResult.noApk(compareVersion));
            return;
        }

        UpdateState newState = StateStore.load(context);
        newState.latestVersion = compareVersion;
        newState.notes = (release.body != null && !release.body.isEmpty()) ? release.body : release.name;
        newState.apkUrl = asset != null ? asset.browserDownloadUrl : "";
        newState.apkSize = asset != null ? asset.size : 0L;
        newState.lastCheckSuccessTime = System.currentTimeMillis();
        newState.nextRetryTime = 0L;
        newState.detailsUrl = UpdateChecker.RELEASES_PAGE_URL;
        StateStore.save(context, newState);
        detailsUrl = UpdateChecker.RELEASES_PAGE_URL;

        if (isNewer) {
            callback.onResult(CheckResult.newVersion(newState));
        } else {
            callback.onResult(CheckResult.upToDate(newState));
        }
    }

    private static void tryFallback(Context context, String currentVersion, CheckCallback callback) {
        if (fallbackUrl == null || fallbackUrl.isEmpty()) {
            callback.onResult(CheckResult.failed());
            return;
        }

        FallbackChecker.fetchFallbackRelease(fallbackUrl, new FallbackChecker.FallbackCallback() {
            @Override
            public void onResult(FallbackRelease fbRelease) {
                processFallback(context, currentVersion, fbRelease, callback);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Fallback also failed", e);
                callback.onResult(CheckResult.failed());
            }
        });
    }

    private static void processFallback(Context context, String currentVersion, FallbackRelease fbRelease, CheckCallback callback) {
        long now = System.currentTimeMillis();
        boolean isNewer = FallbackChecker.isNewer(
                fbRelease.versionName, currentVersion,
                fbRelease.versionCode, UpdateChecker.currentVersionCode
        );

        boolean hasApk = fbRelease.downloadUrl != null && !fbRelease.downloadUrl.isEmpty();

        if (isNewer && !hasApk) {
            setNextRetry(context, 0L);
            callback.onResult(CheckResult.noApk(fbRelease.versionName));
            return;
        }

        // Fetch description if needed
        if (fbRelease.desContent != null && !fbRelease.desContent.isEmpty()) {
            FallbackChecker.fetchDescription(fbRelease.desContent, notes -> {
                saveAndReturnFallback(context, currentVersion, fbRelease, notes, isNewer, callback);
            });
        } else {
            saveAndReturnFallback(context, currentVersion, fbRelease, "", isNewer, callback);
        }
    }

    private static void saveAndReturnFallback(Context context, String currentVersion, FallbackRelease fbRelease,
                                               String notes, boolean isNewer, CheckCallback callback) {
        UpdateState newState = StateStore.load(context);
        newState.latestVersion = fbRelease.versionName;
        newState.notes = notes;
        newState.apkUrl = fbRelease.downloadUrl;
        newState.apkSize = 0L;
        newState.lastCheckSuccessTime = System.currentTimeMillis();
        newState.nextRetryTime = 0L;
        newState.detailsUrl = fbRelease.desUrl;
        StateStore.save(context, newState);
        detailsUrl = fbRelease.desUrl;

        if (isNewer) {
            callback.onResult(CheckResult.newVersion(newState));
        } else {
            callback.onResult(CheckResult.upToDate(newState));
        }
    }

    private static void setNextRetry(Context context, long time) {
        UpdateState state = StateStore.load(context);
        state.nextRetryTime = time;
        StateStore.save(context, state);
    }
}
