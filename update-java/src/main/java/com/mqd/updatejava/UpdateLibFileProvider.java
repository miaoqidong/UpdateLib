package com.mqd.updatejava;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * 简易 FileProvider（不依赖 androidx.core.content.FileProvider）。
 */
public class UpdateLibFileProvider extends ContentProvider {

    private static final String[] COLUMNS = {
            OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
    };

    private File baseDir;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        // Parse paths XML
        try {
            int resId = context.getResources().getIdentifier("updatelib_file_paths", "xml", context.getPackageName());
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
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = getFileForUri(uri);
        if (file == null) return null;

        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = getFileForUri(uri);
        if (file == null) return null;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = getFileForUri(uri);
        if (file == null) throw new FileNotFoundException("File not found for URI: " + uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }

    private File getFileForUri(Uri uri) {
        if (uri == null || uri.getPath() == null) return null;
        String path = uri.getPath();
        if (path.startsWith("/")) path = path.substring(1);
        File file = new File(baseDir, path);
        // Security: ensure the resolved file is under baseDir
        try {
            String canonical = file.getCanonicalPath();
            String base = baseDir.getCanonicalPath();
            if (!canonical.startsWith(base)) return null;
        } catch (Exception e) {
            return null;
        }
        return file.exists() ? file : null;
    }
}
