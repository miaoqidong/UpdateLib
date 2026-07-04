package com.mqd.updatejava.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 使用 Android 内置 org.json 进行 JSON 解析，无需 kotlinx.serialization。
 */
public final class JsonHelper {

    private JsonHelper() {}

    public static GithubRelease parseGithubRelease(String json) {
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
        } catch (Exception ignored) {
        }
        return release;
    }

    public static FallbackRelease parseFallbackRelease(String json) {
        FallbackRelease release = new FallbackRelease();
        try {
            JSONObject obj = new JSONObject(json);
            release.versionName = obj.optString("versionName", "");
            release.versionCode = obj.optLong("versionCode", 0);
            release.downloadUrl = obj.optString("downloadUrl", "");
            release.desContent = obj.optString("des", "");
            release.desUrl = obj.optString("desUrl", "");
        } catch (Exception ignored) {
        }
        return release;
    }

    public static String updateStateToJson(UpdateState state) {
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
        } catch (Exception e) {
            return "{}";
        }
    }

    public static UpdateState updateStateFromJson(String json) {
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
        } catch (Exception ignored) {
        }
        return state;
    }
}
