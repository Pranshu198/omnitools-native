package com.jackbhai.omnitools.nativ.games;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Word Guess (Wordle-style): 5 letters, 6 guesses. Marks per letter:
 *  2 = right letter + place, 1 = right letter wrong place, 0 = absent,
 *  with standard duplicate handling (exact matches consume letters first).
 *  Win score = (7 - guesses used) * 100. */
public final class WordGuessLogic {
    public static final int LEN = 5, MAX = 6;
    private static final Set<String> VALID = new HashSet<>();
    static {
        for (String w : GameWords.FIVE_ANSWERS) VALID.add(w);
        for (String w : GameWords.FIVE_GUESSES) VALID.add(w);
    }
    private final String answer;
    private final List<String> guesses = new ArrayList<>();
    private final List<int[]> marks = new ArrayList<>();
    private boolean won;

    public WordGuessLogic(Random rnd) { this(GameWords.FIVE_ANSWERS[rnd.nextInt(GameWords.FIVE_ANSWERS.length)]); }
    public WordGuessLogic(String answer) { this.answer = answer.toLowerCase(); }

    public String answer() { return answer; }
    public int guessCount() { return guesses.size(); }
    public List<String> guesses() { return guesses; }
    public List<int[]> marks() { return marks; }
    public boolean isWon() { return won; }
    public boolean isOver() { return won || guesses.size() >= MAX; }
    public int score() { return won ? (MAX + 1 - guesses.size()) * 100 : 0; }

    public static boolean isValidWord(String w) { return w != null && VALID.contains(w.toLowerCase()); }

    /** Score a guess against an answer (pure, testable). */
    public static int[] evaluate(String guess, String answer) {
        int[] out = new int[LEN];
        int[] left = new int[26];
        for (int i = 0; i < LEN; i++) left[answer.charAt(i) - 'a']++;
        for (int i = 0; i < LEN; i++) {
            if (guess.charAt(i) == answer.charAt(i)) { out[i] = 2; left[guess.charAt(i) - 'a']--; }
        }
        for (int i = 0; i < LEN; i++) {
            if (out[i] == 2) continue;
            int c = guess.charAt(i) - 'a';
            if (left[c] > 0) { out[i] = 1; left[c]--; }
        }
        return out;
    }

    /** Submit a guess. @return false if invalid word or game over. */
    public boolean submit(String guess) {
        if (isOver() || guess == null) return false;
        guess = guess.toLowerCase();
        if (guess.length() != LEN || !isValidWord(guess)) return false;
        int[] m = evaluate(guess, answer);
        guesses.add(guess); marks.add(m);
        won = guess.equals(answer);
        return true;
    }

    /** Best-known state per letter for keyboard colouring: -1 unknown,
     *  else max mark seen (0/1/2). */
    public int letterState(char c) {
        int best = -1;
        for (int gi = 0; gi < guesses.size(); gi++) {
            String g = guesses.get(gi);
            for (int i = 0; i < LEN; i++)
                if (g.charAt(i) == c) best = Math.max(best, marks.get(gi)[i]);
        }
        return best;
    }
}
