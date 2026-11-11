package com.shadhinkhan.grabit;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Settings > Connection (user request 2026-11-09): one place to
 *  connect the accounts whose LOGIN-WALLED videos the engine cannot
 *  fetch anonymously - Facebook (private groups/pages) and Instagram
 *  (private accounts / refused posts). Both sessions are optional,
 *  stored Keystore-encrypted, and used only for their own site.
 *  Without them the app keeps working through the public routes. */
public class ConnectionActivity extends ThemedActivity {

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        initTheme();
        LinearLayout page = chrome("Connection");

        TextView note = note("Log in once here, then paste private video links on the home screen as usual. Sessions stay encrypted on this phone and are never shared.");
        page.addView(note);

        page.addView(networkCard(
                "Facebook",
                FbSessionVault.statusText(this),
                "For videos from private Facebook groups and pages that ask you to log in.",
                FbSessionVault.hasSession(this),
                v -> {
                    Intent it = new Intent(this, LoginActivity.class);
                    it.putExtra("network", "facebook");
                    startActivityForResult(it, 41);
                },
                v -> { FbSessionVault.clear(this); Toast.makeText(this, "Facebook session removed", Toast.LENGTH_SHORT).show(); recreate(); }));

        page.addView(networkCard(
                "Instagram",
                SessionVault.statusText(this),
                "For private accounts and posts or reels that refuse to load through the public routes.",
                SessionVault.hasSession(this),
                v -> startActivityForResult(new Intent(this, LoginActivity.class), 42),
                v -> { SessionVault.clear(this); Toast.makeText(this, "Instagram session removed", Toast.LENGTH_SHORT).show(); recreate(); }));
    }

    private LinearLayout networkCard(String title, String status, String desc, boolean connected,
                                     View.OnClickListener onConnect, View.OnClickListener onDisconnect) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, dp(14), 0, 0);
        card.setLayoutParams(clp);

        TextView t = new TextView(this);
        t.setText(title); t.setTextColor(INK); t.setTextSize(16); t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);
        TextView st = new TextView(this);
        st.setText(status); st.setTextColor(GREY); st.setTextSize(12.5f);
        st.setPadding(0, dp(3), 0, 0);
        card.addView(st);
        TextView d = new TextView(this);
        d.setText(desc); d.setTextColor(DIM); d.setTextSize(12f);
        d.setPadding(0, dp(3), 0, 0);
        card.addView(d);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView connect = new TextView(this);
        connect.setText(connected ? "Reconnect" : "Connect");
        connect.setTextColor(Color.WHITE); connect.setTextSize(13); connect.setTypeface(Typeface.DEFAULT_BOLD);
        connect.setGravity(android.view.Gravity.CENTER);
        connect.setBackground(violetBg(10));
        connect.setPadding(dp(14), dp(8), dp(14), dp(8));
        connect.setOnClickListener(onConnect);
        row.addView(connect);
        if (connected) {
            TextView disc = new TextView(this);
            disc.setText("Disconnect");
            disc.setTextColor(GREY); disc.setTextSize(13);
            disc.setGravity(android.view.Gravity.CENTER);
            disc.setBackground(chipBg(false));
            disc.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.setMargins(dp(8), 0, 0, 0);
            disc.setLayoutParams(dlp);
            disc.setOnClickListener(onDisconnect);
            row.addView(disc);
        }
        card.addView(row);
        return card;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 41 || req == 42) recreate();
    }
}
