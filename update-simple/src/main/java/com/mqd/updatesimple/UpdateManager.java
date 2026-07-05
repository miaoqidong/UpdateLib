package com.mqd.updatesimple;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 极简更新助手——检查更新 + 弹窗 + 跳转网站，零资源依赖。
 *
 * 支持三种模式：
 * 1. 自定义 JSON：{"versionName":"1.2.0","desUrl":"https://...","des":"更新内容（可选）"}
 * 2. GitHub Releases：传入 owner/repo，自动读取最新 Release，解析 assets 找到 APK 直链；
 *    版本号优先从 APK 文件名提取，回退到 tag_name
 * 3. GitHub 优先 + JSON 兜底：GitHub 获取失败（含速率限制）时自动降级到自定义 JSON
 *
 * 更新日志自动识别 HTML 与纯文本，用 TextView 渲染。
 */
public class UpdateManager {

    private static String jsonUrl;
    private static String apiUrl;
    private static String releasesUrl;
    private static boolean isGithub;
    private static boolean hasFallback;
    private static String curVer;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private UpdateManager() {}

    /**
     * 自定义 JSON 方案：传入 JSON 端点 URL。
     * JSON 格式：{"versionName":"1.2.0","desUrl":"https://...","des":"更新内容（可选）"}
     */
    public static void init(Context ctx, String url) {
        jsonUrl = url;
        isGithub = false;
        hasFallback = false;
        detectVersion(ctx);
    }

    /**
     * GitHub Releases 方案：传入 owner 和 repo，自动读取最新 Release。
     */
    public static void init(Context ctx, String owner, String repo) {
        apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        releasesUrl = "https://github.com/" + owner + "/" + repo + "/releases";
        isGithub = true;
        hasFallback = false;
        detectVersion(ctx);
    }

    /**
     * GitHub 优先 + JSON 兜底：先尝试 GitHub Releases，失败后降级到自定义 JSON。
     */
    public static void init(Context ctx, String owner, String repo, String fallbackJsonUrl) {
        apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        releasesUrl = "https://github.com/" + owner + "/" + repo + "/releases";
        jsonUrl = fallbackJsonUrl;
        isGithub = true;
        hasFallback = true;
        detectVersion(ctx);
    }

    private static void detectVersion(Context ctx) {
        try {
            PackageInfo pi;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pi = ctx.getPackageManager().getPackageInfo(
                        ctx.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            }
            curVer = pi.versionName != null ? pi.versionName : "0";
        } catch (Exception e) { curVer = "0"; }
    }

