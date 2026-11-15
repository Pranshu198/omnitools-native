import com.jackbhai.omnitools.nativ.update.Versions;

/** Pure-JVM tests for the update checker's version compare. */
public class UpdateTestMain {

    private static int pass = 0, fail = 0;

    private static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    public static void main(String[] args) {
        // Equal versions are never "newer".
        check("equal plain", !Versions.isNewer("3.1", "3.1"));
        check("equal suffixed", !Versions.isNewer("3.1-native.1", "3.1-native.1"));
        check("equal tag v-prefix", !Versions.isNewer("v3.1-native.1", "3.1-native.1"));
        check("compare equal == 0", Versions.compare("3.2-native.1", "3.2-native.1") == 0);

        // Newer minor / major / patch-segment.
        check("newer minor", Versions.isNewer("3.2", "3.1"));
        check("newer minor suffixed", Versions.isNewer("3.2-native.1", "3.1-native.1"));
        check("newer major", Versions.isNewer("4.0-native.1", "3.9-native.9"));
        check("newer build segment", Versions.isNewer("3.1-native.2", "3.1-native.1"));
        check("newer extra segment", Versions.isNewer("3.1.1", "3.1"));

        // Older is never newer.
        check("older minor", !Versions.isNewer("3.1-native.1", "3.2-native.1"));
        check("older major", !Versions.isNewer("2.9-native.9", "3.0-native.1"));
        check("older build segment", !Versions.isNewer("3.1-native.1", "3.1-native.2"));

        // Suffix semantics: same number, release beats pre-release suffix.
        check("release beats suffix", Versions.isNewer("3.2", "3.2-native.1"));
        check("suffix below release", !Versions.isNewer("3.2-native.1", "3.2"));
        check("suffix order alpha<beta", Versions.isNewer("3.2-beta", "3.2-alpha"));

        // Robustness.
        check("null latest", !Versions.isNewer(null, "3.1"));
        check("null current", !Versions.isNewer("3.1", null));
        check("empty latest", !Versions.isNewer("", "3.1"));
        check("two-digit segment", Versions.isNewer("3.10-native.1", "3.9-native.1"));

        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
