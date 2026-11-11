# GrabIt Audio Engine Rebuild — Diagnosis, Plan, Requirement Map, Tests

Implements the spec: "Upgrade Audio Download Engine to SnapTube-Level Reliability" (GrabIt v21 / 3.5).

## 1. Diagnosis — why audio downloads failed (found by inspecting the existing project)

1. **No resume state at all.** Downloads wrote straight to the final temp file. Any network interruption, process kill, or app close discarded all progress and the job either restarted silently or died.
2. **Signed-URL expiry was unrecoverable.** YouTube media URLs expire. When one died mid-job (HTTP 403), the failure was terminal and misreported; the app never re-resolved a fresh URL for the same song.
3. **Route inconsistency.** One code path downloaded sequentially, another used parallel fragments, another fell into the yt-dlp engine (whose child process the phone freezes in the background). Same tap, different behaviour per song — the core of the user's complaint.
4. **No HTTP error taxonomy.** 403 (expired), 416 (bad resume position), 429 (rate limit) and 5xx were all treated the same; there was no backoff, no Retry-After handling, and no permanent-vs-temporary distinction.
5. **No completion validation.** A download was "done" when the stream ended — error pages and truncated files could pass as audio.
6. **Progress blindness.** The notification only refreshed when the percent changed, and several stages report no percent — so jobs looked frozen and then jumped to "Done" (fixed in v20, kept here).

## 2. Implementation plan (as executed)

- Add a dedicated `AudioEngine` class owning the whole audio transfer; leave the UI, the fetcher, the video pipeline and TikTok exactly as they were (they work).
- Route **every** audio job through it: resolve fresh streams → AudioEngine download → on-device conversion (bundled-ffmpeg MP3 320K → Android-native M4A extraction → raw OPUS save). The yt-dlp engine remains only as the last-resort route for fully locked songs.
- Persist enough state (`.part` + JSON sidecar + pending-job record) for pause/resume to survive process death.

## 3. Requirement map (spec section → implementation)

| Spec | Implementation |
|---|---|
| §2 Connection manager | Single pooled client code path (HttpURLConnection, keep-alive via the platform pool), 20s connect / 60s read timeouts, redirects followed, error streams closed, bodies always closed in `finally`. Status taxonomy: 403 → refresh URL (max 2), 404 → permanent, 408/429/5xx → backoff retry, 416 → see §4, other 4xx → permanent. Expired signed URLs detected via 403 and refreshed through the app's own resolver. |
| §3 Streaming engine | 64 KB buffered streams straight to disk (buffer is a named constant), file is never loaded into RAM, output flushed + `fd.sync()` every 2 MB, partial writes tracked in a sidecar, disk-full detected (preflight space check + ENOSPC classification), downloads land in `<base>.part` until validated. |
| §4 Advanced resume | Range + If-Range (ETag preferred, else Last-Modified) sent when a `.part` exists; 206 required and its Content-Range start must equal our byte count or the part is discarded and restarted; a 200 answer to a resume truncates and restarts safely (never appends a full body); 416 with a complete part validates as finished, otherwise one clean restart then a permanent error; expired URL → fresh URL from the resolver before resuming (the 206 checks re-validate the position against the new URL). Per-video deterministic file names (`audio_<hash>`) so resume works across restarts and songs can't contaminate each other. |
| §5 Retry system | Exponential backoff 1s→30s cap with ±30% jitter, `Retry-After` honoured (whichever is longer), max 6 attempts per session (plus unlimited later resumes), connectivity failures classified as temporary, 403-after-refresh / 404 / other 4xx classified permanent and never retried endlessly. |
| §6 Adaptive concurrency | One connection per file by design (the spec's default); segmented downloading was removed from the audio path in v20 because on this device it caused invisible progress and join failures; separate-file concurrency is limited to one active job — the reliable setting for the target phone. |
| §7 Background execution | Foreground service (dataSync type, Android 14/15-compliant), partial wake lock, work independent of the Activity lifecycle, Pause + Cancel actions in the notification, job state persisted so process death is recoverable: reopening the app shows "Resume paused download — <title>", and re-downloading the same song resumes from the saved part automatically. |
| §8 File validation | Exact byte-count check against Content-Range/Content-Length totals, first-bytes sniffing rejects HTML/XML error pages, MediaExtractor must parse at least one track, only then is the `.part` renamed to the final file; incomplete/corrupt parts are deleted with the reason recorded. Conversion output is size-checked too. |
| §9 Diagnostics | Every attempt appends one JSON line to `files/download_diagnostics.log`: timestamp, host only (never query strings), HTTP status, failure reason, retry count, downloaded bytes, expected bytes, Range support, ETag, duration, error category, result. No tokens, cookies or signed URLs are ever written. |
| §1 Stack note | The shipping app is the existing Java codebase (the environment that produces this APK has no Gradle/Kotlin toolchain, so a Kotlin rewrite cannot be compiled here). The engine implements the spec's behaviours directly; the equivalent Gradle dependencies for a Kotlin build are listed in §5 below for the separate Kotlin project. |

## 4. Manifest permissions (all already declared)

INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS, WAKE_LOCK, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.

## 5. Gradle dependencies (reference, for the Kotlin/Gradle project)

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.work:work-runtime-ktx:2.9.1")
implementation("androidx.media3:media3-common:1.4.1")
implementation("androidx.media3:media3-extractor:1.4.1")
```

## 6. Testing — the 13 scenarios from the spec

Run on the phone with GrabIt v21. After any test, the diagnostics log (`Android/data/com.shadhinkhan.grabit/files/` is the media folder; the log lives in the app's internal files — ask Muse to read it via a diagnostics screenshot if needed) records what the engine decided.

1. **Normal MP3 URL** — download any song as MP3 320K. Expect steady % progress, conversion, file in Music/GrabIt.
2. **Normal M4A URL** — pick a lower-quality audio format. Expect the same pipeline to complete and save.
3. **Slow network** — enable a data saver / move to a weak-signal spot mid-download. Expect progress to slow but continue; retries visible in the notification.
4. **Disconnection during download** — turn on airplane mode for 30s mid-download. Expect backoff retries, then automatic resume from the saved part when the network returns (no restart from 0%).
5. **Reconnection** — same as 4; watch the byte counter continue from where it stopped.
6. **App in background** — start a download, switch to YouTube. Expect the notification to keep ticking (direct downloads are not frozen).
7. **Process killed** — start a download, swipe GrabIt away / force-stop, reopen. Expect the "Resume paused download" button; tapping it continues from the saved part.
8. **Server without Range support** — TikTok music download (single-connection source). Expect a clean full download with live MB progress.
9. **HTTP 403** — leave a fetched song open 30+ minutes, then download. Expect "Refreshing expired link…" and a successful retry with a fresh URL.
10. **Expired media URL** — same setup as 9; if refresh fails, expect a clear failure message naming the 403 — not a stuck 0%.
11. **HTTP 416** — pause a download near completion, resume it. Expect the engine to validate/complete instead of corrupting the file.
12. **Insufficient storage** — fill the phone, start a large download. Expect a clear "not enough space / storage full" failure, with the partial file preserved for later resume.
13. **Concurrent downloads** — start a second download while one runs. Expect it to queue behind the active job rather than corrupting either (one active job by design).

## 7. Known limits (honest)

- A small set of songs is fully locked by YouTube for every route; those still use the labelled engine backup route, which the phone may pause in the background — the notification says so when it happens.
- "Concurrent downloads" is intentionally sequential (reliability setting for this phone), per §6 above.
