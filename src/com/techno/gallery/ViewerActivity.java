package com.techno.gallery;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActionBar;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Full-screen picture viewer: swipe, zoom, slideshow, rotate, crop, share, delete. */
public class ViewerActivity extends BaseActivity implements ZoomImageView.Listener {
    static final String EXTRA_INDEX = "techno.index";
    private static final int REQ_CROP = 3;
    private static final int REQ_EDIT = 4;

    /** Handed over by the grid (kept static to avoid parceling thousands of items). */
    static List<Media> items;

    private ZoomImageView image;
    private TextView playButton;
    private ProgressBar progress;
    private int index;
    private AsyncTask<Void, Void, Bitmap> loadTask;
    private boolean barVisible = true;

    // slideshow
    private final Handler handler = new Handler();
    private boolean slideshow;
    private List<Integer> order;
    private int orderPos;
    private final Runnable slideStep = new Runnable() {
        @Override public void run() { advanceSlideshow(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.viewer);
        image = (ZoomImageView) findViewById(R.id.image);
        playButton = (TextView) findViewById(R.id.play);
        progress = (ProgressBar) findViewById(R.id.progress);
        image.setListener(this);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playVideo(); }
        });
        ActionBar bar = getActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);

        Uri data = getIntent().getData();
        if (data != null) {
            openSingle(data);
        } else if (items == null || items.isEmpty()) {
            finish();
        } else {
            index = Math.max(0, Math.min(items.size() - 1, getIntent().getIntExtra(EXTRA_INDEX, 0)));
            if (b != null) index = Math.min(items.size() - 1, b.getInt("index", index));
            show(index, 0);
        }
    }

    @Override
    void onStorageReady() {}

    /** VIEW intent from another app: show that picture, plus its album when readable. */
    private void openSingle(final Uri uri) {
        final Media first = Repo.fromUri(this, uri);
        items = new ArrayList<Media>();
        items.add(first);
        index = 0;
        show(0, 0);
        if (first.bucketId != null && first.bucketId.length() > 0 && hasStorage()) {
            new AsyncTask<Void, Void, List<Media>>() {
                @Override protected List<Media> doInBackground(Void... p) {
                    return Repo.load(ViewerActivity.this, Repo.KIND_IMAGES, first.bucketId,
                            Prefs.ascending(ViewerActivity.this));
                }
                @Override protected void onPostExecute(List<Media> list) {
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).id == first.id) {
                            items = list;
                            index = i;
                            updateTitle();
                            return;
                        }
                    }
                }
            }.execute();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("index", index);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSlideshow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadTask != null) loadTask.cancel(true);
    }

    private Media current() {
        return items.get(index);
    }

    private void updateTitle() {
        Media m = current();
        setTitle((m.name != null ? m.name : "") );
        ActionBar bar = getActionBar();
        if (bar != null && items.size() > 1) bar.setSubtitle((index + 1) + " / " + items.size());
        else if (bar != null) bar.setSubtitle(null);
        invalidateOptionsMenu();
    }

    /**
     * Shows items[idx]. dir: 0 = no animation, +1/-1 = slide in from right/left, 2 = cross-fade.
     */
    private void show(int idx, final int dir) {
        index = idx;
        final Media m = current();
        updateTitle();
        playButton.setVisibility(m.video ? View.VISIBLE : View.GONE);
        if (loadTask != null) loadTask.cancel(true);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int maxDim = Math.min(2560, Math.max(dm.widthPixels, dm.heightPixels));
        progress.setVisibility(View.VISIBLE);
        loadTask = new AsyncTask<Void, Void, Bitmap>() {
            @Override protected Bitmap doInBackground(Void... p) {
                return Bmp.decode(getApplicationContext(), m, maxDim);
            }

            @Override protected void onPostExecute(Bitmap b) {
                if (isCancelled()) return;
                progress.setVisibility(View.GONE);
                if (b == null) {
                    Toast.makeText(ViewerActivity.this, R.string.load_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                image.animate().cancel();
                image.setBitmap(b);
                if (dir == 1 || dir == -1) {
                    image.setTranslationX(dir * image.getWidth());
                    image.setAlpha(0.2f);
                    image.animate().translationX(0).alpha(1f).setDuration(200).setListener(null);
                } else if (dir == 2) {
                    image.setTranslationX(0);
                    image.setAlpha(0f);
                    image.animate().alpha(1f).setDuration(400).setListener(null);
                } else {
                    image.setTranslationX(0);
                    image.setAlpha(1f);
                }
            }
        }.execute();
    }

    private void go(int dir) {
        int n = index + dir;
        if (n < 0 || n >= items.size()) return;
        show(n, dir);
    }

    // ---- ZoomImageView.Listener ----

    @Override
    public void onTap() {
        if (slideshow) {
            stopSlideshow();
            return;
        }
        setBarVisible(!barVisible);
    }

    @Override
    public void onSwipe(int dir) {
        stopSlideshow();
        go(dir);
    }

    @Override
    public void onUserTouch() {
        if (slideshow) stopSlideshow();
    }

    private void setBarVisible(boolean v) {
        barVisible = v;
        ActionBar bar = getActionBar();
        if (bar != null) {
            if (v) bar.show(); else bar.hide();
        }
        image.setSystemUiVisibility(v ? View.SYSTEM_UI_FLAG_VISIBLE : View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    // ---- slideshow ----

    private void startSlideshow() {
        if (items.size() < 2) return;
        slideshow = true;
        order = new ArrayList<Integer>();
        for (int i = 0; i < items.size(); i++) if (!items.get(i).video) order.add(i);
        if (order.isEmpty()) { slideshow = false; return; }
        if (Prefs.shuffle(this)) {
            Collections.shuffle(order);
            orderPos = 0;
        } else {
            orderPos = Math.max(0, order.indexOf(index));
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setBarVisible(false);
        handler.postDelayed(slideStep, Prefs.slideshowSeconds(this) * 1000L);
    }

    private void advanceSlideshow() {
        if (!slideshow) return;
        orderPos++;
        if (orderPos >= order.size()) {
            if (Prefs.repeat(this)) {
                orderPos = 0;
                if (Prefs.shuffle(this)) Collections.shuffle(order);
            } else {
                stopSlideshow();
                return;
            }
        }
        show(order.get(orderPos), Prefs.transition(this) == 1 ? 1 : 2);
        handler.postDelayed(slideStep, Prefs.slideshowSeconds(this) * 1000L);
    }

    private void stopSlideshow() {
        if (!slideshow) return;
        slideshow = false;
        handler.removeCallbacks(slideStep);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setBarVisible(true);
    }

    // ---- menu ----

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.viewer, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Media m = items == null || items.isEmpty() ? null : current();
        boolean photo = m != null && !m.video;
        menu.findItem(R.id.menu_crop).setVisible(photo);
        menu.findItem(R.id.menu_edit).setVisible(photo);
        menu.findItem(R.id.menu_rotate_left).setVisible(photo);
        menu.findItem(R.id.menu_rotate_right).setVisible(photo);
        menu.findItem(R.id.menu_set_as).setVisible(photo);
        menu.findItem(R.id.menu_slideshow).setVisible(items != null && items.size() > 1);
        menu.findItem(R.id.menu_map).setVisible(m != null && Actions.latLong(m) != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Media m = current();
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.menu_slideshow) {
            startSlideshow();
        } else if (id == R.id.menu_share) {
            List<Media> one = new ArrayList<Media>();
            one.add(m);
            Actions.share(this, one);
        } else if (id == R.id.menu_delete) {
            List<Media> one = new ArrayList<Media>();
            one.add(m);
            Actions.delete(this, one, new Runnable() {
                @Override public void run() { afterDelete(); }
            });
        } else if (id == R.id.menu_edit) {
            Intent e = new Intent(this, EditActivity.class);
            e.setData(m.uri);
            startActivityForResult(e, REQ_EDIT);
        } else if (id == R.id.menu_crop) {
            Intent c = new Intent(this, CropActivity.class);
            c.setData(m.uri);
            startActivityForResult(c, REQ_CROP);
        } else if (id == R.id.menu_rotate_left) {
            rotate(m, 270);
        } else if (id == R.id.menu_rotate_right) {
            rotate(m, 90);
        } else if (id == R.id.menu_set_as) {
            Intent i = new Intent(Intent.ACTION_ATTACH_DATA);
            i.setDataAndType(m.uri, m.mime != null ? m.mime : "image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.putExtra("mimeType", m.mime != null ? m.mime : "image/*");
            try {
                startActivity(Intent.createChooser(i, getString(R.string.setImage)));
            } catch (Exception e) {
                Toast.makeText(this, R.string.no_way_to_share, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.menu_details) {
            Actions.details(this, m);
        } else if (id == R.id.menu_map) {
            float[] ll = Actions.latLong(m);
            if (ll != null) Actions.showOnMap(this, ll);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void afterDelete() {
        items.remove(index);
        if (items.isEmpty()) {
            finish();
            return;
        }
        if (index >= items.size()) index = items.size() - 1;
        show(index, 0);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if ((req == REQ_CROP || req == REQ_EDIT) && res == RESULT_OK) {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        }
    }

    private void playVideo() {
        Media m = current();
        if (!m.video) return;
        Intent i = new Intent(this, VideoActivity.class);
        i.setData(m.uri);
        startActivity(i);
    }

    /** Rotates the picture file itself (re-encodes it) and reloads. */
    private void rotate(final Media m, final int degrees) {
        progress.setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... p) {
                Bitmap src = Bmp.decode(getApplicationContext(), m, 8192);
                if (src == null) return false;
                Matrix mx = new Matrix();
                mx.postRotate(degrees);
                Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), mx, true);
                boolean png = m.mime != null && m.mime.contains("png");
                try {
                    OutputStream os = getContentResolver().openOutputStream(m.uri, "wt");
                    if (os == null) return false;
                    try {
                        out.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 95, os);
                    } finally {
                        os.close();
                    }
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Images.Media.ORIENTATION, 0);
                    cv.put(android.provider.MediaStore.Images.Media.WIDTH, out.getWidth());
                    cv.put(android.provider.MediaStore.Images.Media.HEIGHT, out.getHeight());
                    try { getContentResolver().update(m.uri, cv, null, null); } catch (Exception ignored) {}
                    m.orientation = 0;
                    m.width = out.getWidth();
                    m.height = out.getHeight();
                    return true;
                } catch (Exception e) {
                    return false;
                } finally {
                    if (out != src) src.recycle();
                    out.recycle();
                }
            }

            @Override protected void onPostExecute(Boolean ok) {
                progress.setVisibility(View.GONE);
                Thumbs.clear();
                if (!ok) Toast.makeText(ViewerActivity.this, R.string.save_failed, Toast.LENGTH_LONG).show();
                show(index, 0);
            }
        }.execute();
    }
}
