package com.jackbhai.omnitools.nativ;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

/**
 * Full-screen native video playback for the TV section. Landscape, system
 * bars hidden, PlayerView's own controller. If the source offers only
 * split video+audio streams (the usual case from the front-end pool), they
 * are combined with a MergingMediaSource so sound is never missing.
 */
public class WatchActivity extends Activity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_VIDEO = "video";
    public static final String EXTRA_AUDIO = "audio";

    private ExoPlayer player;
    private PlayerView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null) setTitle(title);
        String video = getIntent().getStringExtra(EXTRA_VIDEO);
        String audio = getIntent().getStringExtra(EXTRA_AUDIO);

        view = new PlayerView(this);
        view.setUseController(true);
        setContentView(view);

        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        if (video != null && !video.isEmpty()) {
            DefaultDataSource.Factory ds = new DefaultDataSource.Factory(this);
            ProgressiveMediaSource.Factory f = new ProgressiveMediaSource.Factory(ds);
            MediaSource vSrc = f.createMediaSource(MediaItem.fromUri(video));
            if (audio != null && !audio.isEmpty()) {
                MediaSource aSrc = f.createMediaSource(MediaItem.fromUri(audio));
                player.setMediaSource(new MergingMediaSource(vSrc, aSrc));
            } else {
                player.setMediaSource(vSrc);
            }
            player.prepare();
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
