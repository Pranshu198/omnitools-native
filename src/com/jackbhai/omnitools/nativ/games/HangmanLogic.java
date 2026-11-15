package com.jackbhai.omnitools.nativ.games;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Hangman: guess letters of a hidden word. 6 wrong guesses lose.
 *  Score for a win = 10 per letter + 15 per life left. */
public final class HangmanLogic {
    public static final int MAX_WRONG = 6;
    private final String word;
    private final Set<Character> guessed = new HashSet<>();
    private int wrong;

    public HangmanLogic(Random rnd) { this(GameWords.HANGMAN[rnd.nextInt(GameWords.HANGMAN.length)]); }
    public HangmanLogic(String word) { this.word = word.toLowerCase(); }

    public String word() { return word; }
    public int wrong() { return wrong; }
    public int livesLeft() { return MAX_WRONG - wrong; }
    public boolean isGuessed(char c) { return guessed.contains(c); }

    /** @return true if the letter is in the word. Ignores repeats. */
    public boolean guess(char c) {
        c = Character.toLowerCase(c);
        if (c < 'a' || c > 'z' || guessed.contains(c) || isOver()) return word.indexOf(c) >= 0;
        guessed.add(c);
        boolean hit = word.indexOf(c) >= 0;
        if (!hit) wrong++;
        return hit;
    }

    public boolean isWon() {
        for (int i = 0; i < word.length(); i++) if (!guessed.contains(word.charAt(i))) return false;
        return true;
    }
    public boolean isOver() { return isWon() || wrong >= MAX_WRONG; }
    public int score() { return isWon() ? word.length() * 10 + livesLeft() * 15 : 0; }

    /** Display form: revealed letters and underscores, space separated. */
    public String masked() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) sb.append(' ');
            char c = word.charAt(i);
            sb.append(guessed.contains(c) ? c : '_');
        }
        return sb.toString();
    }
}
