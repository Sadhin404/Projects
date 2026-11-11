package com.shadhinkhan.grabit;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Meet the Developers - the professional team page from the owner's
 *  design (2026-11-09), baked in FIXED: the three members below show
 *  identically on every phone the app is installed on.
 *
 *  HIDDEN owner mode (no visible trace while locked, per his order):
 *  press and HOLD the word "Zynox" in the big title for 10 seconds
 *  and the page unlocks (the earlier 3-finger tap gesture did not
 *  work on his phone and was removed 2026-11-09) - then cards can be
 *  edited (name, role, university
 *  line, bio, photo, and Facebook / Instagram / GitHub / TikTok
 *  links) and new member cards added. Social icons with a saved link
 *  open that person's profile directly. Unlock lasts only while this
 *  page is open. On-device edits/additions persist on this phone;
 *  content baked into the APK is what travels when the app is shared. */
public class DeveloperActivity extends ThemedActivity {
    private static final String OVERLINE = "FCUB • CSE Final Year";
    private static final String TITLE_A = "The Minds";
    private static final String TITLE_B = "Behind Zynox";
    private static final String TAGLINE = "Four developers. One vision. Built with passion and innovation.";
    private static final String UNI = "FCUB • CSE Final Year";
    private static final String[] NAMES = {"Sadhin Khan", "Ali Emam Rabby", "Sristi Sorower", "Meharab Hosen"};
    private static final String[] ROLES = {"Main Developer", "Frontend Developer", "Backend Developer", "Project Manager"};
    private static final String[] BIOS = {
            "Leading the development of Zynox, overseeing architecture, core features, and overall project direction.",
            "Crafting intuitive interfaces and smooth user experiences with a focus on modern design, responsiveness, and performance.",
            "Building and maintaining the backbone of Zynox, focusing on application logic, data management, and reliable services.",
            "Overseeing project planning, coordination, and team collaboration to keep Zynox on track and deliver a high-quality product."};
    private static final String[] ROLE_ICON = {"♛", "</>", "▦", "📋"};
    private static final String[] BADGE = {"</>", "🖥", "☰", "📋"};
    private static final int[] ACCENT = {Color.rgb(139, 92, 246), Color.rgb(56, 150, 255), Color.rgb(236, 72, 153), Color.rgb(245, 158, 11)};
    private static final int[] PHOTO = {R.drawable.dev_m1, R.drawable.dev_m2, R.drawable.dev_m3, R.drawable.dev_m4};
    private static final String[] NETS = {"facebook", "instagram", "github", "tiktok", "linkedin", "mail", "web"};
    private static final Map<String, String> GLYPH = new LinkedHashMap<>();
    static {
        GLYPH.put("facebook", "f"); GLYPH.put("instagram", "📷"); GLYPH.put("github", "GH");
        GLYPH.put("tiktok", "♪"); GLYPH.put("linkedin", "in"); GLYPH.put("mail", "✉"); GLYPH.put("web", "🌐");
    }
    private static final String[] FORM_NETS = {"facebook", "instagram", "github", "tiktok"};
    // his real profiles, baked in (2026-11-09): row order Instagram,
    // Facebook, TikTok; each icon opens the profile on tap
    private static final String[][] DEF_LINKS = {
            {"https://www.facebook.com/share/1GRMfN9pwh/", "sadhin.404", "sadvibess_999"},
            {"https://www.facebook.com/share/1E3GyCanz2/", "ali.emam.rabbi", "rabbi_10k"},
            {"https://www.facebook.com/share/1C8fXfc2j3/", "sristisorower", "sristisorower0"},
            {"https://www.facebook.com/share/1A4nXS2xHP/", "hosen.meharab", "!"}};
    private static final String[] DISP_ORDER = {"instagram", "facebook", "tiktok", "github", "linkedin", "mail", "web"};
    private static String defLink(int i, String net) {
        if (i < 0 || i >= DEF_LINKS.length) return "";
        if (net.equals("facebook")) return DEF_LINKS[i][0];
        if (net.equals("instagram")) return DEF_LINKS[i][1];
        if (net.equals("tiktok")) return DEF_LINKS[i][2];
        return "";
    }
    private static final String[] FORM_NET_LABEL = {"Facebook", "Instagram", "GitHub", "TikTok"};

