package com.shadhinkhan.grabit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** Settings home: Download settings, Meet the Developer, the
 *  diagnostics + Instagram session tools, and a standalone manual
 *  engine update check ("Check for update" row, his order 2026-11-08
 *  - it used to live inside the diagnostics dialog, where its themed
 *  text was unreadable on the white system dialog). */
public class SettingsActivity extends ThemedActivity {
    private TextView dlSubtitle;
    private TextView battSubtitle;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        initTheme();
        LinearLayout page = chrome("Settings");

        // order per the owner (2026-11-09): Meet the Developer,
        // Download settings, Connection, Check for update, Diagnostics
        page.addView(row("Meet the Developer", "About the maker of Zynox", v ->
                startActivity(new Intent(this, DeveloperActivity.class))));

        LinearLayout dlRow = row("Download settings", "", v ->
                startActivity(new Intent(this, DownloadSettingsActivity.class)));
        dlSubtitle = (TextView) ((LinearLayout) dlRow.getChildAt(0)).getChildAt(1);
        page.addView(dlRow);

        page.addView(row("Connection", "Facebook & Instagram login for private videos", v ->
                startActivity(new Intent(this, ConnectionActivity.class))));

        page.addView(row("Check for update", "Manual check for yt-dlp engine updates", v ->
                showUpdateDialog()));

        page.addView(row("Diagnostics & Instagram session", "Engine version, download logs, optional Instagram connection", v ->
                showDiagnostics()));