    /** 从 APK 文件名中提取版本号，如 "app-v1.2.3.apk" → "1.2.3"。失败返回 null。 */
    private static String extractVersionFromFileName(String fileName) {
        if (fileName == null) return null;
        String nameWithoutExt;
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) nameWithoutExt = fileName.substring(0, dotIdx);
        else nameWithoutExt = fileName;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+){1,3})");
        java.util.regex.Matcher matcher = pattern.matcher(nameWithoutExt);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 检查更新，发现新版本则弹窗提示。需在主线程调用。 */
    public static void check(Activity act) {
        new Thread(() -> {
            String[] result = null;

            if (isGithub && apiUrl != null) {
                result = doFetch(apiUrl, true);
            }

            if (result == null && jsonUrl != null) {
                if (hasFallback || !isGithub) {
                    result = doFetch(jsonUrl, false);
                }
            }

            if (result != null && !result[0].isEmpty() && isNewer(result[0], curVer)) {
                String remote = result[0], targetUrl = result[1], notes = result[2];
                mainHandler.post(() -> showDialog(act, remote, targetUrl, notes));
            }
        }).start();
    }

    /** 执行 HTTP 请求并解析，返回 [versionName, targetUrl, releaseNotes] 或 null。 */
    private static String[] doFetch(String url, boolean github) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(5000);
            if (github) {
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "updatesimple (Android)");
            }
            int code = c.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String l;
                while ((l = r.readLine()) != null) sb.append(l);
                r.close(); c.disconnect();
                JSONObject o = new JSONObject(sb.toString());
                String remote, targetUrl, notes;
                if (github) {
                    // 解析 assets 数组，找到第一个 .apk 文件的直链地址
                    org.json.JSONArray assetsArr = o.optJSONArray("assets");
                    String apkUrl = null;
                    String apkFileName = null;
                    if (assetsArr != null) {
                        for (int i = 0; i < assetsArr.length(); i++) {
                            org.json.JSONObject a = assetsArr.getJSONObject(i);
                            String name = a.optString("name", "");
                            if (name.toLowerCase().endsWith(".apk")) {
                                apkUrl = a.optString("browser_download_url", null);
                                apkFileName = name;
                                break;
                            }
                        }
                    }
                    // 版本号：优先从 APK 文件名提取，回退到 tag_name
                    String extractedVer = extractVersionFromFileName(apkFileName);
                    remote = (extractedVer != null) ? extractedVer : o.optString("tag_name", "");
                    // 跳转地址：优先 APK 直链，回退到 Releases 页面
                    targetUrl = (apkUrl != null && !apkUrl.isEmpty())
                            ? apkUrl : o.optString("html_url", releasesUrl);
                    notes = o.optString("body", "");
                } else {
                    remote = o.optString("versionName", "");
                    targetUrl = o.optString("desUrl", url);
                    notes = o.optString("des", "");
                }
                return new String[]{remote, targetUrl, notes};
            }
            // GitHub API 速率限制：429 或 X-RateLimit-Remaining=0 的 403
            if (github && (code == 429 || code == 403)) {
                String remaining = c.getHeaderField("X-RateLimit-Remaining");
                if (code == 429 || "0".equals(remaining)) {
                    c.disconnect();
                    return null; // 触发 fallback
                }
            }
            c.disconnect();
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isNewer(String remote, String local) {
        int[] rp = parse(remote), lp = parse(local);
        int n = Math.max(rp.length, lp.length);
        for (int i = 0; i < n; i++) {
            int r = i < rp.length ? rp[i] : 0;
            int l = i < lp.length ? lp[i] : 0;
            if (r != l) return r > l;
        }
        return false;
    }

    private static int[] parse(String v) {
        if (v == null) return new int[0];
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String[] parts = s.split("\\.");
        int[] res = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { res[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", "")); }
            catch (Exception e) { res[i] = 0; }
        }
        return res;
    }

    private static boolean isHtml(String content) {
        if (content == null) return false;
        String s = content.trim().toLowerCase();
        return s.contains("<p") || s.contains("<br") || s.contains("<div")
                || s.contains("<ul") || s.contains("<ol") || s.contains("<li")
                || s.contains("<h") || s.contains("<a ") || s.contains("<img")
                || s.contains("<table") || s.contains("<html") || s.contains("<body")
                || s.startsWith("<");
    }

    private static void showDialog(Activity act, String newVer, String targetUrl, String releaseNotes) {
        float density = act.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        int pad8 = (int) (8 * density);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView tvVersion = new TextView(act);
        tvVersion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvVersion.setTypeface(tvVersion.getTypeface(), Typeface.BOLD);
        tvVersion.setText(act.getString(R.string.updatelib_simple_version_compare, curVer, newVer));
        tvVersion.setPadding(0, 0, 0, pad8);
        root.addView(tvVersion);

        if (releaseNotes != null && !releaseNotes.trim().isEmpty()) {
            String notes = releaseNotes.trim();
            TextView tvNotes = new TextView(act);
            tvNotes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tvNotes.setMaxLines(10);
            if (isHtml(notes)) {
                tvNotes.setMovementMethod(LinkMovementMethod.getInstance());
                tvNotes.setText(android.text.Html.fromHtml(notes, android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvNotes.setMovementMethod(ScrollingMovementMethod.getInstance());
                tvNotes.setText(notes);
            }
            root.addView(tvNotes);
        }

        new AlertDialog.Builder(act)
                .setTitle(act.getString(R.string.updatelib_simple_update_available_title))
                .setView(root)
                .setPositiveButton(act.getString(R.string.updatelib_simple_go_download), (d, w) -> {
                    try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))); }
                    catch (Exception ignored) {}
                })
                .setNegativeButton(act.getString(R.string.updatelib_simple_cancel), (d, w) -> d.dismiss())
                .show();
    }
}
