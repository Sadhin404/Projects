package com.shadhinkhan.grabit;

import android.content.Context;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast format fetching shared by MainActivity and ShareActivity.
 * - in-memory cache (10 min) so repeat fetches are instant
 * - TikTok goes through the tikwm API (fast, ~1-2s)
 * - YouTube/other sites: fast client route first, full route as fallback
 */
public class Fetcher {
    public static class Result {
        public String title;
        public ArrayList<Fmt> formats;
        public boolean tiktok;
        public boolean viaEngine;
        public String diag;
        public String thumbnail;
        public long durationSec;
    }

    private static class Cached {
        Result result; long at;
        Cached(Result r) { result = r; at = System.currentTimeMillis(); }
    }
    private static final ConcurrentHashMap<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static final long TTL = 10 * 60 * 1000;

    public static Result fetch(Context ctx, String url, boolean noWm) throws Exception {
        Cached c = CACHE.get(url);
        if (c != null && System.currentTimeMillis() - c.at < TTL) return c.result;
        boolean tiktok = url.contains("tiktok.com");
        boolean youtube = url.contains("youtube.com") || url.contains("youtu.be") || url.contains("youtube-nocookie");
        // YouTube DISPLAY fetch (user order 2026-11-09, v77): the
        // format list is fetched FAST via Innertube first (see
        // fetchInnertubeFast below) - fetch ONLY. The direct stream URLs
        // are stripped from the result before it reaches the UI, so a
        // download tap can only ever start the existing yt-dlp download
        // system (GrabIt v4 pipeline, unchanged). If Innertube yields
        // nothing, execution falls through to the engine fetch at the
        // bottom, exactly as before. Other platforms are untouched.
        // The disk cache stays skipped for YouTube (it stores raw
        // payloads incl. direct URLs); the in-memory cache above holds
        // the stripped list, so repeat fetches are still instant.
        Result disk = youtube ? null : readDiskCache(ctx, url, tiktok, noWm);
        if (disk != null) { CACHE.put(url, new Cached(disk)); return disk; }
        if (url.contains("instagram.com")) {
            try {
                Result ig = fetchInstagramApi(url);
                if (ig != null && !ig.formats.isEmpty()) { CACHE.put(url, new Cached(ig)); return ig; }
            } catch (Throwable ignored) {}
            try {
                Result ig = fetchInstagramDd(url);
                if (ig != null && !ig.formats.isEmpty()) { CACHE.put(url, new Cached(ig)); return ig; }
            } catch (Throwable ignored) {}
            try {
                Result ig = fetchInstagramHttp(url);
                if (ig != null && !ig.formats.isEmpty()) { CACHE.put(url, new Cached(ig)); return ig; }
            } catch (Throwable ignored) {}
            try {
                Result igw = fetchInstagramWebView(ctx, url);
                if (igw != null && !igw.formats.isEmpty()) { CACHE.put(url, new Cached(igw)); return igw; }
            } catch (Throwable ignored) {}
        }
        Result r;
        if (tiktok) {
            try {
                String raw = requestTikwm(url);
                r = parseTikwm(new JSONObject(raw), noWm);
                writeDiskCache(ctx, url, "tikwm", raw);
                CACHE.put(url, new Cached(r));
                return r;
            } catch (Throwable e) { /* fall through to yt-dlp */ }
        }
        if (url.contains("instagram.com")) return fetchInstagramEngine(ctx, url, noWm);
        if (youtube) {
            try {
                Result fast = fetchInnertubeFast(ctx, url);
                if (fast != null && fast.formats != null && !fast.formats.isEmpty()) {
                    stripDirect(fast);
                    logFormats(ctx, "innertube-fast", fast);
                    CACHE.put(url, new Cached(fast));
                    return fast;
                }
            } catch (Throwable ignored) {}
        }
        if (!DlState.ensureEngineBlocking(ctx)) throw new Exception("Downloader engine could not start");
        r = fetchYtdlp(ctx, url, tiktok, noWm);
        r.viaEngine = true;
        CACHE.put(url, new Cached(r));
        return r;
    }

