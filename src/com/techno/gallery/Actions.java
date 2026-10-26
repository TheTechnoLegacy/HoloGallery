package com.techno.gallery;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Share / delete / details shared by the grid and the viewer. */
final class Actions {
    private Actions() {}

    static void share(Activity a, List<Media> items) {
        if (items.isEmpty()) return;
        Intent i;
        if (items.size() == 1) {
            Media m = items.get(0);
            i = new Intent(Intent.ACTION_SEND);
            i.setType(m.mime != null ? m.mime : (m.video ? "video/*" : "image/*"));
            i.putExtra(Intent.EXTRA_STREAM, m.uri);
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.setType("*/*");
            ArrayList<Uri> uris = new ArrayList<Uri>();
            for (Media m : items) uris.add(m.uri);
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, a.getString(R.string.sendImage)));
        } catch (Exception e) {
            Toast.makeText(a, R.string.no_way_to_share, Toast.LENGTH_SHORT).show();
        }
    }

    static void delete(final Activity a, final List<Media> items, final Runnable done) {
        if (items.isEmpty()) return;
        if (!Prefs.confirmDelete(a)) {
            runDelete(a, items, done);
            return;
        }
        int msg = items.size() > 1 ? R.string.confirm_delete_multiple_message
                : (items.get(0).video ? R.string.confirm_delete_video_message
                                      : R.string.confirm_delete_message);
        new AlertDialog.Builder(a)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(msg)
                .setPositiveButton(R.string.review_toss, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        runDelete(a, items, done);
                    }
                })
                .setNegativeButton(R.string.review_cancel, null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private static void runDelete(final Activity a, final List<Media> items, final Runnable done) {
        final ProgressDialog pd = new ProgressDialog(a);
        pd.setMessage(a.getString(R.string.delete_images_message));
        pd.setCancelable(false);
        pd.show();
        new AsyncTask<Void, Void, Integer>() {
            @Override protected Integer doInBackground(Void... p) {
                int ok = 0;
                for (Media m : items) {
                    try {
                        int n = a.getContentResolver().delete(m.uri, null, null);
                        if (n <= 0 && m.path != null) {
                            if (new File(m.path).delete()) n = 1;
                        }
                        if (n > 0) ok++;
                    } catch (Exception ignored) {}
                }
                return ok;
            }

            @Override protected void onPostExecute(Integer ok) {
                try { pd.dismiss(); } catch (Exception ignored) {}
                Thumbs.clear();
                if (ok < items.size()) {
                    Toast.makeText(a, R.string.delete_failed, Toast.LENGTH_LONG).show();
                }
                if (done != null) done.run();
            }
        }.execute();
    }

    private static void line(StringBuilder sb, Activity a, int label, String value) {
        if (value == null || value.length() == 0) return;
        sb.append(a.getString(label)).append(' ').append(value).append('\n');
    }

    static float[] latLong(Media m) {
        if (m.path == null || m.video) return null;
        try {
            float[] ll = new float[2];
            if (new ExifInterface(m.path).getLatLong(ll)) return ll;
        } catch (Exception ignored) {}
        return null;
    }

    static void details(final Activity a, final Media m) {
        StringBuilder sb = new StringBuilder();
        line(sb, a, R.string.d_name, m.name);
        line(sb, a, R.string.d_path, m.path);
        line(sb, a, R.string.d_type, m.mime);
        if (m.size > 0) line(sb, a, R.string.d_size, Formatter.formatFileSize(a, m.size));
        if (m.width > 0 && m.height > 0) line(sb, a, R.string.d_resolution, m.width + " x " + m.height);
        if (m.taken > 0) {
            line(sb, a, R.string.d_taken, DateFormat.getMediumDateFormat(a).format(new Date(m.taken))
                    + " " + DateFormat.getTimeFormat(a).format(new Date(m.taken)));
        }
        if (m.video && m.duration > 0) {
            long s = m.duration / 1000;
            line(sb, a, R.string.d_duration, String.format("%d:%02d", s / 60, s % 60));
        }
        if (!m.video && m.path != null) {
            try {
                ExifInterface ex = new ExifInterface(m.path);
                line(sb, a, R.string.d_make, ex.getAttribute(ExifInterface.TAG_MAKE));
                line(sb, a, R.string.d_model, ex.getAttribute(ExifInterface.TAG_MODEL));
                String wb = ex.getAttribute(ExifInterface.TAG_WHITE_BALANCE);
                if (wb != null) line(sb, a, R.string.d_wb, "0".equals(wb) ? "Auto" : "Manual");
            } catch (Exception ignored) {}
        }
        final float[] ll = latLong(m);
        if (ll != null) {
            line(sb, a, R.string.d_lat, String.valueOf(ll[0]));
            line(sb, a, R.string.d_lon, String.valueOf(ll[1]));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(R.string.details)
                .setMessage(sb.toString().trim())
                .setPositiveButton(R.string.details_ok, null);
        if (ll != null) {
            b.setNeutralButton(R.string.show_on_map, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { showOnMap(a, ll); }
            });
        }
        b.show();
    }

    static void showOnMap(Activity a, float[] ll) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:" + ll[0] + "," + ll[1] + "?q=" + ll[0] + "," + ll[1])));
        } catch (Exception e) {
            Toast.makeText(a, "No maps application found.", Toast.LENGTH_SHORT).show();
        }
    }
}
