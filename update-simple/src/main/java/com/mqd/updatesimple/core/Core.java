package com.mqd.updatesimple.core;

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
 * 所有核心类合并为一个文件以减少 .class 文件数量，降低 AAR 体积。
 */
public final class Core {
    private Core() {}

    // ─── 模型 ──────────────────────────────────────────────

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

    // ─── Context ───────────────────────────────────────────

    public static class UpdateLibContext {
        private static Context context;
        private static boolean initialized = false;

        public static void init(Context ctx) {
            if (!initialized) {
                context = ctx.getApplicationContext();
                initialized = true;
            }
        }
        public static Context getContext() { return context; }
        public static boolean isInitialized() { return initialized; }
    }

    // ─── JSON ──────────────────────────────────────────────

    public static final class JsonHelper {
        private JsonHelper() {}

        public static GithubRelease parseGithubRelease(String json) {
            GithubRelease release = new GithubRelease();
            try {
                JSONObject obj = new JSONObject(json);
                release.tagName = obj.optString("tag_name", "");
                release.name = obj.optString("name", "");
                release.body = obj.optString("body", "");
                release.htmlUrl = obj.optString("html_url", "");
                JSONArray arr = obj.optJSONArray("assets");
                if (arr != null) {
                    GithubRelease.Asset[] assets = new GithubRelease.Asset[arr.length()];
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject a = arr.getJSONObject(i);
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

        public static FallbackRelease parseFallbackRelease(String json) {
            FallbackRelease r = new FallbackRelease();
            try {
                JSONObject obj = new JSONObject(json);
                r.versionName = obj.optString("versionName", "");
                r.versionCode = obj.optLong("versionCode", 0);
                r.downloadUrl = obj.optString("downloadUrl", "");
                r.desContent = obj.optString("des", "");
                r.desUrl = obj.optString("desUrl", "");
            } catch (Exception ignored) {}
            return r;
        }

        public static String updateStateToJson(UpdateState s) {
            try {
                JSONObject o = new JSONObject();
                o.put("latestVersion", s.latestVersion);
                o.put("notes", s.notes);
                o.put("apkUrl", s.apkUrl);
                o.put("apkSize", s.apkSize);
                o.put("lastCheckSuccessTime", s.lastCheckSuccessTime);
                o.put("lastCheckAttemptTime", s.lastCheckAttemptTime);
                o.put("nextRetryTime", s.nextRetryTime);
                o.put("detailsUrl", s.detailsUrl);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        public static UpdateState updateStateFromJson(String json) {
            UpdateState s = new UpdateState();
            try {
                JSONObject o = new JSONObject(json);
                s.latestVersion = o.optString("latestVersion", "");
                s.notes = o.optString("notes", "");
                s.apkUrl = o.optString("apkUrl", "");
                s.apkSize = o.optLong("apkSize", 0);
                s.lastCheckSuccessTime = o.optLong("lastCheckSuccessTime", 0);
                s.lastCheckAttemptTime = o.optLong("lastCheckAttemptTime", 0);
                s.nextRetryTime = o.optLong("nextRetryTime", 0);
                s.detailsUrl = o.optString("detailsUrl", "");
            } catch (Exception ignored) {}
            return s;
        }
    }

    // ─── StateStore ────────────────────────────────────────

    public static class StateStore {
        private static final String PREFS = "updatelib_simple_state";
        private static final String KEY = "update_state";

        public static UpdateState load(Context ctx) {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return JsonHelper.updateStateFromJson(p.getString(KEY, "{}"));
        }

        public static void save(Context ctx, UpdateState state) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, JsonHelper.updateStateToJson(state)).apply();
        }
    }

    // ─── UpdateChecker ─────────────────────────────────────

    public static class UpdateChecker {
        private static final String TAG = "UpdateChecker";
        private static final int TIMEOUT = 5000;

        private static String latestApi = "";
        public static String RELEASES_PAGE_URL = "https://github.com";
        private static String ua = "updatelib (Android)";
        public static boolean compareByTag = true;
        public static long currentVersionCode = 0L;

        public static void configure(String owner, String repo, String ver,
                                     boolean compareByTag, long vc) {
            if (owner != null && !owner.isEmpty() && repo != null && !repo.isEmpty()) {
                latestApi = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
                RELEASES_PAGE_URL = "https://github.com/" + owner + "/" + repo + "/releases";
            }
            ua = owner + "/" + repo + "/" + ver + " (Android)";
            UpdateChecker.compareByTag = compareByTag;
            currentVersionCode = vc;
        }

        public interface FetchCallback {
            void onSuccess(GithubRelease release);
            void onRateLimited(long resetEpochSeconds);
            void onFailed();
        }

        public static void fetchLatestRelease(FetchCallback cb) {
            new Thread(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(latestApi).openConnection();
                    c.setRequestMethod("GET");
                    c.setConnectTimeout(TIMEOUT); c.setReadTimeout(TIMEOUT);
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    c.setRequestProperty("User-Agent", ua);
                    c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

                    int code = c.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String l;
                        while ((l = r.readLine()) != null) sb.append(l);
                        r.close();
                        cb.onSuccess(JsonHelper.parseGithubRelease(sb.toString()));
                        return;
                    }
                    if (code == 403 || code == 429) {
                        String rs = c.getHeaderField("X-RateLimit-Reset");
                        long reset = 0L;
                        if (rs != null) try { reset = Long.parseLong(rs); } catch (NumberFormatException ignored) {}
                        if (code == 429 || "0".equals(c.getHeaderField("X-RateLimit-Remaining"))) {
                            cb.onRateLimited(reset); return;
                        }
                    }
                    cb.onFailed();
                } catch (Exception e) {
                    Log.e(TAG, "fetch failed", e); cb.onFailed();
                } finally { if (c != null) c.disconnect(); }
            }).start();
        }

