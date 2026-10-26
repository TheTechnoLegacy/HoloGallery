package com.techno.gallery;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Thumbnail grid of one album, with multi-select. */
public class AlbumActivity extends BaseActivity {
    static final String EXTRA_BUCKET = "techno.bucket";
    static final String EXTRA_KIND = "techno.kind";
    static final String EXTRA_TITLE = "techno.title";
    static final String EXTRA_PICK = "techno.pick";
    static final String EXTRA_PICK_TYPE = "techno.pickType";
    private static final int REQ_CROP = 2;

    private GridView grid;
    private TextView empty;
    private final List<Media> items = new ArrayList<Media>();
    private Adapter adapter;
    private String bucket;
    private int kind = Repo.KIND_ALL;
    private boolean pick;
    private int tilePx;
    private AsyncTask<Void, Void, List<Media>> task;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.grid);
        grid = (GridView) findViewById(R.id.grid);
        empty = (TextView) findViewById(R.id.empty);

        Intent in = getIntent();
        bucket = in.getStringExtra(EXTRA_BUCKET);
        kind = in.getIntExtra(EXTRA_KIND, Repo.KIND_ALL);
        pick = in.getBooleanExtra(EXTRA_PICK, false);
        if (in.getAction() != null && Intent.ACTION_VIEW.equals(in.getAction()) && in.getType() != null) {
            kind = in.getType().endsWith("video") ? Repo.KIND_VIDEOS : Repo.KIND_IMAGES;
        }
        String pickType = in.getStringExtra(EXTRA_PICK_TYPE);
        if (pickType != null) {
            if (pickType.startsWith("image/")) kind = Repo.KIND_IMAGES;
            else if (pickType.startsWith("video/")) kind = Repo.KIND_VIDEOS;
        }
        String title = in.getStringExtra(EXTRA_TITLE);
        setTitle(title != null ? title : getString(R.string.gallery_picker_label));

        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                onOpen(pos);
            }
        });
        if (!pick) {
            grid.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
            grid.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
                @Override public boolean onCreateActionMode(ActionMode m, Menu menu) {
                    getMenuInflater().inflate(R.menu.cab, menu);
                    return true;
                }
                @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
                @Override public void onDestroyActionMode(ActionMode m) {}
                @Override public void onItemCheckedStateChanged(ActionMode m, int pos, long id, boolean on) {
                    m.setTitle(getString(R.string.selected_count, grid.getCheckedItemCount()));
                }
                @Override public boolean onActionItemClicked(final ActionMode m, MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.menu_select_all) {
                        for (int i = 0; i < items.size(); i++) grid.setItemChecked(i, true);
                        return true;
                    }
                    List<Media> sel = checked();
                    if (id == R.id.menu_share) {
                        Actions.share(AlbumActivity.this, sel);
                        return true;
                    }
                    if (id == R.id.menu_delete) {
                        Actions.delete(AlbumActivity.this, sel, new Runnable() {
                            @Override public void run() {
                                m.finish();
                                load();
                            }
                        });
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private List<Media> checked() {
        List<Media> out = new ArrayList<Media>();
        android.util.SparseBooleanArray s = grid.getCheckedItemPositions();
        for (int i = 0; i < s.size(); i++) {
            int pos = s.keyAt(i);
            if (s.valueAt(i) && pos >= 0 && pos < items.size()) out.add(items.get(pos));
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        float d = getResources().getDisplayMetrics().density;
        tilePx = Math.round(Prefs.columnDp(this) * d);
        grid.setColumnWidth(tilePx);
        ensureStorage();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (task != null) task.cancel(true);
    }

    @Override
    void onStorageDenied() {
        empty.setText(R.string.need_permission);
        empty.setVisibility(View.VISIBLE);
    }

    @Override
    void onStorageReady() {
        load();
    }

    private void load() {
        if (task != null) task.cancel(true);
        task = new AsyncTask<Void, Void, List<Media>>() {
            @Override protected List<Media> doInBackground(Void... p) {
                return Repo.load(AlbumActivity.this, kind, bucket, Prefs.ascending(AlbumActivity.this));
            }

            @Override protected void onPostExecute(List<Media> r) {
                if (isCancelled()) return;
                items.clear();
                items.addAll(r);
                adapter.notifyDataSetChanged();
                empty.setText(R.string.no_media);
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }.execute();
    }

    private void onOpen(int pos) {
        Media m = items.get(pos);
        if (pick) {
            if (getIntent().hasExtra("crop") && !m.video) {
                Intent c = new Intent(this, CropActivity.class);
                if (getIntent().getExtras() != null) c.putExtras(getIntent().getExtras());
                c.setData(m.uri);
                startActivityForResult(c, REQ_CROP);
                return;
            }
            Intent r = new Intent();
            r.setData(m.uri);
            r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK, r);
            finish();
            return;
        }
        ViewerActivity.items = new ArrayList<Media>(items);
        Intent i = new Intent(this, ViewerActivity.class);
        i.putExtra(ViewerActivity.EXTRA_INDEX, pos);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_CROP && res == RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return items.get(i).id; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(AlbumActivity.this).inflate(R.layout.tile, parent, false);
            Media m = items.get(pos);
            ImageView iv = (ImageView) v.findViewById(R.id.thumb);
            Thumbs.load(AlbumActivity.this, m, Math.max(tilePx, 64), iv);
            v.findViewById(R.id.play).setVisibility(m.video ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