    // ---------- disk cache (survives process death; 30 min TTL) ----------
    private static final long DISK_TTL = 30 * 60 * 1000;
    private static java.io.File cacheFile(Context ctx, String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            java.io.File dir = new java.io.File(ctx.getFilesDir(), "fetch_cache");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            return new java.io.File(dir, "v2_" + sb.toString() + ".json");
        } catch (Throwable e) { return null; }
    }
    private static void writeDiskCache(Context ctx, String url, String kind, String raw) {
        writeDiskCache(ctx, url, kind, raw, "");
    }
    private static void writeDiskCache(Context ctx, String url, String kind, String raw, String ua) {
        try {
            java.io.File f = cacheFile(ctx, url);
            if (f == null) return;
            JSONObject o = new JSONObject();
            o.put("at", System.currentTimeMillis());
            o.put("kind", kind);
            o.put("raw", raw);
            o.put("ua", ua);
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(o.toString()); w.close();
        } catch (Throwable ignored) {}
    }
    private static Result readDiskCache(Context ctx, String url, boolean tiktok, boolean noWm) {
        try {
            java.io.File f = cacheFile(ctx, url);
            if (f == null || !f.exists()) return null;
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            String line; while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject o = new JSONObject(sb.toString());
            if (System.currentTimeMillis() - o.optLong("at", 0) > DISK_TTL) return null;
            String raw = o.optString("raw", "");
            if (raw.isEmpty()) return null;
            if ("tikwm".equals(o.optString("kind"))) return parseTikwm(new JSONObject(raw), noWm);
            if ("innertube".equals(o.optString("kind"))) { lastInnertubeUa = o.optString("ua", ""); Result rr = parseInnertube(new JSONObject(raw)); return (rr == null || rr.formats.isEmpty()) ? null : rr; }
            Result r = parse(new JSONObject(raw), url, tiktok, noWm);
            return r.formats.isEmpty() ? null : r;
        } catch (Throwable e) { return null; }
    }


    /** Engine cookie file: the Keystore-encrypted Instagram session,
     *  materialized into the app-private cache only when one is connected.
     *  Without a session this path does not exist and every caller skips
     *  --cookies (anonymous extraction, exactly as before). */
    public static java.io.File cookieFile(Context ctx) {
        java.io.File m = SessionVault.materialize(ctx);
        return m != null ? m : new java.io.File(ctx.getCacheDir(), "ig_cookies.txt");
    }

    /** Per-site cookie file (user order 2026-11-09): Facebook URLs use
     *  the Facebook session when one is connected (private groups /
     *  login-walled videos); every other URL keeps the existing
     *  behaviour (the Instagram session file). yt-dlp only ever sends
     *  cookies whose domain matches the request, so the two sessions
     *  never leak into each other's sites. */
    public static java.io.File cookieFileFor(Context ctx, String url) {
        if (url != null && (url.contains("facebook.com") || url.contains("fb.watch") || url.contains("fb.com"))) {
            java.io.File f = FbSessionVault.materialize(ctx);
            if (f != null) return f;
        }
        return cookieFile(ctx);
    }


    /** Fresh Innertube-only resolve (no caches, no engine) - used by the download service to upgrade a job to direct streams. */
    public static Result fetchFreshDirect(Context ctx, String url) {
        try {
            Result r = fetchInnertube(ctx, url);
            if (r != null && (url.contains("youtube.com") || url.contains("youtu.be"))) logFormats(ctx, "innertube-fresh", r);
            return r;
        } catch (Throwable e) { return null; }
    }

    private static final String WEBVIEW_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.39 Mobile Safari/537.36";
    private static final String EXTRACT_JS = "(function(){try{var pr=window.ytInitialPlayerResponse;if(!pr||!pr.streamingData)return '';var th='';try{var ts=pr.videoDetails.thumbnail.thumbnails;if(ts&&ts.length)th=ts[ts.length-1].url||'';}catch(e){}return JSON.stringify({sd:pr.streamingData,title:(pr.videoDetails&&pr.videoDetails.title)||'',len:(pr.videoDetails&&parseFloat(pr.videoDetails.lengthSeconds))||0,th:th});}catch(e){return '';}})()";

    /**
     * SnapTube's core trick: resolve streams inside a REAL browser.
     * A hidden WebView loads the watch page - YouTube's own page code produces a valid
     * player response there, where the raw API clients get bot walls. We read that
     * response (or, failing that, capture the media URLs the player itself requests).
     */
    private static Result fetchViaWebView(final Context ctx, final String pageUrl) {
        if (!(ctx instanceof android.app.Activity)) return null;
        if (youtubeId(pageUrl) == null) return null;
        final Result[] holder = new Result[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final android.app.Activity act = (android.app.Activity) ctx;
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> {
            try {
                final android.webkit.WebView wv = new android.webkit.WebView(act);
                final android.view.ViewGroup parent = (android.view.ViewGroup) act.getWindow().getDecorView().findViewById(android.R.id.content);
                parent.addView(wv, new android.view.ViewGroup.LayoutParams(1, 1));
                android.webkit.WebSettings ws = wv.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);
                ws.setMediaPlaybackRequiresUserGesture(false);
                ws.setUserAgentString(WEBVIEW_UA);
                try { android.webkit.CookieManager.getInstance().setAcceptCookie(true); } catch (Throwable ignored) {}
                final java.util.Map<String, String> captured = new java.util.concurrent.ConcurrentHashMap<>();
                final boolean[] finished = {false};
                final Runnable finishNow = () -> {
                    if (finished[0]) return; finished[0] = true;
                    try { wv.stopLoading(); } catch (Throwable ignored) {}
                    try { parent.removeView(wv); } catch (Throwable ignored) {}
                    try { wv.destroy(); } catch (Throwable ignored) {}
                    latch.countDown();
                };
                wv.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, android.webkit.WebResourceRequest request) {
                        try {
                            String u = request.getUrl().toString();
                            if (u.contains("videoplayback")) {
                                if (u.contains("mime=audio") && !captured.containsKey("audio")) captured.put("audio", u);
                                else if (u.contains("mime=video") && !captured.containsKey("video")) captured.put("video", u);
                            }
                        } catch (Throwable ignored) {}
                        return null;
                    }
                    @Override public void onPageFinished(android.webkit.WebView view, String url) {
                        if (url == null || !url.contains("watch")) return;
                        main.postDelayed(() -> {
                            if (finished[0]) return;
                            try {
                                view.evaluateJavascript(EXTRACT_JS, value -> {
                                    try {
                                        Result r = resultFromPlayerJson(value);
                                        if (r != null) holder[0] = r;
                                    } catch (Throwable ignored) {}
                                    if (holder[0] == null) {
                                        try { holder[0] = resultFromCaptured(captured); } catch (Throwable ignored) {}
                                    }
                                    finishNow.run();
                                });
                            } catch (Throwable e) { finishNow.run(); }
                        }, 3000);
                    }
                });
                main.postDelayed(() -> {
                    if (holder[0] == null) {
                        try { holder[0] = resultFromCaptured(captured); } catch (Throwable ignored) {}
                    }
                    finishNow.run();
                }, 20000);
                wv.loadUrl(pageUrl);
            } catch (Throwable e) { latch.countDown(); }
        });
        try { latch.await(24, java.util.concurrent.TimeUnit.SECONDS); } catch (Throwable ignored) {}
        return holder[0];
    }

    private static Result resultFromPlayerJson(String value) {
        try {
            if (value == null || value.equals("null")) return null;
            String json = value;
            if (json.startsWith("\"")) json = new JSONArray("[" + json + "]").getString(0);
            if (json.isEmpty()) return null;
            JSONObject o = new JSONObject(json);
            JSONObject sd = o.optJSONObject("sd");
            if (sd == null) return null;
            lastInnertubeUa = WEBVIEW_UA;
            return parseStreamingData(sd, o.optString("title", "YouTube video"), o.optDouble("len", 0), o.optString("th", ""));
        } catch (Throwable e) { return null; }
    }

    private static Result resultFromCaptured(java.util.Map<String, String> captured) {
        String audio = captured.get("audio");
        String video = captured.get("video");
        if (audio == null && video == null) return null;
        ArrayList<Fmt> list = new ArrayList<>();
        if (video != null) {
            Fmt f = new Fmt();
            f.directUrl = video; f.httpUa = WEBVIEW_UA;
            f.container = "mp4"; f.kind = "Video+Audio"; f.ext = "mp4";
            boolean hd = video.contains("itag=22");
            f.id = hd ? "22" : "18"; f.quality = hd ? "720p" : "360p"; f.size = "\u2014";
            list.add(f);
        }
        if (audio != null) {
            Fmt f = new Fmt();
            f.directUrl = audio; f.httpUa = WEBVIEW_UA;
            boolean webm = audio.contains("mime=audio%2Fwebm");
            f.container = webm ? "webm" : "mp4"; f.kind = "Audio"; f.ext = webm ? "webm" : "mp3";
            f.id = webm ? "251" : "140"; f.quality = webm ? "160 kbps" : "128 kbps"; f.size = "\u2014";
            list.add(f);
        }
        Result r = new Result();
        r.tiktok = false; r.title = "YouTube video";
        r.formats = finishFormats(list, true, 0);
        wireInnertubeDirect(null, r.formats);
        return r.formats.isEmpty() ? null : r;
    }

    // ---------- Instagram error classification + engine with backoff ----------
    static final int IG_AUTH = 1, IG_RATE = 2, IG_GONE = 3, IG_EXTRACTOR = 4, IG_NETWORK = 5;

    static int classifyIgError(String msg) {
        if (msg == null) return IG_EXTRACTOR;
        String m = msg.toLowerCase(java.util.Locale.US);
        if (m.contains("429") || m.contains("rate-limit") || m.contains("rate limit") || m.contains("too many requests")) return IG_RATE;
        if (m.contains("login required") || m.contains("cookies") || m.contains("checkpoint") || m.contains("sign in")) return IG_AUTH;
        if (m.contains("not available") || m.contains("private") || m.contains("no longer available") || m.contains("404")) return IG_GONE;
        if (m.contains("unable to resolve") || m.contains("network") || m.contains("timed out") || m.contains("timeout")) return IG_NETWORK;
        return IG_EXTRACTOR;
    }

    static String igFriendlyError(int kind, boolean hasSession) {
        switch (kind) {
            case IG_AUTH: return hasSession
                ? "Instagram rejected the saved session (it expired). Reconnect Instagram from the gear menu, then try again."
                : "Instagram wants a login for this post and the public routes could not fetch it. You can optionally connect Instagram from the gear menu, or try a public post.";
            case IG_RATE: return "Instagram is rate-limiting requests right now (HTTP 429). Wait a few minutes and try again.";
            case IG_GONE: return "This Instagram post is unavailable (private account, deleted, or age-restricted).";
            case IG_NETWORK: return "Network problem while contacting Instagram. Check the connection and try again.";
            default: return "Instagram extraction failed - the extractor may be outdated or Instagram changed something. The app updates its engine automatically; try again later.";
        }
    }

    /** Engine extraction for Instagram with bounded retries: exponential
     *  backoff on rate limits only (max 3 attempts), saved-session validity
     *  detection, and classified, human-readable failures. */
    private static Result fetchInstagramEngine(Context ctx, String url, boolean noWm) throws Exception {
        if (!DlState.ensureEngineBlocking(ctx)) throw new Exception("Downloader engine could not start");
        int[] backoffSec = {0, 6, 18};
        int lastKind = IG_EXTRACTOR;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (backoffSec[attempt] > 0) {
                try { Thread.sleep(backoffSec[attempt] * 1000L); }
                catch (InterruptedException ie) { throw new Exception("Cancelled"); }
            }
            try {
                Result r = fetchYtdlp(ctx, url, false, noWm);
                r.viaEngine = true;
                return r;
            } catch (Exception e) {
                lastKind = classifyIgError(e.getMessage());
                if (lastKind == IG_AUTH && SessionVault.hasSession(ctx)) SessionVault.markInvalid(ctx);
                if (lastKind != IG_RATE) break;
            }
        }
        throw new Exception(igFriendlyError(lastKind, SessionVault.hasSession(ctx)));
    }

    /** Fresh Instagram resolve with no caches - used by the download service
     *  to refresh an expired CDN link right before retrying a download. */
    public static Result fetchFreshInstagram(Context ctx, String url) {
        try { Result r = fetchInstagramApi(url); if (r != null && !r.formats.isEmpty()) return r; } catch (Throwable ignored) {}
        try { Result r = fetchInstagramDd(url); if (r != null && !r.formats.isEmpty()) return r; } catch (Throwable ignored) {}
        try { Result r = fetchInstagramHttp(url); if (r != null && !r.formats.isEmpty()) return r; } catch (Throwable ignored) {}
        return null;
    }

    /** Carousels: every video becomes its own option, every image joins one
     *  Photos option (the service downloads all of them). */
    private static Result igResultMulti(ArrayList<String> videos, ArrayList<String> pics, String thumb, String title) {
        Result r = new Result();
        r.tiktok = false;
        r.title = (title == null || title.isEmpty()) ? "Instagram post" : title;
        r.thumbnail = thumb == null ? "" : thumb;
        r.formats = new ArrayList<>();
        for (int i = 0; i < videos.size(); i++) {
            Fmt f = new Fmt();
            f.id = "igvideo" + (i == 0 ? "" : String.valueOf(i + 1));
            f.kind = "Video+Audio"; f.ext = "mp4"; f.container = "mp4";
            f.quality = videos.size() > 1 ? "Video " + (i + 1) : "HD";
            f.directUrl = videos.get(i); f.httpUa = IG_UA;
            f.size = i == 0 ? igHeadSize(videos.get(i), IG_UA) : "\u2014";
            r.formats.add(f);
        }
        if (!pics.isEmpty()) {
            Fmt f = new Fmt();
            f.id = "igphoto"; f.kind = "Photos"; f.ext = "jpg"; f.container = "jpg";
            f.quality = pics.size() + (pics.size() == 1 ? " photo" : " photos"); f.size = "\u2014";
            f.imageUrls = new ArrayList<>(pics);
            r.formats.add(f);
        }
        return r.formats.isEmpty() ? null : r;
    }

    /** Instagram carousels arrive from yt-dlp as a playlist of entries, not
     *  one formats list. Take the best video format of each video entry and
     *  gather all image entries into a single Photos option. */
    private static void buildCarouselFormats(JSONObject info, JSONArray entries, Result r) {
        ArrayList<Fmt> out = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        String firstThumb = "";
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.optJSONObject(i);
            if (e == null) continue;
            if (firstThumb.isEmpty()) firstThumb = e.optString("thumbnail", "");
            JSONArray ef = e.optJSONArray("formats");
            JSONObject bestV = null; int bestScore = -1;
            if (ef != null) {
                for (int j = 0; j < ef.length(); j++) {
                    JSONObject f = ef.optJSONObject(j);
                    if (f == null || f.optString("vcodec", "none").equals("none")) continue;
                    int sc = f.optInt("height", 0) * 10
                            + (!f.optString("acodec", "none").equals("none") ? 5 : 0)
                            + ("mp4".equals(f.optString("ext")) ? 1 : 0);
                    if (sc > bestScore) { bestScore = sc; bestV = f; }
                }
            }
            if (bestV != null) {
                Fmt fm = new Fmt();
                fm.id = bestV.optString("format_id", "v" + i);
                fm.ext = "mp4"; fm.container = "mp4";
                fm.kind = !bestV.optString("acodec", "none").equals("none") ? "Video+Audio" : "Video only";
                int h = bestV.optInt("height", 0);
                fm.quality = h > 0 ? h + "p" : "Video";
                long sz = bestV.optLong("filesize", 0); if (sz == 0) sz = bestV.optLong("filesize_approx", 0);
                fm.size = sz > 0 ? mb(sz) : "\u2014";
                String du = bestV.optString("url", "");
                if (!du.isEmpty()) { fm.directUrl = du; fm.httpUa = IG_UA; }
                out.add(fm);
            } else {
                String iu = e.optString("url", "");
                if (!iu.isEmpty() && e.optString("vcodec", "none").equals("none") && e.optString("acodec", "none").equals("none")) images.add(iu);
            }
        }
        if (!images.isEmpty()) {
            Fmt ph = new Fmt();
            ph.id = "igphotos"; ph.kind = "Photos"; ph.ext = "jpg"; ph.container = "jpg";
            ph.quality = images.size() + (images.size() == 1 ? " photo" : " photos");
            ph.size = "\u2014"; ph.imageUrls = images;
            out.add(ph);
        }
        if (r.thumbnail.isEmpty() && !firstThumb.isEmpty()) r.thumbnail = firstThumb;
        r.formats = out;
    }

    // ---------- Instagram via the public embed page (no login) ----------
    // yt-dlp's Instagram route demands cookies/login, which the user does not
    // want. Public posts/reels also render on instagram's own embed page,
    // which carries the direct media URL - the route downloader apps use.
    private static final String IG_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static String instagramCode(String url) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("instagram\\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)").matcher(url);
            return m.find() ? m.group(1) : null;
        } catch (Throwable e) { return null; }
    }

    private static String igUnescape(String v) {
        return v.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=").replace("\\\"", "\"");
    }

    private static String igJsonString(String html, String key) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(html);
            return m.find() ? igUnescape(m.group(1)) : null;
        } catch (Throwable e) { return null; }
    }

    private static String igMeta(String html, String prop) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<meta[^>]+property=\"og:" + prop + "\"[^>]+content=\"([^\"]+)\"").matcher(html);
            if (m.find()) return android.text.Html.fromHtml(m.group(1)).toString();
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("<meta[^>]+content=\"([^\"]+)\"[^>]+property=\"og:" + prop + "\"").matcher(html);
            return m2.find() ? android.text.Html.fromHtml(m2.group(1)).toString() : null;
        } catch (Throwable e) { return null; }
    }

    private static String igHeadSize(String u, String ua) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestMethod("HEAD");
            c.setRequestProperty("User-Agent", ua);
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            long len = c.getContentLengthLong();
            return len > 0 ? mb(len) : "\u2014";
        } catch (Throwable e) { return "\u2014"; }
    }

    private static Result igResult(String video, String image, String title, String ua) {
        boolean hasVideo = video != null && !video.isEmpty();
        boolean hasImage = image != null && !image.isEmpty();
        if (!hasVideo && !hasImage) return null;
        Result r = new Result();
        r.tiktok = false;
        r.title = (title == null || title.isEmpty()) ? "Instagram post" : title;
        r.thumbnail = hasImage ? image : "";
        r.formats = new ArrayList<>();
        if (hasVideo) {
            Fmt f = new Fmt();
            f.id = "igvideo"; f.kind = "Video+Audio"; f.ext = "mp4"; f.container = "mp4";
            f.quality = "HD"; f.directUrl = video; f.httpUa = ua;
            f.size = igHeadSize(video, ua);
            r.formats.add(f);
        } else {
            Fmt f = new Fmt();
            f.id = "igphoto"; f.kind = "Photos"; f.ext = "jpg"; f.container = "jpg";
            f.quality = "Photo"; f.size = "\u2014";
            f.imageUrls = new ArrayList<>(); f.imageUrls.add(image);
            r.formats.add(f);
        }
        return r;
    }

    // SnapTube-style resolving: a downloader service's server talks to
    // Instagram and hands back direct media links (the phone never touches
    // Instagram's login wall) - the same model as tikwm for TikTok.
    private static Result fetchInstagramApi(String pageUrl) {
        try {
            String body0 = "q=" + URLEncoder.encode(pageUrl, "UTF-8") + "&t=media&lang=en";
            HttpURLConnection c = (HttpURLConnection) new URL("https://snapinsta.app/api/ajaxSearch").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", IG_UA);
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            c.setRequestProperty("Referer", "https://snapinsta.app/");
            c.setRequestProperty("Origin", "https://snapinsta.app");
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            java.io.OutputStream os = c.getOutputStream();
            os.write(body0.getBytes("UTF-8")); os.close();
            if (c.getResponseCode() / 100 != 2) return null;
            String body = readAll(c.getInputStream());
            try {
                JSONObject o = new JSONObject(body);
                String d = o.optString("data", "");
                if (!d.isEmpty()) body = d;
            } catch (Throwable ignored) {}
            body = body.replace("\\/", "/").replace("&amp;", "&");
            ArrayList<String> links = new ArrayList<>();
            int i = 0;
            while ((i = body.indexOf("href=\"", i)) >= 0) {
                int j = body.indexOf('"', i + 6);
                if (j < 0) break;
                links.add(body.substring(i + 6, j));
                i = j + 1;
            }
            ArrayList<String> videos = new ArrayList<>(), pics = new ArrayList<>();
            for (String l : links) {
                if (l.contains(".mp4")) { if (!videos.contains(l)) videos.add(l); }
                else if (l.contains(".jpg") || l.contains(".jpeg") || l.contains(".webp")) { if (!pics.contains(l)) pics.add(l); }
            }
            if (videos.isEmpty()) {
                for (String l : links) if (l.contains("rapidcdn")) { videos.add(l); if (!pageUrl.contains("/reel")) break; }
            }
            String thumb = null;
            int k = 0;
            while ((k = body.indexOf("src=\"", k)) >= 0) {
                int j = body.indexOf('"', k + 5);
                if (j < 0) break;
                String src = body.substring(k + 5, j);
                if (src.contains("cdninstagram") || src.contains("fbcdn") || src.contains("rapidcdn")) { thumb = src; break; }
                k = j + 1;
            }
            if (!videos.isEmpty() || !pics.isEmpty()) return igResultMulti(videos, pics, thumb, "Instagram post");
            return null;
        } catch (Throwable e) { return null; }
    }

    // ddinstagram: a public mirror that serves the post with open-graph
    // video tags (server-side again), handy when the search API is down.
    private static Result fetchInstagramDd(String pageUrl) {
        String code = instagramCode(pageUrl);
        if (code == null) return null;
        try {
            String type = pageUrl.contains("/reel") ? "reel" : "p";
            HttpURLConnection c = (HttpURLConnection) new URL("https://www.ddinstagram.com/" + type + "/" + code + "/").openConnection();
            c.setRequestProperty("User-Agent", IG_UA);
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            if (c.getResponseCode() / 100 != 2) return null;
            String html = readAll(c.getInputStream());
            String video = igMeta(html, "video");
            if (video == null) video = igMeta(html, "video:url");
            if (video == null) video = igMeta(html, "video:secure_url");
            String image = igMeta(html, "image");
            String title = igMeta(html, "title");
            if (title != null && title.endsWith(" on Instagram")) title = title.substring(0, title.length() - 13).trim();
            return igResult(video, image, title, IG_UA);
        } catch (Throwable e) { return null; }
    }

    private static Result fetchInstagramHttp(String pageUrl) {
        String code = instagramCode(pageUrl);
        if (code == null) return null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://www.instagram.com/p/" + code + "/embed/").openConnection();
            c.setRequestProperty("User-Agent", IG_UA);
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setConnectTimeout(12000); c.setReadTimeout(20000);
            if (c.getResponseCode() / 100 != 2) return null;
            String html = readAll(c.getInputStream());
            String video = igJsonString(html, "video_url");
            String image = igJsonString(html, "display_url");
            if (image == null) image = igMeta(html, "image");
            String title = igMeta(html, "title");
            return igResult(video, image, title, IG_UA);
        } catch (Throwable e) { return null; }
    }

    private static Result fetchInstagramWebView(final Context ctx, final String pageUrl) {
        final String code = instagramCode(pageUrl);
        if (code == null || !(ctx instanceof android.app.Activity)) return null;
        final Result[] holder = new Result[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final android.app.Activity act = (android.app.Activity) ctx;
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> {
            try {
                final android.webkit.WebView wv = new android.webkit.WebView(act);
                final android.view.ViewGroup parent = (android.view.ViewGroup) act.getWindow().getDecorView().findViewById(android.R.id.content);
                parent.addView(wv, new android.view.ViewGroup.LayoutParams(1, 1));
                android.webkit.WebSettings ws = wv.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);
                ws.setMediaPlaybackRequiresUserGesture(false);
                ws.setUserAgentString(IG_UA);
                try { android.webkit.CookieManager.getInstance().setAcceptCookie(true); } catch (Throwable ignored) {}
                final boolean[] finished = {false};
                final Runnable finishNow = () -> {
                    if (finished[0]) return; finished[0] = true;
                    try { wv.stopLoading(); } catch (Throwable ignored) {}
                    try { parent.removeView(wv); } catch (Throwable ignored) {}
                    try { wv.destroy(); } catch (Throwable ignored) {}
                    latch.countDown();
                };
                final String extractJs = "(function(){var v=document.querySelector('video');var vs=v?(v.currentSrc||v.src||''):'';var hasV=!!v;var img='';var metas=document.getElementsByTagName('meta');for(var i=0;i<metas.length;i++){if(metas[i].getAttribute('property')==='og:image'){img=metas[i].getAttribute('content')||'';}}return JSON.stringify({v:vs,hasV:hasV,img:img,t:document.title||''});})()";
                wv.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override public void onPageFinished(android.webkit.WebView view, String url) {
                        for (int k = 0; k < 5; k++) {
                            main.postDelayed(() -> {
                                if (finished[0] || holder[0] != null) return;
                                try {
                                    view.evaluateJavascript(extractJs, value -> {
                                        try {
                                            if (value == null || value.equals("null")) return;
                                            String json = value;
                                            if (json.startsWith("\"")) json = new JSONArray("[" + json + "]").getString(0);
                                            JSONObject o = new JSONObject(json);
                                            String v = o.optString("v", "");
                                            if (!v.isEmpty() && !v.startsWith("blob:")) {
                                                holder[0] = igResult(v, o.optString("img", ""), o.optString("t", ""), IG_UA);
                                                finishNow.run();
                                            } else if (!o.optBoolean("hasV", true)) {
                                                String img = o.optString("img", "");
                                                if (!img.isEmpty()) { holder[0] = igResult(null, img, o.optString("t", ""), IG_UA); finishNow.run(); }
                                            }
                                        } catch (Throwable ignored) {}
                                    });
                                } catch (Throwable ignored) {}
                            }, 1500L * (k + 1));
                        }
                        main.postDelayed(finishNow, 12000);
                    }
                });
                main.postDelayed(finishNow, 18000);
                wv.loadUrl("https://www.instagram.com/p/" + code + "/embed/");
            } catch (Throwable e) { latch.countDown(); }
        });
        try { latch.await(20, java.util.concurrent.TimeUnit.SECONDS); } catch (Throwable ignored) {}
        return holder[0];
    }

    // ---------- TikTok via tikwm (fast) ----------
    private static String requestTikwm(String u) throws Exception {
        String api = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(u, "UTF-8") + "&hd=1";
        HttpURLConnection conn = (HttpURLConnection) new URL(api).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
        conn.setConnectTimeout(15000); conn.setReadTimeout(15000);
        return readAll(conn.getInputStream());
    }
    private static Result parseTikwm(JSONObject root, boolean noWm) throws Exception {
        if (root.optInt("code", -1) != 0) throw new Exception(root.optString("msg", "tikwm failed"));
        JSONObject d = root.getJSONObject("data");
        Result r = new Result();
        r.tiktok = true;
        r.title = d.optString("title", "TikTok");
        r.thumbnail = d.optString("cover", "");
        r.durationSec = d.optLong("duration", 0);
        r.formats = new ArrayList<>();
        JSONArray imgs = d.optJSONArray("images");
        if (imgs != null && imgs.length() > 0) {
            Fmt f = new Fmt();
            f.id = "images"; f.kind = "Photos"; f.ext = "jpg";
            f.quality = imgs.length() + (imgs.length() == 1 ? " photo" : " photos");
            f.size = "—"; f.imageUrls = new ArrayList<>();
            for (int i = 0; i < imgs.length(); i++) f.imageUrls.add(imgs.getString(i));
            r.formats.add(f);
        }
        String hd = d.optString("hdplay", ""), play = d.optString("play", ""), wm = d.optString("wmplay", ""), music = d.optString("music", "");
        if (!hd.isEmpty()) { Fmt f = new Fmt(); f.id = "hd"; f.quality = "HD"; f.kind = "Video+Audio"; f.ext = "mp4"; f.directUrl = hd; f.size = "—"; r.formats.add(f); }
        if (!play.isEmpty() && !play.equals(hd)) { Fmt f = new Fmt(); f.id = "sd"; f.quality = "SD"; f.kind = "Video+Audio"; f.ext = "mp4"; f.directUrl = play; f.size = "—"; r.formats.add(f); }
        if (!noWm && !wm.isEmpty()) { Fmt f = new Fmt(); f.id = "wm"; f.quality = "SD (watermark)"; f.kind = "Video+Audio"; f.ext = "mp4"; f.directUrl = wm; f.watermarked = true; f.size = "—"; r.formats.add(f); }
        if (!music.isEmpty()) { Fmt f = new Fmt(); f.id = "mp3"; f.quality = "Audio"; f.kind = "Audio"; f.ext = "mp3"; f.directUrl = music; f.size = "—"; r.formats.add(f); }
        if (r.formats.isEmpty()) throw new Exception("No TikTok media found");
        return r;
    }

    // ---------- yt-dlp: fast route, then full route ----------
    private static Result fetchYtdlp(Context ctx, String u, boolean tiktok, boolean noWm) throws Exception {
        boolean youtube = u.contains("youtube.com") || u.contains("youtu.be");
        // REVERTED 2026-11-08 (17:28) on the user's explicit order: the
        // "1.3 purity" removals (v60/v61) are undone - everything is back
        // to the v59 configuration he had confirmed working. YouTube fetch
        // first tries the fast pinned pass, then the default pass.
        // From now on: DESIGN changes only, unless he orders otherwise.
        if (youtube) {
            try {
                JSONObject j = runYtdlp(ctx, u, true);
                Result r = parse(j, u, tiktok, noWm);
                if (!r.formats.isEmpty()) { return r; }
            } catch (Throwable ignored) {}
        }
        JSONObject j = runYtdlp(ctx, u, false);
        Result r = parse(j, u, tiktok, noWm);
        if (youtube) logFormats(ctx, "engine", r);
        if (!r.formats.isEmpty() && !youtube) writeDiskCache(ctx, u, "ytdlp", j.toString());
        return r;
    }

    /** One-line format summary into the shared diagnostics log (Settings). */
    static void logFormats(Context ctx, String route, Result r) {
        try {
            if (r == null) return;
            int best = -1, vids = 0;
            StringBuilder ids = new StringBuilder();
            for (Fmt f : r.formats) {
                if (f.kind != null && f.kind.contains("Video") && f.quality != null) {
                    vids++;
                    int h = 0;
                    for (int i = 0; i < f.quality.length() && Character.isDigit(f.quality.charAt(i)); i++) h = h * 10 + (f.quality.charAt(i) - '0');
                    if (h > best) best = h;
                    if (ids.length() < 90) { if (ids.length() > 0) ids.append(","); ids.append(f.id).append(":").append(f.quality); }
                }
            }
            String msg = "fetch route=" + route + " formats=" + r.formats.size() + " videos=" + vids + " bestH=" + (best < 0 ? "?" : best + "p") + " [" + ids + "]";
            java.io.File lf = new java.io.File(ctx.getFilesDir(), "download_diagnostics.log");
            java.io.FileWriter w = new java.io.FileWriter(lf, true);
            w.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + "  " + msg + "\n");
            w.close();
        } catch (Throwable ignored) {}
    }

    private static JSONObject runYtdlp(Context ctx, String u, boolean fast) throws Exception {
        // v59 configuration, restored 2026-11-08 (17:28) on the user's
        // order to add back everything the v60/v61 restorations removed:
        // EJS remote components, --no-warnings, --socket-timeout, cookies
        // when a session file exists, and the pinned fast pass below.
        YoutubeDLRequest req = new YoutubeDLRequest(u);
        java.io.File cookies = cookieFileFor(ctx, u);
        if (cookies.exists()) req.addOption("--cookies", cookies.getAbsolutePath());
        if (!u.contains("instagram.com")) req.addOption("--no-playlist");
        req.addOption("--no-warnings");
        req.addOption("--socket-timeout", "10");
        // YouTube's JavaScript challenges need the EJS solver components;
        // the QuickJS runtime itself is bundled and wired in by the
        // youtubedl-android library (libqjs).
        req.addOption("--remote-components", "ejs:github");
        req.addOption("-J");
        if (fast) {
            req.addOption("--extractor-args", "youtube:player_client=android_vr;player_skip=webpage,configs");
        }
        YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);
        return new JSONObject(resp.getOut());
    }

    private static Result parse(JSONObject info, String u, boolean tiktok, boolean noWm) throws Exception {
        Result r = new Result();
        r.tiktok = tiktok;
        r.title = info.optString("title", "Untitled");
        double duration = info.optDouble("duration", 0);
        r.durationSec = (long) duration;
        r.thumbnail = info.optString("thumbnail", "");
        if (r.thumbnail.isEmpty()) {
            JSONArray ths = info.optJSONArray("thumbnails");
            if (ths != null && ths.length() > 0) r.thumbnail = ths.optJSONObject(ths.length() - 1).optString("url", "");
        }
        JSONArray entries = info.optJSONArray("entries");
        JSONArray probe = info.optJSONArray("formats");
        if ((probe == null || probe.length() == 0) && entries != null && entries.length() > 0) {
            buildCarouselFormats(info, entries, r);
            return r;
        }
        JSONArray arr = info.optJSONArray("formats");
        LinkedHashMap<String, Fmt> best = new LinkedHashMap<>();
        LinkedHashMap<String, Double> bestScore = new LinkedHashMap<>();
        boolean anyUrl = false;
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                Fmt fm = new Fmt();
                fm.id = f.optString("format_id");
                if (fm.id.isEmpty()) continue;
                fm.ext = f.optString("ext");
                String note = f.optString("format_note", "");
                fm.watermarked = note.toLowerCase().contains("watermark") || (tiktok && fm.id.equals("download"));
                if (tiktok && noWm && fm.watermarked) continue;
                String vc = f.optString("vcodec", "none"), ac = f.optString("acodec", "none");
                if (vc.equals("none") && ac.equals("none")) continue;
                if (!f.optString("url", "").isEmpty()) anyUrl = true;
                fm.kind = (!vc.equals("none") && !ac.equals("none")) ? "Video+Audio" : (!vc.equals("none") ? "Video only" : "Audio");
                if (fm.kind.equals("Audio")) fm.ext = "mp3";
                int height = f.optInt("height", 0);
                fm.quality = height > 0 ? height + "p" : (note.isEmpty() ? fm.id : note);
                if (!vc.equals("none") && f.optString("dynamic_range", "").contains("HDR")) fm.quality += " HDR";
                long size = f.optLong("filesize", 0); if (size == 0) size = f.optLong("filesize_approx", 0);
                boolean estimated = false;
                if (size == 0) {
                    double tbr = f.optDouble("tbr", 0);
                    if (tbr > 0 && duration > 0) { size = (long) (tbr * 1000.0 / 8.0 * duration); estimated = true; }
                }
                fm.size = size > 0 ? (estimated ? "~" + mb(size) : mb(size)) : "—";
                String key = fm.quality + "|" + fm.ext + "|" + fm.kind;
                double score = size > 0 ? size : f.optDouble("tbr", 0);
                if (!best.containsKey(key) || score > bestScore.get(key)) { best.put(key, fm); bestScore.put(key, score); }
            }
        }
        dropWebmVideos(best);
        dropLowHdr(best);
        dropLowRes(best);
        r.formats = finishFormats(new ArrayList<>(best.values()), anyUrl, duration);
        return r;
    }

    private static ArrayList<Fmt> finishFormats(ArrayList<Fmt> found, boolean anyUrl, double durationSec) {
        found.sort((a, b) -> {
            if (a.kind.startsWith("Audio") != b.kind.startsWith("Audio")) return a.kind.startsWith("Audio") ? 1 : -1;
            return Integer.compare(parseH(b.quality), parseH(a.quality));
        });
        if (!found.isEmpty() && anyUrl) {
            Fmt bestOpt = new Fmt();
            bestOpt.id = "bestvideo+bestaudio/best";
            bestOpt.quality = "Best quality (recommended)"; bestOpt.kind = "Video+Audio"; bestOpt.ext = "mp4"; bestOpt.size = "—";
            found.add(0, bestOpt);
            Fmt audioMax = new Fmt();
            audioMax.id = "bestaudio/best";
            audioMax.quality = "Max quality"; audioMax.kind = "Audio"; audioMax.ext = "mp3"; audioMax.size = durationSec > 0 ? "~" + mb((long) (durationSec * 320000 / 8)) : "320 kbps";
            found.add(audioMax);
        }
        return found;
    }

    // ---------- YouTube Innertube FAST fetch (display only, v77) ----------
    // Races the three fastest Innertube clients (ANDROID_VR, ANDROID,
    // IOS) IN PARALLEL and takes the first playable format list - on the
    // user's phone this is the ~1-2s "Snaptube-like" fetch he confirmed
    // in GrabIt v10. Used ONLY to build the format list shown in the UI:
    // the caller strips every direct stream URL (stripDirect), so the
    // download itself still runs on the unchanged yt-dlp system. The
    // download service's own fresh-direct resolve (fetchFreshDirect ->
    // fetchInnertube, sequential over all six clients) is NOT touched.
    private static Result fetchInnertubeFast(Context ctx, String url) {
        final String vid = youtubeId(url);
        if (vid == null) return null;
        final String[] names = {"ANDROID_VR", "ANDROID", "IOS"};
        final String[] versions = {"1.65.10", "20.10.38", "20.10.4"};
        final String[] nums = {"28", "3", "5"};
        final String[] keys = {
            "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
        };
        final String[] uas = {
            "Mozilla/5.0 (Linux; Android 12L; Quest 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36",
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18.3.1 like Mac OS X;)"
        };
        final Result[] holder = new Result[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(names.length);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    String raw = postPlayer(vid, names[idx], versions[idx], nums[idx], uas[idx], keys[idx], idx);
                    if (raw != null) {
                        Result r = parseInnertube(new JSONObject(raw));
                        if (r != null && r.formats != null && !r.formats.isEmpty()) {
                            synchronized (holder) { if (holder[0] == null) holder[0] = r; }
                            done.countDown();
                        }
                    }
                } catch (Throwable ignored) {}
                if (pending.decrementAndGet() == 0) done.countDown();
            });
            t.setDaemon(true);
            t.start();
        }
        try { done.await(7, java.util.concurrent.TimeUnit.SECONDS); } catch (Throwable ignored) {}
        return holder[0];
    }

    /** Remove every direct-stream field from a fetch result, so the UI
     *  can only hand the DownloadService a plain yt-dlp job (mode
     *  "ytdlp") - the fetch route can never silently become the
     *  download route (the v11 mistake). Ids are itags, which yt-dlp
     *  uses as its format_ids too, and the synthetic Best/Max ids are
     *  the same strings the engine parser produces, so the existing
     *  download selector works on this list unchanged. */
    private static void stripDirect(Result r) {
        if (r == null || r.formats == null) return;
        for (Fmt f : r.formats) {
            f.directUrl = null;
            f.httpUa = null;
            f.audioUrl = null;
            f.audioContainer = null;
            f.container = null;
        }
    }

    // ---------- YouTube Innertube: one direct API request (no engine/Python) ----------
    private static String youtubeId(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:v=|youtu\\.be/|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static Result fetchInnertube(Context ctx, String url) {
        String vid = youtubeId(url);
        if (vid == null) return null;
        String[] names = {"ANDROID_VR", "ANDROID", "IOS", "WEB_EMBEDDED_PLAYER", "TVHTML5", "MWEB"};
        String[] versions = {"1.65.10", "20.10.38", "20.10.4", "1.20250219.01.00", "7.20250219.14.00", "2.20250219.01.00"};
        String[] nums = {"28", "3", "5", "56", "7", "119"};
        String[] keys = {
            "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        };
        String[] uas = {
            "Mozilla/5.0 (Linux; Android 12L; Quest 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36",
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18.3.1 like Mac OS X;)",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (Sony, BRAVIA_ATV3_4K, W6, ATV3)",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.39 Mobile Safari/537.36"
        };
        for (int i = 0; i < names.length; i++) {
            try {
                String raw = postPlayer(vid, names[i], versions[i], nums[i], uas[i], keys[i], i);
                if (raw == null) continue;
                lastInnertubeUa = uas[i];
                Result r = parseInnertube(new JSONObject(raw));
                if (r != null && !r.formats.isEmpty()) { writeDiskCache(ctx, url, "innertube", raw, uas[i]); return r; }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String postPlayer(String vid, String name, String version, String num, String ua, String key, int kind) throws Exception {
        JSONObject client = new JSONObject();
        client.put("clientName", name);
        client.put("clientVersion", version);
        client.put("hl", "en"); client.put("gl", "US");
        if (kind == 0) {
            client.put("deviceMake", "Oculus"); client.put("deviceModel", "Quest 3");
            client.put("osName", "Android"); client.put("osVersion", "12L"); client.put("androidSdkVersion", 32);
        } else if (kind == 1) {
            client.put("androidSdkVersion", 34); client.put("osName", "Android"); client.put("osVersion", "14");
        } else if (kind == 2) {
            client.put("deviceMake", "Apple"); client.put("deviceModel", "iPhone16,2");
            client.put("osName", "iOS"); client.put("osVersion", "18.3.1");
        } else if (kind == 4) {
            client.put("deviceMake", "Sony"); client.put("deviceModel", "BRAVIA_ATV3_4K");
            client.put("osName", "Android"); client.put("osVersion", "13");
        } else if (kind == 5) {
            client.put("platform", "MOBILE");
            client.put("osName", "Android"); client.put("osVersion", "14");
        }
        JSONObject context = new JSONObject(); context.put("client", client);
        if (kind == 3) {
            JSONObject tp = new JSONObject(); tp.put("embedUrl", "https://www.youtube.com/");
            context.put("thirdParty", tp);
        }
        JSONObject cpc = new JSONObject();
        cpc.put("vis", 0); cpc.put("splay", false); cpc.put("signatureTimestamp", 20000);
        JSONObject pbc = new JSONObject(); pbc.put("contentPlaybackContext", cpc);
        JSONObject body = new JSONObject();
        body.put("context", context);
        body.put("videoId", vid);
        body.put("playbackContext", pbc);
        body.put("racyCheckOk", true); body.put("contentCheckOk", true);
        HttpURLConnection conn = (HttpURLConnection) new URL("https://www.youtube.com/youtubei/v1/player?key=" + key + "&prettyPrint=false").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", ua);
        conn.setRequestProperty("X-YouTube-Client-Name", num);
        conn.setRequestProperty("X-YouTube-Client-Version", version);
        java.io.OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8")); os.close();
        if (conn.getResponseCode() != 200) return null;
        return readAll(conn.getInputStream());
    }

    private static Result parseInnertube(JSONObject d) {
        try {
            JSONObject ps = d.optJSONObject("playabilityStatus");
            if (ps == null || !"OK".equals(ps.optString("status"))) return null;
            JSONObject sd = d.optJSONObject("streamingData");
            if (sd == null) return null;
            JSONObject vd = d.optJSONObject("videoDetails");
            String title = vd != null ? vd.optString("title", "YouTube video") : "YouTube video";
            double duration = vd != null ? vd.optDouble("lengthSeconds", 0) : 0;
            String thumb = "";
            if (vd != null) {
                JSONObject th = vd.optJSONObject("thumbnail");
                JSONArray ths = th != null ? th.optJSONArray("thumbnails") : null;
                if (ths != null) {
                    int bw = -1;
                    for (int i = 0; i < ths.length(); i++) {
                        JSONObject t = ths.optJSONObject(i);
                        if (t != null && t.optInt("width", 0) >= bw) { bw = t.optInt("width", 0); thumb = t.optString("url", ""); }
                    }
                }
            }
            return parseStreamingData(sd, title, duration, thumb);
        } catch (Throwable e) { return null; }
    }

    private static Result parseStreamingData(JSONObject sd, String title, double duration) {
        return parseStreamingData(sd, title, duration, "");
    }

    private static Result parseStreamingData(JSONObject sd, String title, double duration, String thumb) {
        try {
            Result r = new Result();
            r.tiktok = false;
            r.title = title;
            r.durationSec = (long) duration;
            r.thumbnail = thumb;
            LinkedHashMap<String, Fmt> best = new LinkedHashMap<>();
            LinkedHashMap<String, Double> bs = new LinkedHashMap<>();
            boolean anyUrl = collectInnertube(sd.optJSONArray("formats"), true, duration, best, bs);
            anyUrl |= collectInnertube(sd.optJSONArray("adaptiveFormats"), false, duration, best, bs);
            dropWebmVideos(best);
            dropLowHdr(best);
            dropLowRes(best);
            if (best.isEmpty() || !anyUrl) return null;
            r.formats = finishFormats(new ArrayList<>(best.values()), true, duration);
            wireInnertubeDirect(sd, r.formats);
            return r.formats.isEmpty() ? null : r;
        } catch (Throwable e) { return null; }
    }

    private static String lastInnertubeUa = "";


    /** Attach companion audio streams to video-only formats and point "Best quality" at a direct mux. */
    private static void wireInnertubeDirect(JSONObject sd, ArrayList<Fmt> formats) {
        // best audio/mp4 (AAC) stream = mux companion
        Fmt bestAac = null; double bestAacScore = -1;
        for (Fmt f : formats) {
            if (f.kind.equals("Audio") && "mp4".equals(f.container) && f.directUrl != null && !f.directUrl.isEmpty()) {
                double sc = 0; try { sc = Double.parseDouble(f.quality.replace(" kbps", "")); } catch (Throwable ignored) {}
                if (sc > bestAacScore) { bestAacScore = sc; bestAac = f; }
            }
        }
        if (bestAac != null) {
            for (Fmt f : formats) {
                if (f.kind.equals("Video only") && "mp4".equals(f.container)) {
                    f.audioUrl = bestAac.directUrl;
                    f.audioContainer = "mp4";
                }
            }
        }
        // "Best quality" synthetic -> direct mux of the highest mp4 video-only + AAC audio
        Fmt bestOpt = null, topVideo = null;
        for (Fmt f : formats) {
            if (f.id.equals("bestvideo+bestaudio/best")) bestOpt = f;
            if (f.kind.equals("Video only") && "mp4".equals(f.container) && f.audioUrl != null) {
                if (topVideo == null || parseH(f.quality) > parseH(topVideo.quality)) topVideo = f;
            }
        }
        // "Max quality" MP3 synthetic -> best direct audio stream (service converts it to MP3 320K)
        Fmt audioMax = null, bestAudio = null; double bestAudioScore = -1;
        for (Fmt f : formats) {
            if (f.id.equals("bestaudio/best")) audioMax = f;
            if (f.kind.equals("Audio") && f.directUrl != null && !f.directUrl.isEmpty()) {
                double sc = 0; try { sc = Double.parseDouble(f.quality.replace(" kbps", "")); } catch (Throwable ignored) {}
                if ("mp4".equals(f.container)) sc += 0.5; // prefer AAC on ties
                if (sc > bestAudioScore) { bestAudioScore = sc; bestAudio = f; }
            }
        }
        if (audioMax != null && bestAudio != null) {
            audioMax.directUrl = bestAudio.directUrl;
            audioMax.httpUa = bestAudio.httpUa;
            audioMax.container = bestAudio.container;
        }
        // Stubborn (music) videos: audio streams are locked but the combined Video+Audio
        // stream (e.g. 360p) still has a direct URL. Download that and let the built-in
        // converter extract its audio to MP3 - the same trick converter apps use.
        if (audioMax != null && (audioMax.directUrl == null || audioMax.directUrl.isEmpty())) {
            Fmt srcV = null; int bestH = Integer.MAX_VALUE;
            for (Fmt f : formats) {
                if (f.kind.equals("Video+Audio") && "mp4".equals(f.container) && f.directUrl != null && !f.directUrl.isEmpty()) {
                    int h = parseH(f.quality); if (h <= 0) h = 720;
                    if (h < bestH) { bestH = h; srcV = f; }
                }
            }
            if (srcV != null) {
                audioMax.directUrl = srcV.directUrl;
                audioMax.httpUa = srcV.httpUa;
                audioMax.container = "mp4";
            }
        }
        if (bestOpt != null && topVideo != null) {
            bestOpt.directUrl = topVideo.directUrl;
            bestOpt.httpUa = topVideo.httpUa;
            bestOpt.container = "mp4";
            bestOpt.audioUrl = topVideo.audioUrl;
            bestOpt.audioContainer = "mp4";
            bestOpt.size = topVideo.size;
        }
    }

    private static boolean collectInnertube(JSONArray arr, boolean combined, double duration,
                                            LinkedHashMap<String, Fmt> best, LinkedHashMap<String, Double> bs) {
        boolean anyUrl = false;
        if (arr == null) return false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.optJSONObject(i);
            if (f == null) continue;
            if (f.optString("url", "").isEmpty()) continue; // cipher-only entries need the engine route
            anyUrl = true;
            String mime = f.optString("mimeType", "");
            boolean isAudio = mime.startsWith("audio");
            boolean isVideo = mime.startsWith("video");
            if (!isAudio && !isVideo) continue;
            Fmt fm = new Fmt();
            fm.directUrl = f.optString("url", "");
            fm.httpUa = lastInnertubeUa.isEmpty() ? "Mozilla/5.0 (Linux; Android 12L; Quest 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36" : lastInnertubeUa;
            fm.container = mime.contains("webm") ? "webm" : "mp4";
            fm.id = String.valueOf(f.optInt("itag", 0));
            if (fm.id.equals("0")) continue;
            fm.kind = combined ? "Video+Audio" : (isAudio ? "Audio" : "Video only");
            fm.ext = isAudio ? "mp3" : (mime.contains("webm") ? "webm" : "mp4");
            int height = f.optInt("height", 0);
            String ql = f.optString("qualityLabel", "");
            if (isAudio) {
                long abr = f.optLong("averageBitrate", 0); if (abr == 0) abr = f.optLong("bitrate", 0);
                fm.quality = abr > 0 ? Math.round(abr / 1000.0) + " kbps" : "Audio";
            } else {
                fm.quality = !ql.isEmpty() ? ql : (height > 0 ? height + "p" : fm.id);
            }
            long size = 0;
            try { size = Long.parseLong(f.optString("contentLength", "0")); } catch (Throwable ignored) {}
            boolean estimated = false;
            if (size == 0) {
                double br = f.optDouble("bitrate", 0);
                if (br > 0 && duration > 0) { size = (long) (br / 8.0 * duration); estimated = true; }
            }
            fm.size = size > 0 ? (estimated ? "~" + mb(size) : mb(size)) : "—";
            String key = fm.quality + "|" + fm.ext + "|" + fm.kind;
            double score = size > 0 ? size : f.optDouble("bitrate", 0);
            if (!best.containsKey(key) || score > bs.get(key)) { best.put(key, fm); bs.put(key, score); }
        }
        return anyUrl;
    }

    /** User request: no WEBM video formats in the lists. Audio is untouched
     *  (it is converted to MP3 anyway). If a source somehow offers ONLY webm
     *  video, keep it rather than showing nothing. */
    private static void dropWebmVideos(java.util.LinkedHashMap<String, Fmt> best) {
        boolean hasAlt = false;
        for (Fmt f : best.values()) if (!f.kind.equals("Audio") && !"webm".equals(f.ext)) { hasAlt = true; break; }
        if (hasAlt) best.values().removeIf(f -> !f.kind.equals("Audio") && "webm".equals(f.ext));
    }

    /** User request: HDR variants only make sense at high resolutions -
     *  below 1080p they just clutter the list, so they are removed there.
     *  HDR stays available from 1080p up to the best quality. */
    private static void dropLowHdr(java.util.LinkedHashMap<String, Fmt> best) {
        java.util.ArrayList<String> drop = new java.util.ArrayList<>();
        int keepVideo = 0;
        for (java.util.Map.Entry<String, Fmt> e : best.entrySet()) {
            Fmt f = e.getValue();
            if (f.kind.equals("Audio")) continue;
            int h = parseH(f.quality);
            if (f.quality.contains("HDR") && h > 0 && h < 1080) drop.add(e.getKey());
            else keepVideo++;
        }
        if (keepVideo > 0) for (String k : drop) best.remove(k);
    }

    /** User request: no 144p/240p rows, and no combined 360p (Video+Audio) -
     *  the plain 360p video-only row stays. Guarded: if a video somehow has
     *  ONLY such formats, they are kept rather than showing nothing. */
    private static void dropLowRes(java.util.LinkedHashMap<String, Fmt> best) {
        java.util.ArrayList<String> drop = new java.util.ArrayList<>();
        int keep = 0;
        for (java.util.Map.Entry<String, Fmt> e : best.entrySet()) {
            Fmt f = e.getValue();
            if (f.kind.equals("Audio") || f.kind.equals("Photos")) continue;
            int h = parseH(f.quality);
            boolean low = h > 0 && h <= 240;
            boolean comb360 = h == 360 && f.kind.equals("Video+Audio");
            if (low || comb360) drop.add(e.getKey()); else keep++;
        }
        if (keep > 0) for (String k : drop) best.remove(k);
    }

    /** Display size (user request): 1000 MB and up shows as GB with one
     *  decimal (5353.5 MB -> 5.2 GB, 1053.5 MB -> 1.0 GB); smaller sizes
     *  stay in MB. A leading ~ (estimate mark) is preserved. */
    public static String displaySize(String size) {
        if (size == null) return "";
        String s = size.trim();
        boolean approx = s.startsWith("~");
        if (approx) s = s.substring(1).trim();
        if (s.endsWith("MB")) {
            try {
                double mb = Double.parseDouble(s.substring(0, s.length() - 2).trim());
                if (mb >= 1000) return (approx ? "~" : "") + String.format(java.util.Locale.US, "%.1f", mb / 1024.0) + " GB";
            } catch (Exception ignored) {}
        }
        return size;
    }

    /** Display label: 2160p -> 4K, 1440p -> 2K, 4320p -> 8K (user request).
     *  The rest of the label (fps, HDR) is preserved: "2160p60 HDR" -> "4K 60 HDR". */
    public static String displayQuality(String q) {
        if (q == null) return "";
        String[][] map = {{"4320p", "8K"}, {"2160p", "4K"}, {"1440p", "2K"}};
        for (String[] m : map) {
            if (q.startsWith(m[0])) {
                String rest = q.substring(m[0].length()).trim();
                return rest.isEmpty() ? m[1] : m[1] + " " + rest;
            }
        }
        return q;
    }

    private static int parseH(String q) {
        if (q == null) return 0;
        int v = 0, i = 0;
        while (i < q.length() && Character.isDigit(q.charAt(i))) { v = v * 10 + (q.charAt(i) - '0'); i++; }
        return v;
    }
    private static String mb(long bytes) { return String.format(Locale.US, "%.1f MB", bytes / 1048576.0); }
    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }
}
