package com.techno.gallery;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** A FrameLayout whose height always equals its width. */
public class SquareFrame extends FrameLayout {
    public SquareFrame(Context c) { super(c); }
    public SquareFrame(Context c, AttributeSet a) { super(c, a); }
    public SquareFrame(Context c, AttributeSet a, int d) { super(c, a, d); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, widthSpec);
        int w = getMeasuredWidth();
        setMeasuredDimension(w, w);
    }
}
