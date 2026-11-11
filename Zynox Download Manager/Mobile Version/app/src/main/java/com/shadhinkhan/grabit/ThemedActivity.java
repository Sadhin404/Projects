package com.shadhinkhan.grabit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Base for the settings screens: same glass look as the home screen, in
 *  both Dark (original) and Light themes. */
public abstract class ThemedActivity extends Activity {
    protected boolean light;
    protected int INK, GREY, DIM;
    protected static final int VIOLET = Color.rgb(139, 92, 246);
    protected static final int[] VGRAD = new int[]{Color.rgb(167, 139, 250), Color.rgb(139, 92, 246), Color.rgb(109, 40, 217)};

    protected int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    protected void initTheme() {
        light = Ui.isLight(this);
        INK = light ? Color.rgb(24, 21, 33) : Color.rgb(245, 245, 247);
        GREY = light ? Color.rgb(94, 88, 106) : Color.rgb(156, 163, 175);
        DIM = light ? Color.rgb(140, 134, 152) : Color.rgb(107, 114, 128);
        try { getWindow().setStatusBarColor(light ? Color.rgb(243, 242, 247) : Color.rgb(11, 11, 16)); } catch (Throwable ignored) {}
    }

    protected GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        if (light) { g.setColor(Color.argb(235, 255, 255, 255)); g.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { g.setColor(Color.argb(26, 196, 181, 253)); g.setStroke(dp(1), Color.argb(46, 255, 255, 255)); }
        g.setCornerRadius(dp(18));
        return g;
    }

    protected GradientDrawable violetBg(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, VGRAD);
        g.setCornerRadius(dp((int) radiusDp));
        return g;
    }

    protected GradientDrawable chipBg(boolean selected) {
        if (selected) return violetBg(12);
        GradientDrawable g = new GradientDrawable();
        if (light) { g.setColor(Color.rgb(255, 255, 255)); g.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { g.setColor(Color.argb(18, 255, 255, 255)); g.setStroke(dp(1), Color.argb(30, 255, 255, 255)); }
        g.setCornerRadius(dp(12));
        return g;
    }

    /** Root + scroll + page with the glowing backdrop; returns the page. */
    protected LinearLayout chrome(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // v65: backdrop + aurora live on the screen wrapper below
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(36), dp(20), dp(28));
        scroll.addView(page);
        // responsive: on wide screens (tablets, foldables, landscape)
        // the content column stops at 600dp and centres - cards and
        // text never stretch edge to edge
        if (Ui.widthDp(this) > 600) {
            android.widget.FrameLayout.LayoutParams plp = new android.widget.FrameLayout.LayoutParams(dp(600), ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.gravity = Gravity.CENTER_HORIZONTAL;
            page.setLayoutParams(plp);
        }

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(INK); back.setTextSize(21); back.setGravity(Gravity.CENTER);
        back.setBackground(cardBg());
        head.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(INK); t.setTextSize(23); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(14), 0, 0, 0);
        head.addView(t);
        page.addView(head);
        // v65: same moving aurora light as the home screen, so the
        // effect runs across the whole app (Settings / About / etc.)
        android.widget.FrameLayout screen = new android.widget.FrameLayout(this);
        screen.setBackgroundResource(light ? R.drawable.bg_home_light : R.drawable.bg_home);
        screen.addView(new AuroraView(this, light),
                new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen.addView(root,
                new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(screen);
        return page;
    }

    /** A tappable glass settings row with a title, a subtitle and a chevron. */
    protected LinearLayout row(String title, String subtitle, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(cardBg());
        r.setPadding(dp(16), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        r.setLayoutParams(lp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(this);
        tt.setText(title); tt.setTextColor(INK); tt.setTextSize(15.5f); tt.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(tt);
        if (subtitle != null) {
            TextView st = new TextView(this);
            st.setText(subtitle); st.setTextColor(GREY); st.setTextSize(12.5f);
            st.setPadding(0, dp(2), 0, 0);
            st.setTag("subtitle");
            texts.addView(st);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView chev = new TextView(this);
        chev.setText("›"); chev.setTextColor(GREY); chev.setTextSize(22);
        r.addView(chev);
        if (click != null) r.setOnClickListener(click);
        return r;
    }

    protected TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(INK); tv.setTextSize(16); tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(20), 0, dp(2));
        return tv;
    }

    protected TextView note(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(GREY); tv.setTextSize(12.5f);
        tv.setPadding(0, dp(8), 0, 0);
        return tv;
    }
}