    private static class Member {
        String key; boolean added;
        String name, role, uni, bio;
        int photoRes; String photoPath;
        int accentIdx;
        Map<String, String> links = new LinkedHashMap<>();
    }

    private SharedPreferences prefs;
    private final List<Member> members = new ArrayList<>();
    private boolean unlocked = false;
    private String pickKey = null;
    private Runnable unlockRun;
    private float holdX, holdY;

    private String gp(String k, String def) {
        String v = prefs.getString(k, "");
        return v == null || v.isEmpty() ? def : v;
    }

    private void loadMembers() {
        members.clear();
        for (int i = 0; i < NAMES.length; i++) {
            Member m = new Member();
            m.key = "b" + i; m.added = false; m.accentIdx = i;
            m.name = gp("zx_ovr_name_" + m.key, NAMES[i]);
            m.role = gp("zx_ovr_role_" + m.key, ROLES[i]);
            m.uni = gp("zx_ovr_uni_" + m.key, UNI);
            m.bio = gp("zx_ovr_bio_" + m.key, BIOS[i]);
            m.photoRes = PHOTO[i];
            m.photoPath = prefs.getString("zx_photo_" + m.key, "");
            for (String n : NETS) {
                String k = "zx_link_" + n + "_" + m.key;
                m.links.put(n, prefs.contains(k) ? prefs.getString(k, "") : defLink(i, n));
            }
            members.add(m);
        }
        int count = prefs.getInt("zx_count", 0);
        for (int j = 0; j < count; j++) {
            Member m = new Member();
            m.key = "a" + j; m.added = true; m.accentIdx = (NAMES.length + j) % ACCENT.length;
            m.name = gp("zx_add_name_" + j, "Member");
            m.role = gp("zx_add_role_" + j, "");
            m.uni = gp("zx_add_uni_" + j, UNI);
            m.bio = gp("zx_add_bio_" + j, "");
            m.photoRes = 0;
            m.photoPath = prefs.getString("zx_photo_" + m.key, "");
            for (String n : NETS) m.links.put(n, prefs.getString("zx_link_" + n + "_" + m.key, ""));
            members.add(m);
        }
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        initTheme();
        prefs = getSharedPreferences("dev_about", MODE_PRIVATE);
        loadMembers();
        LinearLayout page = chrome("Meet the Developers");

        TextView over = new TextView(this);
        over.setText(OVERLINE);
        over.setTextColor(GREY); over.setTextSize(12); over.setGravity(Gravity.CENTER);
        try { over.setLetterSpacing(0.28f); } catch (Throwable ignored) {}
        over.setPadding(0, dp(8), 0, 0);
        page.addView(over);

        final TextView logo = new TextView(this);
        String title = TITLE_A + "\n" + TITLE_B;
        SpannableString ss = new SpannableString(title);
        ss.setSpan(new ForegroundColorSpan(VIOLET), TITLE_A.length() + 1, title.length(), 0);
        logo.setText(ss);
        logo.setTextColor(INK); logo.setTextSize(44); logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setLineSpacing(0, 1.04f);
        page.addView(logo);
        // hidden unlock: press and hold the word "Zynox" in the title
        // for 10 seconds (no visible hint; a short buzz confirms it)
        unlockRun = () -> {
            if (!unlocked) {
                unlocked = true;
                try { ((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(50); } catch (Throwable ignored) {}
                Toast.makeText(this, "Edit mode on", Toast.LENGTH_SHORT).show();
                recreate();
            }
        };
        logo.setOnTouchListener((v, e) -> {
            if (unlocked) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (onZynoxWord(logo, e)) {
                        holdX = e.getX(); holdY = e.getY();
                        logo.postDelayed(unlockRun, 10000);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - holdX) > dp(24) || Math.abs(e.getY() - holdY) > dp(24)) logo.removeCallbacks(unlockRun);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    logo.removeCallbacks(unlockRun);
                    return true;
                default:
                    return false;
            }
        });

        TextView tag = new TextView(this);
        tag.setText(TAGLINE);
        tag.setTextColor(GREY); tag.setTextSize(13); tag.setGravity(Gravity.CENTER);
        tag.setPadding(dp(30), dp(2), dp(30), 0);
        page.addView(tag);

        if (unlocked) {
            LinearLayout tools = new LinearLayout(this);
            tools.setOrientation(LinearLayout.HORIZONTAL);
            tools.setGravity(Gravity.CENTER);
            tools.setPadding(0, dp(12), 0, 0);
            TextView add = new TextView(this);
            add.setText("＋ Add member");
            add.setTextColor(Color.WHITE); add.setTextSize(12.5f); add.setTypeface(Typeface.DEFAULT_BOLD);
            add.setBackground(violetBg(20));
            add.setPadding(dp(14), dp(7), dp(14), dp(7));
            add.setOnClickListener(v -> openEditDialog(-1));
            tools.addView(add);
            TextView done = new TextView(this);
            done.setText("Done");
            done.setTextColor(GREY); done.setTextSize(12.5f);
            done.setBackground(chipBg(false));
            done.setPadding(dp(14), dp(7), dp(14), dp(7));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.setMargins(dp(8), 0, 0, 0);
            done.setLayoutParams(dlp);
            done.setOnClickListener(v -> { unlocked = false; recreate(); });
            tools.addView(done);
            page.addView(tools);
        }

        for (int i = 0; i < members.size(); i++) page.addView(memberCard(members.get(i), i));
    }

