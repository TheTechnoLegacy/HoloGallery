package com.techno.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Image view with pinch zoom, pan, double-tap zoom and swipe callbacks. */
public class ZoomImageView extends ImageView {

    public interface Listener {
        void onTap();
        /** dir = +1 for "next" (swipe left), -1 for "previous". */
        void onSwipe(int dir);
        void onUserTouch();
    }

    private final Matrix matrix = new Matrix();
    private Bitmap bitmap;
    private float fitScale = 1f;
    private float maxScale = 4f;
    private Listener listener;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestures;

    public ZoomImageView(Context c) { super(c); init(c); }
    public ZoomImageView(Context c, AttributeSet a) { super(c, a); init(c); }

    private void init(Context c) {
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoomBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                if (bitmap == null) return true;
                if (currentScale() > fitScale * 1.05f) {
                    fit();
                } else {
                    zoomBy(Math.min(maxScale, fitScale * 2.5f) / currentScale(), e.getX(), e.getY());
                }
                return true;
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (bitmap == null || scaleDetector.isInProgress()) return false;
                if (currentScale() > fitScale * 1.02f) {
                    matrix.postTranslate(-dx, -dy);
                    clamp();
                    setImageMatrix(matrix);
                }
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (bitmap == null || e1 == null) return false;
                if (currentScale() <= fitScale * 1.02f
                        && Math.abs(vx) > 700 && Math.abs(vx) > Math.abs(vy) * 1.5f) {
                    if (listener != null) listener.onSwipe(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
    }

    public void setListener(Listener l) { listener = l; }

    public boolean isZoomed() { return currentScale() > fitScale * 1.02f; }

    public void setBitmap(Bitmap b) {
        bitmap = b;
        setImageBitmap(b);
        fit();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    private float currentScale() {
        float[] v = new float[9];
        matrix.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private void fit() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        fitScale = Math.min(Math.min(getWidth() / bw, getHeight() / bh), 3f);
        maxScale = Math.max(fitScale * 4f, 1f);
        matrix.reset();
        matrix.postScale(fitScale, fitScale);
        matrix.postTranslate((getWidth() - bw * fitScale) / 2f, (getHeight() - bh * fitScale) / 2f);
        setImageMatrix(matrix);
    }

    private void zoomBy(float factor, float px, float py) {
        float target = currentScale() * factor;
        if (target > maxScale) factor = maxScale / currentScale();
        if (target < fitScale) factor = fitScale / currentScale();
        matrix.postScale(factor, factor, px, py);
        clamp();
        setImageMatrix(matrix);
    }

    private void clamp() {
        if (bitmap == null) return;
        RectF r = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        matrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null) listener.onUserTouch();
        scaleDetector.onTouchEvent(e);
        gestures.onTouchEvent(e);
        return true;
    }
}
