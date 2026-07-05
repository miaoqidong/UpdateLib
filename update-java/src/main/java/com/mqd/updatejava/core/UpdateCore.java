package com.mqd.updatejava.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 更新核心逻辑：GitHub Releases 检查 + 备用 JSON 源 + 版本比较 + 状态持久化（合并自 8 个文件）。
 */
public class UpdateCore {

    private static final String TAG = "UpdateCore";
    private static final int TIMEOUT_MS = 5000;
    private static final int FALLBACK_TIMEOUT_MS = 8000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L;
    private static final long ATTEMPT_TTL_MS = 60 * 1000L;
    private static final long FAILED_RETRY_BACKOFF_MS = 2 * 60 * 60 * 1000L;

    // ── GitHub 配置 ──
    private static String latestReleaseApi = "";
    public static String RELEASES_PAGE_URL = "https://github.com";
    private static String USER_AGENT = "updatelib (Android)";
    public static boolean compareByTag = true;
    public static long currentVersionCode = 0L;

    // ── Fallback 配置 ──
    public static String fallbackUrl = "";
    public static boolean useFallbackOnly = false;

    // ── 详情页缓存 ──
    private static volatile String detailsUrl = "";

    public static String getDetailsUrl() { return detailsUrl; }

    // ════════════════════ 数据模型 ════════════════════

    public static class GithubRelease {
        public String tagName = "";
        public String name = "";
        public String body = "";
        public String htmlUrl = "";
        public Asset[] assets = new Asset[0];

        public static class Asset {
            public String name = "";
            public String browserDownloadUrl = "";
            public long size = 0;
        }
    }

    public static class FallbackRelease {
        public String versionName = "";
        public long versionCode = 0L;
        public String downloadUrl = "";
        public String desContent = "";
        public String desUrl = "";
    }

    public static class UpdateState {
        public String latestVersion = "";
        public String notes = "";
        public String apkUrl = "";
        public long apkSize = 0L;
        public long lastCheckSuccessTime = 0L;
        public long lastCheckAttemptTime = 0L;
        public long nextRetryTime = 0L;
        public String detailsUrl = "";
    }

    public static class CheckResult {
        public enum Type { SKIPPED, FAILED, RATE_LIMITED, NO_APK, NEW_VERSION, UP_TO_DATE }
        public Type type;
        public UpdateState state;
        public String version;
        public long resetEpochSeconds;

        public boolean isNewVersion() { return type == Type.NEW_VERSION; }
        public boolean isUpToDate() { return type == Type.UP_TO_DATE; }
        public boolean isFailed() { return type == Type.FAILED; }
        public boolean isRateLimited() { return type == Type.RATE_LIMITED; }
        public boolean isNoApk() { return type == Type.NO_APK; }
        public boolean isSkipped() { return type == Type.SKIPPED; }

        static CheckResult skipped() { CheckResult r = new CheckResult(); r.type = Type.SKIPPED; return r; }
        static CheckResult failed() { CheckResult r = new CheckResult(); r.type = Type.FAILED; return r; }
        static CheckResult rateLimited(long reset) { CheckResult r = new CheckResult(); r.type = Type.RATE_LIMITED; r.resetEpochSeconds = reset; return r; }
        static CheckResult noApk(String v) { CheckResult r = new CheckResult(); r.type = Type.NO_APK; r.version = v; return r; }
        static CheckResult newVersion(UpdateState s) { CheckResult r = new CheckResult(); r.type = Type.NEW_VERSION; r.state = s; return r; }
        static CheckResult upToDate(UpdateState s) { CheckResult r = new CheckResult(); r.type = Type.UP_TO_DATE; r.state = s; return r; }
    }

    // ════════════════════ 回调接口 ════════════════════

    public interface FetchCallback {
        void onSuccess(GithubRelease release);
        void onRateLimited(long resetEpochSeconds);
        void onFailed();
    }

    public interface CheckCallback {
        void onResult(CheckResult result);
    }

    // ════════════════════ GitHub 配置 ════════════════════

    public static void configure(String owner, String repo, String versionName,
                                  boolean compareByTag, long versionCode) {
        if (owner != null && !owner.isEmpty() && repo != null && !repo.isEmpty()) {
            latestReleaseApi = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            RELEASES_PAGE_URL = "https://github.com/" + owner + "/" + repo + "/releases";
        }
        USER_AGENT = owner + "/" + repo + "/" + versionName + " (Android)";
        UpdateCore.compareByTag = compareByTag;
        currentVersionCode = versionCode;
    }

