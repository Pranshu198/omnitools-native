package com.jackbhai.omnitools.nativ;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny async thumbnail loader with an LRU memory cache (no image library). */
public final class Img {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override protected int sizeOf(String k, Bitmap b) {
                    return b.getByteCount();
                }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Img() {}

    public static void load(final String url, final ImageView into) {
        if (url == null || url.isEmpty()) {
            into.setImageBitmap(null);
            into.setTag(null);
            return;
        }
        into.setTag(url);
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            into.setImageBitmap(hit);
            return;
        }
        into.setImageBitmap(null);
        POOL.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", Net.UA);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in, null, o);
                    if (b != null) {
                        CACHE.put(url, b);
                        UI.post(() -> {
                            if (url.equals(into.getTag())) into.setImageBitmap(b);
                        });
                    }
                }
            } catch (Exception ignored) { /* art is decorative; absence is fine */ }
        });
    }
}
