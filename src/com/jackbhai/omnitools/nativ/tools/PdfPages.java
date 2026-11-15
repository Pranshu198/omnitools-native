package com.jackbhai.omnitools.nativ.tools;

/**
 * Pure page-layout maths for PDF export (Document Scanner). Pages are
 * A4 at 72 dpi (595 x 842 pt), orientation follows the image, and the
 * image is fitted inside a fixed margin, centred, aspect preserved.
 * The Android side (PdfDocument) consumes exactly these numbers.
 */
public final class PdfPages {

    public static final int A4_W = 595, A4_H = 842;
    public static final int MARGIN_PT = 36;

    private PdfPages() {}

    /** {pageW, pageH}: A4 portrait, or A4 landscape for wide images. */
    public static int[] pageSizeFor(int imgW, int imgH) {
        if (imgW > imgH) return new int[]{A4_H, A4_W};
        return new int[]{A4_W, A4_H};
    }

    /**
     * {left, top, width, height} of the image drawn on a page of
     * {@code pageW} x {@code pageH} points: fitted within the margin
     * box and centred.
     */
    public static int[] fitRect(int imgW, int imgH, int pageW, int pageH) {
        int availW = pageW - 2 * MARGIN_PT;
        int availH = pageH - 2 * MARGIN_PT;
        double scale = Math.min(availW / (double) imgW, availH / (double) imgH);
        int w = Math.max(1, (int) Math.round(imgW * scale));
        int h = Math.max(1, (int) Math.round(imgH * scale));
        int left = (pageW - w) / 2;
        int top = (pageH - h) / 2;
        return new int[]{left, top, w, h};
    }
}
