package com.shadhinkhan.grabit;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.Executors;

/** Bottom-sheet popup shown when the user shares a link to GrabIt from another app. */
public class ShareActivity extends Activity {
    private static int INK = Color.rgb(245, 245, 247);
    private static int GREY = Color.rgb(156, 163, 175);
    private static int CARD = Color.rgb(23, 23, 31);
    private static int STROKE = Color.rgb(38, 38, 48);
    private static int SHEET = Color.rgb(15, 15, 21);
    private static int CHIPINK = Color.rgb(205, 200, 225);
    private boolean lightTheme = false;

    private void applyTheme() {
        lightTheme = Ui.isLight(this);
        if (lightTheme) {
            INK = Color.rgb(24, 21, 33); GREY = Color.rgb(94, 88, 106);
            CARD = Color.rgb(255, 255, 255); STROKE = Color.argb(30, 52, 40, 110);
            SHEET = Color.rgb(247, 246, 250); CHIPINK = Color.rgb(62, 54, 92);
        }
    }
    private static final int[] GRAD = new int[]{Color.rgb(167, 139, 250), Color.rgb(139, 92, 246), Color.rgb(109, 40, 217)};

    private TextView status;
    private LinearLayout listBox, cardBox;
    private android.widget.ImageView cardThumb;
    private TextView cardTitle, cardSub, cardDur;
    private Button dlBtn;
    private String url = "";
    private String cleanTitle = "download";
    private Fmt selectedFmt = null;
    private final ArrayList<Row> rows = new ArrayList<>();
    private final android.widget.FrameLayout[] quickCards = new android.widget.FrameLayout[3];
    private final Fmt[] quickFmts = new Fmt[3];
    private final TextView[] quickBadges = new TextView[3];
    private final TextView[] quickPills = new TextView[3];

