package com.shadhinkhan.grabit;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Main screen - dark violet design (v22), modelled on the user's reference mockup:
 * header + platform icons, paste card, Format cards (Video / Audio (MP3) / Other),
 * watermark switch, big Download button, bottom nav (Home / Downloads / Files).
 * All download behaviour is unchanged from v21.
 */
public class MainActivity extends Activity {
    private final ArrayList<Fmt> formats = new ArrayList<>();
    private EditText urlInput;
    private TextView status;
    private LinearLayout resultCard;
    private android.widget.ImageView resultThumb;
    private TextView resultTitle, resultSub, resultDur;
    private RadioGroup group;
    private Switch noWmSwitch;
    private Button dlBtn, resumeBtn;
    private android.widget.ImageView fabDl;
    private boolean fabReady = false;
    private TextView headline;
    private Runnable hlTick;
    private int hlPhrase = 0, hlShown = 0, hlPhase = 0;
    private boolean hlCursor = true, hlDead = false;
    private long hlHoldUntil = 0;
    private static final String[] HL_PLAIN = {"Download from ", "One Link, ", "Fast Downloads, ", "Your Media, ", "Just Paste, ", "Music & Videos, ", "From Online, ", "Less Waiting, ", "Your Favorites, ", "Download Smart, "};
    private static final String[] HL_ACCENT = {"Anywhere.", "Every Platform.", "Zero Hassle.", "Your Way.", "And Download.", "One Place.", "To Offline.", "More Watching.", "Always Ready.", "Stay Simple."};
    /** Press feedback: custom gradient backgrounds have no pressed state,
     *  so taps felt dead/static. A RippleDrawable restores the native touch
     *  ripple, clipped to the same rounded shape by the mask. */
    private android.graphics.drawable.Drawable rippleBg(android.graphics.drawable.Drawable base, int maskRadiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE); mask.setCornerRadius(dp(maskRadiusDp));
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(55, 255, 255, 255)), base, mask);
    }

    // ---- v64 design FX (his order 2026-11-08: layout NOT changed -
    // only glass buttons, click effect, shadow, background 3D light) ----
    private final java.util.ArrayList<android.animation.Animator> fxAnims = new java.util.ArrayList<>();

    /** Violet gradient + soft top gloss + light edge = glass pill. */
    private android.graphics.drawable.Drawable glassViolet(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, VGRAD);
        g.setCornerRadius(dp((int) radiusDp));
        g.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(80, 255, 255, 255), Color.argb(0, 255, 255, 255)});
        gloss.setCornerRadius(dp((int) radiusDp));
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{g, gloss});
    }

    /** 3D press: quick dip on touch-down, springy pop back on release. */
    private void pressFx(final View v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getAction();
            if (a == android.view.MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.94f).scaleY(0.94f).translationY(dp(1))
                        .setDuration(90).start();
            } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).translationY(0f)
                        .setDuration(230)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
            }
            return false;
        });
    }

    /** One soft radial light blob for the animated background glow. */
    private View glowBlob(int argb, int sizeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(sizeDp * getResources().getDisplayMetrics().density * 0.5f);
        g.setColors(new int[]{argb, Color.TRANSPARENT});
        View v = new View(this);
        v.setBackground(g);
        v.setClickable(false);
        return v;
    }

    /** Slow endless drift for a background glow blob (moving light). */
    private void driftFx(View v, float dx, float dy, long dur) {
        android.animation.ObjectAnimator ax = android.animation.ObjectAnimator.ofFloat(v, "translationX", 0f, dx);
        android.animation.ObjectAnimator ay = android.animation.ObjectAnimator.ofFloat(v, "translationY", 0f, dy);
        for (android.animation.ObjectAnimator a : new android.animation.ObjectAnimator[]{ax, ay}) {
            a.setDuration(dur);
            a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.start();
            fxAnims.add(a);
        }
    }

    /** Gentle breathing pulse (used on the round download button). */
    private void pulseFx(View v) {
        android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.07f);
        android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.07f);
        for (android.animation.ObjectAnimator a : new android.animation.ObjectAnimator[]{sx, sy}) {
            a.setDuration(1500);
            a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.start();
            fxAnims.add(a);
        }
    }
    private android.graphics.drawable.RippleDrawable rippleFg() {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(50, 90, 70, 160)), null, null);
    }

    /** Light mode: the glossy Analyze pill shows the plain word (its
     *  magnifier is a compound drawable on the button itself). Dark mode
     *  keeps the arrow prefix. (An overlay label view was tried first and
     *  did not render on the user's phone, so the text lives in the Button.) */
    private void setDlBtnText(String txt) {
        dlBtn.setText(lightTheme ? txt.replace("\u2b07", "").trim() : txt);
    }
    private FrameLayout barTrack;
    private View barFill;
    private String currentUrl = "";
    private String currentTitle = "download";
    private long shownStamp = 0;
    private final Handler handler = new Handler();

    private int selectedCategory = 0; // 0 Video, 1 Audio (MP3)
    private boolean downloadAfterFetch = false;
    // v78: the background-running permission is requested once per
    // process on app entry (see maybePromptBackground below)
    private static boolean battPromptShown = false;
    private final TextView[] catCards = new TextView[3];
    private final TextView[] catBadges = new TextView[3];
    private final LinearLayout[] navItems = new LinearLayout[3];
    private final TextView[] navLabels = new TextView[3];
    private View[] pages = new View[3];
    private LinearLayout filesList;
    private TextView downloadsStatus;
    private LinearLayout downloadsList;
    private LinearLayout dlLiveCard;
    private TextView dlLiveTitle, dlLiveMeta;
    private android.widget.ProgressBar dlLiveBar;
    private FileItem pendingDeleteItem;
    private View pendingDeleteCard;
    private LinearLayout emptyState;
    private LinearLayout filesLiveCard;
    private TextView filesLiveTitle, filesLiveMeta;
    private android.widget.ProgressBar filesLiveBar;
    private boolean filesWasBusy = false;
    private int currentTab = 0;
    private boolean filesRefreshing = false;

    private static int BG = Color.rgb(11, 11, 16);
    private static int CARD = Color.rgb(23, 23, 31);
    private static int CARD_STROKE = Color.rgb(38, 38, 48);
    private static int CHIP = Color.rgb(28, 28, 38);
    private static int INK = Color.rgb(245, 245, 247);
    private static int GREY = Color.rgb(156, 163, 175);
    private static int DIM = Color.rgb(107, 114, 128);
    private static int BADGE_INK = Color.rgb(216, 210, 235);
    private boolean lightTheme = false;
    private String builtTheme = "dark";

    /** Dark (original) or Light palette from Settings > Appearance.
     *  Recolours the palette before any view is built; the glass helpers
     *  branch on lightTheme. */
    private void applyTheme() {
        lightTheme = Ui.isLight(this);
        builtTheme = lightTheme ? "light" : "dark";
        if (lightTheme) {
            BG = Color.rgb(243, 242, 247); CARD = Color.rgb(255, 255, 255);
            CARD_STROKE = Color.argb(30, 52, 40, 110); CHIP = Color.rgb(255, 255, 255);
            INK = Color.rgb(24, 21, 33); GREY = Color.rgb(94, 88, 106); DIM = Color.rgb(140, 134, 152);
            BADGE_INK = Color.rgb(62, 54, 92);
        } else {
            BG = Color.rgb(11, 11, 16); CARD = Color.rgb(23, 23, 31);
            CARD_STROKE = Color.rgb(38, 38, 48); CHIP = Color.rgb(28, 28, 38);
            INK = Color.rgb(245, 245, 247); GREY = Color.rgb(156, 163, 175); DIM = Color.rgb(107, 114, 128);
            BADGE_INK = Color.rgb(216, 210, 235);
        }
    }
    private static final int VIOLET = Color.rgb(139, 92, 246);
    private static final int VIOLET_DEEP = Color.rgb(124, 58, 237);
    private static final int[] VGRAD = new int[]{Color.rgb(167, 139, 250), Color.rgb(139, 92, 246), Color.rgb(109, 40, 217)};

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable violetBg(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, VGRAD);
        g.setCornerRadius(dp((int) radiusDp));
        return g;
    }
    private GradientDrawable cardBg() {
        // glass: translucent lavender fill + soft light border (mockup)
        GradientDrawable g = new GradientDrawable();
        if (lightTheme) { g.setColor(Color.argb(235, 255, 255, 255)); g.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { g.setColor(Color.argb(26, 196, 181, 253)); g.setStroke(dp(1), Color.argb(46, 255, 255, 255)); }
        g.setCornerRadius(dp(20));
        return g;
    }
    private GradientDrawable fieldBg() {
        GradientDrawable g = new GradientDrawable();
        if (lightTheme) { g.setColor(Color.rgb(238, 236, 245)); g.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { g.setColor(Color.argb(22, 255, 255, 255)); g.setStroke(dp(1), Color.argb(34, 255, 255, 255)); }
        g.setCornerRadius(dp(14));
        return g;
    }
    private GradientDrawable chipBg() {
        GradientDrawable g = new GradientDrawable();
        if (lightTheme) { g.setColor(Color.rgb(255, 255, 255)); g.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { g.setColor(Color.argb(24, 255, 255, 255)); g.setStroke(dp(1), Color.argb(40, 255, 255, 255)); }
        g.setCornerRadius(dp(14));
        return g;
    }
    private GradientDrawable catBg(boolean selected) {
        if (selected) { GradientDrawable s = violetBg(16); s.setStroke(dp(1), Color.argb(130, 255, 255, 255)); return s; }
        GradientDrawable g = new GradientDrawable();
        if (lightTheme) { g.setColor(Color.rgb(255, 255, 255)); g.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { g.setColor(Color.argb(18, 255, 255, 255)); g.setStroke(dp(1), Color.argb(30, 255, 255, 255)); }
        g.setCornerRadius(dp(16));
        return g;
    }
    private GradientDrawable rowBg(boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(selected ? Color.argb(38, 139, 92, 246) : (lightTheme ? Color.argb(235, 255, 255, 255) : Color.argb(20, 255, 255, 255)));
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), selected ? VIOLET : CARD_STROKE);
        return g;
    }
    private String mb(long bytes) { return String.format(Locale.US, "%.1f MB", bytes / 1048576.0); }
    private String cleanName(String s) {
        String c = s.replaceAll("[^\\w\\- ]", "").trim();
        return c.length() > 70 ? c.substring(0, 70).trim() : (c.isEmpty() ? "download" : c);
    }
    private String shortMsg(String m) {
        if (m == null) return "unknown error";
        return m.length() > 350 ? m.substring(m.length() - 350) : m;
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        applyTheme();
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7); } catch (Throwable ignored) {}
        } else {
            // older Android has no notification runtime prompt - go
            // straight to the background-running prompt (v78)
            handler.postDelayed(this::maybePromptBackground, 1200);
        }
        try { getWindow().setStatusBarColor(BG); } catch (Throwable ignored) {}

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // v65: the backdrop + moving aurora light live on the screen
        // wrapper (see setContentView below) so the light covers the
        // whole app incl. the nav bar area; root itself is transparent.

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // v65: background moving light is the full-screen AuroraView
        // on the screen wrapper (below) - nothing is added here.

        // ================= HOME PAGE =================
        ScrollView homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(40), dp(20), dp(28));
        homeScroll.addView(page);
        capPage(page);
        content.addView(homeScroll);
        pages[0] = homeScroll;

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView bName = new TextView(this);
        SpannableString logo = new SpannableString("Zynox");
        logo.setSpan(new ForegroundColorSpan(lightTheme ? Color.rgb(23, 20, 34) : Color.WHITE), 0, 2, 0);
        logo.setSpan(new ForegroundColorSpan(VIOLET), 2, 5, 0);
        bName.setText(logo);
        bName.setTextSize(23); bName.setTypeface(Typeface.DEFAULT_BOLD);
        bName.setSingleLine(true);
        titles.addView(bName);
        TextView bSub = new TextView(this);
        bSub.setText("Download Manager");
        bSub.setTextColor(GREY); bSub.setTextSize(14.5f);
        titles.addView(bSub);
        android.widget.ImageView logoMark = new android.widget.ImageView(this);
        logoMark.setImageResource(R.mipmap.ic_launcher);
        logoMark.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        logoMark.setClipToOutline(true);
        logoMark.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12)); }
        });
        LinearLayout.LayoutParams lmp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lmp.setMargins(0, 0, dp(12), 0);
        header.addView(logoMark, lmp);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        // Dark/Light switcher beside the settings gear, using his moon
        // logos: the button shows the CURRENT theme's tile (dark tile in
        // dark mode, white tile in light mode, like his header mockup);
        // tapping it flips the theme and rebuilds the screen.
        android.widget.ImageView themeBtn = new android.widget.ImageView(this);
        themeBtn.setImageResource(lightTheme ? R.drawable.icon_theme_light : R.drawable.icon_theme_dark);
        themeBtn.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        themeBtn.setClipToOutline(true);
        themeBtn.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(11)); }
        });
        themeBtn.setContentDescription("Switch dark / light mode");
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(dp(38), dp(38));
        tblp.setMargins(0, 0, dp(10), 0);
        themeBtn.setForeground(rippleFg());
        header.addView(themeBtn, tblp);
        themeBtn.setOnClickListener(v -> {
            Ui.setTheme(this, lightTheme ? "dark" : "light");
            Toast.makeText(this, lightTheme ? "Dark mode on" : "Light mode on", Toast.LENGTH_SHORT).show();
            recreate();
        });
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(17); gear.setTextColor(GREY);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(rippleBg(chipBg(), 12));
        header.addView(gear, new LinearLayout.LayoutParams(dp(38), dp(38)));
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        page.addView(header);

        // headline: one-line two-colour typewriter animation (the
        // user's 10 lines, looping like his reference video) + badges
        headline = new TextView(this);
        headline.setSingleLine(true);
        float hlSize = Ui.widthDp(this) < 340 ? 16.5f : (Ui.widthDp(this) >= 600 ? 22 : 19);
        if (getResources().getConfiguration().fontScale > 1.2f) hlSize -= 2f; // big system font: keep the one-line typewriter from clipping
        headline.setTextColor(INK); headline.setTextSize(hlSize); headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setPadding(0, dp(20), 0, 0);
        page.addView(headline);
        startHeadlineAnim();
        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setPadding(0, dp(12), 0, 0);
        page.addView(badges);
        String[] badgeTexts = {"\u26a1 Fast", "\u25c8 High Quality", "\ud83d\udee1\ufe0f Safe"};
        final int[] badgeIcons = {R.drawable.icon_bolt, R.drawable.icon_layers, R.drawable.icon_shield};
        final String[] badgeLabels = {"Fast", "High Quality", "Safe"};
        for (int i = 0; i < badgeTexts.length; i++) {
            View badgeView;
            if (lightTheme) {
                // his light-mode badge pills: white pill, real icon, bold dark text, tiny shadow
                LinearLayout pill = new LinearLayout(this);
                pill.setOrientation(LinearLayout.HORIZONTAL);
                pill.setGravity(Gravity.CENTER);
                GradientDrawable bbg = new GradientDrawable();
                bbg.setColor(Color.rgb(255, 255, 255)); bbg.setStroke(dp(1), Color.argb(30, 52, 40, 110));
                bbg.setCornerRadius(dp(99));
                pill.setBackground(bbg);
                pill.setPadding(dp(11), dp(5), dp(11), dp(5));
                pill.setElevation(dp(2));
                android.widget.ImageView biv = new android.widget.ImageView(this);
                biv.setImageResource(badgeIcons[i]);
                pill.addView(biv, new LinearLayout.LayoutParams(dp(13), dp(13)));
                TextView b = new TextView(this);
                b.setText("  " + badgeLabels[i]);
                b.setTextColor(INK); b.setTextSize(12f); b.setTypeface(Typeface.DEFAULT_BOLD);
                pill.addView(b);
                badgeView = pill;
            } else {
                TextView b = new TextView(this);
                b.setText(badgeTexts[i]);
                b.setTextColor(BADGE_INK); b.setTextSize(12f);
                b.setGravity(Gravity.CENTER);
                GradientDrawable bbg = new GradientDrawable();
                bbg.setColor(Color.argb(26, 255, 255, 255)); bbg.setStroke(dp(1), Color.argb(38, 255, 255, 255));
                bbg.setCornerRadius(dp(99));
                b.setBackground(bbg);
                b.setPadding(dp(11), dp(5), dp(11), dp(5));
                badgeView = b;
            }
            LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) blp2.setMargins(dp(8), 0, 0, 0);
            badges.addView(badgeView, blp2);
        }

        // analyze card (mockup): link field + big gradient button
        LinearLayout analyzeCard = new LinearLayout(this);
        analyzeCard.setOrientation(LinearLayout.VERTICAL);
        analyzeCard.setBackground(cardBg());
        analyzeCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acp.setMargins(0, dp(18), 0, 0);
        page.addView(analyzeCard, acp);
        LinearLayout fieldRow = new LinearLayout(this);
        fieldRow.setOrientation(LinearLayout.HORIZONTAL);
        fieldRow.setGravity(Gravity.CENTER_VERTICAL);
        fieldRow.setBackground(fieldBg());
        fieldRow.setPadding(dp(12), dp(6), dp(6), dp(6));
        if (!lightTheme) {
            // violet neon glow around the link box (his reference
            // mock): bright rim + violet shadow halo on all sides
            GradientDrawable glowBg = fieldBg();
            glowBg.setStroke(dp(2), Color.argb(215, 167, 139, 250));
            fieldRow.setBackground(glowBg);
            if (Build.VERSION.SDK_INT >= 21) fieldRow.setElevation(dp(10));
            if (Build.VERSION.SDK_INT >= 28) { try { fieldRow.setOutlineSpotShadowColor(VIOLET); fieldRow.setOutlineAmbientShadowColor(VIOLET); } catch (Throwable ignored) {} }
        }
        analyzeCard.addView(fieldRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        TextView linkIc = new TextView(this);
        linkIc.setText("\ud83d\udd17"); linkIc.setTextSize(15);
        if (Ui.widthDp(this) < 340) linkIc.setVisibility(View.GONE); // very narrow screens: give the input + Paste/Clear the room
        fieldRow.addView(linkIc);
        urlInput = new EditText(this);
        urlInput.setHint("Paste a link here\u2026");
        urlInput.setHintTextColor(lightTheme ? Color.rgb(94, 88, 106) : Color.argb(205, 226, 220, 245));
        urlInput.setTextColor(INK);
        urlInput.setSingleLine(true);
        urlInput.setEllipsize(android.text.TextUtils.TruncateAt.END);
        urlInput.setTextSize(14f);
        urlInput.setBackgroundColor(Color.TRANSPARENT);
        urlInput.setPadding(dp(8), 0, dp(4), 0);
        fieldRow.addView(urlInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView pasteBtn = new TextView(this);
        pasteBtn.setText("Paste");
        pasteBtn.setTextColor(Color.WHITE); pasteBtn.setTextSize(13); pasteBtn.setTypeface(Typeface.DEFAULT_BOLD);
        pasteBtn.setGravity(Gravity.CENTER);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(lightTheme ? Color.rgb(196, 181, 253) : Color.argb(51, 139, 92, 246)); pbg.setCornerRadius(dp(10));
        pasteBtn.setBackground(rippleBg(pbg, 10));
        pasteBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        fieldRow.addView(pasteBtn);
        pasteBtn.setOnClickListener(v -> pasteFromClipboard());
        TextView clearBtn = new TextView(this);
        clearBtn.setText("\u2715");
        clearBtn.setTextColor(INK); clearBtn.setTextSize(13); clearBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearBtn.setGravity(Gravity.CENTER);
        GradientDrawable clbg = new GradientDrawable();
        clbg.setColor(lightTheme ? Color.rgb(238, 236, 245) : Color.argb(38, 255, 255, 255)); clbg.setCornerRadius(dp(10));
        clearBtn.setBackground(rippleBg(clbg, 10));
        clearBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp2.setMargins(dp(6), 0, 0, 0);
        fieldRow.addView(clearBtn, clp2);
        clearBtn.setOnClickListener(v -> clearLinkBox());
        if (Ui.widthDp(this) < 340) { pasteBtn.setPadding(dp(9), dp(8), dp(9), dp(8)); clearBtn.setPadding(dp(9), dp(8), dp(9), dp(8)); }

        Intent in = getIntent();
        if (in != null && in.hasExtra("prefill_url")) {
            urlInput.setText(in.getStringExtra("prefill_url"));
        }
        urlInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                handler.removeCallbacks(autoFetchRunnable);
                handler.postDelayed(autoFetchRunnable, 800);
            }
        });

        dlBtn = new Button(this);
        dlBtn.setTextColor(Color.WHITE); dlBtn.setTextSize(15f);
        dlBtn.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout btnWrap = new FrameLayout(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        dlp.setMargins(0, dp(12), 0, 0);
        analyzeCard.addView(btnWrap, dlp);
        btnWrap.addView(dlBtn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (lightTheme) {
            // glossy pill like his Analyze artwork: violet gradient + gloss
            // sweep, white magnifier + bold label centred as one group, >> right
            GradientDrawable pillG = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(168, 120, 250), Color.rgb(139, 92, 246), Color.rgb(109, 40, 217)});
            pillG.setCornerRadius(dp(23));
            GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.argb(72, 255, 255, 255), Color.argb(0, 255, 255, 255)});
            gloss.setCornerRadius(dp(23));
            dlBtn.setBackground(rippleBg(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{pillG, gloss}), 23));
            android.graphics.drawable.Drawable mag = getResources().getDrawable(R.drawable.icon_search_white, getTheme());
            int ms = dp(17); mag.setBounds(0, 0, ms, ms);
            dlBtn.setCompoundDrawables(mag, null, null, null);
            dlBtn.setCompoundDrawablePadding(dp(8));
            TextView chev = new TextView(this);
            chev.setText("\u00bb");
            chev.setTextColor(Color.WHITE); chev.setTextSize(18); chev.setTypeface(Typeface.DEFAULT_BOLD);
            chev.setClickable(false);
            FrameLayout.LayoutParams chlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            chlp.setMargins(0, 0, dp(14), 0);
            btnWrap.addView(chev, chlp);
        } else {
            dlBtn.setBackground(rippleBg(glassViolet(23), 23));
        }
        setDlBtnText("\u2b07  Analyze");
        dlBtn.setOnClickListener(v -> onDownloadPressed());
        // v64: violet shadow glowing under the main button + 3D press
        if (Build.VERSION.SDK_INT >= 21) dlBtn.setElevation(dp(9));
        if (Build.VERSION.SDK_INT >= 28) {
            try { dlBtn.setOutlineSpotShadowColor(VIOLET); dlBtn.setOutlineAmbientShadowColor(VIOLET); } catch (Throwable ignored) {}
        }
        pressFx(dlBtn);

        // Supported Platforms (mockup): title row + his logo chips
        LinearLayout platTitle = new LinearLayout(this);
        platTitle.setOrientation(LinearLayout.HORIZONTAL);
        platTitle.setGravity(Gravity.CENTER_VERTICAL);
        platTitle.setPadding(0, dp(22), 0, dp(10));
        page.addView(platTitle);
        TextView pt = new TextView(this);
        pt.setText("Supported Platforms");
        pt.setTextColor(INK); pt.setTextSize(16); pt.setTypeface(Typeface.DEFAULT_BOLD);
        platTitle.addView(pt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout icons = new LinearLayout(this);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(icons);
        final int[] chipIcons = lightTheme
                ? new int[]{R.drawable.chip_youtube_light, R.drawable.chip_tiktok_light, R.drawable.chip_instagram_light, R.drawable.chip_facebook_light, R.drawable.chip_pinterest_light}
                : new int[]{R.drawable.chip_youtube, R.drawable.chip_tiktok, R.drawable.chip_instagram, R.drawable.chip_facebook, R.drawable.chip_pinterest};
        final String[] chipPkgs = {"com.google.android.youtube", "com.zhiliaoapp.musically|com.ss.android.ugc.trill", "com.instagram.android", "com.facebook.katana", "com.pinterest"};
        final String[] chipWebs = {"https://www.youtube.com", "https://www.tiktok.com", "https://www.instagram.com", "https://www.facebook.com", "https://www.pinterest.com"};
        final String[] chipNames = {"YouTube", "TikTok", "Instagram", "Facebook", "Pinterest"};
        for (int i = 0; i < chipIcons.length; i++) {
            final int ci = i;
            android.widget.ImageView ic = new android.widget.ImageView(this);
            ic.setImageResource(chipIcons[i]);
            ic.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            ic.setClipToOutline(true);
            ic.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(13)); }
            });
            ic.setContentDescription(chipNames[i]);
            if (lightTheme) ic.setElevation(dp(2)); // tiny soft shadow under the logos, like his screenshot
            ic.setForeground(rippleFg());
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(50), 1);
            if (i > 0) ilp.setMargins(dp(8), 0, 0, 0);
            icons.addView(ic, ilp);
            ic.setOnClickListener(v -> openPlatform(chipPkgs[ci], chipWebs[ci], chipNames[ci]));
            pressFx(ic);
        }

        // Download Options card (mockup): category cards + watermark row inside
        LinearLayout optionsCard = new LinearLayout(this);
        optionsCard.setOrientation(LinearLayout.VERTICAL);
        optionsCard.setBackground(cardBg());
        optionsCard.setPadding(dp(14), dp(14), dp(14), dp(12));
        LinearLayout.LayoutParams ocp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ocp.setMargins(0, dp(20), 0, 0);
        page.addView(optionsCard, ocp);
        TextView fmtLabel = new TextView(this);
        fmtLabel.setText("Download Options");
        fmtLabel.setTextColor(INK); fmtLabel.setTextSize(15.5f); fmtLabel.setTypeface(Typeface.DEFAULT_BOLD);
        fmtLabel.setPadding(0, 0, 0, dp(10));
        optionsCard.addView(fmtLabel);
        LinearLayout cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.HORIZONTAL);
        optionsCard.addView(cats);
        String[][] catDefs = {{"\u25b6", "Video"}, {"\u266a", "Audio (MP3)"}};
        for (int i = 0; i < catDefs.length; i++) {
            final int ci = i;
            FrameLayout wrap = new FrameLayout(this);
            TextView card = new TextView(this);
            card.setText(catDefs[i][0] + "\n" + catDefs[i][1]);
            card.setTextSize(14.5f); card.setTypeface(Typeface.DEFAULT_BOLD);
            card.setGravity(Gravity.CENTER);
            card.setPadding(0, dp(14), 0, dp(14));
            wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TextView badge = new TextView(this);
            badge.setText("\u2713");
            badge.setTextColor(VIOLET_DEEP); badge.setTextSize(11); badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable bdg = new GradientDrawable();
            bdg.setShape(GradientDrawable.OVAL); bdg.setColor(Color.WHITE);
            badge.setBackground(bdg);
            badge.setVisibility(View.GONE);
            FrameLayout.LayoutParams blp3 = new FrameLayout.LayoutParams(dp(18), dp(18));
            blp3.gravity = Gravity.TOP | Gravity.END;
            blp3.setMargins(0, dp(6), dp(6), 0);
            wrap.addView(badge, blp3);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) clp.setMargins(dp(10), 0, 0, 0);
            wrap.setClipToOutline(true);
            wrap.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16)); }
            });
            wrap.setForeground(rippleFg());
            cats.addView(wrap, clp);
            catCards[i] = card;
            catBadges[i] = badge;
            wrap.setOnClickListener(v -> selectCategory(ci));
            pressFx(wrap);
        }
        styleCategories();

        // watermark switch row (inside the options card)
        LinearLayout wmRow = new LinearLayout(this);
        wmRow.setOrientation(LinearLayout.HORIZONTAL);
        wmRow.setGravity(Gravity.CENTER_VERTICAL);
        wmRow.setPadding(dp(2), dp(10), dp(2), dp(2));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.setMargins(0, dp(8), 0, 0);
        optionsCard.addView(wmRow, wlp);
        android.widget.ImageView drop = new android.widget.ImageView(this);
        drop.setImageResource(lightTheme ? R.drawable.chip_tiktok_light : R.drawable.chip_tiktok);
        drop.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        drop.setClipToOutline(true);
        drop.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(6)); }
        });
        wmRow.addView(drop, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView wmText = new TextView(this);
        wmText.setText("  Remove watermark (TikTok)");
        wmText.setTextColor(INK); wmText.setTextSize(14.5f);
        if (lightTheme) wmText.setTypeface(Typeface.DEFAULT_BOLD);
        wmRow.addView(wmText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        noWmSwitch = new Switch(this);
        noWmSwitch.setChecked(true);
        if (Build.VERSION.SDK_INT >= 21) {
            noWmSwitch.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            noWmSwitch.setTrackTintList(ColorStateList.valueOf(VIOLET));
        }
        wmRow.addView(noWmSwitch);

        // status + progress
        status = new TextView(this);
        status.setText("Ready. Paste a link — formats load automatically.");
        status.setTextColor(GREY); status.setTextSize(13.5f);
        status.setPadding(0, dp(14), 0, 0);
        page.addView(status);
        barTrack = new FrameLayout(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(lightTheme ? Color.rgb(226, 223, 235) : Color.rgb(32, 32, 42)); trackBg.setCornerRadius(dp(99));
        barTrack.setBackground(trackBg);
        barFill = new View(this);
        barFill.setBackground(violetBg(99));
        barTrack.addView(barFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(9));
        blp.setMargins(0, dp(12), 0, dp(4));
        page.addView(barTrack, blp);
        setProgressUi(0);

        // Result card (like the share popup's video card): thumbnail with a
        // duration badge + title + "Platform  ·  duration". Replaces the old
        // plain title text; filled by showFormats, hidden while fetching.
        resultCard = new LinearLayout(this);
        resultCard.setOrientation(LinearLayout.HORIZONTAL);
        resultCard.setGravity(Gravity.CENTER_VERTICAL);
        resultCard.setBackground(cardBg());
        resultCard.setPadding(dp(10), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams rcp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rcp.setMargins(0, dp(14), 0, dp(4));
        resultCard.setVisibility(View.GONE);
        page.addView(resultCard, rcp);
        FrameLayout thumbWrap = new FrameLayout(this);
        resultThumb = new android.widget.ImageView(this);
        resultThumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        resultThumb.setBackgroundColor(lightTheme ? Color.rgb(233, 230, 242) : Color.rgb(38, 32, 54));
        resultThumb.setClipToOutline(true);
        resultThumb.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12)); }
        });
        thumbWrap.addView(resultThumb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        resultDur = new TextView(this);
        resultDur.setTextSize(10f); resultDur.setTextColor(Color.WHITE); resultDur.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable durBg = new GradientDrawable();
        durBg.setColor(Color.argb(185, 0, 0, 0)); durBg.setCornerRadius(dp(6));
        resultDur.setBackground(durBg);
        resultDur.setPadding(dp(6), dp(2), dp(6), dp(2));
        FrameLayout.LayoutParams durLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        durLp.setMargins(0, 0, dp(6), dp(6));
        thumbWrap.addView(resultDur, durLp);
        resultCard.addView(thumbWrap, new LinearLayout.LayoutParams(dp(118), dp(66)));
        LinearLayout cardTexts = new LinearLayout(this);
        cardTexts.setOrientation(LinearLayout.VERTICAL);
        cardTexts.setPadding(dp(12), 0, 0, 0);
        resultTitle = new TextView(this);
        resultTitle.setTextSize(14f); resultTitle.setTextColor(INK);
        resultTitle.setMaxLines(2); resultTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cardTexts.addView(resultTitle);
        resultSub = new TextView(this);
        resultSub.setTextSize(12f); resultSub.setTextColor(GREY);
        resultSub.setPadding(0, dp(5), 0, 0);
        cardTexts.addView(resultSub);
        resultCard.addView(cardTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        page.addView(group);
        group.setOnCheckedChangeListener((g, checkedId) -> styleRows());

        // Floating download button (the user's own violet-circle
        // artwork): fixed at the bottom-right of the Home tab, above the
        // nav bar, shown once formats are loaded - no scrolling back up
        // to the top Download button needed.
        fabDl = new android.widget.ImageView(this);
        fabDl.setImageResource(R.drawable.fab_download);
        fabDl.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        fabDl.setContentDescription("Download");
        if (Build.VERSION.SDK_INT >= 21) fabDl.setElevation(dp(6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(64), dp(64));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.setMargins(0, 0, dp(16), dp(14));
        fabDl.setLayoutParams(flp);
        fabDl.setVisibility(View.GONE);
        content.addView(fabDl);
        fabDl.setOnClickListener(v -> { if (!formats.isEmpty()) downloadSelected(); });
        pulseFx(fabDl); // v64: gentle breathing pulse, feels alive

        // empty state (mockup): shown until formats load
        emptyState = new LinearLayout(this);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyState.setPadding(0, dp(28), 0, dp(4));
        page.addView(emptyState);
        FrameLayout badgeCircle = new FrameLayout(this);
        GradientDrawable cg = new GradientDrawable();
        cg.setShape(GradientDrawable.OVAL); cg.setColor(Color.argb(44, 139, 92, 246)); cg.setStroke(dp(1), Color.argb(90, 167, 139, 250));
        badgeCircle.setBackground(cg);
        TextView badgeArrow = new TextView(this);
        badgeArrow.setText("\u2b07"); badgeArrow.setTextColor(Color.WHITE); badgeArrow.setTextSize(25); badgeArrow.setGravity(Gravity.CENTER);
        badgeCircle.addView(badgeArrow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyState.addView(badgeCircle, new LinearLayout.LayoutParams(dp(66), dp(66)));
        TextView esTitle = new TextView(this);
        esTitle.setText("No downloads yet");
        esTitle.setTextColor(INK); esTitle.setTextSize(15.5f); esTitle.setTypeface(Typeface.DEFAULT_BOLD);
        esTitle.setPadding(0, dp(12), 0, 0);
        emptyState.addView(esTitle);
        TextView esSub = new TextView(this);
        esSub.setText("Paste a link and start downloading");
        esSub.setTextColor(GREY); esSub.setTextSize(12.5f);
        emptyState.addView(esSub);


        resumeBtn = new Button(this);
        resumeBtn.setTextColor(VIOLET); resumeBtn.setBackground(cardBg()); resumeBtn.setTextSize(14f);
        resumeBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams rbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        rbp.setMargins(0, dp(10), 0, 0);
        page.addView(resumeBtn, rbp);
        resumeBtn.setOnClickListener(v -> {
            org.json.JSONObject p = AudioEngine.readPending(this);
            if (p == null) return;
            Intent svc = new Intent(this, DownloadService.class);
            svc.putExtra("mode", "audioResume");
            svc.putExtra("url", p.optString("pageUrl", ""));
            svc.putExtra("formatId", p.optString("formatId", ""));
            svc.putExtra("kind", "Audio");
            svc.putExtra("title", p.optString("title", "Audio"));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        });


        // ================= DOWNLOADS PAGE =================
        ScrollView dlScroll = new ScrollView(this);
        LinearLayout dlPage = new LinearLayout(this);
        dlPage.setOrientation(LinearLayout.VERTICAL);
        dlPage.setPadding(dp(20), dp(24), dp(20), dp(28));
        dlScroll.addView(dlPage);
        capPage(dlPage);
        dlPage.addView(pageTitle("Downloads"));
        downloadsStatus = new TextView(this);
        downloadsStatus.setTextColor(GREY); downloadsStatus.setTextSize(13.5f);
        downloadsStatus.setPadding(0, dp(6), 0, dp(10));
        dlPage.addView(downloadsStatus);
        // Live progress card (same details as the notification, in a card).
        dlLiveCard = new LinearLayout(this);
        dlLiveCard.setOrientation(LinearLayout.VERTICAL);
        dlLiveCard.setBackground(cardBg());
        dlLiveCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams dlLiveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlLiveLp.setMargins(0, 0, 0, dp(10));
        dlLiveCard.setLayoutParams(dlLiveLp);
        dlLiveTitle = new TextView(this);
        dlLiveTitle.setTextColor(INK); dlLiveTitle.setTextSize(14f);
        dlLiveTitle.setMaxLines(2); dlLiveTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dlLiveCard.addView(dlLiveTitle);
        dlLiveBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        dlLiveBar.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) dlLiveBar.setProgressTintList(ColorStateList.valueOf(VIOLET));
        LinearLayout.LayoutParams dlBarLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        dlBarLp.setMargins(0, dp(8), 0, dp(6));
        dlLiveCard.addView(dlLiveBar, dlBarLp);
        dlLiveMeta = new TextView(this);
        dlLiveMeta.setTextColor(GREY); dlLiveMeta.setTextSize(12.5f);
        dlLiveCard.addView(dlLiveMeta);
        dlLiveCard.setVisibility(View.GONE);
        dlPage.addView(dlLiveCard);
        downloadsList = new LinearLayout(this);
        downloadsList.setOrientation(LinearLayout.VERTICAL);
        dlPage.addView(downloadsList);
        content.addView(dlScroll);
        pages[1] = dlScroll;
        dlScroll.setVisibility(View.GONE);

        // ================= FILES PAGE =================
        ScrollView fScroll = new ScrollView(this);
        LinearLayout fPage = new LinearLayout(this);
        fPage.setOrientation(LinearLayout.VERTICAL);
        fPage.setPadding(dp(20), dp(24), dp(20), dp(28));
        fScroll.addView(fPage);
        capPage(fPage);
        fPage.addView(pageTitle("Files"));
        TextView fHint = new TextView(this);
        fHint.setText("Your downloaded videos, music and photos (Zynox folder). Tap one to open it.");
        fHint.setTextColor(GREY); fHint.setTextSize(13f);
        fHint.setPadding(0, dp(6), 0, dp(10));
        fPage.addView(fHint);
        // Live downloading card: title, progress bar, and MB of MB - % - time left.
        filesLiveCard = new LinearLayout(this);
        filesLiveCard.setOrientation(LinearLayout.VERTICAL);
        filesLiveCard.setBackground(cardBg());
        filesLiveCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        liveLp.setMargins(0, 0, 0, dp(10));
        filesLiveCard.setLayoutParams(liveLp);
        filesLiveTitle = new TextView(this);
        filesLiveTitle.setTextColor(INK); filesLiveTitle.setTextSize(14f);
        filesLiveTitle.setMaxLines(2); filesLiveTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        filesLiveCard.addView(filesLiveTitle);
        filesLiveBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        filesLiveBar.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) filesLiveBar.setProgressTintList(ColorStateList.valueOf(VIOLET));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        barLp.setMargins(0, dp(8), 0, dp(6));
        filesLiveCard.addView(filesLiveBar, barLp);
        filesLiveMeta = new TextView(this);
        filesLiveMeta.setTextColor(GREY); filesLiveMeta.setTextSize(12.5f);
        filesLiveCard.addView(filesLiveMeta);
        filesLiveCard.setVisibility(View.GONE);
        fPage.addView(filesLiveCard);
        filesList = new LinearLayout(this);
        filesList.setOrientation(LinearLayout.VERTICAL);
        fPage.addView(filesList);
        content.addView(fScroll);
        pages[2] = fScroll;
        fScroll.setVisibility(View.GONE);

        // ================= BOTTOM NAV =================
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable navBgD = new GradientDrawable();
        if (lightTheme) { navBgD.setColor(Color.argb(242, 255, 255, 255)); navBgD.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { navBgD.setColor(Color.argb(46, 32, 28, 54)); navBgD.setStroke(dp(1), Color.argb(52, 255, 255, 255)); }
        navBgD.setCornerRadius(dp(30));
        nav.setBackground(navBgD);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout navWrap = new LinearLayout(this);
        navWrap.setPadding(dp(14), dp(4), dp(14), dp(12));
        navWrap.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(10)); // floating glass bar shadow
        root.addView(navWrap);
        String[] navNames = {"Home", "Downloads", "Files"};
        for (int i = 0; i < 3; i++) {
            final int ni = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, dp(5)); // v64 fix: icon+label were taller than the item, so labels clipped at the bottom (his screenshot)
            if (i == 0) {
                TextView ic = new TextView(this);
                ic.setText("\u2302"); ic.setTextSize(21); ic.setGravity(Gravity.CENTER);
                item.addView(ic);
            } else {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageResource(i == 1
                        ? (lightTheme ? R.drawable.icon_dl_light : R.drawable.icon_dl_dark)
                        : (lightTheme ? R.drawable.icon_files_light : R.drawable.icon_files_dark));
                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                iv.setClipToOutline(true);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(8)); }
                });
                item.addView(iv, new LinearLayout.LayoutParams(dp(27), dp(27)));
            }
            TextView lb = new TextView(this);
            lb.setText(navNames[i]); lb.setTextSize(11.5f); lb.setGravity(Gravity.CENTER);
            lb.setPadding(0, dp(2), 0, 0);
            item.addView(lb);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            nlp.setMargins(dp(3), dp(3), dp(3), dp(3));
            nav.addView(item, nlp);
            navItems[i] = item;
            navLabels[i] = lb;
            item.setOnClickListener(v -> switchTab(ni));
            pressFx(item);
        }
        styleNav(0);

        // v65: screen wrapper = static backdrop + AuroraView (moving
        // purple light, whole screen) + the app root on top.
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundResource(lightTheme ? R.drawable.bg_home_light : R.drawable.bg_home);
        screen.addView(new AuroraView(this, lightTheme),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen.addView(root,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(screen);
        DlState.ensureEngineAsync(this);
        DlState.maybeUpdateLater(this);
        refreshResumeButton();
        pollState();
    }

    // ---------- headline typewriter animation ----------
    // Types the line in two colours (ink + violet accent) with a
    // blinking cursor, holds, deletes, moves to the next line - loops
    // through all 10 lines until the activity is destroyed.
    private void startHeadlineAnim() {
        hlDead = false;
        hlTick = new Runnable() {
            @Override public void run() {
                if (hlDead || headline == null) return;
                long delay;
                if (hlPhase == 0) {              // typing
                    if (hlShown < HL_PLAIN[hlPhrase].length() + HL_ACCENT[hlPhrase].length()) { hlShown++; delay = 45; }
                    else { hlPhase = 1; hlHoldUntil = System.currentTimeMillis() + 1600; delay = 60; }
                } else if (hlPhase == 1) {       // hold; cursor blinks
                    hlCursor = !hlCursor; delay = 270;
                    if (System.currentTimeMillis() >= hlHoldUntil) { hlPhase = 2; delay = 200; }
                } else if (hlPhase == 2) {       // deleting
                    hlShown = Math.max(0, hlShown - 3); delay = 16;
                    if (hlShown == 0) { hlPhase = 3; hlHoldUntil = System.currentTimeMillis() + 350; delay = 60; }
                } else {                          // gap, then next line
                    if (System.currentTimeMillis() >= hlHoldUntil) { hlPhrase = (hlPhrase + 1) % HL_PLAIN.length; hlPhase = 0; }
                    delay = 60;
                }
                renderHeadline();
                handler.postDelayed(this, delay);
            }
        };
        handler.post(hlTick);
    }

    private void renderHeadline() {
        String plain = HL_PLAIN[hlPhrase], accent = HL_ACCENT[hlPhrase];
        int pShow = Math.min(hlShown, plain.length());
        int aShow = Math.max(0, hlShown - plain.length());
        String txt = plain.substring(0, pShow) + accent.substring(0, aShow);
        SpannableString ss = new SpannableString(txt + "|");
        ss.setSpan(new ForegroundColorSpan(INK), 0, txt.length(), 0);
        if (aShow > 0) ss.setSpan(new ForegroundColorSpan(VIOLET), pShow, txt.length(), 0);
        boolean cursorOn = hlPhase != 1 || hlCursor;
        ss.setSpan(new ForegroundColorSpan(cursorOn ? VIOLET : Color.TRANSPARENT), txt.length(), txt.length() + 1, 0);
        headline.setText(ss);
    }

    @Override protected void onDestroy() {
        hlDead = true;
        if (hlTick != null) handler.removeCallbacks(hlTick);
        for (android.animation.Animator a : fxAnims) { try { a.cancel(); } catch (Throwable ignored) {} }
        super.onDestroy();
    }

    private TextView pageTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(INK); tv.setTextSize(24); tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    // ---------- tabs ----------
    private void switchTab(int idx) {
        int prevTab = currentTab;
        currentTab = idx;
        for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        // v64: incoming page turns in with a slight 3D tilt + fade
        View pg = pages[idx];
        if (pg != null && idx != prevTab) {
            pg.setCameraDistance(dp(1200));
            pg.setRotationY(idx > prevTab ? 7f : -7f);
            pg.setAlpha(0.35f);
            pg.animate().rotationY(0f).alpha(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        styleNav(idx);
        if (idx == 1) { refreshDownloadsTab(); updateLiveCard(); }
        if (idx == 2) { refreshFilesTab(); updateLiveCard(); }
        if (fabDl != null) fabDl.setVisibility(idx == 0 && fabReady ? View.VISIBLE : View.GONE);
    }
    private void styleNav(int selected) {
        for (int i = 0; i < navItems.length; i++) {
            boolean sel = i == selected;
            navItems[i].setBackground(sel ? glassViolet(24) : null);
            if (sel) {
                // v64: selected icon flips in 3D, pill pops softly
                View ic = navItems[i].getChildAt(0);
                if (ic != null) {
                    ic.setRotationY(-75f);
                    ic.animate().rotationY(0f).setDuration(330)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
                }
                navItems[i].setScaleX(0.93f); navItems[i].setScaleY(0.93f);
                navItems[i].animate().scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
            }
            navLabels[i].setTextColor(sel ? Color.WHITE : GREY);
            if (navItems[i].getChildAt(0) instanceof TextView) {
                ((TextView) navItems[i].getChildAt(0)).setTextColor(sel ? Color.WHITE : GREY);
            }
        }
    }
    private TextView listRow(String main, String sub) {
        TextView tv = new TextView(this);
        tv.setText(sub == null || sub.isEmpty() ? main : main + "\n" + sub);
        tv.setTextColor(INK); tv.setTextSize(13.5f);
        tv.setBackground(cardBg());
        tv.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        tv.setLayoutParams(lp);
        return tv;
    }
    private void refreshDownloadsTab() {
        if (downloadsStatus == null) return;
        downloadsStatus.setText(DlState.busy ? "Now: " + DlState.status : (DlState.status == null || DlState.status.isEmpty() ? "Nothing running right now." : "Last: " + DlState.status));
        downloadsList.removeAllViews();
        try {
            java.io.File log = new java.io.File(getFilesDir(), "download_diagnostics.log");
            if (!log.exists()) { downloadsList.addView(listRow("No downloads yet", "Finished and failed attempts will appear here.")); return; }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(log));
            ArrayList<String> lines = new ArrayList<>();
            String ln; while ((ln = br.readLine()) != null) lines.add(ln);
            br.close();
            if (lines.isEmpty()) { downloadsList.addView(listRow("No downloads yet", "")); return; }
            for (int i = lines.size() - 1; i >= 0 && i >= lines.size() - 12; i--) {
                try {
                    org.json.JSONObject o = new org.json.JSONObject(lines.get(i));
                    String res = o.optString("result", "");
                    String host = o.optString("host", "");
                    long bytes = o.optLong("downloaded", 0);
                    int retries = o.optInt("retries", 0);
                    String cat = o.optString("category", "");
                    String main = ("success".equals(res) ? "\u2713 Completed" : "paused".equals(res) ? "\u23f8 Paused" : "\u2717 " + (cat.isEmpty() ? "Failed" : cat)) + "  \u00b7  " + host;
                    String sub = mb(bytes) + (retries > 0 ? "  \u00b7  retried \u00d7" + retries : "");
                    downloadsList.addView(listRow(main, sub));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            downloadsList.addView(listRow("Could not read the activity log", ""));
        }
    }

    // ---------- Files tab: cards with thumbnails + live download card ----------
    private static class FileItem {
        String name; long size; long durMs; String mime;
        long mediaId = -1; String dataPath; String safUri; java.io.File file;
        android.graphics.Bitmap thumb; String glyph = "\u25b6";
    }

    private static String stripJobTag(String n) {
        return n.replaceFirst(" \\[j\\d+\\]", "");
    }

    /** Keeps the Files tab's downloading card current (called from the
     *  poll loop): title, progress bar, and MB of MB - % - time left. */
    private void updateLiveCard() {
        boolean show = DlState.busy && !DlState.dlTitle.isEmpty();
        bindLive(filesLiveCard, filesLiveTitle, filesLiveBar, filesLiveMeta, show);
        bindLive(dlLiveCard, dlLiveTitle, dlLiveBar, dlLiveMeta, show);
        if (!show && filesWasBusy) {
            if (currentTab == 1) refreshDownloadsTab();
            if (currentTab == 2) refreshFilesTab();
        }
        filesWasBusy = show;
    }

    private void bindLive(LinearLayout card, TextView title, android.widget.ProgressBar bar, TextView meta, boolean show) {
        if (card == null) return;
        if (!show) { if (card.getVisibility() == View.VISIBLE) card.setVisibility(View.GONE); return; }
        card.setVisibility(View.VISIBLE);
        title.setText("\u2b07 " + DlState.dlTitle);
        int pct = DlState.progress;
        bar.setIndeterminate(pct < 0);
        if (pct >= 0) bar.setProgress(Math.min(100, pct));
        StringBuilder m = new StringBuilder();
        if (pct >= 0) m.append(pct).append("%");
        if (DlState.dlTotal > 0 && DlState.dlDone >= 0) m.append("  \u00b7  ").append(dlPair(DlState.dlDone, DlState.dlTotal));
        if (DlState.dlEta > 0) m.append("  \u00b7  ~").append(DlState.dlEta).append("s left");
        meta.setText(m.toString());
    }

    /** Compact pair like 92/104MB (or 1.2/1.5GB), matching the notification. */
    private static String dlPair(long done, long total) {
        if (total >= 1073741824L) return dlNum(done / 1073741824.0) + "/" + dlNum(total / 1073741824.0) + "GB";
        return dlNum(done / 1048576.0) + "/" + dlNum(total / 1048576.0) + "MB";
    }
    private static String dlNum(double v) {
        String t = String.format(Locale.US, "%.1f", v);
        return t.endsWith(".0") ? t.substring(0, t.length() - 2) : t;
    }

    private void refreshFilesTab() {
        if (filesRefreshing) return;
        filesRefreshing = true;
        new Thread(() -> {
            final ArrayList<FileItem> items = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    Uri coll = MediaStore.Files.getContentUri("external");
                    String[] proj = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DURATION};
                    Cursor c = getContentResolver().query(coll, proj,
                            MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? OR " + MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? OR " + MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? OR " + MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? OR " + MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ?",
                            new String[]{"%Zynox%", "%Zenvy%", "%DhorSalare%", "%DhoreSalare%", "%GrabIt%", "%DoreFel%", "%DhoreFel%"},
                            MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC");
                    if (c != null) {
                        while (c.moveToNext() && items.size() < 60) {
                            FileItem it = new FileItem();
                            it.mediaId = c.getLong(0);
                            it.name = stripJobTag(c.getString(1) == null ? "file" : c.getString(1));
                            it.size = c.getLong(2);
                            it.mime = c.getString(3);
                            it.dataPath = c.getString(4);
                            it.durMs = c.getLong(5);
                            seen.add(it.name.toLowerCase(Locale.US));
                            items.add(it);
                        }
                        c.close();
                    }
                }
            } catch (Throwable ignored) {}
            // Files saved into the user's custom folder (recorded at export).
            try {
                String raw = getSharedPreferences("grabit_prefs", MODE_PRIVATE).getString("saf_files", "[]");
                org.json.JSONArray arr = new org.json.JSONArray(raw == null ? "[]" : raw);
                ArrayList<FileItem> safItems = new ArrayList<>();
                for (int k = arr.length() - 1; k >= 0; k--) {
                    org.json.JSONObject o = arr.getJSONObject(k);
                    String nm = o.optString("n", "");
                    if (nm.isEmpty() || seen.contains(nm.toLowerCase(Locale.US))) continue;
                    FileItem it = new FileItem();
                    it.name = nm; it.size = o.optLong("s", 0); it.safUri = o.optString("u", "");
                    try { it.mime = getContentResolver().getType(Uri.parse(it.safUri)); } catch (Throwable ignored) {}
                    seen.add(nm.toLowerCase(Locale.US));
                    safItems.add(it);
                }
                items.addAll(0, safItems);
            } catch (Throwable ignored) {}
            // App working-folder files not exported yet.
            try {
                java.io.File dir = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "GrabIt");
                java.io.File[] fs = dir.listFiles();
                if (fs != null) {
                    java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (java.io.File f : fs) {
                        String n = f.getName();
                        if (n.endsWith(".part") || n.endsWith(".json") || n.contains("_src") || n.startsWith("audio_dl") || n.startsWith("video_src")) continue;
                        String clean = stripJobTag(n);
                        if (seen.contains(clean.toLowerCase(Locale.US))) continue;
                        FileItem it = new FileItem();
                        it.name = clean; it.size = f.length(); it.file = f; it.dataPath = f.getAbsolutePath();
                        seen.add(clean.toLowerCase(Locale.US));
                        items.add(it);
                    }
                }
            } catch (Throwable ignored) {}
            // Thumbnails + durations (background; capped).
            for (FileItem it : items) fillThumbAndDuration(it);
            runOnUiThread(() -> {
                filesRefreshing = false;
                if (filesList == null) return;
                filesList.removeAllViews();
                if (items.isEmpty()) { filesList.addView(listRow("No files yet", "Downloaded media will appear here.")); return; }
                for (FileItem it : items) filesList.addView(fileCard(it));
            });
        }).start();
    }

    private void fillThumbAndDuration(FileItem it) {
        String mime = it.mime == null ? "" : it.mime;
        try {
            if (mime.startsWith("audio")) { it.glyph = "\u266a"; return; }
            if (mime.startsWith("image")) {
                android.graphics.Bitmap b = null;
                if (it.dataPath != null) {
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inSampleSize = 4;
                    b = android.graphics.BitmapFactory.decodeFile(it.dataPath, o);
                } else if (it.safUri != null) {
                    java.io.InputStream is = getContentResolver().openInputStream(Uri.parse(it.safUri));
                    if (is != null) { android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inSampleSize = 4; b = android.graphics.BitmapFactory.decodeStream(is, null, o); is.close(); }
                }
                if (b != null) it.thumb = b;
                return;
            }
            // video: a frame + the real duration
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            try {
                if (it.safUri != null) r.setDataSource(this, Uri.parse(it.safUri));
                else if (it.dataPath != null) r.setDataSource(it.dataPath);
                else return;
                if (it.durMs <= 0) {
                    String d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (d != null) it.durMs = Long.parseLong(d);
                }
                android.graphics.Bitmap b = r.getFrameAtTime(0);
                if (b != null) {
                    int w = 264, h = Math.max(1, (int) (b.getHeight() * (264.0 / Math.max(1, b.getWidth()))));
                    it.thumb = android.graphics.Bitmap.createScaledBitmap(b, w, h, true);
                }
            } finally { try { r.release(); } catch (Throwable ignored) {} }
        } catch (Throwable ignored) {}
    }

    private View fileCard(final FileItem it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(cardBg());
        card.setPadding(dp(10), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(clp);
        android.widget.FrameLayout thumbBox = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(dp(118), dp(66));
        tblp.setMarginEnd(dp(12));
        card.addView(thumbBox, tblp);
        android.widget.ImageView iv = new android.widget.ImageView(this);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.rgb(43, 36, 78)); thumbBg.setCornerRadius(dp(10));
        iv.setBackground(thumbBg);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10)); }
        });
        if (it.thumb != null) iv.setImageBitmap(it.thumb);
        thumbBox.addView(iv, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (it.thumb == null) {
            TextView g = new TextView(this);
            g.setText(it.glyph); g.setTextColor(Color.WHITE); g.setTextSize(22); g.setGravity(Gravity.CENTER);
            thumbBox.addView(g, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (it.durMs > 0) {
            TextView badge = new TextView(this);
            badge.setText(fmtDur(it.durMs / 1000));
            badge.setTextColor(Color.WHITE); badge.setTextSize(10.5f);
            GradientDrawable bb = new GradientDrawable();
            bb.setColor(Color.argb(168, 0, 0, 0)); bb.setCornerRadius(dp(6));
            badge.setBackground(bb);
            badge.setPadding(dp(5), dp(2), dp(5), dp(2));
            android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
            blp.setMargins(0, 0, dp(5), dp(5));
            thumbBox.addView(badge, blp);
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(it.name); t.setTextColor(INK); t.setTextSize(13.5f);
        t.setMaxLines(2); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(t);
        TextView sub = new TextView(this);
        String subTxt = mb(it.size) + (it.durMs > 0 ? "  \u00b7  " + fmtDur(it.durMs / 1000) : "") + (it.safUri != null ? "  \u00b7  custom folder" : "");
        sub.setText(subTxt); sub.setTextColor(GREY); sub.setTextSize(12f);
        sub.setPadding(0, dp(4), 0, 0);
        col.addView(sub);
        card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView dots = new TextView(this);
        dots.setText("\u22ee"); dots.setTextColor(GREY); dots.setTextSize(19); dots.setGravity(Gravity.CENTER);
        dots.setPadding(dp(8), dp(4), dp(2), dp(4));
        dots.setOnClickListener(v -> showFileMenu(it, card, dots));
        card.addView(dots);
        card.setOnClickListener(v -> openFileItem(it, card));
        return card;
    }

    /** Type-specific MediaStore Uri (Video/Audio/Images collection):
     *  gallery and player apps accept these directly, unlike the generic
     *  Files collection Uri, which they refuse ("No app can open"). */
    private Uri mediaUriFor(FileItem it) {
        String mime = it.mime == null ? "" : it.mime;
        Uri coll = mime.startsWith("video") ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : mime.startsWith("audio") ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                : mime.startsWith("image") ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Files.getContentUri("external");
        return ContentUris.withAppendedId(coll, it.mediaId);
    }

    private void openUri(Uri u, String mime) {
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(u, mime == null || mime.isEmpty() ? "*/*" : mime);
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(open); }
        catch (Throwable e) {
            try { startActivity(Intent.createChooser(open, "Open with")); }
            catch (Throwable e2) { Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show(); }
        }
    }

    /** Finds a MediaStore twin for a working-folder file, by clean name. */
    private Object[] findMediaByName(String cleanName) {
        String base = cleanName.contains(".") ? cleanName.substring(0, cleanName.lastIndexOf('.')) : cleanName;
        Uri[] colls = {MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.EXTERNAL_CONTENT_URI};
        String[] proj = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE};
        for (Uri coll : colls) {
            try {
                Cursor c = getContentResolver().query(coll, proj, MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?", new String[]{"%" + base + "%"}, MediaStore.MediaColumns.DATE_MODIFIED + " DESC");
                if (c != null) {
                    while (c.moveToNext()) {
                        String nm = c.getString(1);
                        if (nm != null && stripJobTag(nm).equals(cleanName)) {
                            long id = c.getLong(0); String mime = c.getString(2);
                            c.close();
                            return new Object[]{ContentUris.withAppendedId(coll, id), mime};
                        }
                    }
                    c.close();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** The user's rule: tap a card to open the file in the gallery/player
     *  if it still exists; if it was deleted, a popup says so. */
    private void openFileItem(final FileItem it, final View card) {
        try {
            if (it.safUri != null) {
                Uri u = Uri.parse(it.safUri);
                boolean exists = false;
                try { Cursor c = getContentResolver().query(u, null, null, null, null); if (c != null) { exists = c.moveToFirst(); c.close(); } } catch (Throwable ignored) {}
                if (!exists) { notExistPopup(card); return; }
                openUri(u, it.mime);
                return;
            }
            if (it.mediaId >= 0) {
                boolean exists = it.dataPath != null && new java.io.File(it.dataPath).exists();
                if (!exists) {
                    try { Cursor c = getContentResolver().query(ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), it.mediaId), null, null, null, null); if (c != null) { exists = c.moveToFirst(); c.close(); } } catch (Throwable ignored) {}
                }
                if (!exists) { notExistPopup(card); return; }
                openUri(mediaUriFor(it), it.mime);
                return;
            }
            if (it.file != null) {
                if (!it.file.exists()) { notExistPopup(card); return; }
                Object[] twin = findMediaByName(it.name);
                if (twin != null) { openUri((Uri) twin[0], (String) twin[1]); return; }
                Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) { Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show(); }
    }

    private void showFileMenu(final FileItem it, final View card, View anchor) {
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Open");
        pm.getMenu().add(0, 2, 0, "Delete");
        pm.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == 1) { openFileItem(it, card); return true; }
            if (mi.getItemId() == 2) { confirmDelete(it, card); return true; }
            return false;
        });
        pm.show();
    }

    private void confirmDelete(final FileItem it, final View card) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Delete file?")
            .setMessage("\"" + it.name + "\" will be deleted from storage.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (d, w) -> doDelete(it, card))
            .show();
    }

    private void doDelete(final FileItem it, final View card) {
        try {
            boolean gone = false;
            if (it.safUri != null) {
                Uri u = Uri.parse(it.safUri);
                try { gone = android.provider.DocumentsContract.deleteDocument(getContentResolver(), u); } catch (Throwable ignored) {}
                if (gone) removeSafRecord(it.safUri);
            } else if (it.mediaId >= 0) {
                int n = getContentResolver().delete(mediaUriFor(it), null, null);
                gone = n > 0;
                if (gone) deleteWorkingTwin(it.name);
            } else if (it.file != null) {
                gone = it.file.delete();
                Object[] twin = findMediaByName(it.name);
                if (twin != null) { try { getContentResolver().delete((Uri) twin[0], null, null); } catch (Throwable ignored) {} }
            }
            if (gone) {
                if (filesList != null && card.getParent() == filesList) filesList.removeView(card);
                Toast.makeText(this, "Deleted from storage", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Could not delete this file", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) {
            if (e instanceof android.app.RecoverableSecurityException) {
                pendingDeleteItem = it; pendingDeleteCard = card;
                try { startIntentSenderForResult(((android.app.RecoverableSecurityException) e).getUserAction().getActionIntent().getIntentSender(), 733, null, 0, 0, 0); }
                catch (Throwable ignored) { Toast.makeText(this, "Could not delete this file", Toast.LENGTH_SHORT).show(); }
            } else {
                Toast.makeText(this, "Could not delete this file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 733) {
            if (resultCode == RESULT_OK && pendingDeleteCard != null) {
                if (filesList != null && pendingDeleteCard.getParent() == filesList) filesList.removeView(pendingDeleteCard);
                if (pendingDeleteItem != null) deleteWorkingTwin(pendingDeleteItem.name);
                Toast.makeText(this, "Deleted from storage", Toast.LENGTH_SHORT).show();
            }
            pendingDeleteItem = null; pendingDeleteCard = null;
        }
    }

    private void deleteWorkingTwin(String cleanName) {
        try {
            java.io.File dir = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "GrabIt");
            java.io.File[] fs = dir.listFiles();
            if (fs != null) for (java.io.File f : fs) if (stripJobTag(f.getName()).equals(cleanName)) { /*noinspection ResultOfMethodCallIgnored*/ f.delete(); }
        } catch (Throwable ignored) {}
    }

    private void removeSafRecord(String uriStr) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("grabit_prefs", MODE_PRIVATE);
            org.json.JSONArray arr = new org.json.JSONArray(sp.getString("saf_files", "[]"));
            org.json.JSONArray keep = new org.json.JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (!uriStr.equals(o.optString("u"))) keep.put(o);
            }
            sp.edit().putString("saf_files", keep.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private void notExistPopup(final View card) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Not exist")
            .setMessage("Sorry, this file does not exist anymore. It may have been deleted or moved.")
            .setPositiveButton("OK", (d, w) -> { if (card != null && filesList != null && card.getParent() == filesList) filesList.removeView(card); })
            .show();
    }

    private void showDiagnostics() {
        StringBuilder sb = new StringBuilder();
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("grabit_prefs", MODE_PRIVATE);
            String yv = prefs.getString("ytdlp_version", "");
            sb.append("Download engine: yt-dlp ").append(yv.isEmpty() ? "(checking - reopen in a minute)" : yv).append("\n");
            sb.append(SessionVault.statusText(this)).append("\n\n");
        } catch (Throwable ignored) {}
        try {
            java.io.File log = new java.io.File(getFilesDir(), "download_diagnostics.log");
            if (log.exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(log));
                ArrayList<String> lines = new ArrayList<>();
                String ln; while ((ln = br.readLine()) != null) lines.add(ln);
                br.close();
                for (int i = Math.max(0, lines.size() - 8); i < lines.size(); i++) sb.append(lines.get(i)).append("\n\n");
            }
        } catch (Throwable ignored) {}
        if (sb.length() == 0) sb.append("No download diagnostics yet.");
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
            .setTitle("Download diagnostics")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null);
        final boolean connected = SessionVault.hasSession(this);
        b.setNeutralButton(connected ? "Reconnect Instagram" : "Connect Instagram (optional)",
            (d, w) -> startActivity(new android.content.Intent(this, LoginActivity.class)));
        if (connected) b.setNegativeButton("Disconnect", (d, w) -> {
            SessionVault.clear(this);
            Toast.makeText(this, "Instagram session removed", Toast.LENGTH_SHORT).show();
        });
        b.show();
    }

    // ---------- home interactions ----------
    /** Clear chip in the link box (his reference mock): empties the
     *  link and resets the fetched formats back to the ready state. */
    private void clearLinkBox() {
        urlInput.setText("");
        formats.clear();
        currentUrl = "";
        renderFormatRows();
        setDlBtnText("\u2b07  Analyze");
        status.setText("Ready. Paste a link \u2014 formats load automatically.");
    }

    /** Responsive rule (2026-11-09): on wide screens (tablets,
     *  foldables, landscape) a page column stops at 600dp and centres,
     *  so cards never stretch edge to edge; phones are untouched. */
    private void capPage(LinearLayout p) {
        if (Ui.widthDp(this) > 600) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(600), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            p.setLayoutParams(lp);
        }
    }

    private void pasteFromClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                if (t != null && t.length() > 0) { urlInput.setText(t.toString()); return; }
            }
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) { Toast.makeText(this, "Could not read the clipboard", Toast.LENGTH_SHORT).show(); }
    }
    /** Tapping a platform logo opens that platform's app (its website
     *  when the app is not installed). */
    private void openPlatform(String pkgs, String web, String name) {
        for (String p : pkgs.split("\\|")) {
            try {
                android.content.Intent li = getPackageManager().getLaunchIntentForPackage(p.trim());
                if (li != null) { li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); return; }
            } catch (Throwable ignored) {}
        }
        try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(web))); }
        catch (Throwable e) { Toast.makeText(this, name + " is not installed", Toast.LENGTH_SHORT).show(); }
    }

    private void selectCategory(int idx) {
        selectedCategory = idx;
        styleCategories();
        if (!formats.isEmpty()) renderFormatRows();
    }
    private void styleCategories() {
        String[][] defs = {{"▶", "Video"}, {"♪", "Audio (MP3)"}};
        for (int i = 0; i < catCards.length; i++) {
            if (catCards[i] == null) continue;
            boolean sel = i == selectedCategory;
            catCards[i].setBackground(catBg(sel));
            catCards[i].setTextColor(sel ? Color.WHITE : GREY);
            if (catBadges[i] != null) catBadges[i].setVisibility(sel ? View.VISIBLE : View.GONE);
        }
    }
    private int pickIndexForCategory(ArrayList<Fmt> found, int cat) {
        if (cat == 1) { // Audio: MP3 Max first, else first audio
            for (int i = 0; i < found.size(); i++) if (found.get(i).id.equals("bestaudio/best")) return i;
            for (int i = 0; i < found.size(); i++) if (found.get(i).kind.startsWith("Audio")) return i;
        } else if (cat == 0) { // Video: Best quality first, else first video
            for (int i = 0; i < found.size(); i++) if (found.get(i).id.equals("bestvideo+bestaudio/best")) return i;
            for (int i = 0; i < found.size(); i++) if (found.get(i).kind.contains("Video")) return i;
        }
        return found.isEmpty() ? -1 : 0;
    }
    private void onDownloadPressed() {
        // Downloads queue in the service (Settings > Multiple downloads), so starting while another runs is fine.
        if (formats.isEmpty()) {
            downloadAfterFetch = true;
            fetchFormats();
            if (formats.isEmpty()) return; // fetch started (or invalid link toast already shown)
        }
        downloadSelected();
    }

    /** Same audio rule as the popup: only 129 / 135 kbps rows, plus the
     *  320 kbps MP3 Max. Audio without a kbps label is left alone. */
    private boolean audioVisible(Fmt fm) {
        if (!fm.kind.equals("Audio") || fm.id.equals("bestaudio/best")) return true;
        if (!fm.quality.contains("kbps")) return true;
        try {
            int kb = Integer.parseInt(fm.quality.replace("kbps", "").trim());
            return kb == 129 || kb == 135;
        } catch (Exception e) { return true; }
    }

    private void styleRows() {
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            rb.setBackground(rowBg(rb.isChecked()));
        }
    }
    private void setProgressUi(int pct) {
        barFill.post(() -> {
            int w = barTrack.getWidth();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) barFill.getLayoutParams();
            lp.width = (int) (w * pct / 100.0);
            barFill.setLayoutParams(lp);
        });
    }
    private void pollState() {
        handler.postDelayed(() -> {
            // Self-heal: a wedged busy flag (stuck job, no live jobs, stale state) must not freeze the UI.
            if (DlState.busy && !DownloadService.anyActive() && System.currentTimeMillis() - DlState.stamp > 15000) {
                DlState.set(false, 0, "Ready. Paste a link \u2014 formats load automatically.");
            }
            if (DlState.stamp != shownStamp) {
                shownStamp = DlState.stamp;
                if (!DlState.status.isEmpty()) status.setText(DlState.status);
                if (!DlState.busy && DlState.progress == 100) setProgressUi(100);
                if (!DlState.busy) dlBtn.setEnabled(true);
            }
            if (DlState.busy) {
                setProgressUi(DlState.progress);
                dlBtn.setEnabled(false);
            }
            if (fabDl != null) fabDl.setEnabled(dlBtn.isEnabled());
            updateLiveCard();
            pollState();
        }, 700);
    }

    // ---------- format fetching ----------
    private String lastAutoUrl = "";
    private final Runnable autoFetchRunnable = () -> {
        String txt = urlInput.getText().toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(txt);
        if (m.find()) {
            String found = m.group();
            if (!found.equals(lastAutoUrl) && !DlState.busy) {
                lastAutoUrl = found;
                if (!found.equals(txt.trim())) urlInput.setText(found);
                fetchFormats();
            }
        }
    };

    private void fetchFormats() {
        String url = urlInput.getText().toString().trim();
        if (!url.startsWith("http")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(url);
            if (m.find()) url = m.group();
        }
        if (url.isEmpty() || !url.startsWith("http")) {
            downloadAfterFetch = false;
            Toast.makeText(this, "Paste a valid link first", Toast.LENGTH_SHORT).show(); return;
        }
        // Block only while a download is REALLY active. If the busy flag was left wedged by a stuck
        // job (no active jobs and no state updates for a while), clear it and carry on instead of
        // staying dead forever (the "Cancelling…" freeze the user hit).
        if (DlState.busy && (DownloadService.anyActive() || System.currentTimeMillis() - DlState.stamp < 15000)) { downloadAfterFetch = false; Toast.makeText(this, "A download is running — wait for it to finish", Toast.LENGTH_SHORT).show(); return; }
        if (DlState.busy) DlState.set(false, 0, "Ready. Paste a link — formats load automatically.");
        currentUrl = url;
        lastAutoUrl = url;
        dlBtn.setEnabled(false);
        group.removeAllViews(); formats.clear(); resultCard.setVisibility(View.GONE);
        setProgressUi(0);
        status.setText("Fetching all available formats…");
        final String u = url;
        final boolean wm = noWmSwitch.isChecked();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Fetcher.Result r = Fetcher.fetch(this, u, wm);
                runOnUiThread(() -> showFormats(r));
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    downloadAfterFetch = false;
                    status.setText("Could not fetch formats: " + shortMsg(e.getMessage()));
                    dlBtn.setEnabled(true);
                });
            }
        });
    }

    private static String fmtDur(long sec) {
        if (sec <= 0) return "";
        long h = sec / 3600, m = (sec % 3600) / 60, sec2 = sec % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec2)
                     : String.format(java.util.Locale.US, "%d:%02d", m, sec2);
    }

    private static String sourceNameFor(String u) {
        String l = u == null ? "" : u.toLowerCase();
        if (l.contains("youtube") || l.contains("youtu.be")) return "YouTube";
        if (l.contains("tiktok")) return "TikTok";
        if (l.contains("instagram")) return "Instagram";
        if (l.contains("facebook") || l.contains("fb.")) return "Facebook";
        if (l.contains("pinterest")) return "Pinterest";
        if (l.contains("vimeo")) return "Vimeo";
        if (l.contains("twitter") || l.contains("x.com")) return "X";
        return "Video";
    }

    private void loadResultThumb(final String thumbUrl) {
        if (thumbUrl == null || thumbUrl.isEmpty()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(thumbUrl).openConnection();
                c.setConnectTimeout(6000); c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
                java.io.InputStream in = c.getInputStream();
                final android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                if (bm != null) runOnUiThread(() -> { if (!isFinishing()) resultThumb.setImageBitmap(bm); });
            } catch (Throwable ignored) {}
        });
    }

    private boolean lastTiktok = false;
    private boolean lastViaEngine = false;

    /** Category filter (the user's request): the Video card shows only
     *  video formats, the Audio card shows only audio formats. */
    private boolean categoryMatch(Fmt fm) {
        if (selectedCategory == 1) return fm.kind.startsWith("Audio");
        return fm.kind.contains("Video");
    }

    /** Rebuilds the format rows for the selected category only. If the
     *  link has nothing in that category (e.g. a photo post), all formats
     *  are shown so the list is never empty. */
    private void renderFormatRows() {
        group.removeAllViews();
        boolean anyMatch = false;
        for (Fmt fm : formats) if (audioVisible(fm) && categoryMatch(fm)) { anyMatch = true; break; }
        int shown = 0;
        for (int i = 0; i < formats.size(); i++) {
            Fmt fm = formats.get(i);
            if (!audioVisible(fm)) continue;
            if (anyMatch && !categoryMatch(fm)) continue;
            shown++;
            RadioButton rb = new RadioButton(this);
            String label = Fetcher.displayQuality(fm.quality) + "  \u00b7  " + fm.kind + "  \u00b7  " + fm.ext.toUpperCase() + "  \u00b7  " + Fetcher.displaySize(fm.size);
            if (lastTiktok && fm.kind.contains("Video") && !fm.watermarked) label += "  \u00b7  No watermark";
            if (fm.watermarked) label += "  \u00b7  Watermark";
            rb.setText(label);
            rb.setTextSize(13); rb.setTextColor(INK);
            if (Build.VERSION.SDK_INT >= 21) rb.setButtonTintList(ColorStateList.valueOf(VIOLET));
            rb.setPadding(dp(12), dp(11), dp(12), dp(11));
            rb.setGravity(Gravity.CENTER_VERTICAL);
            rb.setId(i);
            RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            group.addView(rb, lp);
        }
        int pick = pickIndexForCategory(formats, selectedCategory);
        if (pick >= 0) {
            for (int c = 0; c < group.getChildCount(); c++) {
                View ch = group.getChildAt(c);
                if (ch.getId() == pick) { ((RadioButton) ch).setChecked(true); break; }
            }
        }
        styleRows();
        status.setText(formats.isEmpty() ? "No downloadable formats found for this link." : "");
        fabReady = shown > 0;
        if (fabDl != null) fabDl.setVisibility(fabReady && currentTab == 0 ? View.VISIBLE : View.GONE);
    }

    private void showFormats(Fetcher.Result r) {
        ArrayList<Fmt> found = r.formats;
        boolean tiktok = r.tiktok;
        String title = r.title;
        currentTitle = cleanName(title);
        formats.clear(); formats.addAll(found);
        if (emptyState != null) emptyState.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
        if (found.isEmpty()) {
            resultCard.setVisibility(View.GONE);
        } else {
            resultCard.setVisibility(View.VISIBLE);
            resultTitle.setText(title);
            String dur = fmtDur(r.durationSec);
            resultSub.setText(sourceNameFor(currentUrl) + (dur.isEmpty() ? "" : "  \u00b7  " + dur));
            resultDur.setText(dur);
            resultDur.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
            resultThumb.setImageBitmap(null);
            loadResultThumb(r.thumbnail);
        }
        lastTiktok = tiktok;
        lastViaEngine = r.viaEngine;
        renderFormatRows();
        dlBtn.setEnabled(!found.isEmpty());
        setDlBtnText(found.isEmpty() ? "\u2b07  Analyze" : "\u2b07  Download");
        if (downloadAfterFetch) {
            downloadAfterFetch = false;
            if (!found.isEmpty()) downloadSelected();
        }
    }

    // ---------- start background download ----------
    private void downloadSelected() {
        int idx = -1;
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            if (rb.isChecked()) { idx = rb.getId(); break; }
        }
        if (idx < 0 || idx >= formats.size()) { Toast.makeText(this, "Select a format first", Toast.LENGTH_SHORT).show(); return; }
        // Downloads queue in the service (Settings > Multiple downloads), so starting while another runs is fine.
        Fmt fm = formats.get(idx);
        Intent svc = new Intent(this, DownloadService.class);
        svc.putExtra("url", currentUrl);
        svc.putExtra("formatId", fm.id);
        svc.putExtra("kind", fm.kind);
        svc.putExtra("quality", fm.quality);
        svc.putExtra("title", currentTitle);
        svc.putExtra("ext", fm.ext);
        if (fm.imageUrls != null) {
            svc.putExtra("mode", "images");
            svc.putStringArrayListExtra("images", fm.imageUrls);
        } else if (fm.directUrl != null && fm.httpUa != null) {
            svc.putExtra("mode", "direct2");
            svc.putExtra("directUrl", fm.directUrl);
            svc.putExtra("httpUa", fm.httpUa);
            svc.putExtra("container", fm.container);
            if (fm.audioUrl != null) svc.putExtra("audioUrl", fm.audioUrl);
        } else if (fm.directUrl != null) {
            svc.putExtra("mode", "direct");
            svc.putExtra("directUrl", fm.directUrl);
        } else {
            svc.putExtra("mode", "ytdlp");
        }
        setProgressUi(0);
        dlBtn.setEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        status.setText("Downloading in the background — you can close the app now. Watch the notification for progress.");
        Toast.makeText(this, "Download started in background", Toast.LENGTH_SHORT).show();
    }

    /** v78 (owner order): right after the notification permission
     *  prompt resolves, ask for the BACKGROUND-RUNNING permission the
     *  same system-dialog way - he showed the vivo notification prompt
     *  ("Zynox wants to send you notifications" / Allow) and asked for
     *  the background one "eitar moto" (like this one). Android has no
     *  runtime permission for this; the system request below is the
     *  equivalent dialog. The Settings > Background running row stays
     *  as the manual route. */
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 7) handler.postDelayed(this::maybePromptBackground, 800);
    }

    private void maybePromptBackground() {
        if (battPromptShown || isFinishing()) return;
        battPromptShown = true;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) return; // already allowed - never nag
            Intent bi = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            bi.setData(Uri.parse("package:" + getPackageName()));
            startActivity(bi);
        } catch (Throwable e) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Throwable ignored) {}
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!Ui.themeName(this).equals(builtTheme)) { recreate(); return; }
        refreshResumeButton();
    }


    private void refreshResumeButton() {
        if (resumeBtn == null) return;
        org.json.JSONObject p = AudioEngine.readPending(this);
        if (p != null) {
            resumeBtn.setText("Resume paused download — " + p.optString("title", "Audio"));
            resumeBtn.setVisibility(View.VISIBLE);
        } else {
            resumeBtn.setVisibility(View.GONE);
        }
    }
}