    // ════════════════════ GitHub Releases API ════════════════════

    public static void fetchLatestRelease(FetchCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(latestReleaseApi).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    callback.onSuccess(parseGithubRelease(sb.toString()));
                    return;
                }
                if (code == HttpURLConnection.HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS) {
                    String remaining = conn.getHeaderField("X-RateLimit-Remaining");
                    String resetStr = conn.getHeaderField("X-RateLimit-Reset");
                    long reset = 0L;
                    if (resetStr != null) {
                        try { reset = Long.parseLong(resetStr); } catch (NumberFormatException ignored) {}
                    }
                    if (code == HTTP_TOO_MANY_REQUESTS || "0".equals(remaining)) {
                        callback.onRateLimited(reset);
                        return;
                    }
                }
                callback.onFailed();
            } catch (Exception e) {
                Log.e(TAG, "fetchLatestRelease failed", e);
                callback.onFailed();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public static boolean isRemoteNewer(String remoteTag, String localName) {
        int[] remote = parseVersion(remoteTag);
        int[] local = parseVersion(localName);
        int size = Math.max(remote.length, local.length);
        for (int i = 0; i < size; i++) {
            int r = i < remote.length ? remote[i] : 0;
            int l = i < local.length ? local[i] : 0;
            if (r != l) return r > l;
        }
        return false;
    }

    static int[] parseVersion(String version) {
        if (version == null) return new int[0];
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        String[] parts = v.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            StringBuilder num = new StringBuilder();
            for (char c : parts[i].toCharArray()) {
                if (Character.isDigit(c)) num.append(c);
                else break;
            }
            try { result[i] = num.length() > 0 ? Integer.parseInt(num.toString()) : 0; }
            catch (NumberFormatException e) { result[i] = 0; }
        }
        return result;
    }

    public static String extractVersionFromFileName(String fileName) {
        if (fileName == null) return null;
        String nameWithoutExt;
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) nameWithoutExt = fileName.substring(0, dotIdx);
        else nameWithoutExt = fileName;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+){1,3})");
        java.util.regex.Matcher matcher = pattern.matcher(nameWithoutExt);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static GithubRelease.Asset pickApkAsset(GithubRelease release) {
        if (release == null || release.assets == null) return null;
        for (GithubRelease.Asset asset : release.assets) {
            if (asset.name != null && asset.name.toLowerCase().endsWith(".apk")) return asset;
        }
        return null;
    }

    public static String displayVersion(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) trimmed = trimmed.substring(1);
        return "v" + trimmed;
    }

    // ════════════════════ JSON 解析 ════════════════════

    private static GithubRelease parseGithubRelease(String json) {
        GithubRelease release = new GithubRelease();
        try {
            JSONObject obj = new JSONObject(json);
            release.tagName = obj.optString("tag_name", "");
            release.name = obj.optString("name", "");
            release.body = obj.optString("body", "");
            release.htmlUrl = obj.optString("html_url", "");
            JSONArray assetsArr = obj.optJSONArray("assets");
            if (assetsArr != null) {
                GithubRelease.Asset[] assets = new GithubRelease.Asset[assetsArr.length()];
                for (int i = 0; i < assetsArr.length(); i++) {
                    JSONObject a = assetsArr.getJSONObject(i);
                    GithubRelease.Asset asset = new GithubRelease.Asset();
                    asset.name = a.optString("name", "");
                    asset.browserDownloadUrl = a.optString("browser_download_url", "");
                    asset.size = a.optLong("size", 0);
                    assets[i] = asset;
                }
                release.assets = assets;
            }
        } catch (Exception ignored) {}
        return release;
    }

    private static FallbackRelease parseFallbackRelease(String json) {
        FallbackRelease release = new FallbackRelease();
        try {
            JSONObject obj = new JSONObject(json);
            release.versionName = obj.optString("versionName", "");
            release.versionCode = obj.optLong("versionCode", 0);
            release.downloadUrl = obj.optString("downloadUrl", "");
            release.desContent = obj.optString("des", "");
            release.desUrl = obj.optString("desUrl", "");
        } catch (Exception ignored) {}
        return release;
    }

    private static String updateStateToJson(UpdateState state) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("latestVersion", state.latestVersion);
            obj.put("notes", state.notes);
            obj.put("apkUrl", state.apkUrl);
            obj.put("apkSize", state.apkSize);
            obj.put("lastCheckSuccessTime", state.lastCheckSuccessTime);
            obj.put("lastCheckAttemptTime", state.lastCheckAttemptTime);
            obj.put("nextRetryTime", state.nextRetryTime);
            obj.put("detailsUrl", state.detailsUrl);
            return obj.toString();
        } catch (Exception e) { return "{}"; }
    }

    private static UpdateState updateStateFromJson(String json) {
        UpdateState state = new UpdateState();
        try {
            JSONObject obj = new JSONObject(json);
            state.latestVersion = obj.optString("latestVersion", "");
            state.notes = obj.optString("notes", "");
            state.apkUrl = obj.optString("apkUrl", "");
            state.apkSize = obj.optLong("apkSize", 0);
            state.lastCheckSuccessTime = obj.optLong("lastCheckSuccessTime", 0);
            state.lastCheckAttemptTime = obj.optLong("lastCheckAttemptTime", 0);
            state.nextRetryTime = obj.optLong("nextRetryTime", 0);
            state.detailsUrl = obj.optString("detailsUrl", "");
        } catch (Exception ignored) {}
        return state;
    }

    // ════════════════════ 备用更新源 ════════════════════

    private static void fetchFallbackRelease(String jsonUrl, FallbackCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(FALLBACK_TIMEOUT_MS);
                conn.setReadTimeout(FALLBACK_TIMEOUT_MS);
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    Log.d(TAG, "Fallback JSON body=" + sb);
                    callback.onResult(parseFallbackRelease(sb.toString()));
                } else {
                    Log.w(TAG, "Fallback HTTP " + conn.getResponseCode());
                    callback.onError(new Exception("HTTP " + conn.getResponseCode()));
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchFallbackRelease failed", e);
                callback.onError(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private interface FallbackCallback {
        void onResult(FallbackRelease release);
        void onError(Exception e);
    }

    private static void fetchDescription(String desUrl, DescriptionCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(desUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(FALLBACK_TIMEOUT_MS);
                conn.setReadTimeout(FALLBACK_TIMEOUT_MS);
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    callback.onResult(sb.toString());
                } else {
                    callback.onResult("");
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchDescription failed", e);
                callback.onResult("");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private interface DescriptionCallback {
        void onResult(String content);
    }

    private static boolean isFallbackNewer(String remoteName, String localName, long remoteCode, long localCode) {
        if (isRemoteNewer(remoteName, localName)) return true;
        if (remoteName != null && remoteName.equals(localName)) return remoteCode > localCode;
        return false;
    }

    public static boolean isHtmlContent(String content) {
        if (content == null) return false;
        String trimmed = content.trim();
        return trimmed.startsWith("<") || trimmed.contains("<html")
                || trimmed.contains("<div") || trimmed.contains("<p>")
                || trimmed.contains("<br");
    }

    // ════════════════════ 状态持久化 ════════════════════

    private static final String PREFS_NAME = "updatelib_state";
    private static final String KEY_STATE = "update_state";

    private static UpdateState loadState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return updateStateFromJson(prefs.getString(KEY_STATE, "{}"));
    }

    private static void saveState(Context context, UpdateState state) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_STATE, updateStateToJson(state)).apply();
    }

    // ════════════════════ 更新检查编排 ════════════════════

    public static boolean shouldCheck(Context context) {
        UpdateState state = loadState(context);
        long now = System.currentTimeMillis();
        if (now - state.lastCheckSuccessTime < CHECK_INTERVAL_MS) return false;
        return now >= state.nextRetryTime;
    }

    public static void checkAndCache(Context context, boolean force, String currentVersion, CheckCallback callback) {
        UpdateState current = loadState(context);
        long now = System.currentTimeMillis();
        if (!force && now - current.lastCheckAttemptTime < ATTEMPT_TTL_MS) {
            callback.onResult(CheckResult.skipped());
            return;
        }
        current.lastCheckAttemptTime = now;
        saveState(context, current);

        if (useFallbackOnly) {
            tryFallback(context, currentVersion, callback);
            return;
        }

        fetchLatestRelease(new FetchCallback() {
            @Override public void onSuccess(GithubRelease release) {
                processRelease(context, currentVersion, release, callback);
            }
            @Override public void onRateLimited(long resetEpochSeconds) {
                Log.w(TAG, "GitHub rate limited, trying fallback...");
                tryFallback(context, currentVersion, result -> {
                    if (result.type != CheckResult.Type.FAILED) callback.onResult(result);
                    else {
                        setNextRetry(context, now + FAILED_RETRY_BACKOFF_MS);
                        callback.onResult(CheckResult.rateLimited(resetEpochSeconds));
                    }
                });
            }
            @Override public void onFailed() {
                Log.w(TAG, "GitHub failed, trying fallback...");
                tryFallback(context, currentVersion, result -> {
                    if (result.type != CheckResult.Type.FAILED) callback.onResult(result);
                    else {
                        setNextRetry(context, now + FAILED_RETRY_BACKOFF_MS);
                        callback.onResult(CheckResult.failed());
                    }
                });
            }
        });
    }

    private static void processRelease(Context context, String currentVersion, GithubRelease release,
                                        CheckCallback callback) {
        GithubRelease.Asset asset = pickApkAsset(release);
        String compareVersion;
        if (compareByTag) {
            compareVersion = release.tagName;
        } else {
            String extracted = asset != null ? extractVersionFromFileName(asset.name) : null;
            compareVersion = (extracted != null) ? extracted : release.tagName;
        }

        boolean isNewer = isRemoteNewer(compareVersion, currentVersion);
        boolean hasApk = asset != null && asset.size > 0;

        if (isNewer && !hasApk) {
            setNextRetry(context, 0L);
            callback.onResult(CheckResult.noApk(compareVersion));
            return;
        }

        UpdateState newState = loadState(context);
        newState.latestVersion = compareVersion;
        newState.notes = (release.body != null && !release.body.isEmpty()) ? release.body : release.name;
        newState.apkUrl = asset != null ? asset.browserDownloadUrl : "";
        newState.apkSize = asset != null ? asset.size : 0L;
        newState.lastCheckSuccessTime = System.currentTimeMillis();
        newState.nextRetryTime = 0L;
        newState.detailsUrl = RELEASES_PAGE_URL;
        saveState(context, newState);
        detailsUrl = RELEASES_PAGE_URL;

        callback.onResult(isNewer ? CheckResult.newVersion(newState) : CheckResult.upToDate(newState));
    }

    private static void tryFallback(Context context, String currentVersion, CheckCallback callback) {
        if (fallbackUrl == null || fallbackUrl.isEmpty()) {
            callback.onResult(CheckResult.failed());
            return;
        }
        fetchFallbackRelease(fallbackUrl, new FallbackCallback() {
            @Override public void onResult(FallbackRelease fbRelease) {
                processFallback(context, currentVersion, fbRelease, callback);
            }
            @Override public void onError(Exception e) {
                Log.e(TAG, "Fallback also failed", e);
                callback.onResult(CheckResult.failed());
            }
        });
    }

    private static void processFallback(Context context, String currentVersion, FallbackRelease fbRelease,
                                         CheckCallback callback) {
        boolean isNewer = isFallbackNewer(fbRelease.versionName, currentVersion,
                fbRelease.versionCode, currentVersionCode);
        boolean hasApk = fbRelease.downloadUrl != null && !fbRelease.downloadUrl.isEmpty();

        if (isNewer && !hasApk) {
            setNextRetry(context, 0L);
            callback.onResult(CheckResult.noApk(fbRelease.versionName));
            return;
        }

        if (fbRelease.desContent != null && !fbRelease.desContent.isEmpty()) {
            fetchDescription(fbRelease.desContent, notes ->
                    saveAndReturnFallback(context, currentVersion, fbRelease, notes, isNewer, callback));
        } else {
            saveAndReturnFallback(context, currentVersion, fbRelease, "", isNewer, callback);
        }
    }

    private static void saveAndReturnFallback(Context context, String currentVersion, FallbackRelease fbRelease,
                                               String notes, boolean isNewer, CheckCallback callback) {
        UpdateState newState = loadState(context);
        newState.latestVersion = fbRelease.versionName;
        newState.notes = notes;
        newState.apkUrl = fbRelease.downloadUrl;
        newState.apkSize = 0L;
        newState.lastCheckSuccessTime = System.currentTimeMillis();
        newState.nextRetryTime = 0L;
        newState.detailsUrl = fbRelease.desUrl;
        saveState(context, newState);
        detailsUrl = fbRelease.desUrl;

        callback.onResult(isNewer ? CheckResult.newVersion(newState) : CheckResult.upToDate(newState));
    }

    private static void setNextRetry(Context context, long time) {
        UpdateState state = loadState(context);
        state.nextRetryTime = time;
        saveState(context, state);
    }
}