        public static boolean isRemoteNewer(String remote, String local) {
            int[] rp = parseVer(remote), lp = parseVer(local);
            int n = Math.max(rp.length, lp.length);
            for (int i = 0; i < n; i++) {
                int r = i < rp.length ? rp[i] : 0;
                int l = i < lp.length ? lp[i] : 0;
                if (r != l) return r > l;
            }
            return false;
        }

        static int[] parseVer(String v) {
            if (v == null) return new int[0];
            String s = v.trim();
            if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
            String[] parts = s.split("\\.");
            int[] res = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                StringBuilder num = new StringBuilder();
                for (char ch : parts[i].toCharArray()) { if (Character.isDigit(ch)) num.append(ch); else break; }
                try { res[i] = num.length() > 0 ? Integer.parseInt(num.toString()) : 0; }
                catch (NumberFormatException e) { res[i] = 0; }
            }
            return res;
        }

        public static String displayVersion(String raw) {
            if (raw == null) return "";
            String t = raw.trim();
            if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
            return "v" + t;
        }
    }

    // ─── FallbackChecker ───────────────────────────────────

    public static class FallbackChecker {
        private static final String TAG = "FB";
        private static final int TIMEOUT = 8000;

        public interface FallbackCallback { void onResult(FallbackRelease r); void onError(Exception e); }
        public interface DescriptionCallback { void onResult(String content); }

        public static void fetchFallbackRelease(String url, FallbackCallback cb) {
            new Thread(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod("GET");
                    c.setConnectTimeout(TIMEOUT); c.setReadTimeout(TIMEOUT);
                    if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String l;
                        while ((l = r.readLine()) != null) sb.append(l);
                        r.close();
                        cb.onResult(JsonHelper.parseFallbackRelease(sb.toString()));
                    } else {
                        cb.onError(new Exception("HTTP " + c.getResponseCode()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "fetchFallback failed", e); cb.onError(e);
                } finally { if (c != null) c.disconnect(); }
            }).start();
        }

        public static void fetchDescription(String url, DescriptionCallback cb) {
            new Thread(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod("GET");
                    c.setConnectTimeout(TIMEOUT); c.setReadTimeout(TIMEOUT);
                    if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String l;
                        while ((l = r.readLine()) != null) sb.append(l);
                        r.close(); cb.onResult(sb.toString());
                    } else { cb.onResult(""); }
                } catch (Exception e) { Log.e(TAG, "fetchDes failed", e); cb.onResult("");
                } finally { if (c != null) c.disconnect(); }
            }).start();
        }

        public static boolean isNewer(String rn, String ln, long rc, long lc) {
            if (UpdateChecker.isRemoteNewer(rn, ln)) return true;
            if (rn != null && rn.equals(ln)) return rc > lc;
            return false;
        }

        public static boolean isHtml(String s) {
            if (s == null) return false;
            String t = s.trim();
            return t.startsWith("<") || t.contains("<html") || t.contains("<div") || t.contains("<p>") || t.contains("<br");
        }
    }

    // ─── UpdateRepository ──────────────────────────────────

    public static class UpdateRepository {
        private static final String TAG = "Repo";
        private static final long CHECK_INT = 86400000L;
        private static final long ATTEMPT_TTL = 60000L;
        private static final long RETRY_BACKOFF = 7200000L;

        public static String fallbackUrl = "";
        public static boolean useFallbackOnly = false;
        private static volatile String detailsUrl = "";
        public static String getDetailsUrl() { return detailsUrl; }

        public interface CheckCallback { void onResult(CheckResult r); }

        public static class CheckResult {
            public static final int SKIPPED = 0, FAILED = 1, RATE_LIMITED = 2, NEW_VERSION = 3, UP_TO_DATE = 4;
            public int type;
            public UpdateState state;
            public long resetEpochSeconds;

            public static CheckResult skipped() { CheckResult r = new CheckResult(); r.type = SKIPPED; return r; }
            public static CheckResult failed() { CheckResult r = new CheckResult(); r.type = FAILED; return r; }
            public static CheckResult rateLimited(long ts) { CheckResult r = new CheckResult(); r.type = RATE_LIMITED; r.resetEpochSeconds = ts; return r; }
            public static CheckResult newVersion(UpdateState s) { CheckResult r = new CheckResult(); r.type = NEW_VERSION; r.state = s; return r; }
            public static CheckResult upToDate(UpdateState s) { CheckResult r = new CheckResult(); r.type = UP_TO_DATE; r.state = s; return r; }
        }

        public static void checkAndCache(Context ctx, boolean force, String curVer, CheckCallback cb) {
            UpdateState cur = StateStore.load(ctx);
            long now = System.currentTimeMillis();
            if (!force && now - cur.lastCheckAttemptTime < ATTEMPT_TTL) { cb.onResult(CheckResult.skipped()); return; }
            cur.lastCheckAttemptTime = now;
            StateStore.save(ctx, cur);

            if (useFallbackOnly) { tryFallback(ctx, curVer, cb); return; }

            UpdateChecker.fetchLatestRelease(new UpdateChecker.FetchCallback() {
                public void onSuccess(GithubRelease rel) { processRelease(ctx, curVer, rel, cb); }
                public void onRateLimited(long ts) {
                    tryFallback(ctx, curVer, r -> {
                        if (r.type != CheckResult.FAILED) cb.onResult(r);
                        else { setNextRetry(ctx, now + RETRY_BACKOFF); cb.onResult(CheckResult.rateLimited(ts)); }
                    });
                }
                public void onFailed() {
                    tryFallback(ctx, curVer, r -> {
                        if (r.type != CheckResult.FAILED) cb.onResult(r);
                        else { setNextRetry(ctx, now + RETRY_BACKOFF); cb.onResult(CheckResult.failed()); }
                    });
                }
            });
        }

        private static void processRelease(Context ctx, String curVer, GithubRelease rel, CheckCallback cb) {
            String cv = UpdateChecker.compareByTag ? rel.tagName : rel.tagName;
            boolean newer = UpdateChecker.isRemoteNewer(cv, curVer);
            UpdateState ns = StateStore.load(ctx);
            ns.latestVersion = cv;
            ns.notes = (rel.body != null && !rel.body.isEmpty()) ? rel.body : rel.name;
            ns.lastCheckSuccessTime = System.currentTimeMillis();
            ns.nextRetryTime = 0L;
            ns.detailsUrl = UpdateChecker.RELEASES_PAGE_URL;
            StateStore.save(ctx, ns);
            detailsUrl = UpdateChecker.RELEASES_PAGE_URL;
            cb.onResult(newer ? CheckResult.newVersion(ns) : CheckResult.upToDate(ns));
        }

        private static void tryFallback(Context ctx, String curVer, CheckCallback cb) {
            if (fallbackUrl == null || fallbackUrl.isEmpty()) { cb.onResult(CheckResult.failed()); return; }
            FallbackChecker.fetchFallbackRelease(fallbackUrl, new FallbackChecker.FallbackCallback() {
                public void onResult(FallbackRelease fr) { processFallback(ctx, curVer, fr, cb); }
                public void onError(Exception e) { Log.e(TAG, "FB err", e); cb.onResult(CheckResult.failed()); }
            });
        }

        private static void processFallback(Context ctx, String curVer, FallbackRelease fr, CheckCallback cb) {
            boolean newer = FallbackChecker.isNewer(fr.versionName, curVer, fr.versionCode, UpdateChecker.currentVersionCode);
            if (fr.desContent != null && !fr.desContent.isEmpty()) {
                FallbackChecker.fetchDescription(fr.desContent, notes -> saveAndReturn(ctx, fr, notes, newer, cb));
            } else {
                saveAndReturn(ctx, fr, "", newer, cb);
            }
        }

        private static void saveAndReturn(Context ctx, FallbackRelease fr, String notes, boolean newer, CheckCallback cb) {
            UpdateState ns = StateStore.load(ctx);
            ns.latestVersion = fr.versionName;
            ns.notes = notes;
            ns.lastCheckSuccessTime = System.currentTimeMillis();
            ns.nextRetryTime = 0L;
            ns.detailsUrl = fr.desUrl;
            StateStore.save(ctx, ns);
            detailsUrl = fr.desUrl;
            cb.onResult(newer ? CheckResult.newVersion(ns) : CheckResult.upToDate(ns));
        }

        private static void setNextRetry(Context ctx, long t) {
            UpdateState st = StateStore.load(ctx);
            st.nextRetryTime = t;
            StateStore.save(ctx, st);
        }
    }
}
