package com.techno.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Asynchronous, cached square thumbnails. */
final class Thumbs {
    private Thumbs() {}

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(Math.max(4 * 1024 * 1024,
                    (int) (Runtime.getRuntime().maxMemory() / 8))) {
                @Override protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    static void clear() {
        CACHE.evictAll();
    }

    static void load(Context context, final Media m, final int size, final ImageView view) {
        final Context app = context.getApplicationContext();
        final String key = m.uri + "@" + size;
        view.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(new Runnable() {
            @Override public void run() {
                if (!key.equals(view.getTag())) return; // view was recycled for another item
                Bitmap b = Bmp.decode(app, m, size);
                if (b == null) return;
                final Bitmap sq = Bmp.square(b, size);
                CACHE.put(key, sq);
                UI.post(new Runnable() {
                    @Override public void run() {
                        if (key.equals(view.getTag())) view.setImageBitmap(sq);
                    }
                });
            }
        });
    }
}
