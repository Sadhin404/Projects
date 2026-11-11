package com.shadhinkhan.grabit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;

/**
 * OPTIONAL account session connect (user-authorized; the app works
 * without it via public routes). Used for Instagram (the original
 * session) and, since 2026-11-09 on the user's request, Facebook -
 * so private-group / login-walled videos can be fetched with the
 * user's own session. The user logs in on the network's own page
 * inside this WebView; the session cookies are stored ENCRYPTED
 * (Android Keystore) and used only for that network's extraction.
 * Which network is chosen by the "network" intent extra
 * ("facebook" or default "instagram"). Cookies are never logged,
 * and Zynox never sees the password - it is typed on the network's
 * own page.
 */
public class LoginActivity extends Activity {
    private static final int BG = Color.rgb(11, 11, 16);
    private static final int INK = Color.rgb(245, 245, 247);
    private static final int GREY = Color.rgb(156, 163, 175);
    private static final int VIOLET = Color.rgb(124, 58, 237);
    private static final int LIGHT = Color.rgb(167, 139, 250);
    private WebView web;
    private boolean facebook;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        facebook = "facebook".equals(getIntent() != null ? getIntent().getStringExtra("network") : null);
        final String netName = facebook ? "Facebook" : "Instagram";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView hint = new TextView(this);
        hint.setText(facebook
                ? "Optional: log in to Facebook here so videos from private groups and pages can be downloaded with your own session. Your session is stored ENCRYPTED on this phone only, is used just for Facebook extraction, and you can disconnect anytime from Settings > Connection. Zynox never sees or sends your password - you type it on Facebook's own page."
                : "Optional: log in to Instagram here if a post or reel refuses to load through the public routes. Your session is stored ENCRYPTED on this phone only, is used just for Instagram extraction, and you can disconnect anytime from Settings > Connection. Zynox never sees or sends your password - you type it on Instagram's own page.");
        hint.setTextSize(13);
        hint.setTextColor(GREY);
        hint.setPadding(dp(14), dp(12), dp(14), dp(8));
        root.addView(hint);

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        web.setWebViewClient(new WebViewClient());
        CookieManager.getInstance().setAcceptCookie(true);
        try { CookieManager.getInstance().setAcceptThirdPartyCookies(web, true); } catch (Throwable ignored) {}
        web.loadUrl(facebook ? "https://www.facebook.com/login/" : "https://www.instagram.com/accounts/login/");
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button save = new Button(this);
        save.setText("Save session");
        save.setTextColor(Color.WHITE);
        save.setTextSize(15f); save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setAllCaps(false);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{LIGHT, VIOLET});
        g.setCornerRadius(dp(14));
        save.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(dp(14), dp(10), dp(14), dp(14));
        root.addView(save, lp);
        save.setOnClickListener(v -> saveCookies(netName));

        setContentView(root);
    }

    private void saveCookies(String netName) {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.flush();
            LinkedHashMap<String, String> jar = new LinkedHashMap<>();
            String[] hosts = facebook
                    ? new String[]{"https://www.facebook.com", "https://m.facebook.com", "https://facebook.com"}
                    : new String[]{"https://www.instagram.com", "https://instagram.com"};
            for (String h : hosts) {
                String raw = cm.getCookie(h);
                if (raw == null) continue;
                for (String part : raw.split(";")) {
                    String kv = part.trim();
                    int eq = kv.indexOf('=');
                    if (eq > 0) jar.put(kv.substring(0, eq), kv.substring(eq + 1));
                }
            }
            String required = facebook ? "c_user" : "sessionid";
            if (!jar.containsKey(required)) {
                Toast.makeText(this, "Login not detected yet - finish logging in to " + netName + " first, then tap Save session", Toast.LENGTH_LONG).show();
                return;
            }
            // CookieManager does not expose real expiry dates; use a 60-day
            // horizon - the diagnostics screen offers reconnect when a
            // session stops working.
            long expiry = System.currentTimeMillis() / 1000 + 60L * 24 * 3600;
            String domain = facebook ? ".facebook.com" : ".instagram.com";
            StringBuilder sb = new StringBuilder("# Netscape HTTP Cookie File\n");
            for (java.util.Map.Entry<String, String> e : jar.entrySet()) {
                sb.append(domain).append("\tTRUE\t/\tTRUE\t").append(expiry).append('\t').append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            boolean ok = facebook ? FbSessionVault.save(this, sb.toString()) : SessionVault.save(this, sb.toString());
            if (ok) {
                Toast.makeText(this, netName + " session saved (encrypted) ✓", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, "Could not save the session securely on this phone", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "Could not save session: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
