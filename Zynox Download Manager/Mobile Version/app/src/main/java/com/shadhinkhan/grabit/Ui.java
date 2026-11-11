package com.shadhinkhan.grabit;

import android.content.Context;

/** App-wide theme choice (Settings > Appearance): "dark" (the original
 *  design) or "light". Stored in the shared prefs. */
public class Ui {
    public static boolean isLight(Context c) {
        try {
            return "light".equals(c.getSharedPreferences("grabit_prefs", Context.MODE_PRIVATE)
                    .getString("theme", "dark"));
        } catch (Throwable e) { return false; }
    }

    public static String themeName(Context c) { return isLight(c) ? "light" : "dark"; }

    /** Screen width in dp - the basis of the responsive layout rules
     *  (narrow phones, normal phones, tablets / foldables). */
    public static int widthDp(Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        return (int) (m.widthPixels / m.density);
    }

    public static void setTheme(Context c, String name) {
        try {
            c.getSharedPreferences("grabit_prefs", Context.MODE_PRIVATE)
                    .edit().putString("theme", name).apply();
        } catch (Throwable ignored) {}
    }
}
