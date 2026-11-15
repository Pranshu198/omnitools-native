package com.jackbhai.omnitools.nativ.tools;

/**
 * The SOS blink pattern (... --- ...) as a pure timing table, so the
 * Flashlight tool just plays it back and the tests can verify it.
 * Standard Morse timing: dot = 1 unit, dash = 3 units, gap between
 * symbols = 1 unit, gap between letters = 3 units, and a 7-unit pause
 * before the pattern repeats. One unit = {@link #UNIT_MS}.
 */
public final class SosPattern {

    public static final long UNIT_MS = 200;

    /** On/off steps of one full cycle, in order. */
    public static final boolean[] STATES;
    /** Duration of each step in {@link #STATES}, milliseconds. */
    public static final long[] DURATIONS_MS;
    public static final long CYCLE_MS;

    static {
        // S = . . .   O = - - -   S = . . .
        boolean[] symOn = {
                true, true, true,          // S dots (gaps inserted below)
                true, true, true,          // O dashes
                true, true, true};         // S dots
        long[] symLen = {
                UNIT_MS, UNIT_MS, UNIT_MS,
                3 * UNIT_MS, 3 * UNIT_MS, 3 * UNIT_MS,
                UNIT_MS, UNIT_MS, UNIT_MS};
        // Steps: symbol, gap, symbol, gap ... letter gap after index 2 and 5,
        // repeat gap after the last symbol.
        java.util.ArrayList<Boolean> st = new java.util.ArrayList<>();
        java.util.ArrayList<Long> du = new java.util.ArrayList<>();
        for (int i = 0; i < symOn.length; i++) {
            st.add(Boolean.TRUE); du.add(symLen[i]);
            long gap;
            if (i == symOn.length - 1) gap = 7 * UNIT_MS;        // before repeat
            else if (i == 2 || i == 5) gap = 3 * UNIT_MS;        // between letters
            else gap = UNIT_MS;                                  // between symbols
            st.add(Boolean.FALSE); du.add(gap);
        }
        STATES = new boolean[st.size()];
        DURATIONS_MS = new long[du.size()];
        long total = 0;
        for (int i = 0; i < st.size(); i++) {
            STATES[i] = st.get(i);
            DURATIONS_MS[i] = du.get(i);
            total += DURATIONS_MS[i];
        }
        CYCLE_MS = total;
    }

    private SosPattern() {}

    /** Is the light ON at {@code elapsedMs} after the pattern started?
     *  Loops forever over the cycle. */
    public static boolean stateAt(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        long t = elapsedMs % CYCLE_MS;
        for (int i = 0; i < STATES.length; i++) {
            if (t < DURATIONS_MS[i]) return STATES[i];
            t -= DURATIONS_MS[i];
        }
        return false;
    }

    /** Number of ON steps (symbols) in one cycle — 9 for SOS. */
    public static int symbolCount() {
        int n = 0;
        for (boolean s : STATES) if (s) n++;
        return n;
    }
}
