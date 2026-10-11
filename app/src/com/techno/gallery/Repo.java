package com.techno.gallery;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads pictures and videos from MediaStore. */
final class Repo {
    private Repo() {}

    static final int KIND_ALL = 0, KIND_IMAGES = 1, KIND_VIDEOS = 2;

    static class Album {
        String bucketId;      // null for the "All ..." albums
        String name;
        int kind = KIND_ALL;
        int count;
        Media cover;
    }

    private static final String[] IMG_COLS = {
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.ORIENTATION
    };

    private static final String[] VID_COLS = {
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATE_TAKEN,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.DURATION
    };

    private static void query(ContentResolver cr, boolean video, String bucketId, List<Media> out) {
        String sel = null;
        String[] args = null;
        if (bucketId != null) {
            sel = (video ? MediaStore.Video.Media.BUCKET_ID : MediaStore.Images.Media.BUCKET_ID) + "=?";
            args = new String[] { bucketId };
        }
        query(cr, video, sel, args, out);
    }

    private static void query(ContentResolver cr, boolean video, String sel, String[] args, List<Media> out) {
        Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                         : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor c = null;
        try {
            c = cr.query(base, video ? VID_COLS : IMG_COLS, sel, args, null);
            if (c == null) return;
            while (c.moveToNext()) {
                Media m = new Media();
                m.video = video;
                m.id = c.getLong(0);
                m.uri = ContentUris.withAppendedId(base, m.id);
                m.bucketId = c.getString(1) == null ? "" : c.getString(1);
                m.bucketName = c.getString(2) == null ? "" : c.getString(2);
                m.path = c.getString(3);
                m.mime = c.getString(4);
                m.name = c.getString(5);
                long taken = c.getLong(6);
                if (taken <= 0) taken = c.getLong(7) * 1000L;
                m.taken = taken;
                m.size = c.getLong(8);
                m.width = c.getInt(9);
                m.height = c.getInt(10);
                if (video) m.duration = c.getLong(11);
                else m.orientation = c.getInt(11);
                out.add(m);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    static void sort(List<Media> list, final boolean ascending) {
        Collections.sort(list, new Comparator<Media>() {
            @Override public int compare(Media a, Media b) {
                int r = a.taken < b.taken ? -1 : (a.taken > b.taken ? 1 : 0);
                if (r == 0) r = a.id < b.id ? -1 : (a.id > b.id ? 1 : 0);
                return ascending ? r : -r;
            }
        });
    }

    /** Media of a bucket (or everything when bucketId == null). */
    static List<Media> load(Context ctx, int kind, String bucketId, boolean ascending) {
        List<Media> out = new ArrayList<Media>();
        ContentResolver cr = ctx.getContentResolver();
        if (kind != KIND_VIDEOS) query(cr, false, bucketId, out);
        if (kind != KIND_IMAGES) query(cr, true, bucketId, out);
        sort(out, ascending);
        return out;
    }

    static List<Album> albums(Context ctx, boolean wantImages, boolean wantVideos) {
        List<Media> all = load(ctx, KIND_ALL, null, false); // newest first
        Map<String, Album> map = new LinkedHashMap<String, Album>();
        Album allImages = new Album();
        allImages.kind = KIND_IMAGES;
        allImages.name = ctx.getString(R.string.all_images);
        Album allVideos = new Album();
        allVideos.kind = KIND_VIDEOS;
        allVideos.name = ctx.getString(R.string.all_videos);
        for (Media m : all) {
            if (m.video) {
                if (!wantVideos) continue;
                allVideos.count++;
                if (allVideos.cover == null) allVideos.cover = m;
            } else {
                if (!wantImages) continue;
                allImages.count++;
                if (allImages.cover == null) allImages.cover = m;
            }
            Album a = map.get(m.bucketId);
            if (a == null) {
                a = new Album();
                a.bucketId = m.bucketId;
                a.name = m.bucketName.length() == 0 ? "?" : m.bucketName;
                a.cover = m;
                map.put(m.bucketId, a);
            }
            a.count++;
        }
        List<Album> buckets = new ArrayList<Album>(map.values());
        Collections.sort(buckets, new Comparator<Album>() {
            @Override public int compare(Album a, Album b) {
                boolean ca = isCamera(a), cb = isCamera(b);
                if (ca != cb) return ca ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        List<Album> result = new ArrayList<Album>();
        if (allImages.count > 0) result.add(allImages);
        if (allVideos.count > 0) result.add(allVideos);
        result.addAll(buckets);
        return result;
    }

    private static boolean isCamera(Album a) {
        if ("Camera".equalsIgnoreCase(a.name)) return true;
        return a.cover != null && a.cover.path != null && a.cover.path.contains("/DCIM/Camera");
    }

    /** Builds a Media for any uri; fills in MediaStore data when it is a media uri. */
    static Media fromUri(Context ctx, Uri uri) {
        Media m = null;
        ContentResolver cr = ctx.getContentResolver();
        String type = null;
        try { type = cr.getType(uri); } catch (Exception ignored) {}
        boolean video = type != null && type.startsWith("video/");
        if ("media".equals(uri.getAuthority())) {
            try {
                long id = ContentUris.parseId(uri);
                List<Media> one = new ArrayList<Media>();
                query(cr, video, MediaStore.MediaColumns._ID + "=?",
                        new String[] { String.valueOf(id) }, one);
                if (!one.isEmpty()) m = one.get(0);
            } catch (Exception ignored) {}
        }
        if (m == null) {
            m = new Media();
            m.uri = uri;
            m.video = video;
            m.mime = type;
            m.name = uri.getLastPathSegment();
            if ("file".equals(uri.getScheme())) m.path = uri.getPath();
        }
        return m;
    }
}
