package com.jackbhai.omnitools.nativ.tools;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Sharing app-private files without a FileProvider: on API 29+ the
 * file is copied into MediaStore (the app's own media collection,
 * which needs no permission) and the resulting content:// URI is
 * shared with a read grant. On older APIs it falls back to the
 * androidx FileProvider declared by the coordinator (authority
 * com.jackbhai.omnitools.native.fileprovider); if that provider is not
 * in the manifest the share simply reports failure instead of crashing.
 */
final class ShareUtil {

    private ShareUtil() {}

    static boolean shareFile(Activity act, File file, String mime, String chooserTitle) {
        Uri uri = shareableUri(act, file, mime);
        if (uri == null) return false;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        act.startActivity(Intent.createChooser(send, chooserTitle));
        return true;
    }

    static Uri shareableUri(Activity act, File file, String mime) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Uri collection;
                String relativePath;
                if (mime.startsWith("audio/")) {
                    collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    relativePath = "Music/OmniTools";
                } else if (mime.startsWith("image/")) {
                    collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    relativePath = "Pictures/OmniTools";
                } else {
                    collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    relativePath = "Download/OmniTools";
                }
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = act.getContentResolver().insert(collection, cv);
                if (uri == null) return null;
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = act.getContentResolver().openOutputStream(uri)) {
                    if (out == null) return null;
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                act.getContentResolver().update(uri, done, null, null);
                return uri;
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return androidx.core.content.FileProvider.getUriForFile(act,
                    "com.jackbhai.omnitools.native.fileprovider", file);
        } catch (Exception e) {
            return null;
        }
    }
}
