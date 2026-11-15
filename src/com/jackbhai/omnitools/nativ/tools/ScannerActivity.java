package com.jackbhai.omnitools.nativ.tools;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.exifinterface.media.ExifInterface;

import com.jackbhai.omnitools.nativ.Glass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Document Scanner (registry id "scan"). Capture is intent-based
 * (ACTION_IMAGE_CAPTURE writing full-res into a MediaStore row we own —
 * the provable route without a camera stack of our own), plus gallery
 * import. Each page then goes through the native additions the PWA
 * scanner could not do: draggable-corner crop and a real perspective
 * warp (homography in {@link Perspective}, applied with
 * Matrix.setPolyToPoly), then the PWA-proved enhance pipeline
 * ({@link Enhance}: contrast stretch + document B&W + sharpen) and a
 * multi-page PDF export via PdfDocument with the pure
 * {@link PdfPages} layout maths.
 * Camera / rendering behaviour is on-device only.
 */
public final class ScannerActivity extends Activity {

    private static final int REQ_CAPTURE = 51;
    private static final int REQ_GALLERY = 52;
    private static final int MAX_W = 1600;      // same cap as the PWA scanner
    private static final int DECODE_CAP = 2400; // pre-crop decode cap

    private LinearLayout root, bodyCol;
    private final List<Bitmap> pages = new ArrayList<>();
    private final Queue<Bitmap> pendingCrops = new ArrayDeque<>();
    private Uri pendingCaptureUri;
    private String mode = "doc"; // doc | colour | orig
    private File lastPdf;

