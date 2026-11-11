package com.shadhinkhan.grabit;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * AudioEngine - the rebuilt audio download engine (SnapTube-level reliability spec).
 *
 * One resumable, self-healing downloader for audio sources:
 *  - Streams to a .part file with a JSON sidecar (never loads a file into RAM).
 *  - HTTP Range resume with If-Range (ETag / Last-Modified) validation, strict
 *    206 + Content-Range checks, safe restart on 200, correct 416 handling.
 *  - Expired signed URLs (403) are refreshed through a UrlRefresher and resumed.
 *  - Exponential backoff with jitter, Retry-After support, permanent vs temporary
 *    error classification (never retries permanent errors endlessly).
 *  - Pause / cancel at chunk boundaries with state persisted for later resume,
 *    including after process death (deterministic per-video file names).
 *  - Completion validation: exact byte count, HTML-error-page sniffing, and a
 *    MediaExtractor parse check before anything is called "downloaded".
 *  - DownloadDiagnostics: host, status, retries, bytes, range support, ETag,
 *    duration, error category - never URLs' query strings, tokens or cookies.
 */
public class AudioEngine {

    // ---------------- public contracts ----------------
    public interface Listener {
        /** done/total in bytes; total = -1 when unknown. stage is a short label. */
        void onProgress(long done, long total, String stage);
        boolean isCancelled();
        boolean isPaused();
    }

    /** Returns {newMediaUrl, newUa} for the same resource, or null when refresh is impossible. */
    public interface UrlRefresher { String[] refresh() throws Exception; }

    public static class PausedException extends Exception { public PausedException() { super("Paused"); } }
    public static class CancelledException extends Exception { public CancelledException() { super("Cancelled"); } }
    public static class FailedException extends Exception {
        public final String category; public final boolean permanent;
        public FailedException(String category, boolean permanent, String msg) { super(msg); this.category = category; this.permanent = permanent; }
    }

    public static class Diagnostics {
        public String host = "";
        public int lastStatus = 0;
        public String failureReason = "";
        public int retryCount = 0;
        public long downloadedBytes = 0;
        public long expectedBytes = -1;
        public boolean rangeSupport = false;
        public String etag = "";
        public long durationMs = 0;
        public String errorCategory = "";
        public String result = "";
        String toLogLine() {
            try {
                JSONObject o = new JSONObject();
                o.put("ts", System.currentTimeMillis());
                o.put("host", host);
                o.put("status", lastStatus);
                o.put("failureReason", failureReason);
                o.put("retries", retryCount);
                o.put("downloaded", downloadedBytes);
                o.put("expected", expectedBytes);
                o.put("range", rangeSupport);
                o.put("etag", etag == null ? "" : etag);
                o.put("durationMs", durationMs);
                o.put("category", errorCategory);
                o.put("result", result);
                return o.toString();
            } catch (Throwable e) { return "{}"; }
        }
    }

    // ---------------- configuration ----------------
    private static final int BUFFER_SIZE = 64 * 1024;          // configurable streaming buffer
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_ATTEMPTS = 6;                  // tries per download() call
    private static final int MAX_REFRESHES = 2;                 // signed-URL refreshes per call
    private static final long SYNC_EVERY_BYTES = 2L * 1024 * 1024;

    // ---------------- pending job (process-death restore) ----------------
    private static File pendingFile(Context ctx) { return new File(ctx.getFilesDir(), "pending_audio.json"); }

    public static void savePending(Context ctx, String pageUrl, String formatId, String title) {
        try {
            JSONObject o = new JSONObject();
            o.put("pageUrl", pageUrl); o.put("formatId", formatId); o.put("title", title);
            o.put("at", System.currentTimeMillis());
            FileOutputStream fos = new FileOutputStream(pendingFile(ctx));
            fos.write(o.toString().getBytes("UTF-8")); fos.close();
        } catch (Throwable ignored) {}
    }

