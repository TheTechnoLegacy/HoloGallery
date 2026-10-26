package com.techno.gallery;

import android.net.Uri;

/** One picture or video. */
public class Media {
    public long id;
    public boolean video;
    public Uri uri;
    public String bucketId = "";
    public String bucketName = "";
    public String path;
    public String mime;
    public String name;
    public long taken;
    public long size;
    public long duration;
    public int width;
    public int height;
    public int orientation;
}
