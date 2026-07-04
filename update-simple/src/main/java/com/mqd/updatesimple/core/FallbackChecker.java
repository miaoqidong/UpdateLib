package com.mqd.updatesimple.core;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 备用更新源检查器。
 */
public class FallbackChecker {

    private static final String TAG = "FallbackChecker";
    private static final int TIMEOUT_MS = 8000;

    public interface FallbackCallback {
        void onResult(FallbackRelease release);
        void onError(Exception e);
    }

    public static void fetchFallbackRelease(String jsonUrl, FallbackCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    callback.onResult(JsonHelper.parseFallbackRelease(sb.toString()));
                } else {
                    Log.w(TAG, "HTTP " + conn.getResponseCode());
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

    public static void fetchDescription(String desUrl, DescriptionCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(desUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
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

    public interface DescriptionCallback {
        void onResult(String content);
    }

    public static boolean isNewer(String remoteName, String localName, long remoteCode, long localCode) {
        if (UpdateChecker.isRemoteNewer(remoteName, localName)) {
            return true;
        } else if (remoteName != null && remoteName.equals(localName)) {
            return remoteCode > localCode;
        }
        return false;
    }

    public static boolean isHtmlContent(String content) {
        if (content == null) return false;
        String trimmed = content.trim();
        return trimmed.startsWith("<") || trimmed.contains("<html")
                || trimmed.contains("<div") || trimmed.contains("<p>")
                || trimmed.contains("<br");
    }
}
