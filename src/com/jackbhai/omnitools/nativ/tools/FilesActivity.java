package com.jackbhai.omnitools.nativ.tools;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jackbhai.omnitools.nativ.Glass;

import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;

/**
 * File Manager-lite (new tool id "files"). Default view: Downloads +
 * Documents from MediaStore (no broad storage permission — scoped
 * MediaStore rows only). A Storage Access Framework tree picker covers
 * any other folder the user chooses. Open via ACTION_VIEW, share via
 * ACTION_SEND, delete with a confirm dialog (with the API 30+
 * MediaStore consent flow when another app owns the file).
 * Storage behaviour is on-device only.
 */
public final class FilesActivity extends BaseToolActivity {

    private static final int REQ_TREE = 61;
    private static final int REQ_DELETE_CONSENT = 62;

    private LinearLayout listBox;
    private TextView sortLabel;
    private int sortMode = ToolMath.SORT_NAME;
    private final List<FileItem> items = new ArrayList<>();

    // SAF state
    private Uri treeUri;
    private String currentDocId;
    private final Deque<String> docStack = new ArrayDeque<>();
    private boolean safMode;

    @Override protected String toolTitle() { return "Files"; }

    @Override protected void buildUi() {
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView downloads = pill("Downloads + Documents", Glass.NEON_CYAN);
        downloads.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        downloads.setOnClickListener(v -> { safMode = false; loadMediaStore(); });
        TextView folder = pill("Open a folder...", Glass.NEON_VIOLET);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        flp.leftMargin = (int) Glass.dp(this, 8);
        folder.setLayoutParams(flp);
        folder.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            try { startActivityForResult(it, REQ_TREE); }
            catch (Exception e) { toast("No files app available for folder picking"); }
        });
        btns.addView(downloads); btns.addView(folder);
        body().addView(btns);

        sortLabel = Glass.text(this, "", 13.5f, Glass.NEON_CYAN, true);
        Glass.pad(sortLabel, this, 2, 12, 2, 2);
        sortLabel.setOnClickListener(v -> {
            sortMode = (sortMode + 1) % 3;
            applySortAndRender();
        });
        body().addView(sortLabel);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        body().addView(listBox);
        loadMediaStore();
    }

    private String sortName() {
        return sortMode == ToolMath.SORT_NAME ? "Name"
                : sortMode == ToolMath.SORT_SIZE ? "Size" : "Date";
    }

    // ------------------------------------------------------------ MediaStore view

    private void loadMediaStore() {
        items.clear();
        Uri collection = MediaStore.Files.getContentUri("external");
        String[] proj = Build.VERSION.SDK_INT >= 29
                ? new String[]{MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.DATE_MODIFIED,
                        MediaStore.Files.FileColumns.MIME_TYPE,
                        MediaStore.Files.FileColumns.RELATIVE_PATH}
                : new String[]{MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.DATE_MODIFIED,
                        MediaStore.Files.FileColumns.MIME_TYPE,
                        MediaStore.Files.FileColumns.DATA};
        String sel;
        String[] args;
        if (Build.VERSION.SDK_INT >= 29) {
            sel = MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? OR "
                    + MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ?";
            args = new String[]{"Download/%", "Documents/%"};
        } else {
            sel = MediaStore.Files.FileColumns.DATA + " LIKE ? OR "
                    + MediaStore.Files.FileColumns.DATA + " LIKE ?";
            args = new String[]{"%/Download/%", "%/Documents/%"};
        }
        try (Cursor c = getContentResolver().query(collection, proj, sel, args, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String name = c.getString(1);
                    if (name == null) continue;
                    items.add(new FileItem(name, c.getLong(2), c.getLong(3) * 1000L,
                            false, ContentUris.withAppendedId(collection, id).toString(),
                            null, c.getString(4)));
                }
            }
        } catch (Exception e) {
            toast("Could not read Downloads/Documents: " + e.getMessage());
        }
        applySortAndRender();
    }

    // ------------------------------------------------------------ SAF view

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_TREE && result == RESULT_OK && data != null && data.getData() != null) {
            treeUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) { }
            safMode = true;
            docStack.clear();
            currentDocId = DocumentsContract.getTreeDocumentId(treeUri);
            loadSafDir();
        } else if (code == REQ_DELETE_CONSENT) {
            refresh();
        }
    }

    private void loadSafDir() {
        items.clear();
        if (treeUri == null || currentDocId == null) { applySortAndRender(); return; }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId);
        String[] proj = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor c = getContentResolver().query(children, proj, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    String docId = c.getString(0);
                    String name = c.getString(1);
                    String mime = c.getString(4);
                    if (name == null || docId == null) continue;
                    boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                    long size = c.isNull(2) ? -1 : c.getLong(2);
                    long mod = c.isNull(3) ? 0 : c.getLong(3);
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    items.add(new FileItem(name, size, mod, isDir,
                            docUri.toString(), docId, mime));
                }
            }
        } catch (Exception e) {
            toast("Could not open that folder: " + e.getMessage());
        }
        applySortAndRender();
    }

    private void refresh() { if (safMode) loadSafDir(); else loadMediaStore(); }

    // ------------------------------------------------------------ rendering + actions

    private void applySortAndRender() {
        Collections.sort(items, ToolMath.fileComparator(sortMode));
        sortLabel.setText("Sort: " + sortName() + "  (tap to change)"
                + (safMode ? "   ·   Folder view" : "   ·   Downloads + Documents"));
        listBox.removeAllViews();
        if (safMode && !docStack.isEmpty()) {
            TextView up = Glass.text(this, "< Up one folder", 14.5f, Glass.NEON_CYAN, true);
            Glass.pad(up, this, 4, 10, 4, 10);
            up.setOnClickListener(v -> {
                currentDocId = docStack.pop();
                loadSafDir();
            });
            listBox.addView(up);
        }
        if (items.isEmpty()) {
            listBox.addView(note(safMode ? "This folder is empty."
                    : "No files found in Downloads or Documents."));
            return;
        }
        for (FileItem it : items) listBox.addView(rowFor(it));
    }

    private View rowFor(final FileItem it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Glass.panel(this, 14));
        Glass.pad(row, this, 12, 10, 12, 10);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = (int) Glass.dp(this, 6);
        row.setLayoutParams(rlp);

        TextView name = Glass.text(this, (it.isDir ? it.name + " /" : it.name),
                15, Glass.TEXT, true);
        row.addView(name);
        String meta = (it.isDir ? "Folder" : ToolMath.formatBytes(it.size))
                + (it.modifiedMs > 0 ? "  ·  " + DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(it.modifiedMs)) : "");
        row.addView(Glass.text(this, meta, 12.5f, Glass.TEXT_DIM, false));

        if (it.isDir) {
            row.setOnClickListener(v -> {
                docStack.push(currentDocId);
                currentDocId = it.docId;
                loadSafDir();
            });
            return row;
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView open = action("Open");
        open.setOnClickListener(v -> openItem(it));
        TextView share = action("Share");
        share.setOnClickListener(v -> shareItem(it));
        TextView del = action("Delete");
        del.setOnClickListener(v -> confirmDelete(it));
        actions.addView(open); actions.addView(share); actions.addView(del);
        row.addView(actions);
        return row;
    }

    private TextView action(String label) {
        TextView t = Glass.text(this, label, 13.5f, Glass.NEON_CYAN, true);
        Glass.pad(t, this, 4, 8, 16, 2);
        return t;
    }

    private void openItem(FileItem it) {
        Uri uri = Uri.parse(it.uri);
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, it.mime != null ? it.mime : "*/*");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(view); }
        catch (Exception e) {
            // Retry without a type so Android can offer any handler.
            try {
                Intent any = new Intent(Intent.ACTION_VIEW);
                any.setData(uri);
                any.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(any);
            } catch (Exception e2) {
                toast("No app can open this file");
            }
        }
    }

    private void shareItem(FileItem it) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(it.mime != null ? it.mime : "*/*");
        send.putExtra(Intent.EXTRA_STREAM, Uri.parse(it.uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(send, "Share " + it.name)); }
        catch (Exception e) { toast("Could not share this file"); }
    }

    private void confirmDelete(final FileItem it) {
        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage(it.name + "\nThis cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> deleteItem(it))
                .show();
    }

    private void deleteItem(FileItem it) {
        Uri uri = Uri.parse(it.uri);
        try {
            if (safMode && it.docId != null) {
                if (DocumentsContract.deleteDocument(getContentResolver(), uri)) {
                    toast("Deleted");
                } else {
                    toast("Delete was not allowed for this file");
                }
            } else {
                int n = getContentResolver().delete(uri, null, null);
                toast(n > 0 ? "Deleted" : "Delete was not allowed for this file");
            }
        } catch (SecurityException se) {
            // API 30+: deleting another app's media needs the user's consent.
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    android.app.PendingIntent pi = MediaStore.createDeleteRequest(
                            getContentResolver(), Collections.singletonList(uri));
                    startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE_CONSENT,
                            null, 0, 0, 0);
                    return;
                } catch (IntentSender.SendIntentException | RuntimeException ignored) { }
            }
            toast("Delete was not allowed for this file");
        } catch (Exception e) {
            toast("Could not delete: " + e.getMessage());
        }
        refresh();
    }
}
