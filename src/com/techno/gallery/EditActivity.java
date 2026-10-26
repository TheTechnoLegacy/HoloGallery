package com.techno.gallery;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

/** Simple picture editor: brightness, contrast, saturation, filters, rotate, flip. Saves a copy. */
public class EditActivity extends Activity {
    private ImageView preview;
    private Bitmap src;          // downscaled working copy for the preview
    private Media media;
    private int rotation;        // degrees, multiples of 90
    private boolean flipped;
    private int filter;          // 0 none, 1 b&w, 2 sepia, 3 invert
    private SeekBar brightness, contrast, saturation;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.edit);
        preview = (ImageView) findViewById(R.id.preview);
        brightness = (SeekBar) findViewById(R.id.brightness);
        contrast = (SeekBar) findViewById(R.id.contrast);
        saturation = (SeekBar) findViewById(R.id.saturation);
        final Uri uri = getIntent().getData();
        if (uri == null) { finish(); return; }

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { applyColor(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        brightness.setOnSeekBarChangeListener(l);
        contrast.setOnSeekBarChangeListener(l);
        saturation.setOnSeekBarChangeListener(l);

        setFilterButton(R.id.f_none, 0);
        setFilterButton(R.id.f_bw, 1);
        setFilterButton(R.id.f_sepia, 2);
        setFilterButton(R.id.f_invert, 3);
        findViewById(R.id.rot_left).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rotation = (rotation + 270) % 360; showTransformed(); }
        });
        findViewById(R.id.rot_right).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rotation = (rotation + 90) % 360; showTransformed(); }
        });
        findViewById(R.id.flip).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { flipped = !flipped; showTransformed(); }
        });
        findViewById(R.id.discard).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setResult(RESULT_CANCELED); finish(); }
        });
        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });

        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int maxDim = Math.min(1600, Math.max(dm.widthPixels, dm.heightPixels));
        new AsyncTask<Void, Void, Bitmap>() {
            @Override protected Bitmap doInBackground(Void... p) {
                media = Repo.fromUri(EditActivity.this, uri);
                return Bmp.decode(getApplicationContext(), media, maxDim);
            }
            @Override protected void onPostExecute(Bitmap bmp) {
                if (bmp == null) {
                    Toast.makeText(EditActivity.this, R.string.load_failed, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                src = bmp;
                showTransformed();
            }
        }.execute();
    }

    private void setFilterButton(int id, final int f) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { filter = f; applyColor(); }
        });
    }

    private Bitmap transform(Bitmap in) {
        if (rotation == 0 && !flipped) return in;
        Matrix mx = new Matrix();
        if (flipped) mx.postScale(-1, 1);
        mx.postRotate(rotation);
        return Bitmap.createBitmap(in, 0, 0, in.getWidth(), in.getHeight(), mx, true);
    }

    private void showTransformed() {
        if (src == null) return;
        preview.setImageBitmap(transform(src));
        applyColor();
    }

    private ColorMatrix colorMatrix() {
        float bright = (brightness.getProgress() - 100) * 2.55f;      // -255..255
        float c = 1f + (contrast.getProgress() - 100) / 100f;         // 0..2
        float sat = saturation.getProgress() / 100f;                  // 0..2
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(sat);
        float t = 128f * (1f - c) + bright;
        m.postConcat(new ColorMatrix(new float[] {
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0 }));
        if (filter == 1) {
            ColorMatrix g = new ColorMatrix();
            g.setSaturation(0f);
            m.postConcat(g);
        } else if (filter == 2) {
            m.postConcat(new ColorMatrix(new float[] {
                    0.393f, 0.769f, 0.189f, 0, 0,
                    0.349f, 0.686f, 0.168f, 0, 0,
                    0.272f, 0.534f, 0.131f, 0, 0,
                    0, 0, 0, 1, 0 }));
        } else if (filter == 3) {
            m.postConcat(new ColorMatrix(new float[] {
                    -1, 0, 0, 0, 255,
                    0, -1, 0, 0, 255,
                    0, 0, -1, 0, 255,
                    0, 0, 0, 1, 0 }));
        }
        return m;
    }

    private void applyColor() {
        if (preview == null || brightness == null || saturation == null || contrast == null) return;
        preview.setColorFilter(new ColorMatrixColorFilter(colorMatrix()));
    }

    @SuppressWarnings("deprecation")
    private void save() {
        if (src == null || media == null) return;
        final ColorMatrix cm = colorMatrix();
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.savingImage));
        pd.setCancelable(false);
        pd.show();
        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... p) {
                try {
                    Bitmap full = Bmp.decode(getApplicationContext(), media, 4096);
                    if (full == null) return null;
                    Bitmap t = transform(full);
                    Bitmap out = Bitmap.createBitmap(t.getWidth(), t.getHeight(), Bitmap.Config.ARGB_8888);
                    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                    paint.setColorFilter(new ColorMatrixColorFilter(cm));
                    new Canvas(out).drawBitmap(t, 0, 0, paint);
                    return MediaStore.Images.Media.insertImage(getContentResolver(), out, "Edited", null);
                } catch (Exception e) {
                    return null;
                } catch (OutOfMemoryError e) {
                    return null;
                }
            }
            @Override protected void onPostExecute(String saved) {
                try { pd.dismiss(); } catch (Exception ignored) {}
                if (saved == null) {
                    Toast.makeText(EditActivity.this, R.string.save_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                Thumbs.clear();
                Intent r = new Intent();
                r.setData(Uri.parse(saved));
                setResult(RESULT_OK, r);
                finish();
            }
        }.execute();
    }
}
