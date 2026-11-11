package com.shadhinkhan.grabit;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Persistent, validating, resumable downloader for direct media streams —
 * the video file and its companion audio file behind the direct route.
 *
 * It generalizes the discipline AudioEngine has proven for audio since v21
 * to every direct stream, and replaces the ad-hoc chunk downloader whose
 * temp files lived only in memory:
 *
 *  - Every stream gets a .part file plus a JSON session sidecar, keyed by
 *    page + format + role, so progress survives process death, parallel
 *    jobs never share a temp file, and a later attempt resumes exactly
 *    where the previous one stopped.
 *  - Every ranged response is validated BEFORE a byte is written: HTTP 206
 *    required, Content-Range start must equal the requested offset, the
 *    total must match the session total, and the ETag must match when both
 *    are known. A 200 answer to a ranged request means the stream changed:
 *    the partial is wiped and restarted — a different stream's bytes are
 *    never appended.
 *  - If-Range (ETag / Last-Modified) is sent on resumes. 403 refreshes the
 *    signed URL through the caller (same format only) and resumes. 416
 *    completes when sizes match, otherwise wipes once. 408 / 429 / 5xx and
 *    network errors retry with exponential backoff + jitter + Retry-After.
 *  - Files of 24 MB+ download through 4 parallel segments whose per-segment
 *    frontiers are persisted, so even a killed parallel download resumes
 *    per segment instead of from zero.
 *  - A download is only "complete" after exact-size verification (when the
 *    total is known), an HTML sniff and a media-parse check; only then is
 *    the .part renamed to the output file.
 */
public class StreamDownloader {

    public interface Listener {
        void onProgress(long done, long total);
        boolean isCancelled();
        boolean isPaused();
        /** Return {url, ua} for the SAME format after a 403, or null. */
        String[] refreshUrl();
    }

    /** The stream changed under us (validators/total disagree): partial data is untrustworthy. */
    static class ChangedException extends Exception {}
    /** A failure that retrying cannot fix (404, no space, refresh exhausted...). */
    static class TerminalException extends Exception {
        TerminalException(String m) { super(m); }
    }

    private static final long PARALLEL_MIN = 24L * 1024 * 1024;
    private static final int SEGMENTS = 4;
    private static final long SEG_CHUNK = 4L * 1024 * 1024;
    private static final int MAX_REFRESHES = 2;

    // ------------------------------------------------------------------
    // Session state (the sidecar JSON)
    // ------------------------------------------------------------------
    static class S {
        String pageUrl = "", formatId = "", role = "", quality = "", container = "";
        long total = -1;
        String etag = "", lastMod = "";
        long[] front = null; // parallel: next-write offset per segment
        int retries = 0;
        boolean rangeOk = true;
    }

