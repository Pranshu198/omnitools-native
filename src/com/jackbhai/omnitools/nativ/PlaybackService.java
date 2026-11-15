package com.jackbhai.omnitools.nativ;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Background playback core for the Songs section: an ExoPlayer living in a
 * MediaSessionService, so music keeps playing with the screen off, with a
 * media notification and lockscreen / headset controls — the Spotify shape.
 * The UI drives it through a MediaController; nothing here preloads a track.
 */
public class PlaybackService extends MediaSessionService {

    public static final String CHANNEL_ID = "omnitools_playback";
    public static final int NOTIFICATION_ID = 2001;

    private MediaSession session;

    // So the Songs UI can attach the visualizer + waveform tooling to the
    // real player's audio session (MediaController does not expose it).
    private static volatile ExoPlayer sharedPlayer;

    public static ExoPlayer sharedPlayer() { return sharedPlayer; }

    @Override
    public void onCreate() {
        super.onCreate();
        ExoPlayer player = new ExoPlayer.Builder(this).build();
        sharedPlayer = player;
        session = new MediaSession.Builder(this, player).build();

        DefaultMediaNotificationProvider provider = new DefaultMediaNotificationProvider(
                this, sessionArg -> NOTIFICATION_ID, CHANNEL_ID, R.string.channel_name);
        provider.setSmallIcon(R.drawable.ic_note);
        setMediaNotificationProvider(provider);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        sharedPlayer = null;
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }
}