    private static class Row {
        LinearLayout box; RadioButton radio; Fmt fmt; TextView chip;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable gradientBg(int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, GRAD);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }
    private GradientDrawable rowBg(boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(selected ? Color.argb(38, 139, 92, 246) : CARD);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), selected ? Color.rgb(139, 92, 246) : STROKE);
        return g;
    }
    private String cleanName(String s) {
        String c = s.replaceAll("[^\\w\\- ]", "").trim();
        return c.length() > 70 ? c.substring(0, 70).trim() : (c.isEmpty() ? "download" : c);
    }
    private GradientDrawable chipBg(boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(selected ? Color.rgb(124, 58, 237) : Color.rgb(52, 48, 66));
        g.setCornerRadius(dp(8));
        return g;
    }
    private GradientDrawable iconBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(11));
        return g;
    }
    private static String fmtDur(long sec) {
        if (sec <= 0) return "";
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
                     : String.format(java.util.Locale.US, "%d:%02d", m, s);
    }
    private String sourceName() {
        String u = url.toLowerCase();
        if (u.contains("youtube") || u.contains("youtu.be")) return "YouTube";
        if (u.contains("tiktok")) return "TikTok";
        if (u.contains("instagram")) return "Instagram";
        if (u.contains("facebook") || u.contains("fb.")) return "Facebook";
        if (u.contains("pinterest")) return "Pinterest";
        if (u.contains("vimeo")) return "Vimeo";
        if (u.contains("twitter") || u.contains("x.com")) return "X";
        return "Video";
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        applyTheme();

        Intent in = getIntent();
        String shared = in != null ? in.getStringExtra(Intent.EXTRA_TEXT) : null;
        if (shared != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(shared);
            if (m.find()) url = m.group();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);

        // Header (reference design): violet download icon + title + close chip
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(18), dp(15), dp(14), dp(2));
        TextView dlIcon = new TextView(this);
        dlIcon.setText("⬇");
        dlIcon.setTextSize(16); dlIcon.setTypeface(Typeface.DEFAULT_BOLD); dlIcon.setTextColor(Color.WHITE);
        dlIcon.setGravity(Gravity.CENTER);
        dlIcon.setBackground(iconBg(Color.rgb(124, 58, 237)));
        head.addView(dlIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout headTexts = new LinearLayout(this);
        headTexts.setOrientation(LinearLayout.VERTICAL);
        headTexts.setPadding(dp(12), 0, dp(8), 0);
        TextView hTitle = new TextView(this);
        hTitle.setText("Download");
        hTitle.setTextSize(17.5f); hTitle.setTypeface(Typeface.DEFAULT_BOLD); hTitle.setTextColor(INK);
        headTexts.addView(hTitle);
        TextView hSub = new TextView(this);
        hSub.setText("Choose format and quality");
        hSub.setTextSize(12f); hSub.setTextColor(GREY);
        headTexts.addView(hSub);
        head.addView(headTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(13); close.setTextColor(GREY);
        close.setGravity(Gravity.CENTER);
        close.setBackground(iconBg(lightTheme ? Color.rgb(232, 230, 240) : Color.rgb(32, 32, 42)));
        close.setOnClickListener(v -> finish());
        head.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        root.addView(head);

        // Video card: thumbnail + duration badge + title + source (filled after fetch)
        cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.HORIZONTAL);
        cardBox.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(CARD); cardBg.setCornerRadius(dp(18)); cardBg.setStroke(dp(1), STROKE);
        cardBox.setBackground(cardBg);
        cardBox.setPadding(dp(8), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbp.setMargins(dp(14), dp(10), dp(14), 0);
        cardBox.setVisibility(View.GONE);
        root.addView(cardBox, cbp);
        android.widget.FrameLayout thumbWrap = new android.widget.FrameLayout(this);
        cardThumb = new android.widget.ImageView(this);
        cardThumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        cardThumb.setBackgroundColor(lightTheme ? Color.rgb(233, 230, 242) : Color.rgb(38, 32, 54));
        cardThumb.setClipToOutline(true);
        cardThumb.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12)); }
        });
        thumbWrap.addView(cardThumb, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cardDur = new TextView(this);
        cardDur.setTextSize(10f); cardDur.setTextColor(Color.WHITE); cardDur.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable durBg = new GradientDrawable();
        durBg.setColor(Color.argb(185, 0, 0, 0)); durBg.setCornerRadius(dp(6));
        cardDur.setBackground(durBg);
        cardDur.setPadding(dp(6), dp(2), dp(6), dp(2));
        android.widget.FrameLayout.LayoutParams durLp = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        durLp.setMargins(0, 0, dp(6), dp(6));
        thumbWrap.addView(cardDur, durLp);
        cardBox.addView(thumbWrap, new LinearLayout.LayoutParams(dp(102), dp(57)));
        LinearLayout cardTexts = new LinearLayout(this);
        cardTexts.setOrientation(LinearLayout.VERTICAL);
        cardTexts.setPadding(dp(12), 0, 0, 0);
        cardTitle = new TextView(this);
        cardTitle.setTextSize(13.5f); cardTitle.setTextColor(INK);
        cardTitle.setMaxLines(2); cardTitle.setEllipsize(TextUtils.TruncateAt.END);
        cardTexts.addView(cardTitle);
        cardSub = new TextView(this);
        cardSub.setTextSize(11.5f); cardSub.setTextColor(GREY);
        cardSub.setPadding(0, dp(5), 0, 0);
        cardTexts.addView(cardSub);
        cardBox.addView(cardTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        status = new TextView(this);
        status.setText("Fetching formats…");
        status.setTextSize(13f); status.setTextColor(GREY);
        status.setPadding(dp(22), dp(2), dp(22), dp(6));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(14), dp(2), dp(14), dp(6));
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        dlBtn = new Button(this);
        dlBtn.setText("Download");
        dlBtn.setTextColor(Color.WHITE);
        GradientDrawable gradMask = new GradientDrawable(); gradMask.setColor(Color.WHITE); gradMask.setCornerRadius(dp(16));
        dlBtn.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(55, 255, 255, 255)), gradientBg(16), gradMask));
        dlBtn.setTextSize(14.5f); dlBtn.setTypeface(Typeface.DEFAULT_BOLD);
        dlBtn.setEnabled(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        dlp.setMargins(dp(16), dp(10), dp(16), dp(18));
        root.addView(dlBtn, dlp);

        setContentView(root);
        // Rounded-sheet look: no top strip, soft rounded top corners (user request).
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(SHEET);
        sheetBg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        getWindow().setBackgroundDrawable(sheetBg);
        WindowManager.LayoutParams wp = getWindow().getAttributes();
        wp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.82);
        wp.gravity = Gravity.BOTTOM;
        getWindow().setAttributes(wp);

        if (url.isEmpty()) {
            status.setText("No link found in the shared text.");
            return;
        }
        dlBtn.setOnClickListener(v -> startDownload());
        DlState.ensureEngineAsync(this); // warm the engine while the user picks a format
        final long fetchStart = System.currentTimeMillis();
        final android.os.Handler fh = new android.os.Handler();
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!isFinishing() && !dlBtn.isEnabled()) {
                long sec = (System.currentTimeMillis() - fetchStart) / 1000;
                if (sec >= 2 && status.getText().toString().startsWith("Fetching")) {
                    status.setText("Fetching formats… " + sec + "s");
                }
                fh.postDelayed(tick[0], 1000);
            }
        };
        fh.postDelayed(tick[0], 1000);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Fetcher.Result r = Fetcher.fetch(this, url, true);
                runOnUiThread(() -> showResult(r));
            } catch (Throwable e) {
                runOnUiThread(() -> status.setText("Could not fetch formats: " + shortMsg(e.getMessage())));
            }
        });
    }

    private View sectionRow(String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = section(left);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView r = new TextView(this);
        r.setText(right);
        r.setTextSize(11f); r.setTextColor(Color.rgb(120, 120, 135));
        r.setPadding(0, dp(11), dp(6), dp(6));
        row.addView(r);
        return row;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11f); t.setTextColor(Color.rgb(120, 120, 135)); t.setTypeface(Typeface.DEFAULT_BOLD);
        if (Build.VERSION.SDK_INT >= 21) t.setLetterSpacing(0.06f);
        t.setPadding(dp(6), dp(11), dp(6), dp(6));
        return t;
    }

    private RadioButton newRadio() {
        RadioButton rb = new RadioButton(this);
        rb.setClickable(false); rb.setFocusable(false);
        rb.setScaleX(0.85f); rb.setScaleY(0.85f);
        if (Build.VERSION.SDK_INT >= 21) rb.setButtonTintList(ColorStateList.valueOf(Color.rgb(139, 92, 246)));
        return rb;
    }
    private TextView sizeView(String sizeText) {
        TextView size = new TextView(this);
        size.setText(sizeText);
        size.setTextSize(11.5f); size.setTextColor(GREY);
        size.setSingleLine(true);
        return size;
    }
    private void attachRow(LinearLayout row, Fmt fm, TextView chip) {
        Row r = new Row();
        r.box = row; r.radio = (RadioButton) row.getChildAt(0); r.fmt = fm; r.chip = chip;
        rows.add(r);
        row.setOnClickListener(v -> select(fm));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        listBox.addView(row, lp);
    }

    /** Quality row (reference style): radio + quality badge chip + friendly name,
     *  with the format itself (container · kind) on a second line so two rows of
     *  the same quality (e.g. MP4 vs WEBM 1080p) are never confusing twins. */
    private void addQualityRow(Fmt fm, String descriptor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(14), dp(12));
        row.setBackground(rowBg(false));
        row.addView(newRadio());
        TextView chip = new TextView(this);
        chip.setText(Fetcher.displayQuality(fm.quality));
        chip.setTextSize(11.5f); chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(CHIPINK);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(chipBg(false));
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chlp.setMargins(dp(4), 0, dp(10), 0);
        row.addView(chip, chlp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView main = new TextView(this);
        main.setText(descriptor);
        main.setTextSize(12.5f); main.setTextColor(INK);
        main.setSingleLine(true); main.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(main);
        TextView meta = new TextView(this);
        String metaText = fm.ext.toUpperCase() + " · " + (fm.kind.equals("Video only") ? "Video only" : "Video + Audio");
        if (fm.watermarked) metaText += " · Watermark";
        meta.setText(metaText);
        meta.setTextSize(11f); meta.setTextColor(GREY);
        meta.setSingleLine(true);
        meta.setPadding(0, dp(2), 0, 0);
        texts.addView(meta);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(sizeView(Fetcher.displaySize(fm.size)));
        attachRow(row, fm, chip);
    }

    /** Media row (reference style): radio + type logo square + title/subtitle + size. */
    private void addMediaRow(Fmt fm, int logoKind, String title, String sub) {
        // logoKind: 0 audio (violet, note), 1 video (blue, play), 2 photos (teal, image)
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(14), dp(12));
        row.setBackground(rowBg(false));
        row.addView(newRadio());
        TextView logo = new TextView(this);
        logo.setText(logoKind == 0 ? "♪" : logoKind == 1 ? "▶" : "🖼");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(logoKind == 0 ? 15 : 12);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(iconBg(logoKind == 0 ? Color.rgb(124, 58, 237) : logoKind == 1 ? Color.rgb(37, 99, 235) : Color.rgb(13, 148, 136)));
        LinearLayout.LayoutParams logolp = new LinearLayout.LayoutParams(dp(33), dp(33));
        logolp.setMargins(dp(4), 0, dp(10), 0);
        row.addView(logo, logolp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView main = new TextView(this);
        main.setText(title);
        main.setTextSize(13f); main.setTextColor(INK); main.setTypeface(Typeface.DEFAULT_BOLD);
        main.setSingleLine(true); main.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(main);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(11f); s.setTextColor(GREY);
            s.setSingleLine(true); s.setEllipsize(TextUtils.TruncateAt.END);
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(sizeView(Fetcher.displaySize(fm.size)));
        attachRow(row, fm, null);
    }

    private GradientDrawable pillBg(boolean sel) {
        GradientDrawable g;
        if (sel) g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{GRAD[0], GRAD[1]});
        else { g = new GradientDrawable(); g.setColor(lightTheme ? Color.rgb(238, 236, 245) : Color.rgb(38, 38, 46)); }
        g.setCornerRadius(dp(14));
        return g;
    }

    private GradientDrawable quickCardBg(boolean selected) {
        GradientDrawable g;
        if (selected) {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.argb(64, 139, 92, 246), Color.argb(30, 124, 58, 237)});
            g.setStroke(dp(1), Color.rgb(139, 92, 246));
        } else {
            g = new GradientDrawable();
            g.setColor(CARD);
            g.setStroke(dp(1), STROKE);
        }
        g.setCornerRadius(dp(16));
        return g;
    }

    private View quickHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(11), dp(6), dp(7));
        TextView bolt = new TextView(this);
        bolt.setText("⚡");
        bolt.setTextSize(13); bolt.setTextColor(Color.rgb(167, 139, 250));
        row.addView(bolt);
        TextView t = new TextView(this);
        t.setText(" Quick download");
        t.setTextSize(13.5f); t.setTextColor(INK); t.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView pill = new TextView(this);
        pill.setText("One-tap");
        pill.setTextSize(11f); pill.setTextColor(Color.rgb(196, 181, 253));
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(Color.argb(46, 139, 92, 246)); pg.setCornerRadius(dp(99));
        pill.setBackground(pg);
        pill.setPadding(dp(10), dp(4), dp(10), dp(4));
        row.addView(pill);
        return row;
    }

    /** Quick-download card (reference style): icon + big title + small subtitle,
     *  violet highlight and a check badge when it is the current selection. */
    private void addQuickCard(LinearLayout parent, int slot, Fmt fm, int iconKind, String title, String sub, boolean withMargin) {
        android.widget.FrameLayout card = new android.widget.FrameLayout(this);
        card.setBackground(quickCardBg(false));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (withMargin) clp.setMargins(dp(8), 0, 0, 0);
        parent.addView(card, clp);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.setPadding(0, dp(13), 0, dp(11));
        card.addView(inner, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sq = new TextView(this);
        sq.setGravity(Gravity.CENTER);
        if (iconKind == 0)      { sq.setText("♪"); sq.setTextSize(15); sq.setTextColor(Color.rgb(167, 139, 250)); sq.setBackground(iconBg(Color.rgb(42, 33, 64))); }
        else if (iconKind == 1) { sq.setText("▶"); sq.setTextSize(12); sq.setTextColor(Color.WHITE); sq.setBackground(iconBg(Color.rgb(124, 58, 237))); }
        else                    { sq.setText("⚡"); sq.setTextSize(14); sq.setBackground(iconBg(Color.rgb(38, 38, 46))); }
        inner.addView(sq, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView t = new TextView(this);
        t.setText(title); t.setTextSize(14f); t.setTypeface(Typeface.DEFAULT_BOLD); t.setTextColor(INK);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, dp(6), 0, 0);
        inner.addView(t, tlp);
        TextView s = new TextView(this);
        s.setText(sub); s.setTextSize(11.5f); s.setTextColor(GREY); s.setGravity(Gravity.CENTER);
        inner.addView(s);
        TextView pill = new TextView(this);
        String sz = fm.size == null ? "" : fm.size.replace("~", "").trim();
        pill.setText(sz.isEmpty() || sz.equals("?") ? "≈ —" : "≈ " + Fetcher.displaySize(sz));
        pill.setTextSize(11.5f); pill.setTextColor(INK); pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(dp(10), dp(4), dp(10), dp(4));
        pill.setBackground(pillBg(false));
        LinearLayout.LayoutParams plp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp2.setMargins(0, dp(7), 0, 0);
        inner.addView(pill, plp2);
        quickPills[slot] = pill;
        TextView badge = new TextView(this);
        badge.setText("✓"); badge.setTextSize(10); badge.setTypeface(Typeface.DEFAULT_BOLD); badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL); bg.setColor(Color.rgb(139, 92, 246));
        badge.setBackground(bg);
        badge.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(dp(18), dp(18));
        blp.gravity = Gravity.TOP | Gravity.RIGHT;
        blp.setMargins(0, dp(6), dp(6), 0);
        card.addView(badge, blp);
        quickCards[slot] = card; quickFmts[slot] = fm; quickBadges[slot] = badge;
        card.setOnClickListener(v -> select(fm));
    }

    private void select(Fmt fm) {
        selectedFmt = fm;
        for (Row r : rows) {
            boolean sel = r.fmt == fm;
            r.radio.setChecked(sel);
            r.box.setBackground(rowBg(sel));
            if (r.chip != null) {
                r.chip.setBackground(chipBg(sel));
                r.chip.setTextColor(sel ? Color.WHITE : Color.rgb(205, 200, 225));
            }
        }
        for (int i = 0; i < 3; i++) {
            if (quickCards[i] != null && quickFmts[i] != null) {
                boolean sel = quickFmts[i] == fm;
                quickCards[i].setBackground(quickCardBg(sel));
                quickBadges[i].setVisibility(sel ? View.VISIBLE : View.GONE);
                if (quickPills[i] != null) {
                    quickPills[i].setBackground(pillBg(sel));
                    quickPills[i].setTextColor(sel ? Color.WHITE : INK);
                }
            }
        }
    }

    private String label(Fmt fm, boolean tiktok) {
        String s = fm.quality + " · " + fm.kind + " · " + fm.ext.toUpperCase();
        if (tiktok && fm.kind.contains("Video") && !fm.watermarked) s += " · No watermark";
        if (fm.watermarked) s += " · Watermark";
        return s;
    }

    private String descriptor(Fmt f, Fmt topPick) {
        if (f == topPick) return "Best quality (Recommended)";
        int h = parseH(f.quality);
        if (h >= 720) return "High quality";
        if (h >= 480) return "Balanced quality";
        if (h >= 360) return "Data saver";
        if (h > 0) return "Smaller size";
        return f.kind.equals("Video only") ? "Video only" : "Video + Audio";
    }

    private void loadThumb(final String thumbUrl) {
        if (thumbUrl == null || thumbUrl.isEmpty()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(thumbUrl).openConnection();
                c.setConnectTimeout(6000); c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
                java.io.InputStream in = c.getInputStream();
                final android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                if (bm != null) runOnUiThread(() -> { if (!isFinishing()) cardThumb.setImageBitmap(bm); });
            } catch (Throwable ignored) {}
        });
    }

    private void showResult(Fetcher.Result r) {
        cleanTitle = cleanName(r.title);
        listBox.removeAllViews();
        rows.clear();
        for (int i = 0; i < 3; i++) { quickCards[i] = null; quickFmts[i] = null; quickBadges[i] = null; }
        selectedFmt = null;
        if (r.formats.isEmpty()) { status.setText("No downloadable formats found."); return; }

        // Video card (thumbnail + title + source · duration)
        cardBox.setVisibility(View.VISIBLE);
        cardTitle.setText(r.title);
        String dur = fmtDur(r.durationSec);
        cardSub.setText(sourceName() + (dur.isEmpty() ? "" : "  ·  " + dur));
        cardDur.setText(dur);
        cardDur.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
        loadThumb(r.thumbnail);

        Fmt mp3 = null, best = null, fast1080 = null, audioMax = null;
        ArrayList<Fmt> videos = new ArrayList<>(), audios = new ArrayList<>(), photos = new ArrayList<>();
        for (Fmt f : r.formats) {
            if (f.id.equals("bestaudio/best")) audioMax = f;
            else if (f.id.equals("bestvideo+bestaudio/best")) best = f;
            else if (f.kind.equals("Audio")) audios.add(f);
            else if (f.kind.equals("Photos")) photos.add(f);
            else videos.add(f);
            if (f.directUrl != null && f.kind.equals("Audio") && mp3 == null) mp3 = f;
        }
        int bestDist = Integer.MAX_VALUE, maxH = 0;
        for (Fmt f : videos) {
            int hgt = parseH(f.quality);
            if (hgt > maxH) maxH = hgt;
            if (hgt > 0) { int dist = Math.abs(hgt - 1080) * 2 + (f.quality.contains("HDR") ? 1 : 0); if (dist < bestDist) { bestDist = dist; fast1080 = f; } }
        }
        // The single "Recommended" pick among same-height twins: prefer combined, then MP4.
        Fmt topPick = null;
        if (maxH > 0) {
            for (Fmt f : videos) {
                if (parseH(f.quality) != maxH) continue;
                if (topPick == null) { topPick = f; continue; }
                if (topPick.kind.equals("Video only") && !f.kind.equals("Video only")) topPick = f;
                else if (f.kind.equals(topPick.kind) && !topPick.ext.equals("mp4") && f.ext.equals("mp4")) topPick = f;
            }
        }

        // Quick download: three cards side by side (reference design), synced with the lists.
        Fmt quickMp3 = audioMax != null ? audioMax : mp3;
        Fmt quickBest = best != null ? best : (!videos.isEmpty() ? videos.get(0) : null);
        if (quickMp3 != null || quickBest != null || fast1080 != null) {
            listBox.addView(quickHeader());
            LinearLayout cards = new LinearLayout(this);
            cards.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams cplp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cplp.setMargins(0, 0, 0, dp(4));
            listBox.addView(cards, cplp);
            boolean first = true;
            if (quickMp3 != null) { addQuickCard(cards, 0, quickMp3, 0, "MP3", audioMax != null ? "320 kbps" : "Audio", !first); first = false; }
            if (quickBest != null) { addQuickCard(cards, 1, quickBest, 1, "Best", "Video + Audio", !first); first = false; }
            if (fast1080 != null) { addQuickCard(cards, 2, fast1080, 2, "Fast", Fetcher.displayQuality(fast1080.quality), !first); first = false; }
        }

        if (!videos.isEmpty() || !photos.isEmpty()) {
            listBox.addView(sectionRow("Quality", "File size (approx.)"));
            for (Fmt f : videos) addQualityRow(f, descriptor(f, topPick));
            for (Fmt f : photos) addMediaRow(f, 2, f.quality, "Photos");
        }
        java.util.ArrayList<Fmt> audiosShown = new java.util.ArrayList<>();
        for (Fmt f : audios) if (audioRowVisible(f)) audiosShown.add(f);
        if (!audiosShown.isEmpty() || audioMax != null) {
            listBox.addView(section("Other formats"));
            if (audioMax != null) addMediaRow(audioMax, 0, "MP3 (320 kbps)", "Audio only · Highest quality");
            for (Fmt f : audiosShown) addMediaRow(f, 0,
                    f.quality.contains("kbps") ? "MP3 (" + f.quality + ")" : "MP3 Audio",
                    "Audio only");
        }
        select(best != null ? best : (audioMax != null ? audioMax : (mp3 != null ? mp3 : r.formats.get(0))));
        // The "fast streams blocked - backup route" note is never
        // shown (owner order 2026-11-09): the backup route is used
        // silently when needed.
        status.setVisibility(View.GONE);
        dlBtn.setEnabled(true);
    }

    /** User wants only 129 / 135 kbps audio rows (plus the 320 kbps MP3 Max,
     *  which is rendered separately). Audio without a kbps label (e.g. a
     *  TikTok song title) is left alone. */
    private boolean audioRowVisible(Fmt f) {
        if (!f.quality.contains("kbps")) return true;
        try {
            int kb = Integer.parseInt(f.quality.replace("kbps", "").trim());
            return kb == 129 || kb == 135;
        } catch (Exception e) { return true; }
    }

    private int parseH(String q) {
        if (q == null) return 0;
        int v = 0, i = 0;
        while (i < q.length() && Character.isDigit(q.charAt(i))) { v = v * 10 + (q.charAt(i) - '0'); i++; }
        return v;
    }
    private String shortMsg(String m) {
        if (m == null) return "unknown error";
        return m.length() > 300 ? m.substring(m.length() - 300) : m;
    }

    private void startDownload() {
        Fmt fm = selectedFmt;
        if (fm == null) { Toast.makeText(this, "Select a format first", Toast.LENGTH_SHORT).show(); return; }
        if (DlState.busy) { Toast.makeText(this, "Another download is already running", Toast.LENGTH_SHORT).show(); return; }
        Intent svc = new Intent(this, DownloadService.class);
        svc.putExtra("url", url);
        svc.putExtra("formatId", fm.id);
        svc.putExtra("kind", fm.kind);
        svc.putExtra("quality", fm.quality);
        svc.putExtra("title", cleanTitle);
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
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        Toast.makeText(this, "Download starting…", Toast.LENGTH_SHORT).show();
        dlBtn.setEnabled(false);
        status.setVisibility(View.VISIBLE);
        status.setText("Starting download…");
        // Keep the popup (and therefore the app process) in the foreground until the
        // download worker is genuinely active - otherwise the phone may freeze the
        // just-started background download. Engine warm-up can take a while for MP3.
        final android.os.Handler h = new android.os.Handler();
        final long startedAt = System.currentTimeMillis();
        final long[] s0 = {-1};
        final Runnable[] check = new Runnable[1];
        check[0] = () -> {
            long waited = System.currentTimeMillis() - startedAt;
            if (s0[0] < 0 && waited >= 700) s0[0] = DlState.stamp;
            boolean workerActive = s0[0] >= 0 && DlState.stamp != s0[0];
            boolean moving = DlState.busy && DlState.progress > 0;
            if (workerActive || moving || waited > 25000) {
                Toast.makeText(this, "Download running in background — check notification", Toast.LENGTH_LONG).show();
                finish();
            } else {
                if (waited > 5000 && status.getText().toString().startsWith("Starting")) {
                    status.setText("Starting download… warming up — keep this popup open for a few seconds");
                }
                h.postDelayed(check[0], 300);
            }
        };
        h.postDelayed(check[0], 300);
    }
}
