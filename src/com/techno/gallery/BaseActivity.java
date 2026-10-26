package com.techno.gallery;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

/** Handles the runtime storage permission. */
abstract class BaseActivity extends Activity {
    private static final int REQ_STORAGE = 42;
    private boolean asked;

    /** Called once storage can be read. */
    abstract void onStorageReady();

    /** Called if the user denies storage access. */
    void onStorageDenied() {}

    boolean hasStorage() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }

    void ensureStorage() {
        if (hasStorage()) {
            onStorageReady();
        } else if (!asked) {
            asked = true;
            requestPermissions(new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_STORAGE) return;
        if (hasStorage()) onStorageReady();
        else onStorageDenied();
    }
}
