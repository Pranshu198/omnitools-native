import com.jackbhai.omnitools.nativ.games.Logic2048;
import com.jackbhai.omnitools.nativ.games.MinesLogic;
import com.jackbhai.omnitools.nativ.games.SlideLogic;
import com.jackbhai.omnitools.nativ.games.TttLogic;

import java.util.Arrays;
import java.util.Random;

/** JVM tests for the pure game logic. Run:
 *  javac -d /tmp/gametest -sourcepath src tests/games/GamesTestMain.java
 *  java -cp /tmp/gametest GamesTestMain */
public final class GamesTestMain {
    static int pass, fail;
    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }
    static void checkRow(String name, int[] got, int[] want) {
        check(name + " got=" + Arrays.toString(got) + " want=" + Arrays.toString(want),
                Arrays.equals(got, want));
    }

    static void test2048() {
        int[] g = {0};
        checkRow("2048 [2,2,2,2] -> [4,4,0,0]", Logic2048.mergeLine(new int[]{2,2,2,2}, g), new int[]{4,4,0,0});
        check("2048 [2,2,2,2] gained 8", g[0] == 8);
        g[0] = 0;
        checkRow("2048 [2,2,4] no double merge -> [4,4,0,0]", Logic2048.mergeLine(new int[]{2,2,4,0}, g), new int[]{4,4,0,0});
        g[0] = 0;
        checkRow("2048 [4,4,8] -> [8,8,0,0]", Logic2048.mergeLine(new int[]{4,4,8,0}, g), new int[]{8,8,0,0});
        check("2048 [4,4,8] gained 8 (not 16)", g[0] == 8);
        checkRow("2048 [2,0,2,4] -> [4,4,0,0]", Logic2048.mergeLine(new int[]{2,0,2,4}, null), new int[]{4,4,0,0});
        checkRow("2048 [8,4,2,0] unchanged", Logic2048.mergeLine(new int[]{8,4,2,0}, null), new int[]{8,4,2,0});

        Logic2048 game = new Logic2048(new Random(42));
        int nonzero = 0;
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) if (game.get(r, c) != 0) nonzero++;
        check("2048 fresh board spawns exactly 2 tiles", nonzero == 2);
        boolean valsOk = true;
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) {
            int v = game.get(r, c); if (v != 0 && v != 2 && v != 4) valsOk = false;
        }
        check("2048 spawn values are 2 or 4", valsOk);
        check("2048 spawn on full board returns false", !fullBoard().spawn());

        Logic2048 locked = fullBoard();
        check("2048 full locked board is game over", locked.isGameOver());
        Logic2048 mergeable = fullBoard();
        mergeable.set(0, 1, 2); // row 0 becomes 2,2,8,16 -> merge exists
        check("2048 full board with a merge is NOT game over", !mergeable.isGameOver());

        Logic2048 m = new Logic2048(new Random(7));
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) m.set(r, c, 0);
        m.set(0, 0, 2); m.set(0, 1, 2);
        boolean moved = m.move(Logic2048.LEFT);
        check("2048 move left merges and scores 4", moved && m.get(0, 0) == 4 && m.score() == 4);
        int count = 0;
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) if (m.get(r, c) != 0) count++;
        check("2048 valid move spawns exactly one new tile", count == 2);
        boolean moved2 = m.move(Logic2048.LEFT); // 4 at left edge, spawn elsewhere may allow move; use blocked case below
        Logic2048 stuck = new Logic2048(new Random(1));
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) stuck.set(r, c, 0);
        stuck.set(0, 0, 2);
        check("2048 invalid move returns false and spawns nothing", !stuck.move(Logic2048.LEFT) && stuck.get(0, 1) == 0);
    }
    static Logic2048 fullBoard() {
        Logic2048 g = new Logic2048(new Random(3));
        int[] vals = {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2, 4, 8, 16, 32, 64};
        int i = 0;
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) g.set(r, c, vals[i++]);
        return g;
    }

    static void testMines() {
        for (int trial = 0; trial < 20; trial++) {
            MinesLogic m = new MinesLogic(9, 9, 10, new Random(trial));
            m.reveal(4, 4);
            int count = 0;
            for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) if (m.isMine(r, c)) count++;
            if (count != 10) { check("mines exact count trial " + trial, false); return; }
        }
        check("mines board has exactly 10 mines (20 trials)", true);

        boolean safe = true;
        for (int trial = 0; trial < 50; trial++) {
            MinesLogic m = new MinesLogic(9, 9, 10, new Random(1000 + trial));
            m.reveal(0, 0);
            for (int dr = 0; dr <= 1; dr++) for (int dc = 0; dc <= 1; dc++)
                if (m.isMine(dr, dc)) safe = false;
            if (m.isLost()) safe = false;
        }
        check("mines first click + neighbours mine-free, never loses (50 trials)", safe);

        MinesLogic corner = new MinesLogic(9, 9, 10, new Random(9));
        corner.placeMines(0, 0);
        // force an empty corner region: count flood from a zero cell if one exists near corner
        int revealed = corner.reveal(0, 0);
        check("mines flood reveal from empty corner reveals >1 cell", revealed > 1);
        check("mines reveal does not reveal mines", !anyMineRevealed(corner));
    }
    static boolean anyMineRevealed(MinesLogic m) {
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++)
            if (m.isMine(r, c) && m.isRevealed(r, c)) return true;
        return false;
    }

    static long tttGames, tttAiLosses;
    static void testTtt() {
        // Exhaustive: player moves first in every cell; explore the whole tree.
        for (int first = 0; first < 9; first++) {
            char[] b = new char[9]; Arrays.fill(b, TttLogic.EMPTY);
            b[first] = TttLogic.X;
            exploreTtt(b, TttLogic.O, TttLogic.X);
        }
        // Also exhaustive with AI moving first (player is O).
        char[] empty = new char[9]; Arrays.fill(empty, TttLogic.EMPTY);
        exploreTtt(empty, TttLogic.X, TttLogic.O);
        check("ttt minimax AI never loses across full game tree (" + tttGames + " terminal lines)", tttAiLosses == 0);
        check("ttt exhaustive tree actually explored many lines", tttGames > 100);
    }
    static void exploreTtt(char[] b, char ai, char human) {
        char w = TttLogic.winner(b);
        if (w != TttLogic.EMPTY || TttLogic.isFull(b)) {
            tttGames++;
            if (w == human) tttAiLosses++;
            return;
        }
        // Whose turn? Count pieces.
        int aiCount = 0, huCount = 0;
        for (char c : b) { if (c == ai) aiCount++; if (c == human) huCount++; }
        boolean aiTurn = aiCount <= huCount && !(aiCount == 0 && huCount == 0 && ai == TttLogic.O);
        // Turn is determined by total moves parity relative to who started:
        // starter is X. If X==ai then ai moves when counts equal.
        int total = aiCount + huCount;
        char starter = TttLogic.X;
        aiTurn = (total % 2 == 0) == (ai == starter);
        if (aiTurn) {
            int mv = TttLogic.bestMove(b, ai, human);
            if (mv < 0) { tttGames++; return; }
            b[mv] = ai; exploreTtt(b, ai, human); b[mv] = TttLogic.EMPTY;
        } else {
            for (int i = 0; i < 9; i++) {
                if (b[i] != TttLogic.EMPTY) continue;
                b[i] = human; exploreTtt(b, ai, human); b[i] = TttLogic.EMPTY;
            }
        }
    }

    static void testSlide() {
        Random rnd = new Random(123);
        boolean allSolvable = true;
        for (int i = 0; i < 500; i++) {
            SlideLogic s = new SlideLogic(4);
            s.shuffle(rnd, 40 + rnd.nextInt(120));
            if (!SlideLogic.isSolvable(s.tiles(), 4)) allSolvable = false;
        }
        check("slide 500 random shuffles all solvable (4x4)", allSolvable);
        boolean all3 = true;
        for (int i = 0; i < 200; i++) {
            SlideLogic s = new SlideLogic(3);
            s.shuffle(rnd, 30 + rnd.nextInt(60));
            if (!SlideLogic.isSolvable(s.tiles(), 3)) all3 = false;
        }
        check("slide 200 random shuffles all solvable (3x3)", all3);
        check("slide solved board detected", new SlideLogic(4).isSolved());
        int[] unsolvable = {2, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        check("slide single-swap board is unsolvable (parity)", !SlideLogic.isSolvable(unsolvable, 4));
        SlideLogic s = new SlideLogic(4);
        check("slide adjacent tile slides, non-adjacent does not",
                s.slide(14) && !s.slide(0) && s.moves() == 1);
        check("slide back returns to solved", s.slide(15) && s.isSolved());
    }

    public static void main(String[] args) {
        test2048(); testMines(); testTtt(); testSlide();
        System.out.println("TOTAL: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
