package com.jackbhai.omnitools.nativ;

import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.io.File;
import java.io.FileOutputStream;

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

    // Widget contract (Track B consumes exactly this): SharedPreferences
    // "omni_nowplaying" {title, artist, art_file, playing, has_track} plus
    // a package-scoped NOW_PLAYING_CHANGED broadcast on every change.
    public static final String NOW_PLAYING_PREFS = "omni_nowplaying";
    public static final String ACTION_NOW_PLAYING_CHANGED =
            "com.jackbhai.omnitools.nativ.NOW_PLAYING_CHANGED";
    public static final String ART_FILE_NAME = "nowplaying_art.jpg";

    private void publishNowPlaying(ExoPlayer player) {
        try {
            MediaItem item = player.getCurrentMediaItem();
            MediaMetadata md = player.getMediaMetadata();
            String title = "", artist = "";
            String artFile = "";
            File art = new File(getFilesDir(), ART_FILE_NAME);
            if (item != null && md != null) {
                if (md.title != null) title = md.title.toString();
                if (md.artist != null) artist = md.artist.toString();
                byte[] data = md.artworkData;
                if (data != null && data.length > 0) {
                    try (FileOutputStream fos = new FileOutputStream(art)) {
                        fos.write(data);
                    }
                    artFile = art.getAbsolutePath();
                } else if (art.exists()) {
                    // No embedded artwork on this track: remove the stale file.
                    //noinspection ResultOfMethodCallIgnored
                    art.delete();
                }
            } else if (art.exists()) {
                //noinspection ResultOfMethodCallIgnored
                art.delete();
            }
            SharedPreferences sp = getSharedPreferences(NOW_PLAYING_PREFS, MODE_PRIVATE);
            sp.edit()
                    .putString("title", title)
                    .putString("artist", artist)
                    .putString("art_file", artFile)
                    .putBoolean("playing", player.isPlaying())
                    .putBoolean("has_track", item != null)
                    .apply();
            Intent broadcast = new Intent(ACTION_NOW_PLAYING_CHANGED);
            broadcast.setPackage(getPackageName());
            sendBroadcast(broadcast);
        } catch (Exception ignored) {
            // The widget feed is best-effort; playback must never break for it.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ExoPlayer player = new ExoPlayer.Builder(this).build();
        sharedPlayer = player;
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                publishNowPlaying(player);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                publishNowPlaying(player);
            }

            @Override
            public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
                publishNowPlaying(player);
            }
        });
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
