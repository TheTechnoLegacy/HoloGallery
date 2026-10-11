package com.techno.gallery;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * Crops a picture. Extras (compatible with the classic Gallery crop intent):
 * aspectX/aspectY, outputX/outputY, return-data, output (Uri), set-as-wallpaper.
 */
public class CropActivity extends Activity {
    private CropView cropView;
    private Bitmap source;
    private Uri uri;
    private boolean wallpaper;
    private int outX, outY;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.crop);
        cropView = (CropView) findViewById(R.id.crop);
        Intent in = getIntent();
        uri = in.getData();
        if (uri == null) { finish(); return; }
        wallpaper = in.getBooleanExtra("set-as-wallpaper", false);
        outX = in.getIntExtra("outputX", 0);
        outY = in.getIntExtra("outputY", 0);

        float aspect = 0;
        int ax = in.getIntExtra("aspectX", 0), ay = in.getIntExtra("aspectY", 0);
        if (ax > 0 && ay > 0) aspect = ax / (float) ay;
        else if (outX > 0 && outY > 0) aspect = outX / (float) outY;
        if (wallpaper) {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            int w = wm.getDesiredMinimumWidth(), h = wm.getDesiredMinimumHeight();
            if (w <= 0 || h <= 0) {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                w = dm.widthPixels;
                h = dm.heightPixels;
            }
            outX = w;
            outY = h;
            aspect = w / (float) h;
        }
        final float finalAspect = aspect;

        findViewById(R.id.discard).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });

        new AsyncTask<Void, Void, Bitmap>() {
            @Override protected Bitmap doInBackground(Void... p) {
                Media m = Repo.fromUri(CropActivity.this, uri);
                return Bmp.decode(getApplicationContext(), m, 3072);
            }

            @Override protected void onPostExecute(Bitmap bmp) {
                if (bmp == null) {
                    Toast.makeText(CropActivity.this, R.string.load_failed, Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                    return;
                }
                source = bmp;
                cropView.setBitmap(bmp);
                cropView.setAspect(finalAspect);
            }
        }.execute();
    }

    @SuppressWarnings("deprecation")
    private void save() {
        if (source == null) return;
        final RectF r = cropView.cropInBitmap();
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(wallpaper ? R.string.wallpaper : R.string.savingImage));
        pd.setCancelable(false);
        pd.show();
        new AsyncTask<Void, Void, Intent>() {
            @Override protected Intent doInBackground(Void... p) {
                try {
                    int x = Math.max(0, Math.round(r.left));
                    int y = Math.max(0, Math.round(r.top));
                    int w = Math.min(source.getWidth() - x, Math.round(r.width()));
                    int h = Math.min(source.getHeight() - y, Math.round(r.height()));
                    if (w <= 0 || h <= 0) return null;
                    Bitmap out = Bitmap.createBitmap(source, x, y, w, h);
                    if (outX > 0 && outY > 0 && (out.getWidth() != outX || out.getHeight() != outY)) {
                        out = Bitmap.createScaledBitmap(out, outX, outY, true);
                    }
                    return deliver(out);
                } catch (Exception e) {
                    return null;
                } catch (OutOfMemoryError e) {
                    return null;
                }
            }

            @Override protected void onPostExecute(Intent result) {
                try { pd.dismiss(); } catch (Exception ignored) {}
                if (result == null) {
                    Toast.makeText(CropActivity.this, R.string.save_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                if (wallpaper) {
                    Toast.makeText(CropActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                }
                setResult(RESULT_OK, result);
                finish();
            }
        }.execute();
    }

    private Intent deliver(Bitmap out) throws Exception {
        Intent in = getIntent();
        if (wallpaper) {
            WallpaperManager.getInstance(this).setBitmap(out);
            return new Intent();
        }
        Parcelable_ outUri = new Parcelable_(in);
        if (outUri.uri != null) {
            OutputStream os = getContentResolver().openOutputStream(outUri.uri);
            if (os == null) return null;
            try { out.compress(Bitmap.CompressFormat.JPEG, 90, os); } finally { os.close(); }
            Intent r = new Intent();
            r.setData(outUri.uri);
            return r;
        }
        if (in.getBooleanExtra("return-data", false)) {
            Bitmap small = out;
            int big = Math.max(out.getWidth(), out.getHeight());
            if (big > 400) {
                float s = 400f / big;
                small = Bitmap.createScaledBitmap(out, Math.round(out.getWidth() * s),
                        Math.round(out.getHeight() * s), true);
            }
            Intent r = new Intent();
            r.putExtra("data", small);
            return r;
        }
        String saved = MediaStore.Images.Media.insertImage(getContentResolver(), out, "Cropped", null);
        if (saved == null) return null;
        Intent r = new Intent();
        r.setData(Uri.parse(saved));
        return r;
    }

    /** Small helper to read the optional EXTRA_OUTPUT uri without unchecked casts. */
    private static final class Parcelable_ {
        Uri uri;
        Parcelable_(Intent in) {
            Object o = in.getExtras() != null ? in.getExtras().get(MediaStore.EXTRA_OUTPUT) : null;
            if (o instanceof Uri) uri = (Uri) o;
        }
    }
}
