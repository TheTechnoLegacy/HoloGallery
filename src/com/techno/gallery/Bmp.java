package com.techno.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;

import java.io.InputStream;

/** Bitmap decoding helpers. */
final class Bmp {
    private Bmp() {}

    /**
     * Decodes a picture (or a video frame) so that its longer side is at least maxDim
     * (never upscaled), with EXIF rotation applied. Returns null on failure.
     */
    static Bitmap decode(Context ctx, Media m, int maxDim) {
        try {
            if (m.video) return videoFrame(ctx, m, maxDim);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = ctx.getContentResolver().openInputStream(m.uri);
            if (in == null) return null;
            try { BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;

            int sample = 1;
            int w = o.outWidth, h = o.outHeight;
            while (w / 2 >= maxDim && h / 2 >= maxDim) {
                sample *= 2;
                w /= 2;
                h /= 2;
            }
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            in = ctx.getContentResolver().openInputStream(m.uri);
            if (in == null) return null;
            Bitmap b;
            try { b = BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
            if (b == null) return null;
            int deg = degrees(m);
            if (deg != 0) {
                Matrix mx = new Matrix();
                mx.postRotate(deg);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), mx, true);
                if (r != b) b.recycle();
                b = r;
            }
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static int degrees(Media m) {
        int deg = m.orientation;
        if (deg == 0 && m.path != null && !m.video) {
            try {
                int o = new ExifInterface(m.path).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                switch (o) {
                    case ExifInterface.ORIENTATION_ROTATE_90: deg = 90; break;
                    case ExifInterface.ORIENTATION_ROTATE_180: deg = 180; break;
                    case ExifInterface.ORIENTATION_ROTATE_270: deg = 270; break;
                    default: break;
                }
            } catch (Exception ignored) {}
        }
        return deg;
    }

    private static Bitmap videoFrame(Context ctx, Media m, int maxDim) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, m.uri);
            Bitmap b = r.getFrameAtTime();
            if (b == null) return null;
            int big = Math.max(b.getWidth(), b.getHeight());
            if (big > maxDim) {
                float s = maxDim / (float) big;
                Bitmap sc = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)),
                        Math.max(1, Math.round(b.getHeight() * s)), true);
                if (sc != b) b.recycle();
                b = sc;
            }
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    /** Center-crops to a square and scales to size x size. */
    static Bitmap square(Bitmap src, int size) {
        int s = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - s) / 2;
        int y = (src.getHeight() - s) / 2;
        Matrix mx = new Matrix();
        float sc = size / (float) s;
        mx.postScale(sc, sc);
        Bitmap out = Bitmap.createBitmap(src, x, y, s, s, mx, true);
        if (out != src) src.recycle();
        return out;
    }
}
