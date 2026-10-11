package com.techno.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Shows a bitmap with a draggable / resizable crop rectangle. */
public class CropView extends View {
    private static final int NONE = -1, MOVE = 4; // 0..3 = corners TL, TR, BL, BR

    private Bitmap bitmap;
    private final RectF dst = new RectF();   // where the bitmap is drawn
    private final RectF crop = new RectF();  // crop rectangle in view coordinates
    private float aspect;                    // width / height, 0 = free
    private float scale = 1f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int mode = NONE;
    private float lastX, lastY;

    public CropView(Context c) { this(c, null); }

    public CropView(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
    }

    public void setBitmap(Bitmap b) {
        bitmap = b;
        layoutBitmap();
        resetCrop();
        invalidate();
    }

    public void setAspect(float ratio) {
        aspect = ratio;
        resetCrop();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutBitmap();
        resetCrop();
    }

    private void layoutBitmap() {
        if (bitmap == null || getWidth() == 0) return;
        float pad = 12 * density;
        float aw = getWidth() - 2 * pad, ah = getHeight() - 2 * pad;
        scale = Math.min(aw / bitmap.getWidth(), ah / bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        dst.set((getWidth() - w) / 2f, (getHeight() - h) / 2f,
                (getWidth() + w) / 2f, (getHeight() + h) / 2f);
    }

    private void resetCrop() {
        if (bitmap == null || dst.isEmpty()) return;
        float w = dst.width() * 0.8f, h = dst.height() * 0.8f;
        if (aspect > 0) {
            if (w / h > aspect) w = h * aspect; else h = w / aspect;
        }
        crop.set(dst.centerX() - w / 2, dst.centerY() - h / 2, dst.centerX() + w / 2, dst.centerY() + h / 2);
    }

    /** The crop rectangle in bitmap pixel coordinates. */
    public RectF cropInBitmap() {
        return new RectF((crop.left - dst.left) / scale, (crop.top - dst.top) / scale,
                (crop.right - dst.left) / scale, (crop.bottom - dst.top) / scale);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xff000000);
        if (bitmap == null) return;
        c.drawBitmap(bitmap, null, dst, null);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000000);
        c.drawRect(dst.left, dst.top, dst.right, crop.top, paint);
        c.drawRect(dst.left, crop.bottom, dst.right, dst.bottom, paint);
        c.drawRect(dst.left, crop.top, crop.left, crop.bottom, paint);
        c.drawRect(crop.right, crop.top, dst.right, crop.bottom, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density * 1.5f);
        paint.setColor(0xffffffff);
        c.drawRect(crop, paint);
        paint.setStrokeWidth(density);
        paint.setColor(0x66ffffff);
        float tw = crop.width() / 3f, th = crop.height() / 3f;
        for (int i = 1; i < 3; i++) {
            c.drawLine(crop.left + tw * i, crop.top, crop.left + tw * i, crop.bottom, paint);
            c.drawLine(crop.left, crop.top + th * i, crop.right, crop.top + th * i, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff33b5e5);
        float r = 7 * density;
        c.drawCircle(crop.left, crop.top, r, paint);
        c.drawCircle(crop.right, crop.top, r, paint);
        c.drawCircle(crop.left, crop.bottom, r, paint);
        c.drawCircle(crop.right, crop.bottom, r, paint);
    }

    private int hit(float x, float y) {
        float slop = 28 * density;
        if (near(x, y, crop.left, crop.top, slop)) return 0;
        if (near(x, y, crop.right, crop.top, slop)) return 1;
        if (near(x, y, crop.left, crop.bottom, slop)) return 2;
        if (near(x, y, crop.right, crop.bottom, slop)) return 3;
        if (crop.contains(x, y)) return MOVE;
        return NONE;
    }

    private static boolean near(float x, float y, float cx, float cy, float r) {
        return Math.abs(x - cx) <= r && Math.abs(y - cy) <= r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mode = hit(e.getX(), e.getY());
                lastX = e.getX();
                lastY = e.getY();
                return mode != NONE;
            case MotionEvent.ACTION_MOVE:
                if (mode == MOVE) {
                    float dx = e.getX() - lastX, dy = e.getY() - lastY;
                    dx = Math.max(dst.left - crop.left, Math.min(dst.right - crop.right, dx));
                    dy = Math.max(dst.top - crop.top, Math.min(dst.bottom - crop.bottom, dy));
                    crop.offset(dx, dy);
                } else if (mode >= 0) {
                    resize(mode, e.getX(), e.getY());
                }
                lastX = e.getX();
                lastY = e.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = NONE;
                return true;
            default:
                return true;
        }
    }

    private void resize(int corner, float x, float y) {
        boolean left = corner == 0 || corner == 2;
        boolean top = corner == 0 || corner == 1;
        float ax = left ? crop.right : crop.left;   // fixed (opposite) corner
        float ay = top ? crop.bottom : crop.top;
        x = Math.max(dst.left, Math.min(dst.right, x));
        y = Math.max(dst.top, Math.min(dst.bottom, y));
        float min = 40 * density;
        float w = Math.max(min, Math.abs(x - ax));
        float h = Math.max(min, Math.abs(y - ay));
        // keep dragging direction relative to the anchor
        boolean growLeft = x < ax;
        boolean growUp = y < ay;
        if (aspect > 0) {
            if (w / h > aspect) w = h * aspect; else h = w / aspect;
            float maxW = growLeft ? ax - dst.left : dst.right - ax;
            float maxH = growUp ? ay - dst.top : dst.bottom - ay;
            if (w > maxW) { w = maxW; h = w / aspect; }
            if (h > maxH) { h = maxH; w = h * aspect; }
        } else {
            float maxW = growLeft ? ax - dst.left : dst.right - ax;
            float maxH = growUp ? ay - dst.top : dst.bottom - ay;
            w = Math.min(w, maxW);
            h = Math.min(h, maxH);
        }
        crop.set(growLeft ? ax - w : ax, growUp ? ay - h : ay,
                 growLeft ? ax : ax + w, growUp ? ay : ay + h);
    }
}
