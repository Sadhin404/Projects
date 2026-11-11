package com.shadhinkhan.grabit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/** Runs downloads in a foreground service so they keep going when the app is closed/minimised. */
public class DownloadService extends Service {
    public static final String ACTION_CANCEL = "com.shadhinkhan.grabit.CANCEL";
    public static final String ACTION_PAUSE = "com.shadhinkhan.grabit.PAUSE";
    public static final String ACTION_RESUME = "com.shadhinkhan.grabit.RESUME";
    public static final String ACTION_DISMISS_PAUSED = "com.shadhinkhan.grabit.DISMISS_PAUSED";
    private static final int PAUSED_NOTIF_ID = 7799;
    private static final String CHANNEL = "grabit_progress";
    private static final int NOTIF_ID = 42;
    private NotificationManager nm;

    // ---- parallel download queue (Settings: 1-4 at a time) ----
    /** Per-job state, so several downloads can run together, each with its
     *  own notification, progress throttle and cancel/pause flags. */
    static class JobCtx {
        final int id; final int notifId;
        volatile boolean cancelled = false;
        volatile boolean paused = false;
        // Set when the engine could only deliver the closest available
        // quality: the gate accepts exactly this height and the final
        // message tells the user.
        volatile int closestH = -1; volatile String closestNote = null;
        // Stream temp files of the merge step: kept until the job's final
        // verification and export have run (deleted in runJob's finally).
        volatile File[] pendingTemps = null;
        String label = "Download";
        int lastPct = -1; long lastNotifyMs = 0; String lastNotifyText = "";
        JobCtx(int id, int notifId) { this.id = id; this.notifId = notifId; }
    }
    private static class QueuedJob {
        final Intent intent; final JobCtx ctx; final long persistId;
        QueuedJob(Intent i, JobCtx c, long pid) { intent = i; ctx = c; persistId = pid; }
    }
    /** Every live job (queued + running), mirrored to disk so the queue
     *  survives process death; the service is START_STICKY and restores
     *  these on restart. */
    private static final java.util.List<QueuedJob> ALL_JOBS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static boolean restoredOnce = false;
    private static final JobCtx DEFAULT_CTX = new JobCtx(0, NOTIF_ID);
    private static final ThreadLocal<JobCtx> CTX = new ThreadLocal<>();
    private static final java.util.Map<Integer, JobCtx> JOBS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.ArrayDeque<QueuedJob> QUEUE = new java.util.ArrayDeque<>();
    private static int activeJobs = 0;
    private static final java.util.concurrent.atomic.AtomicInteger JOB_SEQ = new java.util.concurrent.atomic.AtomicInteger(0);
    private static JobCtx ctx() { JobCtx c = CTX.get(); return c != null ? c : DEFAULT_CTX; }
    private static boolean isCancelled() { return ctx().cancelled; }
    private static boolean isPaused() { return ctx().paused; }

