package com.jackbhai.omnitools.nativ.backup;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Android glue for {@link BackupCodec}: writes the backup JSON into the
 * public Downloads folder, offers it to the share sheet, and imports a
 * user-picked file back. All file IO happens off the UI thread; results
 * are reported by Toast (this is a user-invoked action, unlike the silent
 * update check).
 *
 * <p>Downloads write path: MediaStore on API 29+ (no permission needed);
 * the public Downloads directory on API 26–28 (needs
 * WRITE_EXTERNAL_STORAGE with maxSdkVersion 28 — see INTEGRATION.md).
 */
public final class BackupManager {

    private BackupManager() {}

    public interface Done {
        /** @param uri where the backup landed (export), or null */
        void onDone(Uri uri, String message);
    }

    /* ------------------------------------------------------------ export */

    /** Builds the backup and writes it to Downloads, then calls back on the UI thread. */
    public static void exportAsync(final Activity activity, final Done done) {
        Thread t = new Thread(() -> {
            Uri uri = null;
            String msg;
            try {
                String json = BackupCodec.exportString(
                        new SharedPrefStore(activity, BackupCodec.PREFS_MAIN),
                        new SharedPrefStore(activity, BackupCodec.PREFS_AUDIO),
                        new SharedPrefStore(activity, BackupCodec.PREFS_WIDGETS),
                        appVersion(activity));
                uri = writeToDownloads(activity, json);
                msg = uri != null ? "Backup saved to Downloads."
                        : "Could not write the backup file.";
            } catch (Exception e) {
                msg = "Backup failed: " + e.getMessage();
            }
            final Uri u = uri;
            final String m = msg;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                Toast.makeText(activity, m, Toast.LENGTH_SHORT).show();
                if (done != null) done.onDone(u, m);
            });
        }, "omni-backup-export");
        t.setDaemon(true);
        t.start();
    }

    /** Export, then open the share sheet for the produced file. */
    public static void exportAndShare(final Activity activity) {
        exportAsync(activity, (uri, msg) -> {
            if (uri == null) return;
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/json");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(send, "Share backup"));
            } catch (Exception ignored) {}
        });
    }

    private static Uri writeToDownloads(Context ctx, String json) throws Exception {
        String name = "OmniTools-Backup-"
                + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date())
                + ".json";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = ctx.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) return null;
                os.write(bytes);
            }
            return uri;
        }
        File dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (dir != null && !dir.exists()) dir.mkdirs();
        File f = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        }
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
    }

    /* ------------------------------------------------------------ import */

    /**
     * Reads the picked file and applies it. The caller launches the picker
     * (ACTION_OPEN_DOCUMENT, type application/json or *\/*) and passes the
     * resulting Uri here.
     */
    public static void importAsync(final Activity activity, final Uri uri) {
        Thread t = new Thread(() -> {
            BackupCodec.ImportResult result;
            try {
                String text;
                try (InputStream is = activity.getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new Exception("could not open file");
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    text = bos.toString("UTF-8");
                }
                result = BackupCodec.importJson(text,
                        new SharedPrefStore(activity, BackupCodec.PREFS_MAIN),
                        new SharedPrefStore(activity, BackupCodec.PREFS_AUDIO),
                        new SharedPrefStore(activity, BackupCodec.PREFS_WIDGETS));
            } catch (Exception e) {
                result = BackupCodec.ImportResult.rejected(
                        BackupCodec.ImportResult.Status.CORRUPT,
                        "Could not read the file: " + e.getMessage());
            }
            final String msg = describe(result);
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) {
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                }
            });
        }, "omni-backup-import");
        t.setDaemon(true);
        t.start();
    }

    private static String describe(BackupCodec.ImportResult r) {
        switch (r.status) {
            case OK:
                return "Backup restored: " + r.favourites + " favourites, "
                        + r.playlists + " playlists, "
                        + r.userPresets + " EQ presets, " + r.widgets + " widgets.";
            case OK_WITH_WARNINGS:
                return "Backup restored with notes: "
                        + (r.warnings.isEmpty() ? "" : r.warnings.get(0));
            case NOT_A_BACKUP:
                return "That file is not an OmniTools Native backup.";
            case UNSUPPORTED_VERSION:
                return "That backup was made by a newer app version.";
            case CORRUPT:
            default:
                return "That backup file is damaged and was not applied.";
        }
    }

    private static String appVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName == null ? "" : pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