    @Override protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Glass.BG_DEEP);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Glass.pad(bar, this, 8, 10, 14, 6);
        TextView back = Glass.text(this, "< Back", 15, Glass.NEON_CYAN, true);
        back.setBackground(Glass.neonPill(this, Glass.NEON_CYAN));
        Glass.pad(back, this, 14, 7, 14, 7);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView heading = Glass.text(this, "Doc Scanner", 19, Glass.TEXT, true);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = (int) Glass.dp(this, 12);
        bar.addView(heading, hlp);
        root.addView(bar);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        bodyCol = new LinearLayout(this);
        bodyCol.setOrientation(LinearLayout.VERTICAL);
        Glass.pad(bodyCol, this, 14, 8, 14, 20);
        scroll.addView(bodyCol);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        buildMainUi();
    }

    // ------------------------------------------------------------ main UI

    private TextView pill(String label, int neon) {
        TextView b = Glass.text(this, label, 15, Glass.TEXT, true);
        b.setBackground(Glass.neonPill(this, neon));
        b.setGravity(Gravity.CENTER);
        Glass.pad(b, this, 16, 10, 16, 10);
        return b;
    }

    private void buildMainUi() {
        bodyCol.removeAllViews();
        // Mode selector
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        String[][] opts = {{"doc", "Document B&W"}, {"colour", "Colour"}, {"orig", "Original"}};
        for (String[] o : opts) {
            TextView t = pill(o[1], o[0].equals(mode) ? Glass.NEON_CYAN : Glass.NEON_VIOLET);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = lp.rightMargin = (int) Glass.dp(this, 3);
            t.setLayoutParams(lp);
            final String m = o[0];
            t.setOnClickListener(v -> { mode = m; buildMainUi(); });
            modes.addView(t);
        }
        bodyCol.addView(modes);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = (int) Glass.dp(this, 12);
        btns.setLayoutParams(blp);
        TextView cam = pill("Take photo", Glass.NEON_CYAN);
        cam.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cam.setOnClickListener(v -> takePhoto());
        TextView gal = pill("Add from gallery", Glass.NEON_VIOLET);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        glp.leftMargin = (int) Glass.dp(this, 8);
        gal.setLayoutParams(glp);
        gal.setOnClickListener(v -> pickFromGallery());
        btns.addView(cam); btns.addView(gal);
        bodyCol.addView(btns);

        if (pages.isEmpty()) {
            TextView n = Glass.text(this,
                    "No pages yet. Photograph a document or add photos of it, "
                            + "then drag the corners to straighten each page.",
                    13, Glass.TEXT_DIM, false);
            Glass.pad(n, this, 2, 14, 2, 4);
            bodyCol.addView(n);
        } else {
            TextView h = Glass.text(this, pages.size() + (pages.size() == 1 ? " page" : " pages")
                    + " — in order", 14, Glass.TEXT_DIM, false);
            Glass.pad(h, this, 2, 14, 2, 6);
            bodyCol.addView(h);
            for (int i = 0; i < pages.size(); i++) bodyCol.addView(pageRow(i));
            TextView pdf = pill("Save as one PDF", Glass.NEON_CYAN);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = (int) Glass.dp(this, 14);
            pdf.setLayoutParams(plp);
            pdf.setOnClickListener(v -> exportPdf());
            bodyCol.addView(pdf);
            if (lastPdf != null && lastPdf.exists()) {
                TextView share = pill("Share last PDF", Glass.NEON_VIOLET);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = (int) Glass.dp(this, 8);
                share.setLayoutParams(slp);
                share.setOnClickListener(v -> {
                    if (!ShareUtil.shareFile(this, lastPdf, "application/pdf", "Share scan PDF"))
                        toast("Could not share the PDF");
                });
                bodyCol.addView(share);
            }
        }
        TextView note = Glass.text(this,
                "Everything is processed on this device; no page is uploaded anywhere.",
                12.5f, Glass.TEXT_DIM, false);
        Glass.pad(note, this, 2, 14, 2, 4);
        bodyCol.addView(note);
    }

    private View pageRow(final int idx) {
        Bitmap bmp = pages.get(idx);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Glass.panel(this, 14));
        Glass.pad(row, this, 10, 8, 10, 8);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = (int) Glass.dp(this, 6);
        row.setLayoutParams(rlp);

        ImageView thumb = new ImageView(this);
        thumb.setImageBitmap(bmp);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(thumb, new LinearLayout.LayoutParams(
                (int) Glass.dp(this, 44), (int) Glass.dp(this, 56)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.leftMargin = (int) Glass.dp(this, 10);
        labels.addView(Glass.text(this, "Page " + (idx + 1), 15, Glass.TEXT, true));
        labels.addView(Glass.text(this, bmp.getWidth() + " x " + bmp.getHeight(),
                12.5f, Glass.TEXT_DIM, false));
        row.addView(labels, llp);

        row.addView(smallBtn("Up", v -> movePage(idx, -1)));
        row.addView(smallBtn("Down", v -> movePage(idx, 1)));
        row.addView(smallBtn("Rotate", v -> rotatePage(idx)));
        row.addView(smallBtn("Delete", v -> { pages.remove(idx); buildMainUi(); }));
        return row;
    }

    private TextView smallBtn(String label, View.OnClickListener onClick) {
        TextView t = Glass.text(this, label, 13f, Glass.NEON_CYAN, true);
        Glass.pad(t, this, 6, 8, 6, 8);
        t.setOnClickListener(onClick);
        return t;
    }

    private void movePage(int idx, int dir) {
        int j = idx + dir;
        if (j < 0 || j >= pages.size()) return;
        Bitmap b = pages.get(idx);
        pages.set(idx, pages.get(j));
        pages.set(j, b);
        buildMainUi();
    }

    private void rotatePage(int idx) {
        Bitmap b = pages.get(idx);
        Matrix m = new Matrix();
        m.postRotate(90);
        pages.set(idx, Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true));
        buildMainUi();
    }

    // ------------------------------------------------------------ capture / import

    private void takePhoto() {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "scan-" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (android.os.Build.VERSION.SDK_INT >= 29)
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniTools");
            pendingCaptureUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (pendingCaptureUri == null) { toast("Could not prepare a photo file"); return; }
            Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            it.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            it.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(it, REQ_CAPTURE);
        } catch (Exception e) {
            toast("No camera app available on this device");
        }
    }

    private void pickFromGallery() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.setType("image/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(it, REQ_GALLERY); }
        catch (Exception e) { toast("No gallery / files app available"); }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK) { pendingCaptureUri = null; return; }
        if (code == REQ_CAPTURE && pendingCaptureUri != null) {
            Bitmap b = decodeUri(pendingCaptureUri);
            // The raw shot stays in Pictures/OmniTools as a bonus copy.
            pendingCaptureUri = null;
            if (b != null) { pendingCrops.add(b); nextCrop(); }
            else toast("Could not read that photo");
        } else if (code == REQ_GALLERY && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            int added = 0;
            for (Uri u : uris) {
                Bitmap b = decodeUri(u);
                if (b != null) { pendingCrops.add(b); added++; }
            }
            if (added == 0) toast("Could not read those images");
            else nextCrop();
        }
    }

    /** Decode a content URI down to the pre-crop cap, EXIF-rotated. */
    private Bitmap decodeUri(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxDim / sample > DECODE_CAP) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) orientation = new ExifInterface(in)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL);
            } catch (Exception ignored) { }
            Matrix m = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
                default: break;
            }
            if (!m.isIdentity())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ crop + warp + enhance

    private void nextCrop() {
        final Bitmap raw = pendingCrops.poll();
        if (raw == null) { buildMainUi(); return; }
        bodyCol.removeAllViews();
        TextView h = Glass.text(this, "Drag the corners to the page edges, then tap Done.",
                14, Glass.TEXT, false);
        Glass.pad(h, this, 2, 6, 2, 8);
        bodyCol.addView(h);
        final CropView crop = new CropView(this);
        crop.setBitmap(raw);
        bodyCol.addView(crop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = (int) Glass.dp(this, 12);
        btns.setLayoutParams(blp);
        TextView done = pill("Done — straighten page", Glass.NEON_CYAN);
        done.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        done.setOnClickListener(v -> {
            Bitmap page = processPage(raw, crop.quad());
            if (page != null) pages.add(page);
            nextCrop();
        });
        TextView skip = pill("Skip page", Glass.NEON_VIOLET);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = (int) Glass.dp(this, 8);
        skip.setLayoutParams(slp);
        skip.setOnClickListener(v -> nextCrop());
        btns.addView(done); btns.addView(skip);
        bodyCol.addView(btns);
    }

    /** Perspective-warp the quad flat, cap the width, then enhance. */
    private Bitmap processPage(Bitmap raw, double[] quad) {
        try {
            int[] size = Perspective.outputSize(quad);
            Bitmap warped = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(warped);
            cv.drawColor(0xFFFFFFFF);
            float[] src = new float[8];
            for (int i = 0; i < 8; i++) src[i] = (float) quad[i];
            float[] dst = {0, 0, size[0], 0, size[0], size[1], 0, size[1]};
            Matrix m = new Matrix();
            if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return null;
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            cv.drawBitmap(raw, m, paint);

            Bitmap out = warped;
            if (out.getWidth() > MAX_W) {
                int nh = Math.round(out.getHeight() * (float) MAX_W / out.getWidth());
                out = Bitmap.createScaledBitmap(out, MAX_W, Math.max(1, nh), true);
            }
            if (!"orig".equals(mode)) {
                int w = out.getWidth(), h = out.getHeight();
                int[] px = new int[w * h];
                out.getPixels(px, 0, w, 0, 0, w, h);
                int[] lut = Enhance.buildLut(Enhance.luminanceHistogram(px), (long) w * h);
                if ("doc".equals(mode)) Enhance.applyDoc(px, lut);
                else Enhance.applyColour(px, lut);
                Enhance.sharpen(px, w, h);
                out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                out.setPixels(px, 0, w, 0, 0, w, h);
            }
            return out;
        } catch (Exception e) {
            toast("Could not process that page");
            return null;
        }
    }

    // ------------------------------------------------------------ PDF export

    private void exportPdf() {
        if (pages.isEmpty()) return;
        PdfDocument doc = new PdfDocument();
        try {
            for (Bitmap bmp : pages) {
                int[] ps = PdfPages.pageSizeFor(bmp.getWidth(), bmp.getHeight());
                PdfDocument.PageInfo info = new PdfDocument.PageInfo
                        .Builder(ps[0], ps[1], doc.getPages().size() + 1).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas c = page.getCanvas();
                c.drawColor(0xFFFFFFFF);
                int[] r = PdfPages.fitRect(bmp.getWidth(), bmp.getHeight(), ps[0], ps[1]);
                c.drawBitmap(bmp, null,
                        new android.graphics.Rect(r[0], r[1], r[0] + r[2], r[1] + r[3]),
                        new Paint(Paint.FILTER_BITMAP_FLAG));
                doc.finishPage(page);
            }
            File dir = new File(getExternalFilesDir(null), "scans");
            if (!dir.exists()) dir.mkdirs();
            lastPdf = new File(dir, "scan-" + System.currentTimeMillis() + ".pdf");
            try (OutputStream out = new FileOutputStream(lastPdf)) {
                doc.writeTo(out);
            }
            toast("PDF saved — " + pages.size() + (pages.size() == 1 ? " page" : " pages"));
            buildMainUi();
        } catch (Exception e) {
            toast("Could not build the PDF: " + e.getMessage());
        } finally {
            doc.close();
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
