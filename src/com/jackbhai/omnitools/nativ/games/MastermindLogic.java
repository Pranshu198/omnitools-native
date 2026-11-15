package com.jackbhai.omnitools.nativ.games;

import java.util.Random;

/** Pure-Java Mastermind: 4 pegs, 6 colours, duplicates allowed.
 *  Score = exact (right colour+place) and colour-only counts. */
public final class MastermindLogic {
    public static final int PEGS = 4, COLORS = 6, MAX_GUESSES = 10;
    private final int[] secret = new int[PEGS];
    private int guesses;

    public MastermindLogic(Random rnd) {
        for (int i = 0; i < PEGS; i++) secret[i] = rnd.nextInt(COLORS);
    }
    /** Test constructor. */
    public MastermindLogic(int[] code) { System.arraycopy(code, 0, secret, 0, PEGS); }

    public int[] secret() { return secret.clone(); }
    public int guesses() { return guesses; }

    /** @return {exact, colourOnly} */
    public int[] score(int[] guess) {
        int exact = 0;
        int[] sLeft = new int[COLORS], gLeft = new int[COLORS];
        for (int i = 0; i < PEGS; i++) {
            if (guess[i] == secret[i]) exact++;
            else { sLeft[secret[i]]++; gLeft[guess[i]]++; }
        }
        int colour = 0;
        for (int c = 0; c < COLORS; c++) colour += Math.min(sLeft[c], gLeft[c]);
        guesses++;
        return new int[]{exact, colour};
    }
    public boolean isWin(int[] scoreResult) { return scoreResult[0] == PEGS; }
    public boolean isOver(boolean won) { return won || guesses >= MAX_GUESSES; }
}
