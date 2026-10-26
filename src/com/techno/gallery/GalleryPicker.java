package com.techno.gallery;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Home screen: the list of albums. Also serves PICK / GET_CONTENT requests. */
public class GalleryPicker extends BaseActivity {
    private static final int REQ_ALBUM = 1;

    private GridView grid;
    private TextView empty;
    private final List<Repo.Album> albums = new ArrayList<Repo.Album>();
    private AlbumAdapter adapter;
    private boolean pick;
    private boolean wantImages = true, wantVideos = true;
    private AsyncTask<Void, Void, List<Repo.Album>> task;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.albums);
        grid = (GridView) findViewById(R.id.albums);
        empty = (TextView) findViewById(R.id.empty);

        Intent in = getIntent();
        String action = in.getAction();
        pick = Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action);
        if (pick) {
            String type = in.getType();
            if (type != null) {
                if (type.startsWith("image/")) wantVideos = false;
                else if (type.startsWith("video/")) wantImages = false;
            }
            setTitle(wantVideos && !wantImages ? R.string.select_video : R.string.select_picture);
        }

        adapter = new AlbumAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                open(albums.get(pos));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (task != null) task.cancel(true);
        task = new AsyncTask<Void, Void, List<Repo.Album>>() {
            @Override protected List<Repo.Album> doInBackground(Void... p) {
                return Repo.albums(GalleryPicker.this, wantImages, wantVideos);
            }

            @Override protected void onPostExecute(List<Repo.Album> r) {
                if (isCancelled()) return;
                albums.clear();
                albums.addAll(r);
                adapter.notifyDataSetChanged();
                empty.setText(R.string.no_media);
                empty.setVisibility(albums.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }.execute();
    }

    private void open(Repo.Album a) {
        Intent i = new Intent(this, AlbumActivity.class);
        if (getIntent().getExtras() != null) i.putExtras(getIntent().getExtras());
        i.putExtra(AlbumActivity.EXTRA_BUCKET, a.bucketId);
        i.putExtra(AlbumActivity.EXTRA_KIND, a.kind);
        i.putExtra(AlbumActivity.EXTRA_TITLE, a.name);
        i.putExtra(AlbumActivity.EXTRA_PICK, pick);
        if (pick) {
            i.putExtra(AlbumActivity.EXTRA_PICK_TYPE, getIntent().getType());
            startActivityForResult(i, REQ_ALBUM);
        } else {
            startActivity(i);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_ALBUM && res == RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.albums, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class AlbumAdapter extends BaseAdapter {
        @Override public int getCount() { return albums.size(); }
        @Override public Object getItem(int i) { return albums.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(GalleryPicker.this).inflate(R.layout.album_item, parent, false);
            Repo.Album a = albums.get(pos);
            ((TextView) v.findViewById(R.id.title)).setText(a.name);
            ((TextView) v.findViewById(R.id.count)).setText(getString(R.string.n_items, a.count));
            float d = getResources().getDisplayMetrics().density;
            ImageView iv = (ImageView) v.findViewById(R.id.thumb);
            if (a.cover != null) Thumbs.load(GalleryPicker.this, a.cover, Math.round(142 * d), iv);
            else iv.setImageDrawable(null);
            return v;
        }
    }
}