    private LinearLayout memberCard(final Member m, final int idx) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(cardBg());
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(clp);
        if (unlocked) card.setOnClickListener(v -> openEditDialog(idx));

        FrameLayout photoWrap = new FrameLayout(this);
        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setClipToOutline(true);
        photo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14)); }
        });
        Bitmap pb = loadPhotoFile(m.photoPath);
        if (pb != null) photo.setImageBitmap(pb);
        else if (m.photoRes != 0) photo.setImageResource(m.photoRes);
        photoWrap.addView(photo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView badge = new TextView(this);
        badge.setText(m.added ? "★" : BADGE[m.accentIdx]); badge.setTextColor(Color.WHITE); badge.setTextSize(11); badge.setGravity(Gravity.CENTER);
        GradientDrawable bd = new GradientDrawable();
        bd.setShape(GradientDrawable.OVAL); bd.setColor(ACCENT[m.accentIdx]);
        badge.setBackground(bd);
        FrameLayout.LayoutParams bdp = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END);
        bdp.setMargins(0, dp(5), dp(5), 0);
        photoWrap.addView(badge, bdp);
        boolean narrowScr = Ui.widthDp(this) < 340;
        card.addView(photoWrap, new LinearLayout.LayoutParams(dp(narrowScr ? 84 : 94), dp(narrowScr ? 104 : 116)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        colp.setMargins(dp(12), 0, 0, 0);
        card.addView(col, colp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(m.name);
        name.setTextColor(INK); name.setTextSize(16.5f); name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (unlocked) {
            TextView edit = new TextView(this);
            edit.setText("✎ Edit"); edit.setTextColor(GREY); edit.setTextSize(12);
            edit.setPadding(dp(6), dp(2), 0, dp(2));
            edit.setOnClickListener(v -> openEditDialog(idx));
            topRow.addView(edit);
        }
        col.addView(topRow);

        TextView role = new TextView(this);
        role.setText((m.added ? "✦" : ROLE_ICON[m.accentIdx]) + "  " + m.role);
        role.setTextColor(Color.WHITE); role.setTextSize(10.5f); role.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable rp = new GradientDrawable();
        rp.setColor(ACCENT[m.accentIdx]); rp.setCornerRadius(dp(20));
        role.setBackground(rp);
        role.setPadding(dp(9), dp(4), dp(9), dp(4));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, dp(6), 0, 0);
        role.setLayoutParams(rlp);
        col.addView(role);

        if (m.uni != null && !m.uni.isEmpty()) {
            TextView uni = new TextView(this);
            uni.setText("🎓 " + m.uni);
            uni.setTextColor(GREY); uni.setTextSize(11.5f);
            uni.setPadding(0, dp(6), 0, 0);
            col.addView(uni);
        }

        TextView bio = new TextView(this);
        bio.setText(m.bio);
        bio.setTextColor(GREY); bio.setTextSize(12f);
        bio.setPadding(0, dp(6), 0, 0);
        col.addView(bio);

        col.addView(socialRow(m));
        return card;
    }

    /** Social icons: the owner's real brand logos (Instagram,
     *  Facebook, TikTok) as images inside glass circles, spread
     *  across the card like his reference. A saved link opens the
     *  profile on tap; the value "!" shows the icon with no action
     *  (Meharab has no TikTok). Other networks fall back to glyph
     *  circles; a member with no links shows the 3 brand icons
     *  static, as in his design. */
    private LinearLayout socialRow(final Member m) {
        LinearLayout social = new LinearLayout(this);
        social.setOrientation(LinearLayout.HORIZONTAL);
        social.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, dp(9), 0, 0);
        social.setLayoutParams(slp);
        java.util.List<String[]> items = new java.util.ArrayList<>();
        for (String n : DISP_ORDER) {
            String v = m.links.get(n);
            if (v != null && !v.trim().isEmpty()) items.add(new String[]{n, v});
        }
        if (items.isEmpty()) {
            for (String n : new String[]{"instagram", "facebook", "tiktok"})
                social.addView(socialCell(socialBrandIcon(n, null)));
        } else {
            for (final String[] it : items) {
                final String n = it[0], v = it[1];
                View.OnClickListener click = v.equals("!") ? null : (v2 -> openLink(n, v));
                if (n.equals("instagram") || n.equals("facebook") || n.equals("tiktok"))
                    social.addView(socialCell(socialBrandIcon(n, click)));
                else
                    social.addView(socialCell(socialIcon(GLYPH.get(n), click)));
            }
        }
        return social;
    }

    private LinearLayout socialCell(View child) {
        LinearLayout cell = new LinearLayout(this);
        cell.setGravity(Gravity.CENTER);
        cell.addView(child);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return cell;
    }

    private GradientDrawable glassCircle() {
        GradientDrawable cd = new GradientDrawable();
        cd.setShape(GradientDrawable.OVAL);
        if (light) { cd.setColor(Color.WHITE); cd.setStroke(dp(1), Color.argb(30, 52, 40, 110)); }
        else { cd.setColor(Color.argb(28, 255, 255, 255)); cd.setStroke(dp(1), Color.argb(38, 255, 255, 255)); }
        return cd;
    }

    private ImageView socialBrandIcon(String net, View.OnClickListener click) {
        ImageView b = new ImageView(this);
        b.setImageResource(net.equals("instagram") ? R.drawable.soc_ig : net.equals("facebook") ? R.drawable.soc_fb : R.drawable.soc_tt);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setBackground(glassCircle());
        boolean nrw = Ui.widthDp(this) < 340;
        int sz = nrw ? 34 : 38;
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sz), dp(sz)));
        if (click != null) b.setOnClickListener(click);
        return b;
    }

    private TextView socialIcon(String g, View.OnClickListener click) {
        TextView b = new TextView(this);
        b.setText(g); b.setTextColor(INK); b.setGravity(Gravity.CENTER);
        b.setTextSize(g.length() > 1 ? 10.5f : 12.5f);
        if (g.length() > 1) b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(glassCircle());
        boolean nrw = Ui.widthDp(this) < 340;
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(nrw ? 30 : 32), dp(nrw ? 30 : 32));
        b.setLayoutParams(bp);
        if (click != null) b.setOnClickListener(click);
        return b;
    }

    private void openLink(String net, String raw) {
        try {
            String v = raw.trim();
            String url;
            if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("mailto:")) url = v;
            else {
                String h = v.startsWith("@") ? v.substring(1) : v;
                switch (net) {
                    case "facebook": url = "https://www.facebook.com/" + h; break;
                    case "instagram": url = "https://www.instagram.com/" + h; break;
                    case "github": url = "https://github.com/" + h; break;
                    case "tiktok": url = "https://www.tiktok.com/@" + h; break;
                    case "linkedin": url = "https://www.linkedin.com/in/" + h; break;
                    case "mail": url = "mailto:" + v; break;
                    default: url = "https://" + v;
                }
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Toast.makeText(this, "Could not open that link", Toast.LENGTH_SHORT).show();
        }
    }

    /** True when the touch lands on the word "Zynox" at the end of
     *  the title - the hidden unlock spot. */
    private boolean onZynoxWord(TextView tv, MotionEvent e) {
        try {
            android.text.Layout lay = tv.getLayout();
            if (lay == null) return false;
            int x = (int) e.getX() - tv.getTotalPaddingLeft() + tv.getScrollX();
            int y = (int) e.getY() - tv.getTotalPaddingTop() + tv.getScrollY();
            int line = lay.getLineForVertical(y);
            int off = lay.getOffsetForHorizontal(line, x);
            String t = tv.getText().toString();
            return off >= t.length() - 5 && line == lay.getLineCount() - 1;
        } catch (Throwable ex) { return false; }
    }

    // ---------- hidden-mode editing ----------
    private EditText field(String value, boolean multi) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(Color.rgb(24, 21, 33));
        e.setTextSize(14.5f);
        e.setSingleLine(!multi);
        if (multi) e.setMaxLines(3);
        return e;
    }

    private void openEditDialog(final int idx) {
        final boolean isNew = idx < 0;
        final Member m = isNew ? null : members.get(idx);
        final EditText name = field(isNew ? "" : m.name, false);
        final EditText role = field(isNew ? "" : m.role, false);
        final EditText uni = field(isNew ? UNI : m.uni, false);
        final EditText bio = field(isNew ? "" : m.bio, true);
        final EditText[] linkF = new EditText[FORM_NETS.length];
        for (int i = 0; i < FORM_NETS.length; i++) {
            String lv = isNew ? "" : nz(m.links.get(FORM_NETS[i]));
            linkF[i] = field(lv.equals("!") ? "" : lv, false);
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        String[] labels = {"Name", "Role", "University line", "Bio", "Facebook (username or link)", "Instagram (username or link)", "GitHub (username or link)", "TikTok (username or link)"};
        EditText[] fields = {name, role, uni, bio, linkF[0], linkF[1], linkF[2], linkF[3]};
        for (int i = 0; i < labels.length; i++) {
            TextView l = new TextView(this);
            l.setText(labels[i]); l.setTextColor(Color.rgb(94, 88, 106)); l.setTextSize(12);
            l.setPadding(0, dp(8), 0, 0);
            box.addView(l);
            box.addView(fields[i]);
        }
        if (!isNew && m.added) {
            TextView del = new TextView(this);
            del.setText("Delete this member");
            del.setTextColor(Color.rgb(220, 38, 38)); del.setTextSize(13);
            del.setPadding(0, dp(14), 0, dp(4));
            del.setOnClickListener(v -> confirmDelete(idx));
            box.addView(del);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Add member" : "Edit member")
                .setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> saveMember(idx, name, role, uni, bio, linkF));
        if (!isNew) b.setNeutralButton("Change photo", (d, w) -> pickImage(m.key));
        b.show();
    }

    private String nz(String s) { return s == null ? "" : s; }

    private void saveMember(int idx, EditText name, EditText role, EditText uni, EditText bio, EditText[] linkF) {
        String nm = name.getText().toString().trim();
        if (nm.isEmpty()) { Toast.makeText(this, "Name is empty - nothing saved", Toast.LENGTH_SHORT).show(); return; }
        SharedPreferences.Editor ed = prefs.edit();
        String key;
        if (idx < 0) {
            int j = prefs.getInt("zx_count", 0);
            key = "a" + j;
            ed.putInt("zx_count", j + 1);
            ed.putString("zx_add_name_" + j, nm);
            ed.putString("zx_add_role_" + j, role.getText().toString().trim());
            ed.putString("zx_add_uni_" + j, uni.getText().toString().trim());
            ed.putString("zx_add_bio_" + j, bio.getText().toString().trim());
        } else {
            Member m = members.get(idx);
            key = m.key;
            if (m.added) {
                int j = Integer.parseInt(key.substring(1));
                ed.putString("zx_add_name_" + j, nm);
                ed.putString("zx_add_role_" + j, role.getText().toString().trim());
                ed.putString("zx_add_uni_" + j, uni.getText().toString().trim());
                ed.putString("zx_add_bio_" + j, bio.getText().toString().trim());
            } else {
                ed.putString("zx_ovr_name_" + key, nm);
                ed.putString("zx_ovr_role_" + key, role.getText().toString().trim());
                ed.putString("zx_ovr_uni_" + key, uni.getText().toString().trim());
                ed.putString("zx_ovr_bio_" + key, bio.getText().toString().trim());
            }
        }
        for (int i = 0; i < FORM_NETS.length; i++)
            ed.putString("zx_link_" + FORM_NETS[i] + "_" + key, linkF[i].getText().toString().trim());
        ed.apply();
        Toast.makeText(this, "Saved ✓", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void confirmDelete(final int idx) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this member?")
                .setMessage(members.get(idx).name + " will be removed from this phone's About page.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> deleteAdded(idx))
                .show();
    }

    private void deleteAdded(int idx) {
        int j = idx - NAMES.length; // added members start after the built-ins
        int count = prefs.getInt("zx_count", 0);
        SharedPreferences.Editor ed = prefs.edit();
        for (int k = j; k < count - 1; k++) {
            ed.putString("zx_add_name_" + k, prefs.getString("zx_add_name_" + (k + 1), ""));
            ed.putString("zx_add_role_" + k, prefs.getString("zx_add_role_" + (k + 1), ""));
            ed.putString("zx_add_uni_" + k, prefs.getString("zx_add_uni_" + (k + 1), ""));
            ed.putString("zx_add_bio_" + k, prefs.getString("zx_add_bio_" + (k + 1), ""));
            for (String n : NETS) {
                ed.putString("zx_link_" + n + "_a" + k, prefs.getString("zx_link_" + n + "_a" + (k + 1), ""));
            }
            String nextPhoto = prefs.getString("zx_photo_a" + (k + 1), "");
            if (nextPhoto == null || nextPhoto.isEmpty()) ed.remove("zx_photo_a" + k);
            else ed.putString("zx_photo_a" + k, nextPhoto);
        }
        int last = count - 1;
        ed.remove("zx_add_name_" + last).remove("zx_add_role_" + last).remove("zx_add_uni_" + last).remove("zx_add_bio_" + last).remove("zx_photo_a" + last);
        for (String n : NETS) ed.remove("zx_link_" + n + "_a" + last);
        ed.putInt("zx_count", count - 1);
        ed.apply();
        recreate();
    }

    private void pickImage(String key) {
        pickKey = key;
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.setType("image/*");
        it.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(it, 721); } catch (Throwable ignored) {}
    }

    private Bitmap loadPhotoFile(String path) {
        try {
            if (path == null || path.isEmpty()) return null;
            File f = new File(path);
            if (!f.exists()) return null;
            return BitmapFactory.decodeFile(path);
        } catch (Throwable e) { return null; }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 721 || res != RESULT_OK || data == null || data.getData() == null || pickKey == null) return;
        final Uri uri = data.getData();
        final String key = pickKey;
        pickKey = null;
        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bm = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (bm == null) return;
                int max = Math.max(bm.getWidth(), bm.getHeight());
                if (max > 900) {
                    float sc = 900f / max;
                    bm = Bitmap.createScaledBitmap(bm, Math.round(bm.getWidth() * sc), Math.round(bm.getHeight() * sc), true);
                }
                File dir = new File(getFilesDir(), "devphotos");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, key + ".jpg");
                FileOutputStream fos = new FileOutputStream(out);
                bm.compress(Bitmap.CompressFormat.JPEG, 88, fos);
                fos.close();
                prefs.edit().putString("zx_photo_" + key, out.getAbsolutePath()).apply();
                runOnUiThread(this::recreate);
            } catch (Throwable ignored) {}
        }).start();
    }
}