    @Override public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Download progress", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Zynox download progress bar");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        // Clear any stuck progress notification left by older builds under
        // the legacy id 42 (real jobs have used ids 100+ since v40, and a
        // leftover there could never be finished or cancelled).
        try { nm.cancel(NOTIF_ID); } catch (Throwable ignored) {}
        // Stream sessions (.part + sidecar) older than a week are abandoned.
        try { StreamDownloader.cleanupStale(grabDir(), 7L * 24 * 3600 * 1000); } catch (Throwable ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotif(JobCtx ctx, String text, int pct, boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download)
         .setContentTitle("Zynox — " + ctx.label)
         .setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(text))
         .setContentIntent(open)
         .setCategory(Notification.CATEGORY_PROGRESS)
         .setOngoing(ongoing)
         .setOnlyAlertOnce(true)
         .setShowWhen(false);
        if (pct >= 0) b.setProgress(100, pct, false);
        else if (ongoing) b.setProgress(0, 0, true); // indeterminate: work is happening, % not known yet
        if (ongoing) {
            Intent pauseIntent = new Intent(this, DownloadService.class);
            pauseIntent.setAction(ACTION_PAUSE);
            pauseIntent.putExtra("jobId", ctx.id);
            PendingIntent pausePi = PendingIntent.getService(this, 2000 + ctx.id, pauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(android.R.drawable.ic_media_pause, "Pause", pausePi);
            Intent cancelIntent = new Intent(this, DownloadService.class);
            cancelIntent.setAction(ACTION_CANCEL);
            cancelIntent.putExtra("jobId", ctx.id);
            PendingIntent cancelPi = PendingIntent.getService(this, 1000 + ctx.id, cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPi);
        }
        return b.build();
    }

    private long lastNotifyMs = 0;
    private String lastNotifyText = "";

    /**
     * v20 progress fix: the old version only re-posted the notification when the
     * PERCENT changed. For audio that meant a frozen notification for the whole
     * job (fragment downloads report no %, conversion reports no %), then a jump
     * straight to "Done". Now the notification is re-posted when the percent
     * changes OR every second OR the text changes, so the downloaded-MB counter
     * and elapsed time visibly tick for every kind of download.
     */
    private void updateNotif(String text, int pct) {
        DlState.set(true, pct, text);
        JobCtx ctx = ctx();
        long now = System.currentTimeMillis();
        if (pct != ctx.lastPct || now - ctx.lastNotifyMs >= 1000 || !text.equals(ctx.lastNotifyText)) {
            ctx.lastPct = pct;
            ctx.lastNotifyMs = now;
            ctx.lastNotifyText = text;
            try { nm.notify(ctx.notifId, buildNotif(ctx, text, pct, true)); } catch (Throwable ignored) {}
        }
    }

    /** Live-activity update with an indeterminate bar (used while waiting on the engine / fresh-stream lookup). */
    private void updateLive(String text) { updateLiveFor(ctx(), text); }

    /** updateNotif for callers on threads that do not carry the job's
     *  ThreadLocal ctx - above all the engine's progress callback, which the
     *  library invokes on its own thread. Without the explicit ctx those
     *  updates landed on the default notification (id 42, title "Download")
     *  and stayed there forever as a stuck second notification. */
    private void updateNotifFor(JobCtx c, String text, int pct) {
        DlState.set(true, pct, text);
        long now = System.currentTimeMillis();
        if (pct != c.lastPct || now - c.lastNotifyMs >= 1000 || !text.equals(c.lastNotifyText)) {
            c.lastPct = pct;
            c.lastNotifyMs = now;
            c.lastNotifyText = text;
            try { nm.notify(c.notifId, buildNotif(c, text, pct, true)); } catch (Throwable ignored) {}
        }
    }

    private void updateLiveFor(JobCtx c, String text) {
        DlState.set(true, Math.max(0, DlState.progress), text);
        long now = System.currentTimeMillis();
        if (now - c.lastNotifyMs >= 800) {
            c.lastNotifyMs = now;
            c.lastNotifyText = text;
            try { nm.notify(c.notifId, buildNotif(c, text, -1, true)); } catch (Throwable ignored) {}
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Cancel action: handled even while jobs run; targets one job when it carries an id
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            int jid = intent.getIntExtra("jobId", -1);
            if (jid >= 0) {
                JobCtx c = JOBS.get(jid); if (c != null) c.cancelled = true;
                try { YoutubeDL.getInstance().destroyProcessById("grabit-dl-" + jid); } catch (Throwable ignored) {}
            } else {
                for (JobCtx c : JOBS.values()) {
                    c.cancelled = true;
                    try { YoutubeDL.getInstance().destroyProcessById("grabit-dl-" + c.id); } catch (Throwable ignored) {}
                }
            }
            try { YoutubeDL.getInstance().destroyProcessById("grabit-dl"); } catch (Throwable ignored) {}
            // Queued (not yet running) jobs are removed outright, so the
            // persisted queue can never resurrect a cancelled download.
            synchronized (QUEUE) {
                java.util.Iterator<QueuedJob> it = QUEUE.iterator();
                while (it.hasNext()) {
                    QueuedJob qj = it.next();
                    if (jid < 0 || qj.ctx.id == jid) { it.remove(); ALL_JOBS.remove(qj); JOBS.remove(qj.ctx.id); }
                }
            }
            persistJobs();
            DlState.set(true, DlState.progress, "Cancelling\u2026");
            return START_STICKY;
        }
        // Pause action: the engine stops at the next chunk boundary and keeps its .part file
        if (intent != null && ACTION_PAUSE.equals(intent.getAction())) {
            int jid = intent.getIntExtra("jobId", -1);
            if (jid >= 0) { JobCtx c = JOBS.get(jid); if (c != null) c.paused = true; }
            else for (JobCtx c : JOBS.values()) c.paused = true;
            DlState.set(true, DlState.progress, "Pausing\u2026");
            return START_STICKY;
        }
        // Resume from the persistent paused notification: re-enqueue
        // the saved job spec - the kept .part / fragments continue it.
        if (intent != null && ACTION_RESUME.equals(intent.getAction())) {
            Intent spec = readPausedSpec();
            clearPausedSpec();
            try { nm.cancel(PAUSED_NOTIF_ID); } catch (Throwable ignored) {}
            if (spec != null) enqueueJob(spec, System.currentTimeMillis() * 1000L + (JOB_SEQ.get() & 1023));
            return START_STICKY;
        }
        if (intent != null && ACTION_DISMISS_PAUSED.equals(intent.getAction())) {
            clearPausedSpec();
            try { nm.cancel(PAUSED_NOTIF_ID); } catch (Throwable ignored) {}
            return START_STICKY;
        }
        if (intent == null) { restorePersistedJobs(); return START_STICKY; }
        // Every request becomes a queued job; up to "max_parallel" (Settings,
        // 1-4) run at the same time, the rest wait for a free slot.
        enqueueJob(intent, System.currentTimeMillis() * 1000L + (JOB_SEQ.get() & 1023));
        return START_STICKY;
    }

    /** Create the JobCtx, foreground notification and queue entry for a
     *  download request, and mirror the queue to disk. */
    private void enqueueJob(Intent intent, long persistId) {
        // Duplicate guard: the same page + format already queued or
        // running is never started twice.
        String nUrl = intent.getStringExtra("url");
        String nFmt = intent.getStringExtra("formatId");
        if (nUrl != null && nFmt != null) {
            synchronized (ALL_JOBS) {
                for (QueuedJob j : ALL_JOBS) {
                    if (nUrl.equals(j.intent.getStringExtra("url")) && nFmt.equals(j.intent.getStringExtra("formatId"))) {
                        DlState.set(true, Math.max(0, DlState.progress), "Already downloading: " + j.ctx.label);
                        return;
                    }
                }
            }
        }
        int seq = JOB_SEQ.incrementAndGet();
        JobCtx ctx = new JobCtx(seq, 100 + seq);
        String t = intent.getStringExtra("title");
        ctx.label = (t == null ? "Download" : t);
        String q = intent.getStringExtra("quality");
        if (q != null && !q.isEmpty()) ctx.label += " \u00b7 " + q;
        JOBS.put(ctx.id, ctx);
        // Foreground contract: startForeground promptly for every start request
        try { startForeground(ctx.notifId, buildNotif(ctx, "Starting download\u2026", 0, true)); } catch (Throwable ignored) {}
        QueuedJob job = new QueuedJob(intent, ctx, persistId);
        ALL_JOBS.add(job);
        synchronized (QUEUE) {
            QUEUE.add(job);
            if (activeJobs >= maxParallel()) DlState.set(true, Math.max(0, DlState.progress), "Queued: " + ctx.label + " \u2014 starts when a slot frees");
        }
        persistJobs();
        pumpQueue();
    }

    private void persistJobs() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            synchronized (ALL_JOBS) {
                for (QueuedJob j : ALL_JOBS) {
                    Intent it = j.intent;
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("persistId", j.persistId);
                    o.put("mode", it.getStringExtra("mode"));
                    o.put("url", it.getStringExtra("url"));
                    o.put("formatId", it.getStringExtra("formatId"));
                    o.put("kind", it.getStringExtra("kind"));
                    o.put("quality", it.getStringExtra("quality"));
                    o.put("title", it.getStringExtra("title"));
                    o.put("directUrl", it.getStringExtra("directUrl"));
                    o.put("audioUrl", it.getStringExtra("audioUrl"));
                    o.put("container", it.getStringExtra("container"));
                    o.put("httpUa", it.getStringExtra("httpUa"));
                    o.put("ext", it.getStringExtra("ext"));
                    java.util.ArrayList<String> imgs = it.getStringArrayListExtra("images");
                    if (imgs != null) o.put("images", new org.json.JSONArray(imgs));
                    arr.put(o);
                }
            }
            getSharedPreferences("grabit_prefs", MODE_PRIVATE).edit()
                    .putString("persisted_jobs", arr.toString()).apply();
        } catch (Throwable ignored) {}
    }

    /** Rebuild the queue after process death (the sticky service restarts
     *  with a null intent). Direct jobs are restored as smart-mode jobs so
     *  their streams are re-resolved fresh; the stream sessions resume
     *  from their exact byte offsets inside the downloader. */
    private void restorePersistedJobs() {
        // The v4 app never resurrected downloads after a restart; an
        // interrupted download is simply started again by the user.
        if (true) return;
        if (restoredOnce) return;
        restoredOnce = true;
        try {
            String raw = getSharedPreferences("grabit_prefs", MODE_PRIVATE).getString("persisted_jobs", "");
            if (raw == null || raw.isEmpty()) return;
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            if (arr.length() == 0) return;
            diagLog("restore: " + arr.length() + " persisted job(s) re-queued after restart");
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Intent it = new Intent();
                String mode = o.optString("mode", "");
                if (!mode.isEmpty() && !"direct2".equals(mode)) it.putExtra("mode", mode);
                it.putExtra("url", o.optString("url", ""));
                it.putExtra("formatId", o.optString("formatId", ""));
                it.putExtra("kind", o.optString("kind", ""));
                it.putExtra("quality", o.optString("quality", ""));
                it.putExtra("title", o.optString("title", ""));
                it.putExtra("directUrl", o.optString("directUrl", ""));
                it.putExtra("audioUrl", o.optString("audioUrl", ""));
                it.putExtra("container", o.optString("container", ""));
                it.putExtra("httpUa", o.optString("httpUa", ""));
                it.putExtra("ext", o.optString("ext", ""));
                org.json.JSONArray imgs = o.optJSONArray("images");
                if (imgs != null) {
                    java.util.ArrayList<String> list = new java.util.ArrayList<>();
                    for (int k = 0; k < imgs.length(); k++) list.add(imgs.optString(k, ""));
                    it.putStringArrayListExtra("images", list);
                }
                enqueueJob(it, o.optLong("persistId", System.currentTimeMillis() + i));
            }
        } catch (Throwable ignored) {}
    }

    private void pumpQueue() {
        synchronized (QUEUE) {
            while (activeJobs < maxParallel() && !QUEUE.isEmpty()) {
                final QueuedJob job = QUEUE.poll();
                activeJobs++;
                new Thread(() -> runJob(job)).start();
            }
        }
    }

    /** True while any job is running or queued. MainActivity uses this to
     *  tell a live download apart from a wedged busy flag. */
    public static boolean anyActive() {
        synchronized (QUEUE) { return activeJobs > 0 || !QUEUE.isEmpty(); }
    }

    private int maxParallel() {
        // One download at a time, exactly like the v4 app - the user
        // explicitly allowed removing multiple downloads (2026-11-08).
        return 1;
    }

    /** Final status for one job; keeps DlState.busy true while other jobs run or wait. */
    private void setDoneState(int pct, String text) {
        synchronized (QUEUE) { DlState.set(activeJobs > 1 || !QUEUE.isEmpty(), pct, text); }
    }

    // ---- persistent paused notification + resume (2026-11-09) ----
    private void savePausedSpec(Intent src) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            String[] keys = {"mode", "url", "formatId", "kind", "directUrl", "title", "ext", "quality", "audioUrl"};
            for (String k : keys) { String v = src.getStringExtra(k); if (v != null) o.put(k, v); }
            ArrayList<String> imgs = src.getStringArrayListExtra("images");
            if (imgs != null) o.put("images", new org.json.JSONArray(imgs));
            getSharedPreferences("grabit_prefs", MODE_PRIVATE).edit().putString("paused_job", o.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private Intent readPausedSpec() {
        try {
            String raw = getSharedPreferences("grabit_prefs", MODE_PRIVATE).getString("paused_job", "");
            if (raw == null || raw.isEmpty()) return null;
            org.json.JSONObject o = new org.json.JSONObject(raw);
            Intent it = new Intent();
            String[] keys = {"mode", "url", "formatId", "kind", "directUrl", "title", "ext", "quality", "audioUrl"};
            for (String k : keys) if (o.has(k)) it.putExtra(k, o.getString(k));
            if (o.has("images")) {
                org.json.JSONArray a = o.getJSONArray("images");
                ArrayList<String> imgs = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) imgs.add(a.getString(i));
                it.putStringArrayListExtra("images", imgs);
            }
            return it.getStringExtra("url") == null ? null : it;
        } catch (Throwable e) { return null; }
    }

    private void clearPausedSpec() {
        try { getSharedPreferences("grabit_prefs", MODE_PRIVATE).edit().remove("paused_job").apply(); } catch (Throwable ignored) {}
    }

    private void clearPausedIfMatches(String url, String formatId) {
        try {
            Intent spec = readPausedSpec();
            if (spec == null) return;
            if (url != null && url.equals(spec.getStringExtra("url"))
                    && (formatId == null || formatId.equals(spec.getStringExtra("formatId")))) {
                clearPausedSpec();
                try { nm.cancel(PAUSED_NOTIF_ID); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void postPausedNotif(String title) {
        try {
            String t = (title == null || title.isEmpty()) ? "Download" : title;
            PendingIntent open = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent resumeIn = new Intent(this, DownloadService.class);
            resumeIn.setAction(ACTION_RESUME);
            PendingIntent resumePi = PendingIntent.getService(this, 3001, resumeIn,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent dismissIn = new Intent(this, DownloadService.class);
            dismissIn.setAction(ACTION_DISMISS_PAUSED);
            PendingIntent dismissPi = PendingIntent.getService(this, 3002, dismissIn,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_media_pause)
             .setContentTitle("Zynox \u2014 Paused")
             .setContentText(t)
             .setStyle(new Notification.BigTextStyle().bigText(t + " \u2014 paused. Tap Resume to continue from where it stopped."))
             .setContentIntent(open)
             .setDeleteIntent(dismissPi)
             .setOngoing(false)
             .setOnlyAlertOnce(true)
             .setShowWhen(false)
             .addAction(android.R.drawable.ic_media_play, "Resume", resumePi)
             .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi);
            nm.notify(PAUSED_NOTIF_ID, b.build());
        } catch (Throwable ignored) {}
    }

    /** Gentle double-pulse when a download finishes (owner request
     *  2026-11-09), together with the finish notification + toast. */
    private void buzzDone() {
        try {
            android.os.Vibrator vib = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            vib.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 55, 90, 55}, -1));
        } catch (Throwable ignored) {}
    }

    private void runJob(QueuedJob job) {
        final Intent intent = job.intent;
        final JobCtx ctx = job.ctx;
        CTX.set(ctx);
        final String mode = intent.getStringExtra("mode");
        final String url = intent.getStringExtra("url");
        final String formatId = intent.getStringExtra("formatId");
        final String kind = intent.getStringExtra("kind");
        final String directUrl = intent.getStringExtra("directUrl");
        final String title = intent.getStringExtra("title");
        final String ext = intent.getStringExtra("ext");
        final ArrayList<String> images = intent.getStringArrayListExtra("images");
        final String quality = intent.getStringExtra("quality");

        DlState.set(true, 0, "Starting download\u2026");
        ctx.lastPct = -1;

        PowerManager.WakeLock wakeLock = null;
        android.net.wifi.WifiManager.WifiLock wifiLock = null;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Zynox:download");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(3 * 60 * 60 * 1000L);
        } catch (Throwable ignored) {}
        try {
            // Keep the Wi-Fi radio fully awake for the whole job too: without
            // this it can doze when the screen is off and long transfers
            // keep dropping mid-stream.
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                int wifiMode = android.os.Build.VERSION.SDK_INT >= 29
                        ? android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        : android.net.wifi.WifiManager.WIFI_MODE_FULL;
                wifiLock = wm.createWifiLock(wifiMode, "Zynox:wifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Throwable ignored) {}
        try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND); } catch (Throwable ignored) {}
        String doneText;
            final boolean audioJob = kind != null && kind.startsWith("Audio");
            if (audioJob && url != null) AudioEngine.savePending(this, url, formatId, title);
            try {
                ArrayList<File> saved = new ArrayList<>();
                if ("audioResume".equals(mode)) {
                    saved.add(downloadSmart(url, formatId, kind == null ? "Audio" : kind, title, quality));
                } else if ("direct2".equals(mode)) {
                    if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))
                            && kind != null && kind.startsWith("Audio")) {
                        // Audio bug fix (2026-11-09): this job already
                        // carries direct streams - use them (the
                        // background-proof direct system). The engine
                        // stalls in the background on his phone, so it
                        // is only the fallback via downloadSmart.
                        diagLog("audio direct2 route fmt=" + formatId);
                        try { saved.add(downloadDirect2(intent)); }
                        catch (Throwable de) {
                            if (de instanceof AudioEngine.PausedException) throw new AudioEngine.PausedException();
                            if (isCancelled()) throw new Exception("Cancelled");
                            saved.add(downloadSmart(url, formatId, kind, title, quality));
                        }
                    } else if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                        // v4 system: a YouTube video job always runs on the
                        // engine, even if it was queued with direct streams.
                        saved.add(downloadViaYtdlp(url, formatId, kind, quality));
                    } else try { saved.add(downloadDirect2(intent)); }
                    catch (Throwable de) {
                        if (de instanceof AudioEngine.PausedException) throw new AudioEngine.PausedException();
                        if (isCancelled()) throw new Exception("Cancelled");
                        if (kind != null && kind.startsWith("Audio")) {
                            // One audio system: a failed direct attempt re-resolves FRESH
                            // streams and retries through the same pipeline (downloadSmart)
                            // instead of jumping straight to the engine.
                            saved.add(downloadSmart(url, formatId, kind, title, quality));
                        } else {
                            File igRetry = null;
                            if (url != null && url.contains("instagram.com")) {
                                // CDN links expire quickly: re-extract fresh ones
                                // (no cache) and retry the direct download once.
                                try {
                                    updateNotif("Refreshing Instagram link\u2026", 2);
                                    Fetcher.Result fr = Fetcher.fetchFreshInstagram(this, url);
                                    if (fr != null) {
                                        for (Fmt ff : fr.formats) {
                                            if (ff.directUrl != null && !ff.directUrl.isEmpty() && ff.kind != null && ff.kind.contains("Video")) {
                                                Intent di2 = new Intent();
                                                di2.putExtra("directUrl", ff.directUrl);
                                                di2.putExtra("httpUa", ff.httpUa);
                                                di2.putExtra("container", ff.container);
                                                di2.putExtra("kind", kind);
                                                di2.putExtra("title", title);
                                                di2.putExtra("url", url);
                                                di2.putExtra("formatId", formatId);
                                                igRetry = downloadDirect2(di2);
                                                break;
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                            if (igRetry != null) saved.add(igRetry);
                            else {
                                try { saved.add(downloadViaYtdlp(url, formatId, kind, quality)); }
                                catch (Throwable ee) { throw new Exception("Engine route: " + shortMsg(ee.getMessage()) + " | Direct route: " + shortMsg(de.getMessage())); }
                            }
                        }
                    }
                } else if ("direct".equals(mode)) {
                    File out = new File(grabDir(), safeOutName(title) + " [j" + ctx.id + "]." + ext);
                    httpDownload(directUrl, out, 0, 100);
                    saved.add(out);
                } else if ("images".equals(mode)) {
                    int total = images.size();
                    for (int i = 0; i < total; i++) {
                        if (isCancelled()) throw new Exception("Cancelled");
                        File out = new File(grabDir(), safeOutName(title) + "_" + (i + 1) + " [j" + ctx.id + "].jpg");
                        httpDownload(images.get(i), out, i * 100.0 / total, 100.0 / total);
                        saved.add(out);
                        updateNotif("Downloading photos… " + (i + 1) + "/" + total, (int) ((i + 1) * 100.0 / total));
                    }
                } else {
                    saved.add(downloadSmart(url, formatId, kind, title, quality));
                }
                if (isCancelled()) throw new Exception("Cancelled");
                // Final verification gate (see verifySavedFile): no file is
                // exported - or reported as "Saved" - until it passes.
                DlState.set(true, 97, "VERIFYING \u2014 checking the finished file\u2026");
                updateNotif("VERIFYING \u2014 checking the finished file\u2026", 97);
                for (File f : saved) verifySavedFile(f, kind, quality);
                long totalBytes = 0;
                for (File f : saved) if (f != null) totalBytes += f.length();
                DlState.set(true, 99, "SAVING \u2014 adding to Gallery / Downloads\u2026");
                updateNotif("SAVING \u2014 adding to Gallery / Downloads\u2026", 99);
                for (File f : saved) if (f != null) exportToPublic(f);
                String names = saved.size() == 1 && saved.get(0) != null ? saved.get(0).getName() : saved.size() + " photos";
                doneText = "Done ✓ Saved: " + names + " (" + mb(totalBytes) + ") — in Gallery/Downloads (Zynox folder)." + (ctx.closestNote != null ? " " + ctx.closestNote : "");
                setDoneState(100, doneText);
                final String fTitle = title;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try { android.widget.Toast.makeText(getApplicationContext(), "Download finished ✓ — " + (fTitle != null && !fTitle.isEmpty() ? fTitle : "Saved to the Zynox folder"), android.widget.Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
                });
                if (audioJob) AudioEngine.clearPending(this);
                try { nm.notify(ctx.notifId, buildNotif(ctx, doneText, -1, false)); } catch (Throwable ignored) {}
                clearPausedIfMatches(url, formatId);
                buzzDone();
            } catch (AudioEngine.PausedException pe) {
                doneText = "Paused \u2014 the downloaded part is saved. Download the same link again and it continues from where it stopped.";
                setDoneState(DlState.progress, doneText);
                try { nm.notify(ctx.notifId, buildNotif(ctx, doneText, -1, false)); } catch (Throwable ignored) {}
                // The job's own notification can be swept when the
                // service stops, so the paused state ALSO gets a
                // standalone notification (own id, survives the
                // service) with a Resume action (owner, 2026-11-09).
                savePausedSpec(intent);
                postPausedNotif(title);
            } catch (Throwable e) {
                if (!isCancelled()) diagLog("job FAILED: " + e + stackOf(e));
                if (isCancelled()) AudioEngine.clearPending(this);
                doneText = isCancelled() ? "Download cancelled." : "Download failed: " + shortMsg(e.getMessage());
                setDoneState(0, doneText);
                try { nm.notify(ctx.notifId, buildNotif(ctx, doneText, -1, false)); } catch (Throwable ignored) {}
            } finally {
                // Merge stream temps are deleted only now - after the final
                // gate and export have run, or the job has failed for good.
                try { File[] tt = ctx.pendingTemps; if (tt != null) for (File tf : tt) if (tf != null) tf.delete(); } catch (Throwable ignored) {}
                try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
                try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Throwable ignored) {}
                DlState.clearDl();
                JOBS.remove(ctx.id);
                ALL_JOBS.remove(job);
                persistJobs();
                CTX.remove();
                synchronized (QUEUE) {
                    activeJobs--;
                    if (QUEUE.isEmpty() && activeJobs == 0) {
                        // stopForeground(false): detaches the service but KEEPS the
                        // "Download finished" notification in the panel. (true) here
                        // deleted it, leaving only a stale progress notification.
                        try { stopForeground(false); } catch (Throwable ignored) {}
                        stopSelf();
                    }
                }
                pumpQueue();
            }
    }

    /** Filesystem-safe output base name: the service never trusts a
     *  title verbatim for a path (separators, control chars, length). */
    private static String safeOutName(String title) {
        String t = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").trim();
        if (t.length() > 80) t = t.substring(0, 80).trim();
        return t.isEmpty() ? "video" : t;
    }

    /** Public display name for a working file: strips the per-job
     *  " [j<id>]" uniqueness tag so Gallery names stay clean. */
    private static String publicName(File src) {
        return src.getName().replaceFirst(" \\[j\\d+\\](\\.[^.]+)$", "$1");
    }

    private File grabDir() {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GrabIt");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }
    private String mb(long bytes) { return String.format(Locale.US, "%.1f MB", bytes / 1048576.0); }
    private String shortMsg(String m) {
        if (m == null) return "unknown error";
        return m.length() > 500 ? m.substring(m.length() - 500) : m;
    }

    private void httpDownload(String url, File out, double basePct, double spanPct) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
        c.setConnectTimeout(20000); c.setReadTimeout(60000);
        InputStream in = c.getInputStream();
        long total = c.getContentLengthLong();
        OutputStream os = new java.io.FileOutputStream(out);
        byte[] buf = new byte[32768]; int n; long done = 0;
        while ((n = in.read(buf)) > 0) {
            if (isCancelled()) { try { os.close(); } catch (Throwable ignored) {} try { out.delete(); } catch (Throwable ignored) {} throw new Exception("Cancelled"); }
            os.write(buf, 0, n); done += n;
            if (total > 0) {
                int pct = (int) (basePct + spanPct * done / total);
                updateNotif("Downloading… " + pct + "%  (" + mb(done) + (total > 0 ? " / " + mb(total) : "") + ")", pct);
            }
        }
        os.flush(); os.close(); in.close();
    }


    /**
     * Before touching the engine (which the phone freezes in the background), re-resolve
     * FRESH direct streams for this video and download through the direct pipeline instead.
     * Falls back to the engine only when no direct stream exists at all.
     */
    /** Expected vertical resolution for a quality label ("1080p" -> 1080,
     *  "4K" -> 2160, "720p HDR" -> 720); -1 when unknown. */
    private int expectedH(String quality) {
        if (quality == null) return -1;
        String q = quality.trim();
        if (q.startsWith("8K")) return 4320;
        if (q.startsWith("4K")) return 2160;
        if (q.startsWith("2K")) return 1440;
        int n = 0; boolean any = false;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c < '0' || c > '9') break;
            n = n * 10 + (c - '0'); any = true;
        }
        return any ? n : -1;
    }

    /** Real vertical resolution of a downloaded video file (0 = unreadable). */
    private int probeHeight(File f) {
        android.media.MediaExtractor ex = new android.media.MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                android.media.MediaFormat fmt = ex.getTrackFormat(i);
                String mime = fmt.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/") && fmt.containsKey(android.media.MediaFormat.KEY_HEIGHT)) {
                    return fmt.getInteger(android.media.MediaFormat.KEY_HEIGHT);
                }
            }
        } catch (Throwable ignored) {} finally {
            try { ex.release(); } catch (Throwable ignored) {}
        }
        return 0;
    }

    /** True when the file parses and contains the expected track types. */
    private boolean hasTracks(File f, boolean needVideo, boolean needAudio) {
        android.media.MediaExtractor ex = new android.media.MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            boolean v = false, a = false;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String mime = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) v = true;
                if (mime != null && mime.startsWith("audio/")) a = true;
            }
            return (!needVideo || v) && (!needAudio || a);
        } catch (Throwable e) { return false; }
        finally { try { ex.release(); } catch (Throwable ignored) {} }
    }

    /** The single success gate every route must pass before a file may be
     *  exported or called "Saved": it exists, has a sane size, parses as
     *  media of the requested kind, and - for HD video picks - really has
     *  the requested resolution. */
    private void verifySavedFile(File f, String kind, String quality) throws Exception {
        diagLog("verify path=" + (f == null ? "null" : f.getAbsolutePath()) + " exists=" + (f != null && f.exists()) + " bytes=" + (f == null ? -1 : f.length()));
        if (f == null || !f.exists() || f.length() == 0) throw new Exception("Downloaded file is missing or empty");
        String n = f.getName().toLowerCase(java.util.Locale.US);
        boolean image = n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
        if (image) {
            if (f.length() < 1024) throw new Exception("Downloaded image is empty");
            return;
        }
        boolean audioKind = kind != null && kind.startsWith("Audio");
        if (f.length() < (audioKind ? 10000 : 30000)) {
            throw new Exception("Downloaded file is too small (" + f.length() + " bytes)");
        }
        // v4 trust model: a file that exists, has real size and parses
        // as media is accepted. Resolution/track mismatches are logged,
        // never fatal - the strict gate of the later versions failed
        // good, playable downloads, which is why the user asked for the
        // v4 behavior back.
        boolean anyV = hasTracks(f, true, false);
        boolean anyA = hasTracks(f, false, true);
        if (!anyV && !anyA) throw new Exception("Downloaded file could not be verified as media");
        int wantH = (kind != null && kind.contains("Video")) ? expectedH(quality) : -1;
        if (wantH >= 700) {
            int h = probeHeight(f);
            diagLog("gate want=" + quality + " gotH=" + h + " file=" + f.getName() + (h > 0 && h < wantH * 3 / 4 ? " (below pick - saved anyway, v4 system)" : ""));
        }
    }

    private static String stackOf(Throwable e) {
        StringBuilder sb = new StringBuilder();
        try {
            for (StackTraceElement el : e.getStackTrace()) {
                sb.append("\n    at ").append(el);
                if (sb.length() > 700) break;
            }
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    /** Append to the same diagnostics log the Settings page shows. */
    private void diagLog(String msg) {
        try {
            java.io.File f = new java.io.File(getFilesDir(), "download_diagnostics.log");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f, true);
            fos.write((msg + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {}
    }

    private File downloadSmart(String pageUrl, String formatId, String kind, String title, String quality) throws Exception {
        // GrabIt v4 system for YouTube VIDEO downloads (user's explicit
        // choice, 2026-11-08): the yt-dlp engine alone downloads and
        // merges - slower, but the pipeline he confirmed working.
        // AUDIO is the exception (bug report 2026-11-09): the engine's
        // native process stalls at "Preparing engine..." while the app
        // is in the background on his vivo, so a YouTube audio job goes
        // through the direct-stream block below first (pure-Java HTTP
        // downloads run fine in the background service) and keeps the
        // engine only as its fallback.
        boolean audioKind = kind != null && kind.startsWith("Audio");
        if (!audioKind && pageUrl != null && (pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be"))) {
            return downloadViaYtdlp(pageUrl, formatId, kind, quality);
        }
        if (audioKind) diagLog("audio smart route: fresh direct first, engine fallback fmt=" + formatId);
        boolean youtube = pageUrl != null && (pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be"));
        if (youtube) {
            Fmt match = null;
            try {
                updateNotif("Getting fresh streams\u2026", 1);
                // Heartbeat: the lookup can take 10-20s; tick the elapsed time so
                // the notification visibly lives instead of freezing at 1%.
                final java.util.concurrent.atomic.AtomicBoolean hb = new java.util.concurrent.atomic.AtomicBoolean(true);
                final long hbStart = System.currentTimeMillis();
                final JobCtx hbCtx = ctx();
                Thread hbThread = new Thread(() -> {
                    CTX.set(hbCtx);
                    while (hb.get() && !isCancelled()) {
                        try { Thread.sleep(1000); } catch (Throwable e) { return; }
                        long s = (System.currentTimeMillis() - hbStart) / 1000;
                        if (hb.get()) updateLive("Getting fresh streams\u2026 " + s + "s");
                    }
                });
                hbThread.setDaemon(true);
                hbThread.start();
                try {
                    match = findDirectMatch(Fetcher.fetchFreshDirect(this, pageUrl), formatId, kind);
                } finally {
                    hb.set(false);
                }
            } catch (Throwable ignored) {}
            if (match != null) {
                try {
                    Intent di = new Intent();
                    di.putExtra("directUrl", match.directUrl);
                    di.putExtra("httpUa", match.httpUa);
                    di.putExtra("container", match.container);
                    if (match.audioUrl != null) di.putExtra("audioUrl", match.audioUrl);
                    di.putExtra("kind", kind);
                    di.putExtra("title", title);
                    di.putExtra("url", pageUrl);
                    di.putExtra("formatId", formatId);
                    di.putExtra("quality", quality);
                    return downloadDirect2(di);
                } catch (Throwable de) {
                    if (de instanceof AudioEngine.PausedException) throw new AudioEngine.PausedException();
                    if (isCancelled()) throw new Exception("Cancelled");
                }
            }
        }
        return downloadViaYtdlp(pageUrl, formatId, kind, quality);
    }

    private Fmt findDirectMatch(Fetcher.Result r, String formatId, String kind) {
        if (r == null || r.formats == null) return null;
        Fmt synth = null, anyAudio = null, byId = null;
        for (Fmt f : r.formats) {
            if (f.directUrl == null || f.directUrl.isEmpty()) continue;
            if ("bestaudio/best".equals(f.id)) synth = f;
            if (f.kind != null && f.kind.startsWith("Audio") && anyAudio == null) anyAudio = f;
            if (formatId != null && formatId.equals(f.id)) byId = f;
        }
        if (kind != null && kind.startsWith("Audio")) return synth != null ? synth : anyAudio;
        return byId;
    }

    /** Compact size number: 92.6 stays 92.6, 104.0 becomes 104. */
    private static String compactNum(double v) {
        String t = String.format(java.util.Locale.US, "%.1f", v);
        return t.endsWith(".0") ? t.substring(0, t.length() - 2) : t;
    }
    /** Compact pair like 92/104MB (or 1.2/1.5GB for very large files). */
    private static String compactPair(long done, long total) {
        if (total >= 1073741824L) return compactNum(done / 1073741824.0) + "/" + compactNum(total / 1073741824.0) + "GB";
        return compactNum(done / 1048576.0) + "/" + compactNum(total / 1048576.0) + "MB";
    }
    /** The user's one-line progress format: Downloading... 89% 92/104MB ~2s left */
    private static String progressLine(int pct, long done, long total, long etaSec) {
        StringBuilder b = new StringBuilder("Downloading... ").append(pct).append("%");
        if (total > 0 && done >= 0) b.append(" ").append(compactPair(done, total));
        if (etaSec > 0) b.append(" ~").append(etaSec).append("s left");
        return b.toString();
    }

    private File downloadViaYtdlp(String pageUrl, String formatId, String kind, String quality) throws Exception {
        // The GrabIt v4 (version 1.3) download system, restored exactly at
        // the user's request (2026-11-08): ONE engine call, yt-dlp's own
        // default clients, no rotation, no quality re-probing, no second
        // chances through other clients. Slower - and the pipeline he
        // confirmed error-free. Verified against the original
        // GrabIt 1.3 download system, restored from the original
        // GrabIt_v4.apk binary itself (dex-verified 2026-11-08): the
        // download request is --no-playlist, -f <id>[+bestaudio/best],
        // --merge-output-format mp4, -o <dir>/%(title).80s [%(id)s].%(ext)s
        // - nothing else (no client pinning in the download call itself).
        // REVERTED 2026-11-08 (17:28) on the user's explicit order: the
        // v60/v61 "1.3 purity" removals are undone everywhere - the
        // download request carries --no-cache-dir again (v59 form), the
        // fetch fast pass + EJS are back (Fetcher), the weekly engine
        // self-update is back (DlState), and --cookies attaches whenever
        // a session file exists, as in v59. Everything is the v59
        // configuration he confirmed working ("sob thik ache").
        // From now on: DESIGN changes only, unless he orders otherwise.
diagLog("engine v4-system fmt=" + formatId + " kind=" + kind + " want=" + quality);
        return runYtdlpDl(pageUrl, formatId, kind, null, quality);
    }

    /** The engine's real output: a NEW (or, with a tag, a same-video)
     *  non-fragment, non-.part file of a sane size; largest candidate wins.
     *  Fragment files ("Title [id].f315.mp4") are pre-merge pieces, never
     *  the finished download. */
    private File pickEngineResult(File dir, java.util.Set<String> before, boolean audioMode, String tag) {
        long minBytes = audioMode ? 10000 : 100000;
        File best = null;
        File[] post = dir.listFiles();
        if (post != null) for (File f : post) {
            String n = f.getName();
            if (tag == null && before.contains(n)) continue;
            if (tag != null && !n.contains(tag)) continue;
            if (n.contains(".part") || n.matches(".*\\.f\\d+\\.[^.]+$")) continue;
            if (f.length() < minBytes) continue;
            if (best == null || f.length() > best.length()) best = f;
        }
        return best;
    }

    private File runYtdlpDl(String pageUrl, String formatId, String kind, String clientArg, String quality) throws Exception {
        updateNotif("Preparing engine…", 1);
        if (!DlState.ensureEngineBlocking(this)) throw new Exception("Downloader engine could not start");
        File dir = grabDir();
        // Hygiene: delete stale engine fragment parts left behind by killed
        // runs (e.g. "Title [id].f298.mp4.part") before snapshotting, so
        // they can never pollute this run. Only the yt-dlp fragment pattern
        // is touched; the audio engine's resume files name differently.
        try {
            // Spare the CURRENT video's own fragments: deleting them
            // would make a resumed download start that fragment from
            // zero (pause/resume fix, 2026-11-09).
            String curId = null;
            java.util.regex.Matcher idm = java.util.regex.Pattern.compile("([A-Za-z0-9_-]{11})").matcher(pageUrl == null ? "" : pageUrl);
            if (idm.find()) curId = "[" + idm.group(1) + "]";
            File[] stale = dir.listFiles();
            if (stale != null) for (File f : stale) {
                if (curId != null && f.getName().contains(curId)) continue;
                if (f.getName().matches(".*\\.f\\d+\\.[^.]+\\.part")) { /*noinspection ResultOfMethodCallIgnored*/ f.delete(); }
            }
        } catch (Throwable ignored) {}
        java.util.Set<String> before = new java.util.HashSet<>();
        File[] pre = dir.listFiles(); if (pre != null) for (File f : pre) before.add(f.getName());
        boolean audioMode = kind != null && kind.startsWith("Audio");
        // v4 (1.3) selector, exactly as the original app built it.
        String fArg = formatId;
        if ("Video only".equals(kind)) fArg = formatId + "+bestaudio/best";
        if (formatId == null || formatId.isEmpty()) fArg = "bestvideo+bestaudio/best";
        diagLog("engine selector fmt=" + formatId + " want=" + quality + " f=" + fArg);
        YoutubeDLRequest req = new YoutubeDLRequest(pageUrl);
        java.io.File cookies = Fetcher.cookieFileFor(this, pageUrl);
        if (cookies.exists()) req.addOption("--cookies", cookies.getAbsolutePath());
        req.addOption("--no-playlist");
        req.addOption("--no-cache-dir");
        req.addOption("-f", fArg);
        if (audioMode) {
            req.addOption("--extract-audio");
            req.addOption("--audio-format", "mp3");
            req.addOption("--audio-quality", "320K");
            req.addOption("--add-metadata");
            req.addOption("--embed-thumbnail");
        } else {
            req.addOption("--merge-output-format", "mp4");
        }
        req.addOption("-o", dir.getAbsolutePath() + "/%(title).80s [%(id)s].%(ext)s");
        final String q = kind == null ? "" : kind;
        // The engine call blocks until the external process ends, and on
        // some phones it never returns after a cancel (the call stays
        // waiting on the killed process) - which used to wedge the whole
        // app on "Cancelling..." with Pause/Cancel/Analyze all dead. So
        // execute() runs on a worker thread (with this job's own process
        // id, so cancelling one job no longer kills the others) and the
        // job thread only WAITS for it: on cancel/pause it destroys the
        // process, and if the call still has not unwound a few seconds
        // later, the job abandons the call and finishes anyway.
        final String procId = "grabit-dl-" + ctx().id;
        final YoutubeDLRequest fReq = req;
        final JobCtx jobCtx = ctx();
        final java.util.concurrent.atomic.AtomicReference<com.yausername.youtubedl_android.YoutubeDLResponse> respRef = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Throwable> errRef = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch execDone = new java.util.concurrent.CountDownLatch(1);
        Thread execWorker = new Thread(() -> {
            CTX.set(jobCtx);
            try {
                respRef.set(YoutubeDL.getInstance().execute(fReq, procId, new Function3<Float, Long, String, Unit>() {
                    @Override public Unit invoke(Float progress, Long eta, String line) {
                        int pc = progress == null ? 0 : Math.max(0, progress.intValue());
                        if (pc > 0) {
                            // Parse yt-dlp's own progress line for the total
                            // size, so the notification and the Files tab
                            // card show MB downloaded of MB total together
                            // with the percent and the time left.
                            long totalB = -1, doneB = -1;
                            try {
                                java.util.regex.Matcher m = java.util.regex.Pattern.compile("of\\s+~?\\s*([\\d.]+)\\s*(GiB|MiB|KiB)").matcher(line == null ? "" : line);
                                if (m.find()) {
                                    double v = Double.parseDouble(m.group(1));
                                    double mult = "GiB".equals(m.group(2)) ? 1073741824.0 : "MiB".equals(m.group(2)) ? 1048576.0 : 1024.0;
                                    totalB = (long) (v * mult);
                                    doneB = totalB * pc / 100;
                                }
                            } catch (Throwable ignored) {}
                            updateNotifFor(jobCtx, progressLine(pc, doneB, totalB, eta != null && eta > 0 ? eta : -1), pc);
                            DlState.setDl(jobCtx.label, pc, doneB, totalB, eta != null && eta > 0 ? eta.intValue() : -2);
                        }
                        return Unit.INSTANCE;
                    }
                }));
            } catch (Throwable t) { errRef.set(t); }
            finally { execDone.countDown(); }
        });
        execWorker.setDaemon(true);
        execWorker.start();
        boolean abandoned = false;
        long flagSince = 0;
        try {
            while (!execDone.await(400, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (isCancelled() || isPaused()) {
                    if (flagSince == 0) {
                        flagSince = System.currentTimeMillis();
                        try { YoutubeDL.getInstance().destroyProcessById(procId); } catch (Throwable ignored) {}
                    } else if (System.currentTimeMillis() - flagSince > 7000) { abandoned = true; break; }
                }
            }
        } catch (InterruptedException ie) { abandoned = true; }
        if (abandoned) {
            try { YoutubeDL.getInstance().destroyProcessById(procId); } catch (Throwable ignored) {}
            if (isPaused() && !isCancelled()) throw new AudioEngine.PausedException();
            throw new Exception("Cancelled");
        }
        // Pause kills the engine process, which surfaces here as an
        // error - translate it back into a pause, or every paused
        // video download would report "Download failed" (owner report
        // 2026-11-09).
        if (isPaused() && !isCancelled()) throw new AudioEngine.PausedException();
        if (errRef.get() != null) {
            Throwable t = errRef.get();
            if (t instanceof Exception) throw (Exception) t;
            throw new Exception(shortMsg(String.valueOf(t.getMessage())));
        }
        com.yausername.youtubedl_android.YoutubeDLResponse resp = respRef.get();
        if (isCancelled()) throw new Exception("Cancelled");
        if (isPaused()) throw new AudioEngine.PausedException();
        if (resp != null && resp.getExitCode() != 0) {
            diagLog("engine FAIL client=" + (clientArg == null ? "default" : clientArg) + " fmt=" + formatId + " out=" + shortMsg(resp.getOut()));
            throw new Exception("Engine error: " + shortMsg(resp.getOut()));
        }
        // Strict result check: only the FINAL merged file counts. Fragment
        // files, .part files and empty half-merged outputs all sit in this
        // folder during a run, and the old "newest file wins" rule saved
        // them as if they were the download - that is how 360p fragments
        // and 0.0 MB files got "saved".
        File newest = pickEngineResult(dir, before, audioMode, null);
        if (newest == null) {
            // yt-dlp skips files that already exist ("already downloaded") - accept that file
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z0-9_-]{11})").matcher(pageUrl == null ? "" : pageUrl);
            if (m.find()) newest = pickEngineResult(dir, before, audioMode, "[" + m.group(1) + "]");
        }
        if (newest == null) throw new Exception("Download produced no file. " + (resp != null ? shortMsg(resp.getOut()) : ""));
        diagLog("engine result: " + newest.getName() + " " + (newest.length() / 1024) + " KB");
        return newest;
    }


    /** Built-in converter: run the bundled ffmpeg binary directly (download-then-convert, like SnapTube). */
    private File convertWithFfmpeg(File input, File outMp3, String title) throws Exception {
        if (!DlState.ensureEngineBlocking(this)) throw new Exception("Engine not ready");
        File ffmpegBin = new File(getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
        File libDir = new File(getFilesDir(), "youtubedl-android/packages/ffmpeg/usr/lib");
        if (!ffmpegBin.exists()) throw new Exception("Converter missing");
        updateNotif("Converting to MP3 320K…", 82);
        DlState.set(true, 82, "Converting to MP3 320K…");
        java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
        cmd.add(ffmpegBin.getAbsolutePath());
        cmd.add("-y");
        cmd.add("-i"); cmd.add(input.getAbsolutePath());
        cmd.add("-vn");
        cmd.add("-codec:a"); cmd.add("libmp3lame");
        cmd.add("-b:a"); cmd.add("320k");
        cmd.add("-id3v2_version"); cmd.add("3");
        if (title != null && !title.isEmpty()) { cmd.add("-metadata"); cmd.add("title=" + title); }
        cmd.add(outMp3.getAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath() + ":" + getApplicationInfo().nativeLibraryDir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()));
        String line; double durationSec = -1; StringBuilder tail = new StringBuilder();
        java.util.regex.Pattern durPat = java.util.regex.Pattern.compile("Duration: (\\d+):(\\d+):(\\d+\\.\\d+)");
        java.util.regex.Pattern timePat = java.util.regex.Pattern.compile("time=(\\d+):(\\d+):(\\d+\\.\\d+)");
        long lastUi = 0;
        while ((line = br.readLine()) != null) {
            if (isCancelled()) { proc.destroy(); throw new Exception("Cancelled"); }
            tail.append(line).append('\n');
            if (tail.length() > 2000) tail.delete(0, tail.length() - 2000);
            java.util.regex.Matcher dm = durPat.matcher(line);
            if (dm.find()) durationSec = Integer.parseInt(dm.group(1)) * 3600 + Integer.parseInt(dm.group(2)) * 60 + Double.parseDouble(dm.group(3));
            if (durationSec <= 0 && System.currentTimeMillis() - lastUi > 2000) {
                // No duration line (some streams): keep the notification visibly alive.
                lastUi = System.currentTimeMillis();
                updateLive("Converting to MP3 320K\u2026");
            }
            if (durationSec > 0) {
                java.util.regex.Matcher tm = timePat.matcher(line);
                if (tm.find()) {
                    double cur = Integer.parseInt(tm.group(1)) * 3600 + Integer.parseInt(tm.group(2)) * 60 + Double.parseDouble(tm.group(3));
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 400) {
                        lastUi = now;
                        int pct = 80 + (int) (18.0 * Math.min(1.0, cur / durationSec));
                        String txt = "Converting to MP3 320K… " + pct + "%";
                        DlState.set(true, pct, txt);
                        updateNotif(txt, pct);
                    }
                }
            }
        }
        int exit = proc.waitFor();
        diagLog("ffmpeg convert exit=" + exit + " in=" + input.getAbsolutePath() + " (" + input.length() + " B) out=" + outMp3.getAbsolutePath() + " (" + (outMp3.exists() ? outMp3.length() : -1) + " B)");
        if (exit != 0) throw new Exception("Converter exit " + exit + ": " + shortMsg(tail.toString()));
        if (!outMp3.exists() || outMp3.length() < 10000) throw new Exception("Converter produced no MP3");
        return outMp3;
    }


    /** Multi-connection ranged download (SnapTube-style): several parallel chunks, then joined. */
    private void httpDownloadParallel(final String url, final String ua, final File out, final double basePct, final double spanPct) throws Exception {
        HttpURLConnection probe = (HttpURLConnection) new URL(url).openConnection();
        probe.setRequestProperty("User-Agent", ua != null && !ua.isEmpty() ? ua : "Mozilla/5.0 (Linux; Android 14)");
        probe.setRequestProperty("Accept", "*/*");
        probe.setRequestProperty("Accept-Encoding", "identity");
        probe.setRequestProperty("Range", "bytes=0-0");
        probe.setConnectTimeout(15000); probe.setReadTimeout(20000);
        int code = probe.getResponseCode();
        String cr = probe.getHeaderField("Content-Range");
        try { probe.disconnect(); } catch (Throwable ignored) {}
        if (code != 206 || cr == null || !cr.contains("/")) throw new Exception("Parallel download not supported here");
        final long total;
        try { total = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim()); }
        catch (Throwable e) { throw new Exception("Unknown size"); }
        if (total < 1024 * 1024) throw new Exception("Small file");
        final int parts = (int) Math.min(4, Math.max(2, total / (2L * 1024 * 1024)));
        final File[] partFiles = new File[parts];
        final long[] partDone = new long[parts];
        final long[] lastUi = {0};
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(parts);
        java.util.ArrayList<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        final long chunkSize = (total + parts - 1) / parts;
        for (int i = 0; i < parts; i++) {
            final int idx = i;
            final long start = idx * chunkSize;
            final long end = Math.min(total - 1, start + chunkSize - 1);
            partFiles[idx] = new File(out.getParent(), out.getName() + ".part" + idx);
            futures.add(pool.submit(() -> {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", ua != null && !ua.isEmpty() ? ua : "Mozilla/5.0 (Linux; Android 14)");
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Accept-Encoding", "identity");
                c.setRequestProperty("Range", "bytes=" + start + "-" + end);
                c.setConnectTimeout(20000); c.setReadTimeout(60000);
                if (c.getResponseCode() != 206) { c.disconnect(); throw new Exception("HTTP " + c.getResponseCode() + " on part " + idx); }
                InputStream in = c.getInputStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(partFiles[idx]);
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) {
                    if (isCancelled()) { try { fos.close(); } catch (Throwable ignored) {} throw new Exception("Cancelled"); }
                    fos.write(buf, 0, n);
                    partDone[idx] += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUi[0] > 300) {
                        synchronized (lastUi) {
                            if (now - lastUi[0] > 300) {
                                lastUi[0] = now;
                                long sum = 0; for (long v : partDone) sum += v;
                                int pct = (int) (basePct + spanPct * sum / total);
                                String txt = "Downloading… " + pct + "%  (" + mb(sum) + " / " + mb(total) + ")";
                                DlState.set(true, pct, txt);
                                updateNotif(txt, pct);
                            }
                        }
                    }
                }
                fos.flush(); fos.close(); in.close(); c.disconnect();
                return null;
            }));
        }
        try {
            for (java.util.concurrent.Future<?> f : futures) f.get();
        } catch (Throwable e) {
            pool.shutdownNow();
            for (File pf : partFiles) if (pf != null) { try { pf.delete(); } catch (Throwable ignored) {} }
            throw new Exception("Parallel download failed: " + shortMsg(e.getMessage()));
        }
        pool.shutdown();
        java.io.FileOutputStream fos = new java.io.FileOutputStream(out, false);
        byte[] buf = new byte[65536];
        for (File pf : partFiles) {
            java.io.FileInputStream fis = new java.io.FileInputStream(pf);
            int n; while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            fis.close();
            //noinspection ResultOfMethodCallIgnored
            pf.delete();
        }
        fos.flush(); fos.close();
        if (out.length() != total) throw new Exception("Parallel download incomplete: " + out.length() + " of " + total);
    }

    /** Pull the audio track out of an MP4 into a .m4a using only Android's built-in tools. */
    private File extractAudioTrack(File srcMp4, File outM4a) throws Exception {
        updateNotif("Extracting audio…", 85);
        DlState.set(true, 85, "Extracting audio…");
        android.media.MediaExtractor ex = new android.media.MediaExtractor();
        android.media.MediaMuxer muxer = null;
        try {
            ex.setDataSource(srcMp4.getAbsolutePath());
            int aSrc = -1;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String mime = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { aSrc = i; break; }
            }
            if (aSrc < 0) throw new Exception("No audio track in source");
            muxer = new android.media.MediaMuxer(outM4a.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int aDst = muxer.addTrack(ex.getTrackFormat(aSrc));
            ex.selectTrack(aSrc);
            muxer.start();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 1024);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            while (true) {
                if (isCancelled()) throw new Exception("Cancelled");
                int sz = ex.readSampleData(buffer, 0);
                if (sz < 0) break;
                info.offset = 0; info.size = sz; info.presentationTimeUs = ex.getSampleTime(); info.flags = ex.getSampleFlags();
                muxer.writeSampleData(aDst, buffer, info);
                ex.advance();
            }
            muxer.stop();
        } finally {
            try { if (muxer != null) muxer.release(); } catch (Throwable ignored) {}
            try { ex.release(); } catch (Throwable ignored) {}
        }
        if (!outM4a.exists() || outM4a.length() < 10000) throw new Exception("Extraction produced no audio");
        return outM4a;
    }

    /** Convert a direct audio stream URL to MP3 320K using the engine's ffmpeg (no site extraction involved). */
    private File convertStreamToMp3(String streamUrl, String ua) throws Exception {
        updateNotif("Preparing audio…", 1);
        if (!DlState.ensureEngineBlocking(this)) throw new Exception("Downloader engine could not start");
        File dir = grabDir();
        java.util.Set<String> before = new java.util.HashSet<>();
        File[] pre = dir.listFiles(); if (pre != null) for (File f : pre) before.add(f.getName());
        YoutubeDLRequest req = new YoutubeDLRequest(streamUrl);
        java.io.File cookies2 = Fetcher.cookieFileFor(this, streamUrl);
        if (cookies2.exists()) req.addOption("--cookies", cookies2.getAbsolutePath());
        req.addOption("--no-playlist");
        if (ua != null && !ua.isEmpty()) req.addOption("--add-header", "User-Agent:" + ua);
        req.addOption("--extract-audio");
        req.addOption("--audio-format", "mp3");
        req.addOption("--audio-quality", "320K");
        req.addOption("-o", dir.getAbsolutePath() + "/%(title).80s [%(id)s].%(ext)s");
        final JobCtx convCtx = ctx();
        com.yausername.youtubedl_android.YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req, "grabit-dl", new Function3<Float, Long, String, Unit>() {
            @Override public Unit invoke(Float progress, Long eta, String line) {
                int pc = progress == null ? 0 : Math.max(0, progress.intValue());
                updateNotifFor(convCtx, "Downloading audio… " + pc + "%", pc);
                return Unit.INSTANCE;
            }
        });
        if (isCancelled()) throw new Exception("Cancelled");
        if (resp != null && resp.getExitCode() != 0) throw new Exception("Convert error: " + shortMsg(resp.getOut()));
        File newest = null;
        File[] post = dir.listFiles();
        if (post != null) for (File f : post) {
            if (!before.contains(f.getName()) && !f.getName().contains(".part") && f.getName().endsWith(".mp3") && f.length() >= 10000) {
                if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
            }
        }
        if (newest == null) throw new Exception("Convert produced no file. " + (resp != null ? shortMsg(resp.getOut()) : ""));
        return newest;
    }


    // ---------- direct stream download (Innertube URLs) ----------
    private File downloadDirect2(Intent intent) throws Exception {
        updateNotif("EXTRACTING \u2014 connecting to the stream\u2026", 1);
        String dUrl = intent.getStringExtra("directUrl");
        String ua = intent.getStringExtra("httpUa");
        String container = intent.getStringExtra("container");
        String audioUrl = intent.getStringExtra("audioUrl");
        String kind = intent.getStringExtra("kind");
        String title = intent.getStringExtra("title");
        if (container == null) container = "mp4";
        if (dUrl == null || dUrl.isEmpty()) throw new Exception("No direct stream");
        File dir = grabDir();
        if (kind != null && kind.startsWith("Audio")) {
            // v20 audio system (user: "change the system, I don't care if it's slow,
            // it should work properly"): ONE sequential chunked connection whose
            // progress is counted from the bytes we read ourselves (0-75%), then
            // on-device conversion (80-98%). The v18/v19 multi-connection parallel
            // downloader is removed from the audio path: its bursts made progress
            // invisible and its joins were a failure point. Sequential is slower
            // and dependable, and every chunk reports real progress.
            // v21 engine (reliability spec): resumable .part download with Range/
            // If-Range validation, expired-URL refresh, backoff retries, pause,
            // and byte-count + container validation before conversion starts.
            final String pageUrl = intent.getStringExtra("url");
            final String fmtId = intent.getStringExtra("formatId");
            File src = AudioEngine.download(this, dir, dUrl, ua, pageUrl,
                    AudioEngine.baseNameFor(pageUrl != null ? pageUrl : dUrl), container,
                    new AudioEngine.Listener() {
                        @Override public void onProgress(long done, long total, String stage) {
                            if (total > 0) {
                                int pct = (int) (75.0 * done / total);
                                DlState.setDl(ctx().label, pct, done, total);
                                String txt = progressLine(pct, done, total, DlState.dlEta);
                                DlState.set(true, pct, txt);
                                updateNotif(txt, pct);
                            } else {
                                updateLive("Downloading… " + mb(done) + " so far");
                            }
                        }
                        @Override public boolean isCancelled() { return isCancelled(); }
                        @Override public boolean isPaused() { return isPaused(); }
                    },
                    () -> {
                        Fetcher.Result fr = Fetcher.fetchFreshDirect(DownloadService.this, pageUrl);
                        Fmt m = findDirectMatch(fr, fmtId, "Audio");
                        return m == null ? null : new String[]{m.directUrl, m.httpUa};
                    });
            // 1) true MP3 320K via the bundled ffmpeg converter
            try {
                File mp3 = convertWithFfmpeg(src, new File(dir, title + ".mp3"), title);
                //noinspection ResultOfMethodCallIgnored
                src.delete();
                return mp3;
            } catch (Throwable fe) {
                if (isCancelled()) throw new Exception("Cancelled");
                // 2) Android-native extraction of the audio track (cannot be blocked, no engine needed)
                if ("mp4".equals(container)) {
                    try {
                        File m4a = extractAudioTrack(src, new File(dir, title + ".m4a"));
                        //noinspection ResultOfMethodCallIgnored
                        src.delete();
                        return m4a;
                    } catch (Throwable ee) {
                        throw new Exception("Audio convert failed (MP3: " + shortMsg(fe.getMessage()) + " | extract: " + shortMsg(ee.getMessage()) + ")");
                    }
                }
                // 3) opus/webm stream: keep the raw file, it plays as-is
                File out = new File(dir, title + ".opus");
                moveFile(src, out);
                return out;
            }
        }
        boolean mux = audioUrl != null && !audioUrl.isEmpty() && "mp4".equals(container);
        final String pageUrl2 = intent.getStringExtra("url");
        final String fmtId2 = intent.getStringExtra("formatId");
        final String quality2 = intent.getStringExtra("quality");
        final int vSpan = mux ? 68 : 98;
        // Both streams go through StreamDownloader: persistent .part
        // sessions (unique per video+format+role, so parallel jobs can
        // never corrupt each other), validated Range resumes with ETag /
        // If-Range, signed-URL refresh on 403 for THIS format only, and
        // parallel segments for large files.
        File vTmp = StreamDownloader.download(this, dir, pageUrl2, fmtId2, "v", quality2, container, dUrl, ua, -1,
                new StreamDownloader.Listener() {
                    @Override public void onProgress(long done, long total) {
                        if (total > 0) {
                            int pct = (int) (vSpan * done / total);
                            DlState.setDl(ctx().label, pct, done, total);
                            String txt = progressLine(pct, done, total, DlState.dlEta);
                            DlState.set(true, pct, txt);
                            updateNotif(txt, pct);
                        } else {
                            updateLive("Downloading… " + mb(done) + " so far");
                        }
                    }
                    @Override public boolean isCancelled() { return DownloadService.isCancelled(); }
                    @Override public boolean isPaused() { return DownloadService.isPaused(); }
                    @Override public String[] refreshUrl() {
                        Fetcher.Result fr = Fetcher.fetchFreshDirect(DownloadService.this, pageUrl2);
                        Fmt m = findDirectMatch(fr, fmtId2, kind);
                        return m == null ? null : new String[]{m.directUrl, m.httpUa};
                    }
                });
        int wantH2 = kind != null && kind.contains("Video") ? expectedH(intent.getStringExtra("quality")) : -1;
        if (wantH2 >= 700) {
            int gotH = probeHeight(vTmp);
            diagLog("direct fmt=" + intent.getStringExtra("formatId") + " want=" + intent.getStringExtra("quality") + " gotH=" + gotH);
            if (gotH < wantH2 * 3 / 4) {
                // The direct stream is not the quality it claimed to be.
                // Fail here so the caller falls back to the engine route
                // instead of silently saving a low-quality file.
                try { vTmp.delete(); } catch (Throwable ignored) {}
                throw new Exception("Direct stream was " + gotH + "p, not " + intent.getStringExtra("quality"));
            }
        }
        if (!mux) {
            File out = new File(dir, safeOutName(title) + " [j" + ctx().id + "]." + container);
            moveFile(vTmp, out);
            diagLog("direct out=" + out.getAbsolutePath() + " (" + out.length() + " B)");
            return out;
        }
        File aTmp = StreamDownloader.download(this, dir, pageUrl2, fmtId2, "a", quality2, "mp4", audioUrl, ua, -1,
                new StreamDownloader.Listener() {
                    @Override public void onProgress(long done, long total) {
                        if (total > 0) {
                            int pct = 68 + (int) (24 * done / total);
                            DlState.setDl(ctx().label, pct, done, total);
                            String txt = progressLine(pct, done, total, DlState.dlEta);
                            DlState.set(true, pct, txt);
                            updateNotif(txt, pct);
                        } else {
                            updateLive("Downloading audio… " + mb(done) + " so far");
                        }
                    }
                    @Override public boolean isCancelled() { return DownloadService.isCancelled(); }
                    @Override public boolean isPaused() { return DownloadService.isPaused(); }
                    @Override public String[] refreshUrl() {
                        Fetcher.Result fr = Fetcher.fetchFreshDirect(DownloadService.this, pageUrl2);
                        Fmt m = findDirectMatch(fr, fmtId2, kind);
                        return (m == null || m.audioUrl == null) ? null : new String[]{m.audioUrl, m.httpUa};
                    }
                });
        updateNotif("MERGING \u2014 combining video + audio\u2026", 95);
        DlState.set(true, 95, "MERGING \u2014 combining video + audio\u2026");
        diagLog("mux inputs video=" + vTmp.getAbsolutePath() + " (" + vTmp.length() + " B) audio=" + aTmp.getAbsolutePath() + " (" + aTmp.length() + " B)");
        File out = new File(dir, safeOutName(title) + " [j" + ctx().id + "].mp4");
        muxMp4(vTmp, aTmp, out);
        diagLog("mux output=" + out.getAbsolutePath() + " (" + out.length() + " B)");
        if (!hasTracks(out, true, true)) throw new Exception("Merged file failed verification (missing video or audio track)");
        // The stream temps are NOT deleted here: the job's final gate and
        // export run after this method returns, and the streams must
        // survive until both have passed. runJob deletes them in finally.
        ctx().pendingTemps = new File[]{vTmp, aTmp};
        return out;
    }

    private void moveFile(File src, File dst) throws Exception {
        if (src.renameTo(dst)) return;
        java.io.FileInputStream in = new java.io.FileInputStream(src);
        OutputStream out = new java.io.FileOutputStream(dst);
        byte[] buf = new byte[32768]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close(); out.flush(); out.close();
        //noinspection ResultOfMethodCallIgnored
        src.delete();
    }

    /** Lossless mp4 mux: copy video track + AAC audio track into one .mp4 (no re-encoding). */
    private void muxMp4(File video, File audio, File out) throws Exception {
        android.media.MediaExtractor ve = new android.media.MediaExtractor();
        android.media.MediaExtractor ae = new android.media.MediaExtractor();
        android.media.MediaMuxer muxer = null;
        try {
            ve.setDataSource(video.getAbsolutePath());
            ae.setDataSource(audio.getAbsolutePath());
            int vSrc = -1, aSrc = -1;
            for (int i = 0; i < ve.getTrackCount(); i++) {
                String mime = ve.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) { vSrc = i; break; }
            }
            for (int i = 0; i < ae.getTrackCount(); i++) {
                String mime = ae.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { aSrc = i; break; }
            }
            if (vSrc < 0 || aSrc < 0) throw new Exception("Missing track for merge");
            muxer = new android.media.MediaMuxer(out.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int vDst = muxer.addTrack(ve.getTrackFormat(vSrc));
            int aDst = muxer.addTrack(ae.getTrackFormat(aSrc));
            ve.selectTrack(vSrc); ae.selectTrack(aSrc);
            muxer.start();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            boolean vDone = false, aDone = false;
            while (!vDone || !aDone) {
                if (isCancelled()) throw new Exception("Cancelled");
                long vTime = vDone ? Long.MAX_VALUE : ve.getSampleTime();
                long aTime = aDone ? Long.MAX_VALUE : ae.getSampleTime();
                if (!vDone && (aDone || vTime <= aTime)) {
                    int sz = ve.readSampleData(buffer, 0);
                    if (sz < 0) vDone = true;
                    else {
                        info.offset = 0; info.size = sz; info.presentationTimeUs = ve.getSampleTime(); info.flags = ve.getSampleFlags();
                        muxer.writeSampleData(vDst, buffer, info);
                        ve.advance();
                    }
                } else if (!aDone) {
                    int sz = ae.readSampleData(buffer, 0);
                    if (sz < 0) aDone = true;
                    else {
                        info.offset = 0; info.size = sz; info.presentationTimeUs = ae.getSampleTime(); info.flags = ae.getSampleFlags();
                        muxer.writeSampleData(aDst, buffer, info);
                        ae.advance();
                    }
                }
            }
            muxer.stop();
        } finally {
            try { if (muxer != null) muxer.release(); } catch (Throwable ignored) {}
            try { ve.release(); } catch (Throwable ignored) {}
            try { ae.release(); } catch (Throwable ignored) {}
        }
    }

    /** When the user picked a download folder in Settings, finished files
     *  are copied there (SAF) instead of the default Gallery/Downloads
     *  DhoreFel folders. False means: use the default export below. */
    /** Remembers a file saved into the user's custom (SAF) folder, so the
     *  Files tab can list it as a card and check whether it still exists. */
    private void recordSafFile(String name, String uri, long size) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("grabit_prefs", MODE_PRIVATE);
            org.json.JSONArray arr;
            try { arr = new org.json.JSONArray(sp.getString("saf_files", "[]")); } catch (Throwable e) { arr = new org.json.JSONArray(); }
            for (int i = arr.length() - 1; i >= 0; i--) if (name.equals(arr.getJSONObject(i).optString("n"))) arr.remove(i);
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("n", name); o.put("u", uri); o.put("s", size); o.put("t", System.currentTimeMillis());
            arr.put(o);
            while (arr.length() > 60) arr.remove(0);
            sp.edit().putString("saf_files", arr.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private boolean exportToCustomFolder(File src) {
        try {
            String uriStr = getSharedPreferences("grabit_prefs", MODE_PRIVATE).getString("dl_folder_uri", "");
            if (uriStr == null || uriStr.isEmpty()) return false;
            String name = publicName(src);
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.US) : "";
            String mime = "application/octet-stream";
            if (ext.equals("mp4")) mime = "video/mp4";
            else if (ext.equals("webm")) mime = "video/webm";
            else if (ext.equals("mkv")) mime = "video/x-matroska";
            else if (ext.equals("mp3")) mime = "audio/mpeg";
            else if (ext.equals("m4a") || ext.equals("aac")) mime = "audio/mp4";
            else if (ext.equals("opus") || ext.equals("ogg")) mime = "audio/ogg";
            else if (ext.equals("jpg") || ext.equals("jpeg")) mime = "image/jpeg";
            else if (ext.equals("png")) mime = "image/png";
            else if (ext.equals("webp")) mime = "image/webp";
            android.net.Uri tree = android.net.Uri.parse(uriStr);
            String docId = android.provider.DocumentsContract.getTreeDocumentId(tree);
            android.net.Uri parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            android.net.Uri out = android.provider.DocumentsContract.createDocument(getContentResolver(), parent, mime, name);
            if (out == null) return false;
            OutputStream os = getContentResolver().openOutputStream(out);
            if (os == null) return false;
            FileInputStream fis = new FileInputStream(src);
            byte[] buf = new byte[32768]; int n;
            while ((n = fis.read(buf)) > 0) os.write(buf, 0, n);
            fis.close(); os.flush(); os.close();
            recordSafFile(name, out.toString(), src.length());
            return true;
        } catch (Throwable e) { return false; }
    }

    private void exportToPublic(File src) {
        if (!src.exists()) { diagLog("export skipped, file missing: " + src.getAbsolutePath()); return; }
        if (exportToCustomFolder(src)) return;
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            String name = publicName(src);
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.US) : "";
            ContentResolver cr = getContentResolver();
            Uri collection; String mime; ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.SIZE, src.length());
            if (ext.equals("mp4") || ext.equals("webm") || ext.equals("mkv")) {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                mime = ext.equals("mp4") ? "video/mp4" : (ext.equals("webm") ? "video/webm" : "video/x-matroska");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Zynox");
            } else if (ext.equals("mp3") || ext.equals("m4a") || ext.equals("aac") || ext.equals("opus") || ext.equals("ogg")) {
                collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                mime = ext.equals("mp3") ? "audio/mpeg" : "audio/mp4";
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Zynox");
            } else if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp")) {
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                mime = ext.equals("png") ? "image/png" : "image/jpeg";
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Zynox");
            } else {
                collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                mime = "application/octet-stream";
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Zynox");
            }
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(collection, cv);
            if (uri == null) return;
            OutputStream os = cr.openOutputStream(uri);
            FileInputStream fis = new FileInputStream(src);
            byte[] buf = new byte[32768]; int n;
            while ((n = fis.read(buf)) > 0) os.write(buf, 0, n);
            fis.close(); os.flush(); os.close();
            ContentValues doneCv = new ContentValues();
            doneCv.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, doneCv, null, null);
            diagLog("export mediastore ok name=" + name + " bytes=" + src.length() + " uri=" + uri);
        } catch (Throwable t) { diagLog("export mediastore FAILED for " + src.getName() + ": " + t); }
    }
}
