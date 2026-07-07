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
 * 1. 自定义 JSON：{"versionName":"1.2.0","versionCode":123,"desUrl":"https://...","des":"更新内容（可选）"}
 *    其中 versionCode 可选，用于 versionName 相同时的辅助比较
 * 2. GitHub Releases：传入 owner/repo，自动读取最新 Release，解析 assets 找到 APK 直链；
 *    通过 compareByTag 控制版本号来源（true=tag_name, false=APK 文件名提取）
 * 3. GitHub 优先 + JSON 兜底：GitHub 获取失败（含速率限制）时自动降级到自定义 JSON
 *
 * 更新日志自动识别 HTML、纯 URL、纯文本，用 TextView 渲染。
 */
public class UpdateManager {

    private static String jsonUrl;
    private static String apiUrl;
    private static String releasesUrl;
    private static boolean isGithub;
    private static boolean hasFallback;
    private static String curVer;
    /** 版本比较模式：true 比较 tag_name，false 比较 APK 文件名中的版本号。默认 true，与 update-lib 一致。 */
    public static boolean compareByTag = true;
    /** 当前应用 versionCode，用于 versionName 相同时的辅助比较。默认 0。 */
    public static long currentVersionCode = 0L;
    /** 仅使用自定义 JSON 源，完全跳过 GitHub。默认 false。 */
    public static boolean useFallbackOnly = false;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private UpdateManager() {}

    /**
     * 统一初始化（全参数版本）。参数顺序与 update-java / update-lib 保持一致。
     *
     * @param ctx          上下文
     * @param owner        GitHub 仓库 owner，为空则跳过 GitHub
     * @param repo         GitHub 仓库 repo，为空则跳过 GitHub
     * @param compareByTag true 用 tag_name 比较，false 从 APK 文件名提取版本号
     * @param fallbackUrl  自定义 JSON 端点 URL，为空则不用 JSON 源
     * @param fallbackOnly true 则完全跳过 GitHub，仅用 JSON 源
     */
    public static void init(Context ctx, String owner, String repo,
                            boolean compareByTag,
                            String fallbackUrl, boolean fallbackOnly) {
        if (owner != null && !owner.isEmpty() && repo != null && !repo.isEmpty()) {
            apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            releasesUrl = "https://github.com/" + owner + "/" + repo + "/releases";
            isGithub = true;
        } else {
            isGithub = false;
        }
        jsonUrl = (fallbackUrl != null && !fallbackUrl.isEmpty()) ? fallbackUrl : null;
        hasFallback = jsonUrl != null;
        UpdateManager.compareByTag = compareByTag;
        UpdateManager.useFallbackOnly = fallbackOnly;
        detectVersion(ctx);
    }

    /** GitHub Releases 方案（便捷方法）。 */
    public static void init(Context ctx, String owner, String repo) {
        init(ctx, owner, repo, true, null, false);
    }

    /** GitHub 优先 + JSON 兜底（便捷方法）。 */
    public static void init(Context ctx, String owner, String repo, String fallbackUrl) {
        init(ctx, owner, repo, true, fallbackUrl, false);
    }

    /** 自定义 JSON 方案（便捷方法）。 */
    public static void init(Context ctx, String jsonUrl) {
        init(ctx, null, null, true, jsonUrl, false);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentVersionCode = pi.getLongVersionCode();
            } else {
                currentVersionCode = pi.versionCode;
            }
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

