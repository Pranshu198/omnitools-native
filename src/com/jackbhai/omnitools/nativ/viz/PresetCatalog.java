package com.jackbhai.omnitools.nativ.viz;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where .milk presets come from, in priority order:
 *
 *  1. The bundled starter pack: APK assets "presets/" (staged from
 *     extra-assets/presets/ at build time — see projectm/README), copied
 *     once into filesDir/presets because projectM needs real file paths.
 *  2. Downloads/OmniPresets — the user drops .milk files there with any
 *     file manager; scanned on every open (readable where the app's
 *     storage permissions allow; failures are silent).
 *  3. A user-picked folder via SAF (ACTION_OPEN_DOCUMENT_TREE), whose
 *     URI is persisted in prefs "omni_viz"/"preset_tree_uri". Its .milk
 *     files are copied into filesDir/user-presets so projectM gets
 *     plain paths.
 *
 * Routes 2+3 are how "thousands of presets" is reachable: the classic
 * MilkDrop packs are just folders of .milk files, no APK bloat.
 */
public final class PresetCatalog {

    public static final String PREFS = "omni_viz";
    public static final String KEY_TREE_URI = "preset_tree_uri";

    public static final class Entry {
        public final String name;
        public final File file;
        Entry(File f) {
            this.file = f;
            String n = f.getName();
            this.name = n.toLowerCase().endsWith(".milk")
                    ? n.substring(0, n.length() - 5) : n;
        }
    }

    private PresetCatalog() {}

    /** Copy bundled assets/presets/*.milk into filesDir/presets (once per version stamp). */
    public static File ensureBundledCopied(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "presets");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File stamp = new File(dir, ".pack-version");
        String want = "cotc-selection-1";
        try {
            if (stamp.exists()) {
                byte[] b = new byte[(int) stamp.length()];
                try (InputStream in = new java.io.FileInputStream(stamp)) {
                    //noinspection ResultOfMethodCallIgnored
                    in.read(b);
                }
                if (want.equals(new String(b, "UTF-8"))) return dir;
            }
        } catch (Exception ignored) {}
        try {
            String[] names = ctx.getAssets().list("presets");
            if (names != null) {
                for (String n : names) {
                    if (!n.toLowerCase().endsWith(".milk")) continue;
                    File out = new File(dir, n);
                    if (out.exists() && out.length() > 0) continue;
                    try (InputStream in = ctx.getAssets().open("presets/" + n);
                         OutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                    }
                }
            }
            try (FileOutputStream fos = new FileOutputStream(stamp)) {
                fos.write(want.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
        return dir;
    }

    /** Scan every source and return the merged, name-sorted catalog. */
    public static List<Entry> scan(Context ctx) {
        List<Entry> out = new ArrayList<>();
        collectFromDir(ensureBundledCopied(ctx), out);
        collectFromDir(new File(ctx.getFilesDir(), "user-presets"), out);
        try {
            File dl = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "OmniPresets");
            collectFromDir(dl, out);
        } catch (Exception ignored) {}
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private static void collectFromDir(File dir, List<Entry> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".milk")) {
                out.add(new Entry(f));
            }
        }
    }

    /**
     * Copy every .milk under the SAF tree into filesDir/user-presets.
     * Uses only framework DocumentsContract APIs (no androidx.documentfile
     * in this repo's libs). Returns how many presets were imported.
     * Call off the UI thread.
     */
    public static int importTree(Context ctx, Uri treeUri) {
        File dest = new File(ctx.getFilesDir(), "user-presets");
        //noinspection ResultOfMethodCallIgnored
        dest.mkdirs();
        String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        return importDir(ctx, treeUri, treeId, dest, 0);
    }

    private static int importDir(Context ctx, Uri treeUri, String parentDocId,
                                 File dest, int depth) {
        if (depth > 6) return 0;
        int count = 0;
        ContentResolver cr = ctx.getContentResolver();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        try (Cursor c = cr.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (c == null) return 0;
            while (c.moveToNext()) {
                String docId = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    count += importDir(ctx, treeUri, docId, dest, depth + 1);
                } else if (name != null && name.toLowerCase().endsWith(".milk")) {
                    File out = new File(dest, name);
                    if (out.exists() && out.length() > 0) { count++; continue; }
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    try (InputStream in = cr.openInputStream(docUri);
                         OutputStream os = new FileOutputStream(out)) {
                        if (in == null) continue;
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                        count++;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    public static Uri savedTreeUri(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY_TREE_URI, null);
        return s == null ? null : Uri.parse(s);
    }

    public static void saveTreeUri(Context ctx, Uri uri) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TREE_URI, uri.toString()).apply();
    }
}
