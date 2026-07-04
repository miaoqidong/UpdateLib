package com.mqd.updatesimple.core;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GitHub Releases API 检查器。
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final int TIMEOUT_MS = 5000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private static String latestReleaseApi = "";
    public static String RELEASES_PAGE_URL = "https://github.com";
    private static String USER_AGENT = "updatelib (Android)";
    public static boolean compareByTag = true;
    public static long currentVersionCode = 0L;

    public static void configure(String owner, String repo, String versionName,
                                 boolean compareByTag, long versionCode) {
        if (owner != null && !owner.isEmpty() && repo != null && !repo.isEmpty()) {
            latestReleaseApi = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            RELEASES_PAGE_URL = "https://github.com/" + owner + "/" + repo + "/releases";
        }
        USER_AGENT = owner + "/" + repo + "/" + versionName + " (Android)";
        UpdateChecker.compareByTag = compareByTag;
        currentVersionCode = versionCode;
    }

    public interface FetchCallback {
        void onSuccess(GithubRelease release);
        void onRateLimited(long resetEpochSeconds);
        void onFailed();
    }

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
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    GithubRelease release = JsonHelper.parseGithubRelease(sb.toString());
                    callback.onSuccess(release);
                    return;
                }
                if (code == HttpURLConnection.HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS) {
                    String resetStr = conn.getHeaderField("X-RateLimit-Reset");
                    long reset = 0L;
                    if (resetStr != null) {
                        try { reset = Long.parseLong(resetStr); } catch (NumberFormatException ignored) {}
                    }
                    if (code == HTTP_TOO_MANY_REQUESTS || "0".equals(conn.getHeaderField("X-RateLimit-Remaining"))) {
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
            try {
                result[i] = num.length() > 0 ? Integer.parseInt(num.toString()) : 0;
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    public static String displayVersion(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) trimmed = trimmed.substring(1);
        return "v" + trimmed;
    }
}
