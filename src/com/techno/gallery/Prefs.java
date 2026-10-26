package com.techno.gallery;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    private static int intOf(Context c, String key, int def) {
        try {
            return Integer.parseInt(sp(c).getString(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    /** Column width of the picture grid in dp. */
    static int columnDp(Context c) {
        switch (intOf(c, "pref_gallery_size_key", 1)) {
            case 0: return 80;
            case 2: return 150;
            default: return 110;
        }
    }

    static boolean ascending(Context c) {
        return "ascending".equals(sp(c).getString("pref_gallery_sort_key", "descending"));
    }

    static boolean confirmDelete(Context c) {
        return sp(c).getBoolean("pref_gallery_confirm_delete_key", true);
    }

    static int slideshowSeconds(Context c) {
        return Math.max(1, intOf(c, "pref_gallery_slideshow_interval_key", 2));
    }

    /** 0 = fade, 1 = slide */
    static int transition(Context c) {
        return intOf(c, "pref_gallery_slideshow_transition_key", 0);
    }

    static boolean repeat(Context c) {
        return sp(c).getBoolean("pref_gallery_slideshow_repeat_key", false);
    }

    static boolean shuffle(Context c) {
        return sp(c).getBoolean("pref_gallery_slideshow_shuffle_key", false);
    }
}
