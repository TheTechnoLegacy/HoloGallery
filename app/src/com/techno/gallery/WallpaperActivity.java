package com.techno.gallery;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Handles ATTACH_DATA ("Set picture as" -> Wallpaper): crops to screen size and sets it. */
public class WallpaperActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getIntent().getData() == null) {
            finish();
            return;
        }
        Intent c = new Intent(this, CropActivity.class);
        c.setData(getIntent().getData());
        c.putExtra("set-as-wallpaper", true);
        c.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(c, 1);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        setResult(res);
        finish();
    }
}
