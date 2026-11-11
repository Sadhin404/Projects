package com.shadhinkhan.grabit;

import android.content.Context;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

/** Shared state between MainActivity and DownloadService + one-time engine init. */
public class DlState {
    public static volatile boolean busy = false;
    public static volatile int progress = 0;
    public static volatile String status = "";
    public static volatile long stamp = 0;

    public static volatile boolean engineReady = false;
    private static boolean updateStarted = false;

    /** Blocking init — call only from a background thread. Safe to call repeatedly. */
    public static synchronized boolean ensureEngineBlocking(Context ctx) {
        if (engineReady) return true;
        try {
            Context app = ctx.getApplicationContext();
            YoutubeDL.getInstance().init(app);
            try { FFmpeg.getInstance().init(app); } catch (Throwable ignored) {}
            engineReady = true;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Fire-and-forget init for the Activity. */
    public static void ensureEngineAsync(Context ctx) {
        if (engineReady) return;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> ensureEngineBlocking(app)).start();
    }

    public static void set(boolean busy_, int progress_, String status_) {
        busy = busy_; progress = progress_; status = status_; stamp = System.currentTimeMillis();
    }

    // Live download details for the notification and the Files tab's
    // downloading card: bytes done of total and ETA, fed by the service's
    // progress callbacks. etaSec -2 = estimate from the transfer speed.
    public static volatile String dlTitle = "";
    public static volatile long dlDone = -1, dlTotal = -1;
    public static volatile int dlEta = -1;
    private static long spdT = 0, spdDone = 0;
    private static double spdEma = 0;
    public static void setDl(String title, int pct, long done, long total) { setDl(title, pct, done, total, -2); }
    public static void setDl(String title, int pct, long done, long total, int etaSec) {
        if (title != null && !title.equals(dlTitle)) { spdT = 0; spdEma = 0; dlEta = -1; }
        if (title != null) dlTitle = title;
        dlDone = done; dlTotal = total;
        if (etaSec >= 0) { dlEta = etaSec; spdT = 0; return; }
        if (etaSec == -2 && done >= 0 && total > done) {
            long now = System.currentTimeMillis();
            if (spdT > 0 && now > spdT && done >= spdDone) {
                double inst = (done - spdDone) * 1000.0 / (now - spdT);
                if (inst > 0) {
                    spdEma = spdEma <= 0 ? inst : spdEma * 0.6 + inst * 0.4;
                    if (spdEma > 0) dlEta = (int) ((total - done) / spdEma);
                }
            }
            spdT = now; spdDone = done;
        } else if (etaSec == -1) dlEta = -1;
    }
    public static void clearDl() { dlTitle = ""; dlDone = -1; dlTotal = -1; dlEta = -1; spdT = 0; spdEma = 0; }

    /** GrabIt v4 (1.3) behaviour, verified from its binary: at app
     *  start the original ran YoutubeDL.updateYoutubeDL(STABLE) in a
     *  background thread right after engine init, on every launch. An
     *  earlier round first weekly-gated and then disabled this on the
     *  assumption 1.3 never updated - its dex proves it did (same call,
     *  same STABLE channel), so the update is restored as-is. Also
     *  records the running engine version for Diagnostics. */
    private static boolean updateScheduled = false;
    /** Weekly yt-dlp update, delayed and only from the main app screen, so it never slows a fetch.
     *  This is the v59 form, restored 2026-11-08 (17:28) on the user's order
     *  ("engine self update off keno korco? sob ager moto koro"): engine
     *  self-update is ON again, exactly as it was before the v60 removal. */
    public static void maybeUpdateLater(final Context ctx) {
        if (updateScheduled) return;
        updateScheduled = true;
        final Context app = ctx.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.content.SharedPreferences prefs = app.getSharedPreferences("grabit_prefs", Context.MODE_PRIVATE);
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
                final boolean due = !today.equals(prefs.getString("last_update_day", ""));
                final boolean needVersion = prefs.getString("ytdlp_version", "").isEmpty();
                if (!due && !needVersion) return;
                if (busy) return;
                if (due) prefs.edit().putString("last_update_day", today).apply();
                new Thread(() -> {
                    try {
                        if (!ensureEngineBlocking(app)) return;
                        // DAILY AUTO-UPDATE (owner order 2026-11-09
                        // 05:14, reversing this morning's manual-only
                        // call): once a day, shortly after the app
                        // opens, the engine checks in the background
                        // and silently downloads a newer yt-dlp when
                        // one exists. The Settings manual check stays.
                        if (due) {
                            try { YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE); }
                            catch (Throwable ignored) {}
                        }
                        try {
                            String v = YoutubeDL.getInstance().version(app);
                            if (v != null && !v.isEmpty()) prefs.edit().putString("ytdlp_version", v).apply();
                        } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                }).start();
            } catch (Throwable ignored) {}
        }, 45000);
    }
}
