import com.jackbhai.omnitools.nativ.audio.EqPresets;
import com.jackbhai.omnitools.nativ.audio.Lrc;
import com.jackbhai.omnitools.nativ.audio.mcu.hct.Hct;
import com.jackbhai.omnitools.nativ.audio.mcu.palettes.TonalPalette;
import com.jackbhai.omnitools.nativ.audio.mcu.scheme.SchemeTonalSpot;

/** Pure-JVM tests for Track C's testable cores: LRC parser, EQ preset
 *  shapes, and the vendored Material Color Utilities scheme maths. */
public class TestMain {

    private static int pass = 0, fail = 0;

    private static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    public static void main(String[] args) {
        lrcTests();
        eqTests();
        mcuTests();
        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    private static void lrcTests() {
        String fixture = String.join("\n",
                "[ti:Test Song]",
                "[ar:Tester]",
                "[offset:+500]",
                "[00:10.00][00:20.00]Repeated line",
                "[00:15.50]Middle line",
                "[01:00.25]Last line");
        Lrc.Doc doc = Lrc.parse(fixture);
        check("lrc synced", doc.synced);
        check("lrc tags", "Test Song".equals(doc.tags.get("ti"))
                && "Tester".equals(doc.tags.get("ar")));
        check("lrc offset parsed", doc.offsetMs == 500);
        check("lrc line count (multi-timestamp expands)", doc.lines.size() == 4);
        // Offset +500 is added to every raw timestamp, then sorted.
        check("lrc sorted + shifted", doc.lines.get(0).timeMs == 10500
                && doc.lines.get(1).timeMs == 16000
                && doc.lines.get(2).timeMs == 20500
                && doc.lines.get(3).timeMs == 60750);
        check("lrc multi-timestamp text", "Repeated line".equals(doc.lines.get(0).text)
                && "Repeated line".equals(doc.lines.get(2).text));
        check("lrc indexAt before first", doc.indexAt(0) == -1 && doc.indexAt(10499) == -1);
        check("lrc indexAt exact", doc.indexAt(10500) == 0 && doc.indexAt(16000) == 1);
        check("lrc indexAt between", doc.indexAt(15999) == 0 && doc.indexAt(30000) == 2);
        check("lrc indexAt after last", doc.indexAt(999999) == 3);

        Lrc.Doc frac = Lrc.parse("[00:01.5]half\n[00:02.123]millis\n[00:03:07]colon");
        check("lrc fraction forms", frac.lines.size() == 3
                && frac.lines.get(0).timeMs == 1500
                && frac.lines.get(1).timeMs == 2123
                && frac.lines.get(2).timeMs == 3070);

        Lrc.Doc plain = Lrc.parse("just some words\nsecond line\n");
        check("lrc unsynced fallback", !plain.synced && plain.plain.size() == 2
                && plain.lines.isEmpty());

        Lrc.Doc empty = Lrc.parse("");
        check("lrc empty", !empty.synced && empty.lines.isEmpty() && empty.plain.isEmpty());

        Lrc.Doc neg = Lrc.parse("[offset:-2000]\n[00:05.00]shifted");
        check("lrc negative offset clamps at 0", neg.lines.size() == 1
                && neg.lines.get(0).timeMs == 3000);
    }

    private static void eqTests() {
        float[] flat = EqPresets.gainsDb("Flat", 5);
        boolean allZero = flat.length == 5;
        for (float g : flat) allZero &= g == 0f;
        check("eq flat is flat", allZero);

        float[] bass = EqPresets.gainsDb("Bass Booster", 5);
        check("eq bass booster tilts low", bass[0] > bass[4] && bass[0] == 7f);

        float[] treble = EqPresets.gainsDb("Treble", 5);
        check("eq treble tilts high", treble[4] > treble[0] && treble[4] == 7f);

        float[] rock9 = EqPresets.gainsDb("Rock", 9);
        check("eq interpolation length + ends", rock9.length == 9
                && rock9[0] == 5f && rock9[8] == 5f);
        check("eq interpolation midpoint", Math.abs(rock9[4] - (-1f)) < 0.01f);

        float[] one = EqPresets.gainsDb("Vocal", 1);
        check("eq single band", one.length == 1 && one[0] == 4f);

        check("eq unknown name is flat", EqPresets.gainsDb("Nope", 5)[2] == 0f
                && !EqPresets.isKnown("Nope") && EqPresets.isKnown("Jazz"));
        check("eq zero bands", EqPresets.gainsDb("Rock", 0).length == 0);
    }

    private static void mcuTests() {
        int seed = 0xFF00E5FF; // the Phase-1 neon cyan
        SchemeTonalSpot scheme = new SchemeTonalSpot(Hct.fromInt(seed), true, 0.0);
        int primary = scheme.getPrimary();
        check("mcu primary is opaque argb", (primary >>> 24) == 0xFF);
        check("mcu scheme roles opaque", (scheme.getSecondary() >>> 24) == 0xFF
                && (scheme.getSurface() >>> 24) == 0xFF
                && (scheme.getPrimaryContainer() >>> 24) == 0xFF);
        check("mcu primary differs from seed (tonal spot remaps)",
                primary != seed);
        // Same seed, light scheme: primary must differ from dark primary.
        SchemeTonalSpot light = new SchemeTonalSpot(Hct.fromInt(seed), false, 0.0);
        check("mcu dark vs light primary differ", light.getPrimary() != primary);

        // Tonal palette: tone values track the requested tone (monotonic-ish).
        TonalPalette palette = TonalPalette.fromInt(seed);
        double t20 = Hct.fromInt(palette.tone(20)).getTone();
        double t50 = Hct.fromInt(palette.tone(50)).getTone();
        double t80 = Hct.fromInt(palette.tone(80)).getTone();
        check("mcu tonal palette monotonic", t20 < t50 && t50 < t80);
        check("mcu tonal palette accurate", Math.abs(t20 - 20) < 3
                && Math.abs(t50 - 50) < 3 && Math.abs(t80 - 80) < 3);
        check("mcu tone 0 is black, 100 is white",
                palette.tone(0) == 0xFF000000 && palette.tone(100) == 0xFFFFFFFF);

        // HCT round-trip keeps hue roughly stable.
        Hct hct = Hct.fromInt(0xFFFF0000);
        check("mcu hct red hue", Math.abs(hct.getHue()) < 30 || hct.getHue() > 330);
        check("mcu hct argb roundtrip", (hct.toInt() >>> 24) == 0xFF);
    }
}