    private static void saveS(File meta, S s) {
        try {
            JSONObject o = new JSONObject();
            o.put("pageUrl", s.pageUrl);
            o.put("formatId", s.formatId);
            o.put("role", s.role);
            o.put("quality", s.quality);
            o.put("container", s.container);
            o.put("total", s.total);
            o.put("etag", s.etag);
            o.put("lastMod", s.lastMod);
            o.put("retries", s.retries);
            o.put("rangeOk", s.rangeOk);
            if (s.front != null) {
                JSONArray a = new JSONArray();
                for (long v : s.front) a.put(v);
                o.put("front", a);
            }
            o.put("updatedAt", System.currentTimeMillis());
            FileOutputStream fos = new FileOutputStream(meta, false);
            fos.write(o.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {}
    }

    private static S loadS(File meta, String pageUrl, String formatId, String role) {
        try {
            if (!meta.exists()) return null;
            FileInputStream in = new FileInputStream(meta);
            byte[] buf = new byte[(int) Math.min(meta.length(), 65536)];
            int n = in.read(buf); in.close();
            if (n <= 0) return null;
            JSONObject o = new JSONObject(new String(buf, 0, n, "UTF-8"));
            // A sidecar is only trusted for the exact stream it describes.
            if (!pageUrl.equals(o.optString("pageUrl", null))) return null;
            if (!formatId.equals(o.optString("formatId", null))) return null;
            if (!role.equals(o.optString("role", null))) return null;
            S s = new S();
            s.pageUrl = pageUrl; s.formatId = formatId; s.role = role;
            s.quality = o.optString("quality", "");
            s.container = o.optString("container", "");
            s.total = o.optLong("total", -1);
            s.etag = o.optString("etag", "");
            s.lastMod = o.optString("lastMod", "");
            s.retries = o.optInt("retries", 0);
            s.rangeOk = o.optBoolean("rangeOk", true);
            JSONArray a = o.optJSONArray("front");
            if (a != null && a.length() == SEGMENTS) {
                s.front = new long[SEGMENTS];
                for (int i = 0; i < SEGMENTS; i++) s.front[i] = a.optLong(i, 0);
            }
            return s;
        } catch (Throwable e) { return null; }
    }

    public static String keyFor(String pageUrl, String formatId, String role) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest((pageUrl + "|" + formatId + "|" + role).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i] & 0xff));
            return sb.toString();
        } catch (Throwable e) {
            return Integer.toHexString((pageUrl + formatId + role).hashCode());
        }
    }

    /** Remove session files older than maxAgeMs (called at service start). */
    public static void cleanupStale(File dir, long maxAgeMs) {
        try {
            File[] fs = dir.listFiles();
            if (fs == null) return;
            long now = System.currentTimeMillis();
            for (File f : fs) {
                String n = f.getName();
                if (!n.startsWith("sdl_")) continue;
                if (now - f.lastModified() > maxAgeMs) deleteQuiet(f);
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // URL holder with serialized refresh (segments share one refresh)
    // ------------------------------------------------------------------
    static class UrlBox {
        volatile String url, ua;
        private int refreshes = 0;
        UrlBox(String u, String a) { url = u; ua = a; }
        /** Refresh after a 403 for staleUrl. If another thread already
         *  refreshed past staleUrl, reuse its result instead of refreshing again. */
        synchronized boolean refresh(Listener l, String staleUrl) {
            if (!url.equals(staleUrl)) return true;
            if (refreshes >= MAX_REFRESHES) return false;
            String[] f = null;
            try { f = l.refreshUrl(); } catch (Throwable ignored) {}
            if (f == null || f.length < 1 || f[0] == null || f[0].isEmpty()) return false;
            url = f[0];
            if (f.length >= 2 && f[1] != null) ua = f[1];
            refreshes++;
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------
    public static File download(Context ctx, File dir, String pageUrl, String formatId, String role,
                                String quality, String container, String streamUrl, String ua,
                                long hintTotal, Listener l) throws Exception {
        String key = keyFor(pageUrl, formatId, role);
        File part = new File(dir, "sdl_" + key + ".part");
        File meta = new File(dir, "sdl_" + key + ".json");
        S s = loadS(meta, pageUrl, formatId, role);
        boolean haveBytes = s != null && part.exists() && part.length() > 0;
        if (s == null) {
            s = new S();
            s.pageUrl = pageUrl; s.formatId = formatId; s.role = role;
            s.quality = quality; s.container = container;
        }
        if (s.total < 0 && hintTotal > 0) s.total = hintTotal;
        UrlBox box = new UrlBox(streamUrl, ua);

        if (haveBytes) {
            diag(ctx, "sdl resume key=" + key + " fmt=" + formatId + " role=" + role
                    + " have=" + part.length() + " total=" + s.total);
            int trust = probe(box, s, part, true, l);
            if (trust == 1) {
                diag(ctx, "sdl wipe key=" + key + " reason=probe-changed");
                deleteQuiet(part); s.total = -1; s.front = null; saveS(meta, s);
            } else if (trust == 2) {
                return finishUp(ctx, key, part, meta, s, container);
            }
        } else {
            // Fresh start: one 1-byte probe learns the authoritative total
            // and validators up front, so a large file can go parallel
            // immediately instead of discovering its size mid-stream.
            probe(box, s, part, false, l);
        }

        boolean restarted = false;
        while (true) {
            try {
                if (s.total >= PARALLEL_MIN && s.rangeOk) parallel(ctx, box, s, part, meta, l);
                else sequential(ctx, box, s, part, meta, l);
                break;
            } catch (ChangedException ce) {
                if (restarted) throw new TerminalException("Stream changed repeatedly; aborted to protect the file");
                restarted = true;
                deleteQuiet(part); s.total = -1; s.front = null; s.retries = 0;
                diag(ctx, "sdl wipe key=" + key + " reason=changed-mid-transfer");
                saveS(meta, s);
            }
        }
        return finishUp(ctx, key, part, meta, s, container);
    }

    // ------------------------------------------------------------------
    // Probe: learn total/validators; verify stored bytes when resuming.
    // Returns 0 = ok/proceed, 1 = stored bytes untrusted, 2 = already complete.
    // Advisory only: transfers re-validate every response before writing.
    // ------------------------------------------------------------------
    private static int probe(UrlBox box, S s, File part, boolean haveBytes, Listener l) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (l.isCancelled()) throw new Exception("Cancelled");
            HttpURLConnection c = null;
            try {
                String usedUrl = box.url;
                c = open(usedUrl, box.ua);
                c.setRequestProperty("Range", "bytes=0-0");
                if (haveBytes) {
                    String v = !s.etag.isEmpty() ? s.etag : s.lastMod;
                    if (!v.isEmpty()) c.setRequestProperty("If-Range", v);
                }
                int code = c.getResponseCode();
                if (code == 403) { if (box.refresh(l, usedUrl)) continue; return haveBytes ? 1 : 0; }
                if (code == 416) {
                    if (haveBytes && s.total > 0 && part.length() >= s.total && frontiersDone(s)) return 2;
                    return haveBytes ? 1 : 0;
                }
                if (code == 200) {
                    s.rangeOk = false;
                    long cl = c.getContentLengthLong();
                    if (cl > 0) s.total = cl;
                    updateValidators(c, s);
                    return haveBytes ? 1 : 0; // a full-body answer means stored ranges can't be trusted
                }
                if (code == 206) {
                    long[] cr = parseContentRange(c.getHeaderField("Content-Range"));
                    String et = c.getHeaderField("ETag");
                    if (haveBytes && et != null && !et.isEmpty() && !s.etag.isEmpty() && !et.equals(s.etag)) return 1;
                    if (cr != null && cr[2] > 0) {
                        if (haveBytes && s.total > 0 && cr[2] != s.total) return 1;
                        s.total = cr[2];
                    }
                    updateValidators(c, s);
                    s.rangeOk = true;
                    return 0;
                }
                if (code == 408 || code == 429 || code >= 500) {
                    sleepBackoff(attempt + 1, parseRetryAfter(c.getHeaderField("Retry-After")), l);
                    continue;
                }
                return haveBytes ? 1 : 0;
            } catch (ChangedException ce) {
                return 1;
            } catch (java.io.IOException ioe) {
                // Network trouble during the probe is NOT proof the stored
                // bytes are wrong; transfers validate before appending.
                return 0;
            } finally {
                if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
            }
        }
        return 0;
    }

    private static boolean frontiersDone(S s) {
        if (s.front == null || s.total <= 0) return s.front == null;
        long seg = (s.total + SEGMENTS - 1) / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            long end = Math.min(s.total, (i + 1) * seg);
            if (s.front[i] < end) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Sequential transfer (AudioEngine-style: one connection per attempt,
    // strict validation before appending, resume loop with backoff)
    // ------------------------------------------------------------------
    private static void sequential(Context ctx, UrlBox box, S s, File part, File meta, Listener l) throws Exception {
        int attempts = 0;
        long lastProgressMark = 0;
        boolean restarted416 = false;
        while (true) {
            if (l.isCancelled()) { deleteQuiet(part); deleteQuiet(meta); throw new Exception("Cancelled"); }
            long frontier = part.exists() ? part.length() : 0;
            // Real progress resets the attempt budget: on a multi-GB file a
            // flaky network may need many resumes, and each one that moves
            // the frontier meaningfully is the system working as designed.
            if (frontier - lastProgressMark >= 8L * 1024 * 1024) { attempts = 0; lastProgressMark = frontier; }
            if (l.isPaused()) { saveS(meta, s); throw new AudioEngine.PausedException(); }
            if (s.total > 0 && frontier >= s.total) return;
            if (++attempts > 14) {
                saveS(meta, s);
                throw new TerminalException("Download kept failing after many attempts — progress is saved, resume to continue");
            }
            HttpURLConnection c = null;
            try {
                if (s.total > 0) {
                    long usable = part.getParentFile() != null ? part.getParentFile().getUsableSpace() : 0;
                    if (usable > 0 && usable < (s.total - frontier) + 4L * 1024 * 1024) {
                        throw new TerminalException("Not enough free space for this download");
                    }
                }
                String usedUrl = box.url;
                c = open(usedUrl, box.ua);
                boolean ranged = frontier > 0 && s.rangeOk;
                if (ranged) {
                    c.setRequestProperty("Range", "bytes=" + frontier + "-");
                    String v = !s.etag.isEmpty() ? s.etag : s.lastMod;
                    if (!v.isEmpty()) c.setRequestProperty("If-Range", v);
                }
                int code = c.getResponseCode();
                if (code == 416) {
                    closeQuietly(c);
                    if (s.total > 0 && frontier >= s.total) return;
                    if (restarted416) throw new TerminalException("Server rejected the resume position twice");
                    restarted416 = true;
                    deleteQuiet(part); s.total = -1; s.front = null; saveS(meta, s);
                    continue;
                }
                if (code == 403) {
                    closeQuietly(c);
                    if (!box.refresh(l, usedUrl)) throw new TerminalException("Download link refused (403) and could not be refreshed");
                    diag(ctx, "sdl refresh fmt=" + s.formatId + " role=" + s.role);
                    continue;
                }
                if (code == 404) { closeQuietly(c); throw new TerminalException("The media file no longer exists (404)"); }
                if (code == 408 || code == 429 || code >= 500) {
                    long ra = parseRetryAfter(c.getHeaderField("Retry-After"));
                    closeQuietly(c);
                    s.retries++; saveS(meta, s);
                    sleepBackoff(attempts, ra, l);
                    continue;
                }
                if (code == 206) {
                    long[] cr = parseContentRange(c.getHeaderField("Content-Range"));
                    if (cr == null || cr[0] != frontier) { closeQuietly(c); throw new ChangedException(); }
                    if (cr[2] > 0) {
                        if (s.total > 0 && cr[2] != s.total) { closeQuietly(c); throw new ChangedException(); }
                        s.total = cr[2];
                    }
                    checkEtag(c, s);
                    updateValidators(c, s);
                } else if (code == 200) {
                    if (frontier > 0) { closeQuietly(c); throw new ChangedException(); } // never append a full body
                    long cl = c.getContentLengthLong();
                    if (cl > 0) s.total = cl;
                    updateValidators(c, s);
                } else {
                    closeQuietly(c);
                    throw new TerminalException("Unexpected server response (" + code + ")");
                }
                saveS(meta, s);
                long got = streamAppend(c, part, frontier, s, meta, l);
                frontier += got;
                l.onProgress(frontier, s.total);
                if (s.total > 0 && frontier < s.total) { // connection ended early: resume
                    s.retries++; saveS(meta, s);
                    sleepBackoff(attempts, 0, l);
                    continue;
                }
                return;
            } catch (ChangedException | AudioEngine.PausedException | TerminalException e) {
                throw e;
            } catch (Exception e) {
                if (l.isCancelled()) { deleteQuiet(part); deleteQuiet(meta); throw new Exception("Cancelled"); }
                if (isNoSpace(e)) { saveS(meta, s); throw new TerminalException("Storage full while downloading"); }
                s.retries++; saveS(meta, s);
                sleepBackoff(attempts, 0, l);
            } finally {
                if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    /** Stream the response body onto the end of part. Returns bytes written. */
    private static long streamAppend(HttpURLConnection c, File part, long frontier, S s, File meta, Listener l) throws Exception {
        InputStream in = c.getInputStream();
        FileOutputStream fos = new FileOutputStream(part, true);
        long written = 0;
        long lastUi = 0, lastSave = System.currentTimeMillis();
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                written += n;
                long now = System.currentTimeMillis();
                if (now - lastUi > 300) {
                    lastUi = now;
                    if (l.isCancelled()) throw new Exception("Cancelled");
                    if (l.isPaused()) { fos.flush(); saveS(meta, s); throw new AudioEngine.PausedException(); }
                    l.onProgress(frontier + written, s.total);
                }
                if (now - lastSave > 4000) { lastSave = now; fos.flush(); saveS(meta, s); }
            }
        } finally {
            try { fos.flush(); fos.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
        }
        return written;
    }

    // ------------------------------------------------------------------
    // Parallel transfer: 4 segments, validated responses, persisted frontiers
    // ------------------------------------------------------------------
    private static void parallel(final Context ctx, final UrlBox box, final S s, final File part,
                                 final File meta, final Listener l) throws Exception {
        final long total = s.total;
        final long seg = (total + SEGMENTS - 1) / SEGMENTS;
        if (s.front == null || s.front.length != SEGMENTS) {
            s.front = new long[SEGMENTS];
            for (int i = 0; i < SEGMENTS; i++) s.front[i] = i * seg;
        }
        // Clamp any stale frontier into its segment (paranoia: never write out of range)
        for (int i = 0; i < SEGMENTS; i++) {
            long st = i * seg, en = Math.min(total, st + seg);
            if (s.front[i] < st) s.front[i] = st;
            if (s.front[i] > en) s.front[i] = en;
        }
        RandomAccessFile pre = new RandomAccessFile(part, "rw");
        if (pre.length() != total) pre.setLength(total);
        pre.close();
        saveS(meta, s);

        final long[] segStart = new long[SEGMENTS];
        final AtomicLong doneBytes = new AtomicLong(0);
        for (int i = 0; i < SEGMENTS; i++) {
            segStart[i] = i * seg;
            doneBytes.addAndGet(Math.max(0, s.front[i] - segStart[i]));
        }
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final AtomicReference<ChangedException> changed = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean pauseHit = new java.util.concurrent.atomic.AtomicBoolean(false);

        Thread[] ts = new Thread[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            final int idx = i;
            final long end = Math.min(total, segStart[i] + seg);
            if (s.front[i] >= end) continue;
            ts[i] = new Thread(() -> {
                try {
                    segment(ctx, box, s, part, idx, end, doneBytes, failure, changed, pauseHit, l);
                } catch (ChangedException ce) {
                    changed.compareAndSet(null, ce);
                } catch (AudioEngine.PausedException pe) {
                    pauseHit.set(true);
                } catch (Exception e) {
                    if (!isCancelledSafe(l)) failure.compareAndSet(null, e);
                }
            });
            ts[i].setDaemon(true);
            ts[i].start();
        }
        long lastUi = 0, lastSave = System.currentTimeMillis();
        while (true) {
            boolean anyAlive = false;
            for (Thread t : ts) if (t != null && t.isAlive()) { anyAlive = true; break; }
            long now = System.currentTimeMillis();
            if (now - lastUi > 300) { lastUi = now; l.onProgress(Math.min(doneBytes.get(), total), total); }
            if (now - lastSave > 2000) { lastSave = now; synchronized (s) { saveS(meta, s); } }
            if (!anyAlive) break;
            // Workers are daemons and notice the shared flags at their next
            // chunk boundary; the caller stops waiting as soon as any is set.
            if (l.isCancelled() || changed.get() != null || failure.get() != null || pauseHit.get()) break;
            try { Thread.sleep(150); } catch (InterruptedException ie) { break; }
        }
        for (Thread t : ts) if (t != null) { try { t.join(1500); } catch (Throwable ignored) {} }
        synchronized (s) { saveS(meta, s); }
        if (l.isCancelled()) { deleteQuiet(part); deleteQuiet(meta); throw new Exception("Cancelled"); }
        if (pauseHit.get() || l.isPaused()) throw new AudioEngine.PausedException();
        if (changed.get() != null) throw changed.get();
        if (failure.get() != null) throw failure.get();
        if (!frontiersDone(s)) {
            saveS(meta, s);
            throw new TerminalException("Download incomplete — progress is saved, resume to continue");
        }
    }

    private static boolean isCancelledSafe(Listener l) {
        try { return l.isCancelled(); } catch (Throwable e) { return false; }
    }

    /** One parallel segment: fills [front[idx], end) of the preallocated
     *  .part file, validating every response before writing. */
    private static void segment(Context ctx, UrlBox box, S s, File part, int idx, long end,
                                AtomicLong doneBytes, AtomicReference<Exception> failure,
                                AtomicReference<ChangedException> changed,
                                java.util.concurrent.atomic.AtomicBoolean pauseHit,
                                Listener l) throws Exception {
        final long[] front = s.front;
        RandomAccessFile raf = new RandomAccessFile(part, "rw");
        try {
            int fails = 0;
            while (front[idx] < end) {
                if (l.isCancelled()) return;
                if (l.isPaused()) { pauseHit.set(true); return; }
                if (failure.get() != null || changed.get() != null) return; // a sibling decided for everyone
                final long pos = front[idx];
                final long chunkStart = pos;
                HttpURLConnection c = null;
                try {
                    String usedUrl = box.url;
                    c = open(usedUrl, box.ua);
                    long reqEnd = Math.min(end, pos + SEG_CHUNK) - 1;
                    c.setRequestProperty("Range", "bytes=" + pos + "-" + reqEnd);
                    String v = !s.etag.isEmpty() ? s.etag : s.lastMod;
                    if (!v.isEmpty() && pos > 0) c.setRequestProperty("If-Range", v);
                    int code = c.getResponseCode();
                    if (code == 403) {
                        closeQuietly(c);
                        if (!box.refresh(l, usedUrl)) throw new TerminalException("Download link refused (403) and could not be refreshed");
                        diag(ctx, "sdl refresh fmt=" + s.formatId + " role=" + s.role + " (segment)");
                        continue;
                    }
                    if (code == 416) {
                        closeQuietly(c);
                        if (pos >= end) return;
                        throw new ChangedException();
                    }
                    if (code == 408 || code == 429 || code >= 500) {
                        long ra = parseRetryAfter(c.getHeaderField("Retry-After"));
                        closeQuietly(c);
                        fails++;
                        if (fails > 8) throw new TerminalException("Segment kept failing (HTTP " + code + ")");
                        sleepBackoff(fails, ra, l);
                        continue;
                    }
                    if (code == 200) { closeQuietly(c); throw new ChangedException(); } // full body: stream changed
                    if (code != 206) { closeQuietly(c); throw new TerminalException("Unexpected server response (" + code + ")"); }
                    long[] cr = parseContentRange(c.getHeaderField("Content-Range"));
                    if (cr == null || cr[0] != pos || (cr[2] > 0 && cr[2] != s.total)) { closeQuietly(c); throw new ChangedException(); }
                    checkEtag(c, s);
                    InputStream in = c.getInputStream();
                    byte[] buf = new byte[65536];
                    int n;
                    raf.seek(pos);
                    long p = pos;
                    while ((n = in.read(buf)) > 0) {
                        if (l.isCancelled()) { try { in.close(); } catch (Throwable ignored) {} return; }
                        int w = (int) Math.min((long) n, end - p);
                        if (w > 0) { raf.write(buf, 0, w); p += w; doneBytes.addAndGet(w); front[idx] = p; }
                        if (p >= end) break;
                    }
                    try { in.close(); } catch (Throwable ignored) {}
                    c.disconnect(); c = null;
                    if (p == chunkStart) {
                        fails++;
                        if (fails > 8) throw new TerminalException("Segment stalled at byte " + p);
                        sleepBackoff(fails, 0, l);
                    } else {
                        fails = 0;
                    }
                } catch (ChangedException | TerminalException e) {
                    throw e;
                } catch (Exception e) {
                    if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
                    if (l.isCancelled()) return;
                    if (isNoSpace(e)) throw new TerminalException("Storage full while downloading");
                    fails++;
                    synchronized (s) { s.retries++; }
                    if (fails > 8) throw new TerminalException("Segment kept failing: " + e.getClass().getSimpleName());
                    sleepBackoff(fails, 0, l);
                }
            }
        } finally {
            try { raf.close(); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------
    // Completion: verify, then rename — never before.
    // ------------------------------------------------------------------
    private static File finishUp(Context ctx, String key, File part, File meta, S s, String container) throws Exception {
        long len = part.exists() ? part.length() : 0;
        if (len <= 0) { deleteQuiet(meta); throw new TerminalException("Download produced no data"); }
        if (s.total > 0 && len != s.total) {
            if (len > s.total) {
                deleteQuiet(part); deleteQuiet(meta);
                throw new TerminalException("Downloaded file is corrupt (larger than the source)");
            }
            throw new TerminalException("Download incomplete: " + len + " of " + s.total + " bytes — resume to continue");
        }
        if (looksLikeHtml(part)) {
            deleteQuiet(part); deleteQuiet(meta);
            throw new TerminalException("The server returned a web page instead of media");
        }
        if (!parsesAsMedia(part)) {
            deleteQuiet(part); deleteQuiet(meta);
            throw new TerminalException("Downloaded file could not be parsed as media");
        }
        File out = new File(part.getParentFile(), "sdl_" + key + "." + container);
        deleteQuiet(out);
        if (!part.renameTo(out)) { copyFile(part, out); deleteQuiet(part); }
        deleteQuiet(meta);
        diag(ctx, "sdl done key=" + key + " fmt=" + s.formatId + " role=" + s.role
                + " bytes=" + out.length() + " retries=" + s.retries);
        return out;
    }

    // ------------------------------------------------------------------
    // HTTP + parsing helpers
    // ------------------------------------------------------------------
    private static HttpURLConnection open(String url, String ua) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestProperty("User-Agent", ua != null && !ua.isEmpty() ? ua : "Mozilla/5.0 (Linux; Android 14)");
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    /** "bytes start-end/total" -> {start, end, total(-1 if '*')}; null when malformed. */
    static long[] parseContentRange(String cr) {
        if (cr == null) return null;
        try {
            String t = cr.trim();
            if (!t.startsWith("bytes")) return null;
            int dash = t.indexOf('-'), slash = t.indexOf('/');
            if (dash < 0 || slash < 0 || slash < dash) return null;
            long start = Long.parseLong(t.substring(5, dash).trim());
            long end = Long.parseLong(t.substring(dash + 1, slash).trim());
            String tot = t.substring(slash + 1).trim();
            long total = "*".equals(tot) ? -1 : Long.parseLong(tot);
            return new long[]{start, end, total};
        } catch (Throwable e) { return null; }
    }

    private static long parseRetryAfter(String v) {
        if (v == null) return 0;
        try { return Math.max(0, Long.parseLong(v.trim())) * 1000L; } catch (Throwable e) { return 0; }
    }

    private static void updateValidators(HttpURLConnection c, S s) throws ChangedException {
        checkEtag(c, s);
        String et = c.getHeaderField("ETag");
        if (et != null && !et.isEmpty()) s.etag = et;
        String lm = c.getHeaderField("Last-Modified");
        if (lm != null && !lm.isEmpty()) s.lastMod = lm;
    }

    private static void checkEtag(HttpURLConnection c, S s) throws ChangedException {
        String et = c.getHeaderField("ETag");
        if (et != null && !et.isEmpty() && !s.etag.isEmpty() && !et.equals(s.etag)) throw new ChangedException();
    }

    /** Exponential backoff with jitter; wakes early on cancel/pause. */
    private static void sleepBackoff(int attempt, long retryAfterMs, Listener l) throws Exception {
        long wait = retryAfterMs > 0 ? retryAfterMs : Math.min(30000L, 800L << Math.min(attempt, 5)) + (long) (Math.random() * 400);
        long end = System.currentTimeMillis() + wait;
        while (System.currentTimeMillis() < end) {
            if (l.isCancelled()) throw new Exception("Cancelled");
            if (l.isPaused()) throw new AudioEngine.PausedException();
            try { Thread.sleep(200); } catch (InterruptedException ie) { throw new Exception("Cancelled"); }
        }
    }

    private static boolean isNoSpace(Throwable e) {
        String m = String.valueOf(e.getMessage());
        return m.contains("No space") || m.contains("ENOSPC") || m.contains("Storage full");
    }

    private static boolean looksLikeHtml(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[512];
            int n = in.read(b); in.close();
            if (n <= 0) return false;
            String head = new String(b, 0, n, "UTF-8").trim().toLowerCase();
            return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<?xml");
        } catch (Throwable e) { return false; }
    }

    private static boolean parsesAsMedia(File f) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat fmt = ex.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) return true;
            }
            return false;
        } catch (Throwable e) { return false; }
        finally { try { ex.release(); } catch (Throwable ignored) {} }
    }

    private static void diag(Context ctx, String msg) {
        try {
            File f = new File(ctx.getFilesDir(), "download_diagnostics.log");
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write((msg + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {}
    }

    private static void closeQuietly(HttpURLConnection c) {
        if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
    }

    static void deleteQuiet(File f) {
        if (f != null) try { f.delete(); } catch (Throwable ignored) {}
    }

    private static void copyFile(File src, File dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close(); out.flush(); out.close();
    }
}
