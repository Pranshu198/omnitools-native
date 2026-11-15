package com.jackbhai.omnitools.nativ.games;

/** Pong physics in a unit field (x,y in 0..1, y down). Player paddle on
 *  the left, AI on the right. First to WIN_SCORE wins. Pure logic; the
 *  view feeds dt seconds and paddle targets. */
public final class PongLogic {
    public static final int WIN_SCORE = 7;
    public static final float PADDLE_H = 0.18f, PADDLE_W = 0.02f;
    public static final float AI_SPEED = 0.55f; // fraction of field per second
    public float ballX = 0.5f, ballY = 0.5f, ballVX, ballVY;
    public float playerY = 0.5f, aiY = 0.5f; // paddle centres
    public int playerScore, aiScore;
    private float serveDir = 1;

    public PongLogic() { serve(); }

    public void serve() {
        ballX = 0.5f; ballY = 0.5f;
        ballVX = 0.45f * serveDir;
        ballVY = (float) (Math.random() * 0.4 - 0.2);
    }

    private static float clampPaddle(float y) {
        return Math.max(PADDLE_H / 2, Math.min(1 - PADDLE_H / 2, y));
    }
    public void setPlayerY(float y) { playerY = clampPaddle(y); }

    /** Advance one step. AI tracks the ball with a dead zone and capped
     *  speed, so it is beatable. */
    public void step(float dt) {
        // AI movement
        float target = ballVX > 0 ? ballY : 0.5f;
        float dy = target - aiY;
        float maxMove = AI_SPEED * dt;
        if (Math.abs(dy) > 0.01f) aiY = clampPaddle(aiY + Math.signum(dy) * Math.min(maxMove, Math.abs(dy)));

        ballX += ballVX * dt;
        ballY += ballVY * dt;
        if (ballY < 0.01f) { ballY = 0.01f; ballVY = Math.abs(ballVY); }
        if (ballY > 0.99f) { ballY = 0.99f; ballVY = -Math.abs(ballVY); }
        // player paddle (left, x ~ PADDLE_W)
        if (ballVX < 0 && ballX <= PADDLE_W + 0.01f && ballX >= 0
                && Math.abs(ballY - playerY) <= PADDLE_H / 2 + 0.01f) {
            ballX = PADDLE_W + 0.01f;
            bounce(playerY);
        }
        // AI paddle (right)
        if (ballVX > 0 && ballX >= 1 - PADDLE_W - 0.01f && ballX <= 1
                && Math.abs(ballY - aiY) <= PADDLE_H / 2 + 0.01f) {
            ballX = 1 - PADDLE_W - 0.01f;
            bounce(aiY);
        }
        if (ballX < 0) { aiScore++; serveDir = -1; serve(); }
        else if (ballX > 1) { playerScore++; serveDir = 1; serve(); }
    }

    private void bounce(float paddleCentre) {
        float rel = (ballY - paddleCentre) / (PADDLE_H / 2); // -1..1
        float speed = Math.min(0.95f, Math.abs(ballVX) * 1.06f + 0.02f);
        ballVX = (ballVX < 0 ? 1 : -1) * speed;
        ballVY = rel * 0.55f;
    }

    public boolean isOver() { return playerScore >= WIN_SCORE || aiScore >= WIN_SCORE; }
    public boolean playerWon() { return playerScore >= WIN_SCORE; }
    public void reset() { playerScore = 0; aiScore = 0; playerY = aiY = 0.5f; serveDir = 1; serve(); }
}
