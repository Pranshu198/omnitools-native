package com.jackbhai.omnitools.nativ.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.jackbhai.omnitools.nativ.Glass;

import java.util.Random;

/** Block Fall: falling tetrominoes. Drag to move, tap to rotate,
 *  swipe down for a hard drop. Clear lines to level up. */
public final class BlockFallView extends BaseGameView {
    private static final int[] COLORS = {0, Color.rgb(0, 229, 255), Color.rgb(255, 176, 66),
            Color.rgb(120, 160, 255), Color.rgb(255, 235, 120), Color.rgb(92, 220, 140),
            Color.rgb(179, 136, 255), Color.rgb(255, 92, 92)};
    private BlockFallLogic g = new BlockFallLogic(new Random());
    private final Swipe swipe = new Swipe();
    private long acc;
    private float downX;

    public BlockFallView(Context c) { super(c); }

    @Override protected void onGameResume() { startLoop(); }

    private float boardX0() { return dp(56); }
    private float boardY0() { return dp(104); }
    private float cell() { return (getHeight() - dp(130)) / BlockFallLogic.H; }

    @Override protected void update(long dt) {
        if (g.over) return;
        acc += dt;
        if (acc >= g.dropMs()) { acc = 0; g.stepDown(); }
    }

    @Override protected void drawGame(Canvas cv) {
        float w = getWidth(), pad = dp(16);
        leftText(cv, "Block Fall    Score: " + g.score + "  •  Lines: " + g.lines + "  •  Lv " + g.level,
                pad, dp(34), 15, Glass.TEXT, true);
        leftText(cv, "Tap: [New Game]", pad, dp(58), 12, Glass.NEON_CYAN, false);
        String st = g.over ? "Game over — tap New Game" : "Drag = move • Tap = rotate • Swipe down = drop";
        if (g.over) saveBest("blockfall_best", g.score);
        leftText(cv, st, pad, dp(84), 14, Glass.TEXT_DIM, false);
        float cell = cell(), x0 = boardX0(), y0 = boardY0();
        glassCell(cv, x0 - dp(4), y0 - dp(4), x0 + cell * BlockFallLogic.W + dp(4), y0 + cell * BlockFallLogic.H + dp(4), dp(10));
        for (int r = 0; r < BlockFallLogic.H; r++) for (int c = 0; c < BlockFallLogic.W; c++) {
            int v = g.at(r, c);
            if (v == 0) continue;
            roundRect(cv, x0 + c * cell + dp(1), y0 + r * cell + dp(1),
                    x0 + (c + 1) * cell - dp(1), y0 + (r + 1) * cell - dp(1), dp(3), COLORS[v]);
        }
        if (!g.over) for (int[] cellPos : BlockFallLogic.cellsOf(g.piece, g.state)) {
            int cx = g.px + cellPos[0], cy = g.py + cellPos[1];
            if (cy < 0) continue;
            roundRect(cv, x0 + cx * cell + dp(1), y0 + cy * cell + dp(1),
                    x0 + (cx + 1) * cell - dp(1), y0 + (cy + 1) * cell - dp(1), dp(3), COLORS[g.piece + 1]);
        }
        // next piece preview
        float nx = x0 + cell * BlockFallLogic.W + dp(14), ny = y0 + dp(4);
        leftText(cv, "Next", nx, ny + dp(8), 11, Glass.TEXT_DIM, false);
        for (int[] cellPos : BlockFallLogic.cellsOf(g.nextPiece, 0)) {
            float pc = dp(11);
            roundRect(cv, nx + cellPos[0] * pc, ny + dp(14) + cellPos[1] * pc,
                    nx + (cellPos[0] + 1) * pc - dp(1), ny + dp(14) + (cellPos[1] + 1) * pc - dp(1), dp(2), COLORS[g.nextPiece + 1]);
        }
        if (best("blockfall_best") > 0)
            leftText(cv, "Best: " + best("blockfall_best"), pad, getHeight() - dp(10), 12, Glass.TEXT_DIM, false);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                swipe.down(e.getX(), e.getY()); downX = e.getX();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float cell = cell();
                int steps = (int) ((e.getX() - downX) / cell);
                while (steps > 0) { g.move(1); downX += cell; steps--; }
                while (steps < 0) { g.move(-1); downX -= cell; steps++; }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (e.getY() >= dp(44) && e.getY() < dp(70) && swipe.dir(e.getX(), e.getY(), dp(24)) == -1) {
                    g = new BlockFallLogic(new Random()); acc = 0; return true;
                }
                int d = swipe.dir(e.getX(), e.getY(), dp(24));
                if (d == 3) { g.hardDrop(); acc = 0; }
                else if (d == -1) g.rotate();
                return true;
            }
            default: return true;
        }
    }
}
