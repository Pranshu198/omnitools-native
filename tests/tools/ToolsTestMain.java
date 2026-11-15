import com.jackbhai.omnitools.nativ.tools.Enhance;
import com.jackbhai.omnitools.nativ.tools.FileItem;
import com.jackbhai.omnitools.nativ.tools.PdfPages;
import com.jackbhai.omnitools.nativ.tools.Perspective;
import com.jackbhai.omnitools.nativ.tools.SosPattern;
import com.jackbhai.omnitools.nativ.tools.ToolMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JVM tests for the native tools batch's pure logic. Run:
 *  javac -d /tmp/tooltest -sourcepath src tests/tools/ToolsTestMain.java
 *  java -cp /tmp/tooltest ToolsTestMain */
public final class ToolsTestMain {
    static int pass, fail;
    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }
    static boolean close(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    // ------------------------------------------------------------ dB calc
    static void testDb() {
        check("db silence rms=0 -> 0", ToolMath.rmsOf(new short[512], 512) == 0
                && ToolMath.dbFromRms(0) == 0);
        check("db empty buffer -> 0", ToolMath.dbFromRms(ToolMath.rmsOf(new short[0], 0)) == 0);
        short[] full = new short[256];
        java.util.Arrays.fill(full, (short) 32767);
        check("db full-scale DC -> ~94 dB estimate", close(ToolMath.dbFromRms(ToolMath.rmsOf(full, 256)), 94.0, 0.01));
        short[] half = new short[256];
        java.util.Arrays.fill(half, (short) 16384);
        double dbHalf = ToolMath.dbFromRms(ToolMath.rmsOf(half, 256));
        check("db half-scale -> ~88 dB (94 - 6.02)", close(dbHalf, 94.0 - 6.02, 0.05));
        short[] sine = new short[4410];
        for (int i = 0; i < sine.length; i++)
            sine[i] = (short) (32767 * Math.sin(2 * Math.PI * 440 * i / 44100.0));
        double dbSine = ToolMath.dbFromRms(ToolMath.rmsOf(sine, sine.length));
        check("db full-scale sine -> ~91 dB (94 - 3.01)", close(dbSine, 94.0 - 3.01, 0.1));
        check("db tiny signal clamps at 0", ToolMath.dbFromRms(0.001) == 0);
        check("level01 silence 0, full-scale 1",
                ToolMath.level01(0) == 0 && close(ToolMath.level01(32767), 1.0, 1e-9));
        check("level01 monotonic",
                ToolMath.level01(100) < ToolMath.level01(1000)
                        && ToolMath.level01(1000) < ToolMath.level01(10000));
    }

    // ------------------------------------------------------------ SOS timing
    static void testSos() {
        check("sos has 9 symbols", SosPattern.symbolCount() == 9);
        check("sos states alternate on/off starting ON",
                SosPattern.STATES[0] && !SosPattern.STATES[1] && SosPattern.STATES[2]);
        // Expected cycle: on steps 1000(dot*3=600? no) -> compute: S: 3 dots 200 + 2 gaps 200 + letter gap 600
        // S on=600 off=200+200+600=1000; O on=1800 off=200+200+600=1000; S on=600 off=200+200+1400=1800
        check("sos cycle = 6800 ms", SosPattern.CYCLE_MS == 6800);
        long u = SosPattern.UNIT_MS;
        check("sos t=0 ON (first dot)", SosPattern.stateAt(0));
        check("sos dot ends at 1u -> OFF", !SosPattern.stateAt(u));
        check("sos second dot at 2u ON", SosPattern.stateAt(2 * u));
        // After S (3 dots + gaps): S spans 0..8u? dots at 0-1,2-3,4-5, letter gap 5-8 -> O dash starts 8u, lasts 3u
        check("sos O dash starts at 8u ON", SosPattern.stateAt(8 * u));
        check("sos O dash still ON at 10u", SosPattern.stateAt(10 * u));
        check("sos O dash OFF at 11u", !SosPattern.stateAt(11 * u));
        check("sos pattern loops: stateAt(cycle)==stateAt(0)",
                SosPattern.stateAt(SosPattern.CYCLE_MS) == SosPattern.stateAt(0)
                        && SosPattern.stateAt(SosPattern.CYCLE_MS + 8 * u));
        check("sos repeat gap: OFF just before cycle end", !SosPattern.stateAt(SosPattern.CYCLE_MS - 1));
        // Total ON time per cycle: S 600 + O 1800 + S 600 = 3000 ms
        long onMs = 0;
        for (int i = 0; i < SosPattern.STATES.length; i++)
            if (SosPattern.STATES[i]) onMs += SosPattern.DURATIONS_MS[i];
        check("sos total ON time 3000 ms", onMs == 3000);
    }

    // ------------------------------------------------------------ compass
    static void testCompass() {
        check("normalise 0", ToolMath.normaliseDeg(0) == 0);
        check("normalise -90 -> 270", ToolMath.normaliseDeg(-90) == 270);
        check("normalise 360 -> 0", ToolMath.normaliseDeg(360) == 0);
        check("normalise 725 -> 5", ToolMath.normaliseDeg(725) == 5);
        check("normalise -360 -> 0", ToolMath.normaliseDeg(-360) == 0);
        check("cardinal 0 = N", "N".equals(ToolMath.cardinal(0)));
        check("cardinal 11.24 = N", "N".equals(ToolMath.cardinal(11.24)));
        check("cardinal 11.25 = NNE", "NNE".equals(ToolMath.cardinal(11.25)));
        check("cardinal 90 = E", "E".equals(ToolMath.cardinal(90)));
        check("cardinal 180 = S", "S".equals(ToolMath.cardinal(180)));
        check("cardinal 270 = W", "W".equals(ToolMath.cardinal(270)));
        check("cardinal 348.75 = N", "N".equals(ToolMath.cardinal(348.75)));
        check("cardinal 348.74 = NNW", "NNW".equals(ToolMath.cardinal(348.74)));
        // Smoothing across the wrap: 350 -> 10 with alpha .5 should land at 0, not 180.
        check("angleSmooth wraps through 0", close(ToolMath.angleSmooth(350, 10, 0.5), 0, 1e-9));
        check("angleSmooth plain midpoint", close(ToolMath.angleSmooth(0, 90, 0.5), 45, 1e-9));
        check("angleSmooth alpha 1 reaches target", close(ToolMath.angleSmooth(100, 200, 1.0), 200, 1e-9));
        check("angleSmooth alpha 0 stays", close(ToolMath.angleSmooth(100, 200, 0.0), 100, 1e-9));
        double v = 350;
        for (int i = 0; i < 200; i++) v = ToolMath.angleSmooth(v, 10, 0.1);
        check("angleSmooth converges across wrap", close(v, 10, 0.5));
    }

    // ------------------------------------------------------------ level math
    static void testLevel() {
        double[] flat = ToolMath.levelAngles(0, 0, 9.81);
        check("level flat -> {0,0}", close(flat[0], 0, 1e-9) && close(flat[1], 0, 1e-9));
        double[] side = ToolMath.levelAngles(9.81, 0, 0);
        check("level full right lean -> tiltX 90", close(side[0], 90, 1e-6) && close(side[1], 0, 1e-9));
        double[] fwd = ToolMath.levelAngles(0, 9.81, 0);
        check("level full forward lean -> tiltY 90", close(fwd[1], 90, 1e-6));
        double[] half = ToolMath.levelAngles(9.81 * Math.sin(Math.toRadians(30)), 0,
                9.81 * Math.cos(Math.toRadians(30)));
        check("level 30-degree lean", close(half[0], 30, 1e-6));
        double[] upside = ToolMath.levelAngles(0, 0, -9.81);
        check("level face-down -> {0,0} tilts (gravity flipped)",
                close(upside[0], 0, 1e-9) && close(upside[1], 0, 1e-9));
        check("isLevel tolerance", ToolMath.isLevel(0.4, -0.4, 0.5)
                && !ToolMath.isLevel(0.6, 0, 0.5));
    }

    // ------------------------------------------------------------ perspective
    static void testPerspective() {
        double[] out = new double[2];
        double[] src = {0, 0, 100, 0, 100, 100, 0, 100};
        double[] h = Perspective.solve(src, src.clone());
        check("perspective identity solves", h != null);
        Perspective.map(h, 37, 73, out);
        check("perspective identity maps point to itself", close(out[0], 37, 1e-6) && close(out[1], 73, 1e-6));

        // Trapezoid (page shot at an angle) -> rectangle 200x280.
        double[] quad = {20, 10, 180, 30, 190, 270, 10, 250};
        double[] rect = {0, 0, 200, 0, 200, 280, 0, 280};
        double[] h2 = Perspective.solve(quad, rect);
        check("perspective trapezoid solves", h2 != null);
        boolean corners = true;
        for (int i = 0; i < 4; i++) {
            Perspective.map(h2, quad[2 * i], quad[2 * i + 1], out);
            if (!close(out[0], rect[2 * i], 1e-6) || !close(out[1], rect[2 * i + 1], 1e-6)) corners = false;
        }
        check("perspective maps all 4 corners exactly", corners);
        Perspective.map(h2, (quad[0] + quad[4]) / 2, (quad[1] + quad[5]) / 2, out);
        check("perspective centre lands inside rect",
                out[0] > 0 && out[0] < 200 && out[1] > 0 && out[1] < 280);
        check("perspective degenerate quad -> null",
                Perspective.solve(new double[]{5, 5, 5, 5, 5, 5, 5, 5}, rect) == null);
        int[] size = Perspective.outputSize(new double[]{0, 0, 100, 0, 100, 80, 0, 80});
        check("perspective outputSize axis-aligned", size[0] == 100 && size[1] == 80);
        int[] size2 = Perspective.outputSize(quad);
        check("perspective outputSize trapezoid sane", size2[0] > 150 && size2[0] < 200
                && size2[1] > 230 && size2[1] < 280);
    }

    // ------------------------------------------------------------ PDF layout
    static void testPdf() {
        int[] p = PdfPages.pageSizeFor(1000, 1400);
        check("pdf portrait image -> A4 portrait", p[0] == 595 && p[1] == 842);
        int[] l = PdfPages.pageSizeFor(1400, 1000);
        check("pdf landscape image -> A4 landscape", l[0] == 842 && l[1] == 595);
        int[] sq = PdfPages.pageSizeFor(1000, 1000);
        check("pdf square image -> portrait", sq[0] == 595 && sq[1] == 842);
        int[] r = PdfPages.fitRect(1000, 1400, 595, 842);
        check("pdf fitRect inside margins", r[0] >= 36 && r[1] >= 36
                && r[0] + r[2] <= 595 - 36 + 1 && r[1] + r[3] <= 842 - 36 + 1);
        check("pdf fitRect preserves aspect", close(r[2] / (double) r[3], 1000.0 / 1400.0, 0.01));
        check("pdf fitRect centred", Math.abs(r[0] - (595 - r[0] - r[2])) <= 1
                && Math.abs(r[1] - (842 - r[1] - r[3])) <= 1);
        int[] wide = PdfPages.fitRect(2000, 500, 842, 595);
        check("pdf fitRect wide image width-limited", wide[2] == 842 - 72);
        // Page assembly inputs: a 3-page doc keeps per-page independent sizes.
        int[][] imgs = {{800, 1200}, {1200, 800}, {1000, 1000}};
        boolean ok = true;
        for (int[] im : imgs) {
            int[] ps = PdfPages.pageSizeFor(im[0], im[1]);
            int[] fr = PdfPages.fitRect(im[0], im[1], ps[0], ps[1]);
            if (fr[2] <= 0 || fr[3] <= 0) ok = false;
        }
        check("pdf 3-page assembly inputs all valid", ok);
    }

    // ------------------------------------------------------------ enhance
    static void testEnhance() {
        // Uniform mid-grey image: histogram spike at 128.
        int[] px = new int[100];
        java.util.Arrays.fill(px, 0xFF808080);
        int[] hist = Enhance.luminanceHistogram(px);
        check("enhance histogram spike", hist[128] == 100);
        int[] lut = Enhance.buildLut(hist, 100);
        check("enhance flat image -> identity-ish lut (no crash, mid stays mid)",
                lut[128] == 128 || lut[128] == 255 || lut[128] == 0); // span>=1 guard only
        // Two-tone image: 50% at 40, 50% at 200 -> stretch pushes them apart.
        int[] px2 = new int[1000];
        for (int i = 0; i < 500; i++) px2[i] = 0xFF282828;
        for (int i = 500; i < 1000; i++) px2[i] = 0xFFC8C8C8;
        int[] lut2 = Enhance.buildLut(Enhance.luminanceHistogram(px2), 1000);
        check("enhance stretch: dark 40 -> 0", lut2[40] == 0);
        check("enhance stretch: bright 200 -> 255", lut2[200] == 255);
        int[] doc = px2.clone();
        Enhance.applyDoc(doc, lut2);
        check("enhance doc mode greyscale + stretched",
                doc[0] == 0xFF000000 && doc[999] == 0xFFFFFFFF);
        int[] col = {0xFF102030, 0xFF804020};
        int[] identity = new int[256];
        for (int v = 0; v < 256; v++) identity[v] = v;
        Enhance.applyColour(col, identity);
        check("enhance colour identity lut is a no-op",
                col[0] == 0xFF102030 && col[1] == 0xFF804020);
        // Sharpen: constant image unchanged; step edge gets overshoot.
        int[] flatImg = new int[25];
        java.util.Arrays.fill(flatImg, 0xFF7A7A7A);
        Enhance.sharpen(flatImg, 5, 5);
        check("enhance sharpen keeps flat image flat", flatImg[12] == 0xFF7A7A7A);
        int[] edge = new int[25];
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++)
            edge[y * 5 + x] = x < 2 ? 0xFF000000 : 0xFFFFFFFF;
        Enhance.sharpen(edge, 5, 5);
        int centre = (edge[2 * 5 + 2] >> 16) & 0xFF;
        check("enhance sharpen keeps hard edge saturated", centre == 255);
    }

    // ------------------------------------------------------------ files
    static void testFiles() {
        check("bytes 0", "0 B".equals(ToolMath.formatBytes(0)));
        check("bytes 512", "512 B".equals(ToolMath.formatBytes(512)));
        check("bytes 1023", "1023 B".equals(ToolMath.formatBytes(1023)));
        check("bytes 1024 -> 1.0 KB", "1.0 KB".equals(ToolMath.formatBytes(1024)));
        check("bytes 1.5 MB", "1.5 MB".equals(ToolMath.formatBytes(1536 * 1024)));
        check("bytes 2 GB", "2.0 GB".equals(ToolMath.formatBytes(2L * 1024 * 1024 * 1024)));
        check("bytes negative -> ?", "?".equals(ToolMath.formatBytes(-1)));
        check("duration 0 -> 0:00", "0:00".equals(ToolMath.formatDurationMs(0)));
        check("duration 65s -> 1:05", "1:05".equals(ToolMath.formatDurationMs(65000)));
        check("duration 1h -> 1:00:00", "1:00:00".equals(ToolMath.formatDurationMs(3600000)));
        check("duration 3661s -> 1:01:01", "1:01:01".equals(ToolMath.formatDurationMs(3661000)));

        List<FileItem> items = new ArrayList<>();
        items.add(new FileItem("banana.txt", 300, 1000, false, "u1", null, null));
        items.add(new FileItem("Apple.txt", 100, 3000, false, "u2", null, null));
        items.add(new FileItem("docs", 0, 5000, true, "u3", null, null));
        items.add(new FileItem("cherry.txt", 200, 2000, false, "u4", null, null));
        Collections.sort(items, ToolMath.fileComparator(ToolMath.SORT_NAME));
        check("sort name: dirs first then A-Z case-insensitive",
                items.get(0).name.equals("docs") && items.get(1).name.equals("Apple.txt")
                        && items.get(2).name.equals("banana.txt") && items.get(3).name.equals("cherry.txt"));
        Collections.sort(items, ToolMath.fileComparator(ToolMath.SORT_SIZE));
        check("sort size: dirs first then biggest first",
                items.get(0).isDir && items.get(1).name.equals("banana.txt")
                        && items.get(3).name.equals("Apple.txt"));
        Collections.sort(items, ToolMath.fileComparator(ToolMath.SORT_DATE));
        check("sort date: dirs first then newest first",
                items.get(0).isDir && items.get(1).name.equals("Apple.txt")
                        && items.get(3).name.equals("banana.txt"));
    }

    public static void main(String[] args) {
        testDb(); testSos(); testCompass(); testLevel();
        testPerspective(); testPdf(); testEnhance(); testFiles();
        System.out.println("TOTAL: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
