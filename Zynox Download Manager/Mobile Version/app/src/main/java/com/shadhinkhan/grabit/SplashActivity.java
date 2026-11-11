package com.shadhinkhan.grabit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App-opening animation (user request 2026-11-09): a short, clean
 *  Lottie intro - the Zynox layer-stack logo draws itself in over the
 *  app's own moving ambient light, in the current theme - then a fade
 *  into the home screen. Tapping skips it. If the animation cannot
 *  load, the launcher icon shows briefly instead (never a hang). */
public class SplashActivity extends Activity {
    private boolean gone = false;
    private LottieView lottie;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        boolean light = false;
        try { light = Ui.isLight(this); } catch (Throwable ignored) {}
        try { getWindow().setStatusBarColor(light ? Color.rgb(243, 242, 247) : Color.rgb(11, 11, 16)); } catch (Throwable ignored) {}

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundResource(light ? R.drawable.bg_home_light : R.drawable.bg_home);
        screen.addView(new AuroraView(this, light),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams colp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(col, colp);

        lottie = new LottieView(this);
        boolean ok = lottie.loadRaw(R.raw.zynox_splash);
        int logoPx = Math.min(dp(210), (int) (getResources().getDisplayMetrics().widthPixels * 0.58));
        logoPx = Math.min(logoPx, (int) (getResources().getDisplayMetrics().heightPixels * 0.5)); // landscape: never crowd out the name
        if (ok) {
            col.addView(lottie, new LinearLayout.LayoutParams(logoPx, logoPx));
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageResource(R.mipmap.ic_launcher);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(android.view.View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(28)); }
            });
            int iconPx = Math.min(dp(120), (int) (logoPx * 0.57));
            col.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
            iv.setAlpha(0f); iv.setScaleX(0.85f); iv.setScaleY(0.85f);
            iv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).start();
        }

        TextView name = new TextView(this);
        SpannableString ss = new SpannableString("Zynox");
        ss.setSpan(new ForegroundColorSpan(light ? Color.rgb(23, 20, 34) : Color.WHITE), 0, 2, 0);
        ss.setSpan(new ForegroundColorSpan(Color.rgb(139, 92, 246)), 2, 5, 0);
        name.setText(ss);
        name.setTextSize(31); name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        try { name.setLetterSpacing(0.06f); } catch (Throwable ignored) {}
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.setMargins(0, dp(10), 0, 0);
        name.setLayoutParams(nlp);
        name.setAlpha(0f);
        name.animate().alpha(1f).setStartDelay(250).setDuration(600).start();
        col.addView(name);

        TextView tag = new TextView(this);
        tag.setText("Download from anywhere.");
        tag.setTextColor(light ? Color.rgb(94, 88, 106) : Color.rgb(156, 163, 175));
        tag.setTextSize(13.5f);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, dp(5), 0, 0);
        tag.setAlpha(0f);
        tag.animate().alpha(1f).setStartDelay(500).setDuration(600).start();
        col.addView(tag);

        setContentView(screen);
        screen.setOnClickListener(v -> go());

        long wait = ok ? (long) Math.min(Math.max(lottie.durationMs(), 900), 2400) + 380 : 1100;
        if (ok) lottie.play();
        new Handler(Looper.getMainLooper()).postDelayed(this::go, wait);
    }

    private void go() {
        if (gone) return;
        gone = true;
        try { if (lottie != null) lottie.stopAnim(); } catch (Throwable ignored) {}
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override public void onBackPressed() {
        // 2-second intro: swallow Back so the app does not exit mid-logo
    }
}
