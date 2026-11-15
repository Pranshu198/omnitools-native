package com.jackbhai.omnitools.nativ.tools;

/**
 * Perspective (homography) maths for the Document Scanner — pure, no
 * Android imports. Given the four corners of a photographed page
 * (order: top-left, top-right, bottom-right, bottom-left) it solves the
 * 3x3 homography that maps them onto an output rectangle, exactly the
 * transform the scanner applies with Matrix.setPolyToPoly on device.
 */
public final class Perspective {

    private Perspective() {}

    /**
     * Homography coefficients {a,b,c,d,e,f,g,h} (i = 1) mapping source
     * points to destination points:
     *   X = (a*x + b*y + c) / (g*x + h*y + 1)
     *   Y = (d*x + e*y + f) / (g*x + h*y + 1)
     * src and dst are 4 points each as {x0,y0, x1,y1, x2,y2, x3,y3}.
     * Returns null if the system is singular (degenerate quad).
     */
    public static double[] solve(double[] src, double[] dst) {
        // Build the 8x8 linear system for the 4 point pairs.
        double[][] a = new double[8][8];
        double[] b = new double[8];
        for (int i = 0; i < 4; i++) {
            double x = src[2 * i], y = src[2 * i + 1];
            double X = dst[2 * i], Y = dst[2 * i + 1];
            int r = 2 * i;
            a[r][0] = x; a[r][1] = y; a[r][2] = 1;
            a[r][6] = -x * X; a[r][7] = -y * X;
            b[r] = X;
            a[r + 1][3] = x; a[r + 1][4] = y; a[r + 1][5] = 1;
            a[r + 1][6] = -x * Y; a[r + 1][7] = -y * Y;
            b[r + 1] = Y;
        }
        // Gaussian elimination with partial pivoting.
        for (int col = 0, row = 0; col < 8 && row < 8; col++, row++) {
            int piv = row;
            for (int r = row + 1; r < 8; r++)
                if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r;
            if (Math.abs(a[piv][col]) < 1e-12) return null;
            double[] tmp = a[row]; a[row] = a[piv]; a[piv] = tmp;
            double tb = b[row]; b[row] = b[piv]; b[piv] = tb;
            for (int r = 0; r < 8; r++) {
                if (r == row) continue;
                double f = a[r][col] / a[row][col];
                if (f == 0) continue;
                for (int c = col; c < 8; c++) a[r][c] -= f * a[row][c];
                b[r] -= f * b[row];
            }
        }
        double[] h = new double[8];
        for (int i = 0; i < 8; i++) h[i] = b[i] / a[i][i];
        return h;
    }

    /** Map one source point through homography {@code h} into out[0..1]. */
    public static void map(double[] h, double x, double y, double[] out) {
        double den = h[6] * x + h[7] * y + 1.0;
        out[0] = (h[0] * x + h[1] * y + h[2]) / den;
        out[1] = (h[3] * x + h[4] * y + h[5]) / den;
    }

    /**
     * Sensible output size for a warped page: average of the two
     * horizontal edge lengths for the width, average of the two
     * vertical edge lengths for the height (the standard document
     * scanner estimate). quad is {TL, TR, BR, BL} as x,y pairs.
     * Returns {width, height}, each at least 1.
     */
    public static int[] outputSize(double[] quad) {
        double top = dist(quad[0], quad[1], quad[2], quad[3]);
        double bottom = dist(quad[6], quad[7], quad[4], quad[5]);
        double left = dist(quad[0], quad[1], quad[6], quad[7]);
        double right = dist(quad[2], quad[3], quad[4], quad[5]);
        int w = (int) Math.round((top + bottom) / 2.0);
        int h = (int) Math.round((left + right) / 2.0);
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
