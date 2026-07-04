package com.mqd.updatejava.download;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * FileProvider URI 构建辅助类（不依赖 androidx）。
 */
public class UpdateLibFileProviderHelper {

    private static final Map<String, File> sCache = new HashMap<>();

    /**
     * 为文件生成 content:// URI。
     */
    public static Uri getUriForFile(Context context, String authority, File file) {
        // Parse paths XML if not cached
        String cacheKey = authority;
        if (!sCache.containsKey(cacheKey)) {
            parsePathConfig(context, authority);
        }

        // Find matching root path
        File baseDir = sCache.get(cacheKey);
        if (baseDir != null) {
            String basePath = baseDir.getAbsolutePath();
            String filePath = file.getAbsolutePath();
            if (filePath.startsWith(basePath)) {
                String relativePath = filePath.substring(basePath.length());
                if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                return new Uri.Builder()
                        .scheme("content")
                        .authority(authority)
                        .encodedPath(Uri.encode(relativePath))
                        .build();
            }
        }

        // Fallback for pre-N
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return Uri.fromFile(file);
        }

        // Last resort: external cache dir
        File extCache = context.getExternalCacheDir();
        if (extCache == null) extCache = context.getCacheDir();
        String extPath = extCache.getAbsolutePath();
        String fPath = file.getAbsolutePath();
        if (fPath.startsWith(extPath)) {
            String rel = fPath.substring(extPath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            return new Uri.Builder()
                    .scheme("content")
                    .authority(authority)
                    .encodedPath(Uri.encode("update/" + rel))
                    .build();
        }

        throw new IllegalArgumentException("Failed to build content URI for: " + file.getAbsolutePath());
    }

    private static void parsePathConfig(Context context, String authority) {
        File baseDir = null;
        try {
            int resId = context.getResources().getIdentifier("updatelib_file_paths", "xml", context.getPackageName());
            if (resId == 0) {
                // Try app resources
                resId = context.getResources().getIdentifier("updatelib_file_paths", "xml",
                        context.getPackageName());
            }
            if (resId != 0) {
                XmlResourceParser parser = context.getResources().getXml(resId);
                int eventType = parser.getEventType();
                while (eventType != XmlResourceParser.END_DOCUMENT) {
                    if (eventType == XmlResourceParser.START_TAG && "cache-path".equals(parser.getName())) {
                        String path = parser.getAttributeValue(null, "path");
                        if (path != null) {
                            File extCache = context.getExternalCacheDir();
                            if (extCache == null) extCache = context.getCacheDir();
                            baseDir = new File(extCache, path);
                        }
                    }
                    eventType = parser.next();
                }
                parser.close();
            }
        } catch (Exception ignored) {
        }

        if (baseDir == null) {
            File extCache = context.getExternalCacheDir();
            if (extCache == null) extCache = context.getCacheDir();
            baseDir = new File(extCache, "update");
        }
        sCache.put(authority, baseDir);
    }
}