    public static JSONObject readPending(Context ctx) {
        try {
            File f = pendingFile(ctx);
            if (!f.exists()) return null;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = fis.read(buf); fis.close();
            if (n <= 0) return null;
            return new JSONObject(new String(buf, 0, n, "UTF-8"));
        } catch (Throwable e) { return null; }
    }

    public static void clearPending(Context ctx) { try { pendingFile(ctx).delete(); } catch (Throwable ignored) {} }

    /** Deterministic per-video base name, so a later attempt finds the same .part after process death. */
    public static String baseNameFor(String pageUrl) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((pageUrl == null ? "" : pageUrl).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder("audio_");
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Throwable e) { return "audio_dl"; }
    }

    public static void appendDiagnostics(Context ctx, Diagnostics d) {
        try {
            File f = new File(ctx.getFilesDir(), "download_diagnostics.log");
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write((d.toLogLine() + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {}
    }

    // ---------------- the engine ----------------
    /**
     * Download mediaUrl into dir as baseName + "." + container, resumably.
     * Returns the completed, validated file. Throws Paused/Cancelled/Failed.
     */
    public static File download(Context ctx, File dir, String mediaUrl, String ua, String pageUrl,
                                String baseName, String container, Listener listener, UrlRefresher refresher) throws Exception {
        Diagnostics diag = new Diagnostics();
        long t0 = System.currentTimeMillis();
        try {
            File out = doDownload(dir, mediaUrl, ua, pageUrl, baseName, container, listener, refresher, diag);
            diag.result = "success";
            return out;
        } catch (PausedException pe) { diag.result = "paused"; diag.errorCategory = "PAUSED"; throw pe; }
        catch (CancelledException ce) { diag.result = "cancelled"; diag.errorCategory = "CANCELLED"; throw ce; }
        catch (FailedException fe) { diag.result = "failed"; diag.errorCategory = fe.category; diag.failureReason = String.valueOf(fe.getMessage()); throw fe; }
        finally {
            diag.durationMs = System.currentTimeMillis() - t0;
            appendDiagnostics(ctx, diag);
        }
    }

    private static File doDownload(File dir, String mediaUrl, String ua, String pageUrl,
                                   String baseName, String container, Listener listener, UrlRefresher refresher,
                                   Diagnostics diag) throws Exception {
        File part = new File(dir, baseName + ".part");
        File metaFile = new File(dir, baseName + ".part.json");
        String curUrl = mediaUrl;
        String curUa = ua;

        // --- restore resume state ---
        long existing = part.exists() ? part.length() : 0;
        long expected = -1;
        String etag = "", lastMod = "";
        if (existing > 0 && metaFile.exists()) {
            try {
                JSONObject m = readJsonFile(metaFile);
                if (m != null && pageUrl != null && pageUrl.equals(m.optString("pageUrl", ""))) {
                    expected = m.optLong("expected", -1);
                    etag = m.optString("etag", "");
                    lastMod = m.optString("lastModified", "");
                } else { existing = 0; deleteQuiet(part); }
            } catch (Throwable e) { existing = 0; deleteQuiet(part); }
        } else if (existing > 0) { existing = 0; deleteQuiet(part); } // no provenance: do not trust it

        try { diag.host = new URL(curUrl).getHost(); } catch (Throwable ignored) {}
        diag.downloadedBytes = existing;
        diag.expectedBytes = expected;
        diag.etag = etag;

        int attempts = 0;
        int refreshes = 0;
        boolean restartedAfter416 = false;

        while (true) {
            if (listener.isCancelled()) { deleteQuiet(part); deleteQuiet(metaFile); throw new CancelledException(); }
            if (listener.isPaused()) { saveMeta(metaFile, pageUrl, curUrl, expected, etag, lastMod, existing); throw new PausedException(); }
            if (attempts >= MAX_ATTEMPTS) {
                throw new FailedException("RETRIES_EXHAUSTED", false, "Download kept failing after " + attempts + " attempts (last status " + diag.lastStatus + ")");
            }
            attempts++;

            HttpURLConnection c = null;
            try {
                // Disk-space preflight when the total is known
                if (expected > 0) {
                    long usable = dir.getUsableSpace();
                    if (usable > 0 && usable < (expected - existing) + 4L * 1024 * 1024) {
                        throw new FailedException("NO_SPACE", true, "Not enough free space for this download");
                    }
                }
                c = (HttpURLConnection) new URL(curUrl).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(CONNECT_TIMEOUT_MS);
                c.setReadTimeout(READ_TIMEOUT_MS);
                c.setRequestProperty("User-Agent", curUa != null && !curUa.isEmpty() ? curUa : "Mozilla/5.0 (Linux; Android 14)");
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Accept-Encoding", "identity");
                if (existing > 0) {
                    c.setRequestProperty("Range", "bytes=" + existing + "-");
                    String validator = !etag.isEmpty() ? etag : lastMod;
                    if (!validator.isEmpty()) c.setRequestProperty("If-Range", validator);
                }
                int code = c.getResponseCode();
                diag.lastStatus = code;

                // ---- response classification ----
                if (code == 416) { // Range Not Satisfiable
                    if (expected > 0 && existing >= expected) {
                        break; // we already hold the complete file: go validate
                    }
                    if (restartedAfter416) throw new FailedException("HTTP_416", true, "Server rejected the resume position twice");
                    restartedAfter416 = true;
                    existing = 0; expected = -1; etag = ""; lastMod = "";
                    deleteQuiet(part); deleteQuiet(metaFile);
                    diag.retryCount++;
                    continue;
                }
                if (code == 403) { // signed URL most likely expired
                    closeQuietly(c);
                    if (refresher != null && refreshes < MAX_REFRESHES) {
                        refreshes++; diag.retryCount++;
                        listener.onProgress(existing, expected, "Refreshing expired link…");
                        String[] fresh = null;
                        try { fresh = refresher.refresh(); } catch (Throwable ignored) {}
                        if (fresh != null && fresh.length >= 1 && fresh[0] != null && !fresh[0].isEmpty()) {
                            curUrl = fresh[0];
                            if (fresh.length >= 2 && fresh[1] != null) curUa = fresh[1];
                            try { diag.host = new URL(curUrl).getHost(); } catch (Throwable ignored) {}
                            continue; // resume position is re-validated by the 206/Content-Range checks
                        }
                    }
                    throw new FailedException("HTTP_403", true, "Download link refused (403) and could not be refreshed");
                }
                if (code == 404) { closeQuietly(c); throw new FailedException("HTTP_404", true, "The media file no longer exists (404)"); }
                if (code == 408 || code == 429 || code >= 500) {
                    long retryAfter = parseRetryAfter(c.getHeaderField("Retry-After"));
                    closeQuietly(c);
                    diag.retryCount++;
                    sleepBackoff(attempts, retryAfter, listener);
                    continue;
                }
                if (code != 200 && code != 206) { closeQuietly(c); throw new FailedException("HTTP_" + code, true, "Unexpected server response (" + code + ")"); }

                // ---- validators + totals ----
                String newEtag = c.getHeaderField("ETag"); if (newEtag != null && !newEtag.isEmpty()) etag = newEtag;
                String newMod = c.getHeaderField("Last-Modified"); if (newMod != null && !newMod.isEmpty()) lastMod = newMod;
                diag.etag = etag;
                boolean append;
                if (code == 206) {
                    diag.rangeSupport = true;
                    long[] cr = parseContentRange(c.getHeaderField("Content-Range"));
                    if (cr == null || cr[0] != existing) {
                        // Server resumed from a different position than we hold: trust nothing, restart.
                        closeQuietly(c);
                        existing = 0; deleteQuiet(part);
                        diag.retryCount++;
                        continue;
                    }
                    if (cr[1] > 0) expected = cr[1];
                    append = existing > 0;
                } else { // 200
                    if (existing > 0) { existing = 0; } // range ignored: restart safely, never append a full body
                    long cl = c.getContentLengthLong();
                    if (cl > 0) expected = cl;
                    String ar = c.getHeaderField("Accept-Ranges");
                    if (ar != null && ar.contains("bytes")) diag.rangeSupport = true;
                    append = false;
                }
                diag.expectedBytes = expected;
                saveMeta(metaFile, pageUrl, curUrl, expected, etag, lastMod, existing);

                // ---- stream body to disk ----
                long session = streamToPart(c, part, append, existing, expected, listener, metaFile, pageUrl, curUrl, etag, lastMod, diag);
                existing += session;
                diag.downloadedBytes = existing;
                c.disconnect(); c = null;

                if (expected > 0 && existing < expected) { // connection ended early: resume
                    diag.retryCount++;
                    sleepBackoff(attempts, 0, listener);
                    continue;
                }
                break; // complete (or size unknown and stream ended cleanly)
            } catch (FailedException | PausedException | CancelledException e) {
                throw e;
            } catch (java.io.IOException ioe) {
                if (isNoSpace(ioe)) {
                    saveMeta(metaFile, pageUrl, curUrl, expected, etag, lastMod, part.exists() ? part.length() : existing);
                    throw new FailedException("NO_SPACE", true, "Storage full while downloading");
                }
                // Network reset / timeout / DNS / TLS: temporary. Persist and retry with resume.
                existing = part.exists() ? part.length() : 0;
                diag.downloadedBytes = existing;
                diag.failureReason = ioe.getClass().getSimpleName() + ": " + String.valueOf(ioe.getMessage());
                diag.retryCount++;
                saveMeta(metaFile, pageUrl, curUrl, expected, etag, lastMod, existing);
                sleepBackoff(attempts, 0, listener);
            } finally {
                if (c != null) { try { c.disconnect(); } catch (Throwable ignored) {} }
            }
        }

        // ---- validation before anything is called complete ----
        long finalSize = part.exists() ? part.length() : 0;
        if (finalSize <= 0) throw new FailedException("EMPTY", false, "Download produced no data");
        if (expected > 0 && finalSize != expected) {
            throw new FailedException("SIZE_MISMATCH", false, "File size mismatch: got " + finalSize + " of " + expected + " bytes");
        }
        if (looksLikeHtml(part)) {
            deleteQuiet(part); deleteQuiet(metaFile);
            throw new FailedException("NOT_MEDIA", true, "The server returned a web page instead of audio");
        }
        if (!parsesAsMedia(part)) {
            deleteQuiet(part); deleteQuiet(metaFile);
            throw new FailedException("CORRUPT", true, "Downloaded file could not be parsed as media");
        }
        File out = new File(dir, baseName + "." + container);
        deleteQuiet(out);
        if (!part.renameTo(out)) { copyFile(part, out); deleteQuiet(part); }
        deleteQuiet(metaFile);
        diag.downloadedBytes = out.length();
        return out;
    }

    private static long streamToPart(HttpURLConnection c, File part, boolean append, long existing, long expected,
                                     Listener listener, File metaFile, String pageUrl, String curUrl,
                                     String etag, String lastMod, Diagnostics diag) throws Exception {
        InputStream in = new BufferedInputStream(c.getInputStream(), BUFFER_SIZE);
        FileOutputStream fos = new FileOutputStream(part, append);
        BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
        byte[] buf = new byte[BUFFER_SIZE];
        long session = 0;
        long sinceSync = 0;
        long lastUi = 0;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                if (listener.isCancelled()) { try { bos.close(); } catch (Throwable ignored) {} deleteQuiet(part); deleteQuiet(metaFile); throw new CancelledException(); }
                if (listener.isPaused()) {
                    try { bos.flush(); fos.getFD().sync(); bos.close(); } catch (Throwable ignored) {}
                    saveMeta(metaFile, pageUrl, curUrl, expected, etag, lastMod, existing + session);
                    throw new PausedException();
                }
                bos.write(buf, 0, n);
                session += n; sinceSync += n;
                if (sinceSync >= SYNC_EVERY_BYTES) {
                    bos.flush(); fos.getFD().sync();
                    saveMeta(metaFile, pageUrl, curUrl, expected, etag, lastMod, existing + session);
                    sinceSync = 0;
                }
                long now = System.currentTimeMillis();
                if (now - lastUi > 350) {
                    lastUi = now;
                    listener.onProgress(existing + session, expected, "Downloading");
                }
            }
        } finally {
            try { bos.flush(); fos.getFD().sync(); } catch (Throwable ignored) {}
            try { bos.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
        }
        listener.onProgress(existing + session, expected, "Downloading");
        return session;
    }

    // ---------------- helpers ----------------
    private static long[] parseContentRange(String cr) {
        // "bytes 123-456/7890" or "bytes 123-456/*"
        if (cr == null) return null;
        try {
            String s = cr.trim();
            int sp = s.indexOf(' ');
            if (sp >= 0) s = s.substring(sp + 1);
            int dash = s.indexOf('-');
            int slash = s.indexOf('/');
            if (dash < 0 || slash < 0) return null;
            long start = Long.parseLong(s.substring(0, dash).trim());
            String tot = s.substring(slash + 1).trim();
            long total = "*".equals(tot) ? -1 : Long.parseLong(tot);
            return new long[]{start, total};
        } catch (Throwable e) { return null; }
    }

    private static long parseRetryAfter(String v) {
        if (v == null) return 0;
        try { return Math.min(120L, Math.max(0L, Long.parseLong(v.trim()))); } catch (Throwable e) { return 0; }
    }

    private static void sleepBackoff(int attempt, long retryAfterSec, Listener listener) throws Exception {
        long base = Math.min(30_000L, 1000L * (1L << Math.min(5, attempt)));
        double jitter = 0.7 + Math.random() * 0.6;
        long ms = (long) (base * jitter);
        if (retryAfterSec > 0) ms = Math.max(ms, retryAfterSec * 1000L);
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (listener.isCancelled()) throw new CancelledException();
            if (listener.isPaused()) throw new PausedException();
            Thread.sleep(200);
        }
    }

    private static boolean isNoSpace(java.io.IOException e) {
        String m = String.valueOf(e.getMessage());
        return m.contains("No space left") || m.contains("ENOSPC") || m.contains("Not enough storage");
    }

    private static boolean looksLikeHtml(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] b = new byte[64];
            int n = fis.read(b); fis.close();
            if (n <= 0) return false;
            String s = new String(b, 0, n, "UTF-8").trim().toLowerCase(Locale.US);
            return s.startsWith("<!doctype") || s.startsWith("<html") || s.startsWith("<?xml") || s.startsWith("<head");
        } catch (Throwable e) { return false; }
    }

    private static boolean parsesAsMedia(File f) {
        android.media.MediaExtractor ex = new android.media.MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            return ex.getTrackCount() > 0;
        } catch (Throwable e) { return false; }
        finally { try { ex.release(); } catch (Throwable ignored) {} }
    }

    private static JSONObject readJsonFile(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = fis.read(buf); fis.close();
            if (n <= 0) return null;
            return new JSONObject(new String(buf, 0, n, "UTF-8"));
        } catch (Throwable e) { return null; }
    }

    private static void saveMeta(File metaFile, String pageUrl, String mediaUrl, long expected, String etag, String lastMod, long downloaded) {
        try {
            JSONObject o = new JSONObject();
            o.put("pageUrl", pageUrl == null ? "" : pageUrl);
            o.put("expected", expected);
            o.put("etag", etag == null ? "" : etag);
            o.put("lastModified", lastMod == null ? "" : lastMod);
            o.put("downloaded", downloaded);
            o.put("at", System.currentTimeMillis());
            FileOutputStream fos = new FileOutputStream(metaFile);
            fos.write(o.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {}
    }

    private static void copyFile(File src, File dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[BUFFER_SIZE]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close(); out.flush(); out.close();
    }

    private static void deleteQuiet(File f) { try { if (f != null) f.delete(); } catch (Throwable ignored) {} }
    private static void closeQuietly(HttpURLConnection c) {
        try { InputStream es = c.getErrorStream(); if (es != null) es.close(); } catch (Throwable ignored) {}
        try { c.disconnect(); } catch (Throwable ignored) {}
    }
}