    /** 从 APK 文件名中提取 versionCode。支持 "138-v2.9.0.apk" 或 "138_v2.9.0.apk" 格式。 */
    private static long extractVersionCodeFromFileName(String fileName) {
        if (fileName == null) return 0L;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)[-_]");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            try { return Long.parseLong(matcher.group(1)); }
            catch (NumberFormatException e) { return 0L; }
        }
        return 0L;
    }

    /** 内部结果载体，同时携带 versionCode 用于辅助比较。 */
    private static class ReleaseInfo {
        String version;
        String targetUrl;
        String notes;
        long versionCode;
        ReleaseInfo(String v, String u, String n, long c) { version = v; targetUrl = u; notes = n; versionCode = c; }
    }

    /** 检查更新，发现新版本则弹窗提示。需在主线程调用。 */
    public static void check(Activity act) {
        new Thread(() -> {
            ReleaseInfo result = null;

            if (isGithub && apiUrl != null && !useFallbackOnly) {
                result = doFetch(apiUrl, true);
            }

            if (result == null && jsonUrl != null) {
                if (hasFallback || !isGithub || useFallbackOnly) {
                    result = doFetch(jsonUrl, false);
                }
            }

            if (result != null && !result.version.isEmpty() && isNewer(result)) {
                String remote = result.version, targetUrl = result.targetUrl, notes = result.notes;
                // 如果更新内容是纯 URL，获取该 URL 的页面内容替代之
                if (isPlainUrl(notes)) {
                    String fetched = fetchUrlContent(notes);
                    if (fetched != null && !fetched.isEmpty()) notes = fetched;
                }
                final String finalNotes = notes;
                mainHandler.post(() -> showDialog(act, remote, targetUrl, finalNotes));
            }
        }).start();
    }

    /** 获取 URL 指向的页面内容（纯文本或 HTML），失败返回 null。 */
    private static String fetchUrlContent(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(8000);
            if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String l;
                while ((l = r.readLine()) != null) sb.append(l);
                r.close(); c.disconnect();
                return sb.toString();
            }
            c.disconnect();
        } catch (Exception ignored) {}
        return null;
    }

    /** 执行 HTTP 请求并解析，返回 ReleaseInfo 或 null。 */
    private static ReleaseInfo doFetch(String url, boolean github) {
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
                long remoteCode = 0L;
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
                    // 版本号：compareByTag=true 用 tag_name，false 从 APK 文件名提取
                    if (compareByTag) {
                        remote = o.optString("tag_name", "");
                    } else {
                        String extractedVer = extractVersionFromFileName(apkFileName);
                        remote = (extractedVer != null) ? extractedVer : o.optString("tag_name", "");
                    }
                    // versionCode：从 APK 文件名提取（如 "138-v2.9.0.apk" → 138）
                    remoteCode = extractVersionCodeFromFileName(apkFileName);
                    // 跳转地址：优先 APK 直链，回退到 Releases 页面
                    targetUrl = (apkUrl != null && !apkUrl.isEmpty())
                            ? apkUrl : o.optString("html_url", releasesUrl);
                    notes = o.optString("body", "");
                } else {
                    remote = o.optString("versionName", "");
                    targetUrl = o.optString("desUrl", url);
                    notes = o.optString("des", "");
                    remoteCode = o.optLong("versionCode", 0L);
                }
                return new ReleaseInfo(remote, targetUrl, notes, remoteCode);
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

    /** 版本比较：先比较 versionName，相同则比较 versionCode。与 update-lib 逻辑一致。 */
    private static boolean isNewer(ReleaseInfo remote) {
        // 先比较 versionName
        int[] rp = parse(remote.version), lp = parse(curVer);
        int n = Math.max(rp.length, lp.length);
        for (int i = 0; i < n; i++) {
            int r = i < rp.length ? rp[i] : 0;
            int l = i < lp.length ? lp[i] : 0;
            if (r != l) return r > l;
        }
        // versionName 相同则比较 versionCode
        if (remote.versionCode > 0 && currentVersionCode > 0) {
            return remote.versionCode > currentVersionCode;
        }
        return false;
    }

    /** 解析版本号，每段取前导数字（与 update-java / update-lib 一致）。如 "3-rc1" → 3。 */
    private static int[] parse(String v) {
        if (v == null) return new int[0];
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String[] parts = s.split("\\.");
        int[] res = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            StringBuilder num = new StringBuilder();
            for (char c : parts[i].toCharArray()) {
                if (Character.isDigit(c)) num.append(c);
                else break;
            }
            try { res[i] = Integer.parseInt(num.toString()); }
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

    /** 判断内容是否为纯 URL（http/https 开头且不含空格/换行）。 */
    private static boolean isPlainUrl(String content) {
        if (content == null) return false;
        String s = content.trim();
        return (s.startsWith("http://") || s.startsWith("https://"))
                && !s.contains(" ") && !s.contains("\n");
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
            } else if (isPlainUrl(notes)) {
                tvNotes.setMovementMethod(LinkMovementMethod.getInstance());
                String wrapped = "<a href=\"" + notes + "\">" + notes + "</a>";
                tvNotes.setText(android.text.Html.fromHtml(wrapped, android.text.Html.FROM_HTML_MODE_LEGACY));
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