        // moved here from the home screen (owner, 2026-11-09): the
        // background-running permission as the last Settings button
        LinearLayout battRow = row("Background running", "", v -> openBatterySettings());
        battSubtitle = (TextView) ((LinearLayout) battRow.getChildAt(0)).getChildAt(1);
        page.addView(battRow);
        refreshBattRow();
    }

    private boolean isBatteryAllowed() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable e) { return false; }
    }

    private void refreshBattRow() {
        if (battSubtitle != null)
            battSubtitle.setText(isBatteryAllowed() ? "Allowed \u2713 \u2014 downloads keep running in the background" : "Tap to allow \u2014 so downloads are not stopped");
    }

    private void openBatterySettings() {
        if (isBatteryAllowed()) {
            android.widget.Toast.makeText(this, "Already allowed \u2014 downloads keep running in the background \u2713", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent bi = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            bi.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(bi);
        } catch (Throwable e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable e2) {
                try {
                    Intent ai = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    ai.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(ai);
                } catch (Throwable ignored) {}
                android.widget.Toast.makeText(this, "In Battery settings, set Zynox to 'No restrictions'", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshBattRow();
        if (dlSubtitle != null) {
            SharedPreferences p = getSharedPreferences("grabit_prefs", MODE_PRIVATE);
            String folder = p.getString("dl_folder_label", "");
            int par = p.getInt("max_parallel", 1);
            dlSubtitle.setText("Folder: " + (folder == null || folder.isEmpty() ? "Default (Downloads/Zynox)" : folder)
                    + "  ·  Parallel: " + par + (par == 1 ? " file" : " files"));
        }
    }

    /** Same diagnostics dialog the gear button used to open: engine version,
     *  recent download diagnostics, and the optional Instagram session. */
    private void showDiagnostics() {
        StringBuilder sb = new StringBuilder();
        try {
            SharedPreferences prefs = getSharedPreferences("grabit_prefs", MODE_PRIVATE);
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
                (d, w) -> startActivity(new Intent(this, LoginActivity.class)));
        if (connected) b.setNegativeButton("Disconnect", (d, w) -> {
            SessionVault.clear(this);
            Toast.makeText(this, "Instagram session removed", Toast.LENGTH_SHORT).show();
        });
        b.show();
    }

    /** Manual engine update (the owner's request): checks the same
     *  source the library's STABLE channel uses (GitHub's latest
     *  yt-dlp release) against the installed engine, and offers the
     *  update when one exists. The automatic weekly update in DlState
     *  is not touched by this.
     *
     *  The system dialog is light in BOTH app themes, so this dialog's
     *  texts use fixed dark colours - readable in light and dark mode
     *  alike (the v66 bug: theme-aware INK text turned white-on-white
     *  here in dark mode). */
    private void showUpdateDialog() {
        SharedPreferences prefs = getSharedPreferences("grabit_prefs", MODE_PRIVATE);
        String yv = prefs.getString("ytdlp_version", "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        TextView ver = new TextView(this);
        ver.setText("Download engine: yt-dlp " + (yv.isEmpty() ? "(version not recorded yet)" : yv));
        ver.setTextColor(Color.rgb(24, 21, 33)); ver.setTextSize(14);
        box.addView(ver);
        final TextView status = new TextView(this);
        status.setText("Not checked yet - tap Check for update");
        status.setTextColor(Color.rgb(94, 88, 106)); status.setTextSize(13);
        status.setPadding(0, dp(10), 0, 0);
        box.addView(status);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(10), 0, 0);
        final Button checkBtn = new Button(this);
        checkBtn.setText("Check for update"); checkBtn.setAllCaps(false);
        final Button updBtn = new Button(this);
        updBtn.setText("Update now"); updBtn.setAllCaps(false); updBtn.setEnabled(false);
        btnRow.addView(checkBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btnRow.addView(updBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(btnRow);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Check for update")
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
        checkBtn.setOnClickListener(v -> {
            checkBtn.setEnabled(false); updBtn.setEnabled(false);
            status.setText("Checking\u2026");
            new Thread(() -> {
                String installed = null, latest = null;
                try {
                    DlState.ensureEngineBlocking(SettingsActivity.this);
                    installed = com.yausername.youtubedl_android.YoutubeDL.getInstance().version(SettingsActivity.this);
                } catch (Throwable ignored) {}
                if (installed == null || installed.isEmpty())
                    installed = getSharedPreferences("grabit_prefs", MODE_PRIVATE).getString("ytdlp_version", "");
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest").openConnection();
                    c.setRequestProperty("User-Agent", "Zynox");
                    c.setConnectTimeout(12000); c.setReadTimeout(12000);
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
                    StringBuilder js = new StringBuilder(); String ln;
                    while ((ln = br.readLine()) != null) js.append(ln);
                    br.close();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(js.toString());
                    if (m.find()) latest = m.group(1);
                } catch (Throwable ignored) {}
                final String fi = installed, fl = latest;
                runOnUiThread(() -> {
                    checkBtn.setEnabled(true);
                    if (fl == null) { status.setText("Could not check \u2014 check your internet and try again"); return; }
                    if (fi != null && fi.trim().equals(fl.trim())) {
                        status.setText("Engine is up to date (yt-dlp " + fl + ") \u2713");
                    } else {
                        status.setText("Update available: yt-dlp " + fl + ((fi == null || fi.isEmpty()) ? "" : " (you have " + fi + ")"));
                        updBtn.setEnabled(true);
                    }
                });
            }).start();
        });
        updBtn.setOnClickListener(v -> {
            if (DlState.busy) { status.setText("A download is running \u2014 try again after it finishes"); return; }
            checkBtn.setEnabled(false); updBtn.setEnabled(false);
            status.setText("Updating engine\u2026 keep the app open");
            new Thread(() -> {
                String res;
                try {
                    DlState.ensureEngineBlocking(SettingsActivity.this);
                    com.yausername.youtubedl_android.YoutubeDL.UpdateStatus st = com.yausername.youtubedl_android.YoutubeDL.getInstance()
                            .updateYoutubeDL(SettingsActivity.this, com.yausername.youtubedl_android.YoutubeDL.UpdateChannel._STABLE);
                    String nv = com.yausername.youtubedl_android.YoutubeDL.getInstance().version(SettingsActivity.this);
                    if (nv != null && !nv.isEmpty())
                        getSharedPreferences("grabit_prefs", MODE_PRIVATE).edit().putString("ytdlp_version", nv).apply();
                    res = st == com.yausername.youtubedl_android.YoutubeDL.UpdateStatus.DONE
                            ? "Engine updated" + ((nv == null || nv.isEmpty()) ? "" : " to yt-dlp " + nv) + " \u2713"
                            : "Engine is up to date \u2713";
                } catch (Throwable t) { res = "Update failed \u2014 check your internet and try again"; }
                final String fr = res;
                runOnUiThread(() -> { checkBtn.setEnabled(true); status.setText(fr); });
            }).start();
        });
    }
}
