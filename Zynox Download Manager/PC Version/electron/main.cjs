// Zynox for Windows v3 - Electron main process.
// Download system mirrors the phone app, per platform:
//  - YouTube: fetch = Innertube race UNION (all clients merged) with an
//    engine merge when the list looks thin; download = yt-dlp engine
//    (android_vr-pinned pass like the phone Fetcher, then default
//    clients), and if YouTube still blocks the engine ("confirm you're
//    not a bot"), a direct Innertube stream download of the exact
//    picked format, merged with the bundled ffmpeg.
//  - TikTok: tikwm API first (the phone route) - play/hdplay are the
//    watermark-free streams, downloaded directly; yt-dlp is fallback.
//  - Everything else: yt-dlp (cookies attached for FB/IG when the user
//    connected an account in Settings).
// Downloads live in the main process: they continue in the tray and
// fire a Windows notification on completion.
const { app, BrowserWindow, ipcMain, dialog, shell, clipboard, Notification, Tray, Menu, session } = require('electron');
const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const crypto = require('crypto');
const { spawn } = require('child_process');

let win = null;
let tray = null;
let isQuitting = false;

function binPath(name) {
  const packaged = path.join(process.resourcesPath || '', 'bin', name);
  if (app.isPackaged && fs.existsSync(packaged)) return packaged;
  return path.join(__dirname, '..', 'bin', name);
}
function userBin(name) { return path.join(app.getPath('userData'), 'bin', name); }
function ytdlpExe() { const u = userBin('yt-dlp.exe'); return fs.existsSync(u) ? u : binPath('yt-dlp.exe'); }
function ffmpegExe() { return binPath('ffmpeg.exe'); }
function qjsExe() { return binPath('qjs.exe'); }
// The phone's engine runs with a bundled QuickJS runtime wired in
// (youtubedl-android's libqjs) - without ANY JS runtime, yt-dlp
// cannot solve YouTube's JS challenges and reports videos like
// RsN7VTtR1qo as "This video is not available" (reproduced and
// verified in the sandbox: adding QuickJS clears exactly that
// error). The PC ships qjs.exe for the same job.
function jsRuntimeArgs() { try { return fs.existsSync(qjsExe()) ? ['--js-runtimes', 'quickjs:' + qjsExe()] : []; } catch { return []; } }
function cookiesFile() { return path.join(app.getPath('userData'), 'cookies.txt'); }
function hasCookies() { try { return fs.existsSync(cookiesFile()) && fs.statSync(cookiesFile()).size > 30; } catch { return false; } }

// ---------- settings ----------
function settingsFile() { return path.join(app.getPath('userData'), 'zynox-settings.json'); }
function loadSettings() { try { return JSON.parse(fs.readFileSync(settingsFile(), 'utf8')); } catch { return {}; } }
function saveSettings(patch) { const s = { ...loadSettings(), ...patch }; try { fs.writeFileSync(settingsFile(), JSON.stringify(s, null, 2)); } catch {} return s; }
function downloadsDir() {
  const s = loadSettings();
  if (s.downloadDir && fs.existsSync(s.downloadDir)) return s.downloadDir;
  const d = path.join(app.getPath('downloads'), 'Zynox');
  try { fs.mkdirSync(d, { recursive: true }); } catch {}
  return d;
}

// ---------- window / tray / notifications ----------
function showWindow() { if (!win) return createWindow(); if (win.isMinimized()) win.restore(); win.show(); win.focus(); }
function createWindow() {
  win = new BrowserWindow({
    width: 1180, height: 780, minWidth: 720, minHeight: 540,
    frame: false, backgroundColor: '#07040d', show: false,
    icon: path.join(__dirname, '..', 'assets', 'icon.ico'),
    webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false },
  });
  win.once('ready-to-show', () => win.show());
  win.on('close', (e) => {
    const s = loadSettings();
    if (!isQuitting && s.closeToTray !== false) {
      e.preventDefault();
      win.hide();
      if (!s.trayNotified) {
        saveSettings({ trayNotified: true });
        notify('Zynox is still running', 'Downloads continue in the background. Find Zynox in the system tray.');
      }
    }
  });
  win.on('closed', () => { win = null; });
  if (app.isPackaged) win.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
  else win.loadURL('http://localhost:5173');
}
function createTray() {
  try {
    tray = new Tray(path.join(__dirname, '..', 'assets', 'icon.ico'));
    tray.setToolTip('Zynox — downloader running');
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: 'Open Zynox', click: showWindow },
      { label: 'Open downloads folder', click: () => shell.openPath(downloadsDir()) },
      { type: 'separator' },
      { label: 'Quit Zynox', click: () => { isQuitting = true; app.quit(); } },
    ]));
    tray.on('double-click', showWindow);
  } catch {}
}
function notify(title, body) {
  try {
    if (!Notification.isSupported()) return;
    const n = new Notification({ title, body, icon: path.join(__dirname, '..', 'assets', 'icon.png') });
    n.on('click', showWindow);
    n.show();
  } catch {}
}
// zynox:// protocol - the browser extension launches Zynox with a
// video link (IDM-style): zynox://download?url=<encoded page url>.
try { app.setAsDefaultProtocolClient('zynox'); } catch {}
let pendingExtUrl = '';
function handleExtUrl(raw) {
  try {
    const u = new URL(String(raw));
    if (u.protocol !== 'zynox:') return;
    const target = u.searchParams.get('url') || '';
    if (!/^https?:\/\//.test(target)) return;
    pendingExtUrl = target;
    showWindow();
    if (win && win.webContents && !win.webContents.isLoading()) {
      win.webContents.send('ext-url', target);
      pendingExtUrl = '';
    }
  } catch {}
}
ipcMain.handle('get-pending-url', () => { const u = pendingExtUrl; pendingExtUrl = ''; return u; });

// Single instance: a second launch (or a click while Zynox sits in
// the tray) must focus the existing window, never open another one.
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) app.quit();
else {
  app.on('second-instance', (_e, argv) => {
    showWindow();
    const link = (argv || []).find((a) => String(a).startsWith('zynox://'));
    if (link) handleExtUrl(link);
  });
  app.whenReady().then(() => {
    const link = process.argv.find((a) => String(a).startsWith('zynox://'));
    if (link) { try { pendingExtUrl = new URL(link).searchParams.get('url') || ''; if (!/^https?:\/\//.test(pendingExtUrl)) pendingExtUrl = ''; } catch { pendingExtUrl = ''; } }
    createWindow(); createTray();
    setTimeout(autoEngineDailyCheck, 9000);
  });
}
app.on('before-quit', () => { isQuitting = true; });
app.on('window-all-closed', () => { /* tray keeps us alive */ });
app.on('activate', showWindow);

// ---------- http helpers ----------
function httpsJson(host, pathName, headers, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = https.request({ host, path: pathName, method: body ? 'POST' : 'GET',
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Zynox-PC', ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}), ...headers } },
      (res) => { let s = ''; res.on('data', (c) => (s += c)); res.on('end', () => { try { resolve(JSON.parse(s)); } catch (e) { reject(e); } }); });
    req.setTimeout(9000, () => req.destroy(new Error('timeout')));
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}
function shortErr(s) {
  const lines = String(s || '').split('\n').map((x) => x.trim()).filter(Boolean);
  const e = [...lines].reverse().find((l) => l.startsWith('ERROR')) || lines[lines.length - 1] || 'Engine error';
  return e.replace(/^ERROR:\s*/, '').slice(0, 220);
}

// ---------- Innertube (YouTube) ----------
const INNERTUBE_KEY = 'AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8';
const CLIENTS = [
  { client: { clientName: 'ANDROID_VR', clientVersion: '1.65.10', deviceMake: 'Oculus', deviceModel: 'Quest 3', androidSdkVersion: 32, osName: 'Android', osVersion: '12L', hl: 'en', gl: 'US' },
    ua: 'com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip', num: '28', key: 'AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w' },
  { client: { clientName: 'ANDROID', clientVersion: '20.10.38', androidSdkVersion: 34, osName: 'Android', osVersion: '14', hl: 'en', gl: 'US' },
    ua: 'com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip', num: '3', key: 'AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w' },
  { client: { clientName: 'IOS', clientVersion: '20.10.4', deviceMake: 'Apple', deviceModel: 'iPhone16,2', osName: 'iOS', osVersion: '18.3.1', hl: 'en', gl: 'US' },
    ua: 'com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_1 like Mac OS X;)', num: '5', key: 'AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc' },
];
// The phone's fresh-resolve list (Fetcher.fetchInnertube): the three
// race clients first, then WEB_EMBEDDED / TVHTML5 / MWEB, in order.
const FRESH_CLIENTS = [...CLIENTS,
  { client: { clientName: 'WEB_EMBEDDED_PLAYER', clientVersion: '1.20250219.01.00', hl: 'en', gl: 'US' },
    ua: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36', num: '56', key: INNERTUBE_KEY,
    thirdParty: { embedUrl: 'https://www.youtube.com/' } },
  { client: { clientName: 'TVHTML5', clientVersion: '7.20250219.14.00', deviceMake: 'Sony', deviceModel: 'BRAVIA_ATV3_4K', osName: 'Android', osVersion: '13', hl: 'en', gl: 'US' },
    ua: 'Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (Sony, BRAVIA_ATV3_4K, W6, ATV3)', num: '7', key: INNERTUBE_KEY },
  { client: { clientName: 'MWEB', clientVersion: '2.20250219.01.00', platform: 'MOBILE', osName: 'Android', osVersion: '14', hl: 'en', gl: 'US' },
    ua: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.39 Mobile Safari/537.36', num: '119', key: INNERTUBE_KEY },
];
function youtubeId(url) {
  const m = String(url).match(/(?:youtu\.be\/|[?&]v=|\/shorts\/|\/embed\/)([A-Za-z0-9_-]{11})/);
  return m ? m[1] : (/^[A-Za-z0-9_-]{11}$/.test(String(url).trim()) ? String(url).trim() : null);
}
async function innertubeOnce(videoId, c) {
  const context = { client: c.client };
  if (c.thirdParty) context.thirdParty = c.thirdParty;
  const body = { context, videoId, playbackContext: { contentPlaybackContext: { vis: 0, splay: false, lactMilliseconds: '-1', signatureTimestamp: 20073 } }, racyCheckOk: true, contentCheckOk: true };
  const json = await httpsJson('www.youtube.com', `/youtubei/v1/player?key=${c.key || INNERTUBE_KEY}&prettyPrint=false`,
    { 'User-Agent': c.ua, 'X-YouTube-Client-Name': c.num || '28', 'X-YouTube-Client-Version': c.client.clientVersion }, body);
  const sd = json && json.streamingData;
  if (!sd) throw new Error('no streamingData');
  const all = [...(sd.formats || []), ...(sd.adaptiveFormats || [])];
  if (!all.some((f) => f.url && String(f.mimeType || '').startsWith('video/'))) throw new Error('no playable formats');
  return json;
}
// The phone's DISPLAY fetch (Fetcher.fetchInnertubeFast): the three
// clients race IN PARALLEL and the FIRST playable response wins - the
// list is built from that one response, never a union.
async function innertubeRace(videoId) {
  const one = (c) => innertubeOnce(videoId, c).then((j) => {
    const sd = j.streamingData || {};
    return { videoDetails: j.videoDetails || {},
      formats: [...(sd.formats || []), ...(sd.adaptiveFormats || [])],
      combinedItags: new Set((sd.formats || []).map((f) => f.itag)) };
  });
  try { return await Promise.any(CLIENTS.map(one)); } catch { return null; }
}
// The phone's fresh resolve for downloads (Fetcher.fetchInnertube):
// six clients tried IN ORDER, first playable response wins.
async function freshInnertube(videoId) {
  for (const c of FRESH_CLIENTS) {
    try {
      const j = await innertubeOnce(videoId, c);
      const sd = j.streamingData || {};
      return { formats: [...(sd.formats || []), ...(sd.adaptiveFormats || [])], ua: c.ua };
    } catch { /* next client */ }
  }
  return null;
}
function fmtSize(f, durSec) {
  if (f.contentLength) return parseInt(f.contentLength, 10);
  if (f.bitrate && durSec) return Math.round((f.bitrate * durSec) / 8);
  return 0;
}
function parseInnertubeUnion(u) {
  const vd = u.videoDetails || {};
  const dur = parseInt(vd.lengthSeconds || '0', 10);
  const all = u.formats;
  const pick = (minH) => {
    const vids = new Map();
    for (const f of all) {
      const mime = String(f.mimeType || '');
      if (!mime.startsWith('video/') || !f.height || f.height < minH) continue;
      const key = f.height + 'x' + (f.fps || 0);
      const score = (mime.includes('mp4') ? 1000 : 0) + (f.bitrate || 0);
      if (!vids.has(key) || score > vids.get(key).score) vids.set(key, { f, score });
    }
    return [...vids.values()].map((v) => v.f).sort((a, b) => (b.height - a.height) || ((b.fps || 0) - (a.fps || 0)) || ((b.bitrate || 0) - (a.bitrate || 0)));
  };
  let vlist = pick(360);
  if (!vlist.length) vlist = pick(144);
  const formats = vlist.map((f) => ({
    id: 'v' + f.itag, itag: f.itag, kind: 'video', combined: u.combinedItags.has(f.itag),
    label: `${f.height}p${(f.fps || 0) > 30 ? f.fps : ''}`,
    detail: (String(f.mimeType).includes('webm') ? 'WEBM' : 'MP4') + (u.combinedItags.has(f.itag) ? ' • with audio' : ' • video only'),
    size: fmtSize(f, dur), height: f.height,
  }));
  // The phone's synthetic entries (Fetcher.finishFormats): "Best
  // quality (recommended)" first, "Max quality" audio last.
  if (formats.length) formats.unshift({ id: 'best', itag: '', kind: 'video', combined: true,
    label: 'Best quality', detail: 'Recommended • MP4 • with audio', size: 0, height: 100000 });
  const auds = all.filter((f) => String(f.mimeType || '').startsWith('audio/')).sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0));
  if (auds.length) {
    for (const a of auds.slice(0, 2)) formats.push({ id: 'a' + a.itag, itag: a.itag, kind: 'audio-mp3',
      label: 'MP3', detail: Math.round((a.bitrate || 0) / 1000) + ' kbps • converted to MP3', size: fmtSize(a, dur) });
    formats.push({ id: 'mp3max', itag: 'bestaudio/best', kind: 'audio-mp3', label: 'MP3 · Max quality',
      detail: '320 kbps • best audio converted', size: dur ? Math.round((dur * 320000) / 8) : 0 });
  }
  const thumbs = (vd.thumbnail && vd.thumbnail.thumbnails) || [];
  return { title: vd.title || 'YouTube video', author: vd.author || '', duration: dur,
    thumbnail: thumbs.length ? thumbs[thumbs.length - 1].url : '', formats, source: 'innertube-fast' };
}
// The phone's engine-fetch list builder (Fetcher.parse): formats
// deduped by quality|ext|kind keeping the biggest, webm dropped when
// an mp4 twin exists, sub-360p dropped when a 360p+ exists, plus the
// same Best / Max synthetics the Innertube list carries.
function engineJsonRows(j) {
  const best = new Map();
  let anyUrl = false;
  for (const f of j.formats || []) {
    if (!f || !f.format_id) continue;
    const vc = f.vcodec || 'none', ac = f.acodec || 'none';
    if (vc === 'none' && ac === 'none') continue;
    if (f.url) anyUrl = true;
    const kind = vc !== 'none' && ac !== 'none' ? 'combined' : vc !== 'none' ? 'vonly' : 'audio';
    const h = f.height || 0;
    const size = f.filesize || f.filesize_approx || (f.tbr && j.duration ? Math.round((f.tbr * 1000 * j.duration) / 8) : 0);
    const key = (kind === 'audio' ? 'audio' : h + 'p') + '|' + (kind === 'audio' ? 'mp3' : f.ext) + '|' + kind;
    const score = size || f.tbr || 0;
    if (!best.has(key) || score > best.get(key).score) best.set(key, { f, kind, h, size, score });
  }
  let rows = [...best.values()];
  rows = rows.filter((r) => !(r.kind !== 'audio' && r.f.ext === 'webm' && rows.some((o) => o !== r && o.kind === r.kind && o.h === r.h && o.f.ext === 'mp4')));
  if (rows.some((r) => r.kind !== 'audio' && r.h >= 360)) rows = rows.filter((r) => r.kind === 'audio' || r.h >= 360);
  const vids = rows.filter((r) => r.kind !== 'audio').sort((a, b) => b.h - a.h);
  const auds = rows.filter((r) => r.kind === 'audio');
  const out = [];
  if (anyUrl && vids.length) out.push({ id: 'best', itag: '', kind: 'video', combined: true,
    label: 'Best quality', detail: 'Recommended • MP4 • with audio', size: 0, height: 100000 });
  for (const r of vids) out.push({ id: 'v' + r.f.format_id, itag: String(r.f.format_id), kind: 'video', combined: r.kind === 'combined',
    label: `${r.h}p${(r.f.fps || 0) > 30 ? r.f.fps : ''}`,
    detail: String(r.f.ext || '').toUpperCase() + (r.kind === 'combined' ? ' • with audio' : ' • video only'),
    size: r.size || 0, height: r.h });
  for (const r of auds) out.push({ id: 'a' + r.f.format_id, itag: String(r.f.format_id), kind: 'audio-mp3',
    label: 'MP3', detail: Math.round(r.f.abr || r.f.tbr || 0) + ' kbps • converted to MP3', size: 0 });
  if (anyUrl && auds.length) out.push({ id: 'mp3max', itag: 'bestaudio/best', kind: 'audio-mp3',
    label: 'MP3 · Max quality', detail: '320 kbps • best audio converted', size: j.duration ? Math.round((j.duration * 320000) / 8) : 0 });
  return out;
}
// The phone's engine fetch (Fetcher.runYtdlp): EJS remote components,
// socket-timeout, cookies when a session exists - YouTube tries the
// pinned android_vr pass first, then the default pass.
async function engineFetchYouTube(url) {
  for (const pinned of [true, false]) {
    try {
      const extra = ['--socket-timeout', '10', '--remote-components', 'ejs:github'];
      if (pinned) extra.push('--extractor-args', 'youtube:player_client=android_vr;player_skip=webpage,configs');
      const j = await runYtdlpJson(url, extra);
      const rows = engineJsonRows(j);
      if (rows.length) return { title: j.title || 'Video', author: j.uploader || '', duration: j.duration || 0,
        thumbnail: j.thumbnail || '', formats: rows, source: 'yt-dlp', noWatermark: false };
    } catch { /* next pass */ }
  }
  return null;
}

// ---------- yt-dlp fetch ----------
function isSocial(url) { return /facebook\.com|fb\.watch|instagram\.com/.test(url); }
function cookieArgs(url) { return isSocial(url) && hasCookies() ? ['--cookies', cookiesFile()] : []; }
function runYtdlpJson(url, extra) {
  return new Promise((resolve, reject) => {
    const p = spawn(ytdlpExe(), ['-J', '--no-playlist', '--no-warnings', ...jsRuntimeArgs(), ...(extra || []), ...cookieArgs(url), url], { windowsHide: true });
    let out = '', err = '';
    p.stdout.on('data', (d) => (out += d));
    p.stderr.on('data', (d) => (err += d));
    p.on('error', reject);
    p.on('close', (code) => {
      if (code !== 0) return reject(new Error(shortErr(err)));
      try { resolve(JSON.parse(out)); } catch (e) { reject(e); }
    });
  });
}
function parseGeneric(j, pageUrl) {
  const isTikTok = (j.extractor || '').toLowerCase().includes('tiktok') || /tiktok\.com/.test(pageUrl || '');
  const rawVids = [];
  for (const f of j.formats || []) {
    if (!f || !f.format_id) continue;
    if (isTikTok && String(f.format_id).includes('download')) continue;
    if (f.vcodec && f.vcodec !== 'none' && f.height) rawVids.push(f);
  }
  const build = (minH) => {
    const vids = new Map();
    for (const f of rawVids) {
      if (f.height < minH) continue;
      const key = f.height + 'x' + (f.fps || 0);
      const score = (f.ext === 'mp4' ? 1000 : 0) + (f.tbr || 0);
      if (!vids.has(key) || score > vids.get(key).score) vids.set(key, { f, score });
    }
    return [...vids.values()].map((v) => v.f).sort((a, b) => (b.height - a.height) || ((b.tbr || 0) - (a.tbr || 0)));
  };
  let vlist = build(360);
  if (!vlist.length) vlist = build(1);
  const formats = vlist.map((f) => ({
    id: 'v' + f.format_id, itag: f.format_id, kind: 'video', combined: !!(f.acodec && f.acodec !== 'none'),
    label: `${f.height}p${(f.fps || 0) > 30 ? f.fps : ''}`,
    detail: String(f.ext || '').toUpperCase() + (f.acodec && f.acodec !== 'none' ? ' • with audio' : ' • video only') + (isTikTok ? ' • no watermark' : ''),
    size: f.filesize || f.filesize_approx || 0, height: f.height,
  }));
  if ((j.formats || []).some((f) => f.acodec && f.acodec !== 'none')) {
    formats.push({ id: 'mp3', itag: 0, kind: 'audio-mp3', label: 'MP3', detail: '320 kbps • best audio converted', size: 0 });
  }
  return { title: j.title || 'Video', author: j.uploader || '', duration: j.duration || 0, thumbnail: j.thumbnail || '',
    formats, source: 'yt-dlp', noWatermark: isTikTok };
}

// ---------- TikTok via tikwm (the phone route) ----------
async function fetchTikwm(url) {
  const api = '/api/?url=' + encodeURIComponent(url) + '&hd=1';
  const j = await httpsJson('www.tikwm.com', api, {}, null);
  if (!j || j.code !== 0 || !j.data) throw new Error('tikwm failed');
  const d = j.data;
  const formats = [];
  if (d.hdplay) formats.push({ id: 'tt-hd', kind: 'direct-video', directUrl: d.hdplay, label: 'HD', detail: 'MP4 • no watermark', size: d.hd_size || d.size || 0, ext: 'mp4' });
  if (d.play && d.play !== d.hdplay) formats.push({ id: 'tt-sd', kind: 'direct-video', directUrl: d.play, label: 'SD', detail: 'MP4 • no watermark', size: d.size || 0, ext: 'mp4' });
  if (d.wmplay) formats.push({ id: 'tt-wm', kind: 'direct-video', directUrl: d.wmplay, label: 'WM', detail: 'MP4 • with watermark', size: d.wm_size || 0, ext: 'mp4' });
  if (d.music) formats.push({ id: 'tt-mp3', kind: 'direct-audio', directUrl: d.music, label: 'MP3', detail: 'Original sound', size: 0, ext: 'mp3' });
  (d.images || []).forEach((im, i) => formats.push({ id: 'tt-img' + i, kind: 'image', directUrl: im, label: 'Photo ' + (i + 1), detail: 'Image', size: 0, ext: 'jpg' }));
  if (!formats.length) throw new Error('tikwm: nothing downloadable');
  return { title: d.title || 'TikTok video', author: (d.author && d.author.nickname) || '', duration: d.duration || 0,
    thumbnail: d.cover || d.origin_cover || '', formats, source: 'tikwm', noWatermark: true, videoId: String(d.id || '') };
}

// ---------- downloads ----------
const jobs = new Map();
function sendProgress(payload) { if (win && !win.isDestroyed()) win.webContents.send('dl-progress', payload); }
const SIZE_MULT = { B: 1, KiB: 1024, MiB: 1024 ** 2, GiB: 1024 ** 3, TiB: 1024 ** 4 };
function toBytes(num, unit) { return Math.round(parseFloat(num) * (SIZE_MULT[unit] || 1)); }
function sanitizeName(s) { return String(s || 'video').replace(/[<>:"/\\|?*-]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 80) || 'video'; }
function stemFor(job) { return 'zx_' + crypto.createHash('sha1').update(job.url + '|' + (job.itag || job.label || '')).digest('hex').slice(0, 12); }
function uniquePath(p) {
  if (!fs.existsSync(p)) return p;
  const ext = path.extname(p); const base = p.slice(0, -ext.length);
  for (let i = 1; i < 50; i++) { const c = `${base} (${i})${ext}`; if (!fs.existsSync(c)) return c; }
  return p;
}

// The phone app's engine call (GrabIt v4 system, DownloadService.
// runYtdlpDl), ported exactly: ONE call with yt-dlp's own default
// clients - no client pinning, no EJS, no second attempt. Selectors
// are the phone's: <id> for combined, <id>+bestaudio/best for
// video-only, bestvideo+bestaudio/best for the Best row; audio uses
// the phone's extract flags. Resolves true/false; never rejects;
// pause/cancel are flags on rec.
function enginePhone(job, rec) {
  return new Promise((resolve) => {
    const args = [...cookieArgs(job.url), ...jsRuntimeArgs(), '--no-playlist', '--no-cache-dir', '--newline', '--ffmpeg-location', ffmpegExe()];
    const audioKind = job.kind === 'audio' || job.kind === 'audio-mp3';
    if (audioKind) {
      const it = String(job.itag || '');
      args.push('-f', /^\d+$/.test(it) && it !== '0' ? it : 'bestaudio/best',
        '--extract-audio', '--audio-format', 'mp3', '--audio-quality', '320K', '--add-metadata', '--embed-thumbnail');
    } else {
      const f = !job.itag ? 'bestvideo+bestaudio/best' : job.combined ? String(job.itag) : `${job.itag}+bestaudio/best`;
      args.push('-f', f, '--merge-output-format', 'mp4');
    }
    args.push('--progress-template', 'download:ZXPROG|%(progress.status)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s');
    args.push('--progress-template', 'postprocess:ZXPOST|%(progress.status)s');
    args.push('--print', 'after_move:filepath');
    args.push('-o', path.join(rec.dir, '%(title).80s [%(id)s].%(ext)s'));
    args.push(job.url);
    const proc = spawn(ytdlpExe(), args, { windowsHide: true });
    rec.proc = proc;
    let buf = '', errBuf = '';
    const handleLine = (line) => {
      if (!line) return;
      if (line.startsWith('ZXPROG|')) {
        const [, st, dl, tot, est, spd, eta] = line.split('|');
        const num = (v) => { const n = parseFloat(v); return Number.isFinite(n) ? n : null; };
        const downloaded = num(dl); const total = num(tot) || num(est);
        if (st === 'downloading' && downloaded != null) {
          rec.templAt = Date.now(); rec.templTotal = total || rec.templTotal || 0;
          sendProgress({ id: rec.id, status: 'downloading', downloaded, total: total || 0, pct: total ? (downloaded / total) * 100 : 0, speedBps: num(spd) || 0, etaSec: num(eta) });
        } else if (st === 'finished') sendProgress({ id: rec.id, status: 'processing', pct: 100, downloaded: total || downloaded || 0, total: total || 0 });
        return;
      }
      if (line.startsWith('ZXPOST|')) { sendProgress({ id: rec.id, status: 'processing', pct: 100 }); return; }
      const dest = line.match(/\[download\] Destination: (.+)$/);
      if (dest) rec.baseName = path.basename(dest[1]);
      const prog = line.match(/\[download\]\s+([\d.]+)% of ~?\s*([\d.]+)(B|KiB|MiB|GiB|TiB) at\s+([\d.]+)(B|KiB|MiB|GiB|TiB)\/s ETA\s+(\d+):(\d+)/);
      if (prog) {
        const total = toBytes(prog[2], prog[3]); const pct = parseFloat(prog[1]);
        rec.templAt = Date.now(); rec.templTotal = total;
        sendProgress({ id: rec.id, status: 'downloading', pct, total, downloaded: Math.round((total * pct) / 100), speedBps: toBytes(prog[4], prog[5]), etaSec: parseInt(prog[6], 10) * 60 + parseInt(prog[7], 10) });
        return;
      }
      const seg = line.match(/Downloading segment (\d+) of (\d+)/);
      if (seg) {
        const pct = (parseInt(seg[1], 10) / parseInt(seg[2], 10)) * 100;
        rec.templAt = Date.now();
        sendProgress({ id: rec.id, status: 'downloading', pct, downloaded: 0, total: 0 });
        return;
      }
      if (line.includes('[Merger]') || line.includes('[ExtractAudio]')) { sendProgress({ id: rec.id, status: 'processing', pct: 100 }); return; }
      if (!line.startsWith('[') && /\.(mp4|mp3|m4a|webm|mkv)$/i.test(line) && fs.existsSync(line)) rec.filePath = line;
    };
    const onData = (d) => {
      const s = d.toString(); errBuf += s; buf += s;
      const parts = buf.split(/[\r\n]+/); buf = parts.pop();
      for (const line of parts) handleLine(line.trim());
    };
    proc.stdout.on('data', onData);
    proc.stderr.on('data', onData);
    proc.on('error', () => { rec.lastError = 'Could not start the engine'; resolve(false); });
    proc.on('close', (code) => {
      rec.proc = null;
      if (code === 0) return resolve(true);
      rec.lastError = shortErr(errBuf);
      resolve(false);
    });
  });
}

// Folder-watching progress poller - the phone app's technique. While
// an engine attempt runs, it measures the job's fragment/.part files
// growing on disk and synthesizes progress from that. It only speaks
// when the engine's own progress output has been quiet for a moment,
// so the card can never sit frozen on "Preparing" while bytes flow.
function startWatch(rec, job) {
  const vid = youtubeId(job.url);
  rec.watchMax = 0; rec.watchLast = 0; rec.watchLastT = Date.now();
  const stemOf = (n) => n.replace(/\.[^.]+$/, '');
  rec.watchTimer = setInterval(() => {
    if (!rec.engineActive || rec.paused || rec.cancelled) return;
    if (Date.now() - (rec.templAt || 0) < 1600) return;
    let sum = 0;
    try {
      for (const name of fs.readdirSync(rec.dir)) {
        const mine = (vid && name.includes('[' + vid + ']')) || (rec.baseName && name.startsWith(stemOf(rec.baseName)));
        if (!mine) continue;
        if (!(name.endsWith('.part') || /\.f\d+\./.test(name))) continue;
        try { sum += fs.statSync(path.join(rec.dir, name)).size; } catch {}
      }
    } catch {}
    if (sum <= 0) return;
    rec.watchMax = Math.max(rec.watchMax, sum);
    const now = Date.now(); const dt = (now - rec.watchLastT) / 1000;
    const speed = dt > 0.2 ? Math.max(0, Math.round((rec.watchMax - rec.watchLast) / dt)) : 0;
    rec.watchLast = rec.watchMax; rec.watchLastT = now;
    const total = job.size || rec.templTotal || 0;
    sendProgress({ id: rec.id, status: 'downloading', downloaded: rec.watchMax, total,
      pct: total ? Math.min(99.5, (rec.watchMax / total) * 100) : 0,
      speedBps: speed, etaSec: speed > 0 && total && total > rec.watchMax ? Math.round((total - rec.watchMax) / speed) : null });
  }, 700);
}

// Direct HTTPS file download with Range resume + progress on rec.
function downloadPart(rec, part, totals) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const done = (v) => { if (!settled) { settled = true; resolve(v); } };
    const fail = (e) => { if (!settled) { settled = true; reject(e); } };
    rec.abort = () => fail(new Error('__aborted'));
    let existing = 0;
    try { existing = fs.statSync(part.file).size; } catch {}
    if (part.total && existing >= part.total && part.total > 0) { totals.done += part.total; return done(); }
    const headers = { 'User-Agent': part.ua || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Zynox-PC', ...(part.headers || {}) };
    if (existing > 0) headers.Range = `bytes=${existing}-`;
    const lib = part.url.startsWith('https') ? https : http;
    const req = lib.get(part.url, { headers }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        part.url = res.headers.location;
        return done(downloadPart(rec, part, totals));
      }
      if (res.statusCode === 416) { totals.done += part.total || existing; return done(); }
      if (res.statusCode !== 200 && res.statusCode !== 206) { res.resume(); return fail(new Error('HTTP ' + res.statusCode)); }
      const append = res.statusCode === 206 && existing > 0;
      if (!append) existing = 0;
      const len = parseInt(res.headers['content-length'] || '0', 10);
      if (!part.total) part.total = len + existing;
      const out = fs.createWriteStream(part.file, { flags: append ? 'a' : 'w' });
      rec.abort = () => { try { req.destroy(); } catch {} try { res.destroy(); } catch {} try { out.destroy(); } catch {} fail(new Error('__aborted')); };
      let lastT = Date.now(), lastBytes = existing, lastSend = 0;
      res.on('data', (chunk) => {
        existing += chunk.length;
        const now = Date.now();
        if (now - lastSend > 240) {
          const dt = (now - lastT) / 1000;
          const speed = dt > 0 ? Math.round((existing - lastBytes) / dt) : 0;
          lastT = now; lastBytes = existing; lastSend = now;
          const totalAll = totals.partsTotal || 0;
          const doneAll = totals.done + existing;
          sendProgress({ id: rec.id, status: 'downloading', downloaded: doneAll, total: totalAll,
            pct: totalAll ? (doneAll / totalAll) * 100 : 0, speedBps: speed, etaSec: speed > 0 && totalAll ? Math.round((totalAll - doneAll) / speed) : null });
        }
      });
      res.pipe(out);
      out.on('finish', () => out.close(() => { totals.done += part.total || existing; done(); }));
      out.on('error', fail);
      res.on('error', fail);
    });
    req.on('error', (e) => fail(rec.paused || rec.cancelled ? new Error('__aborted') : e));
  });
}
function runFfmpeg(args) {
  return new Promise((resolve, reject) => {
    const p = spawn(ffmpegExe(), args, { windowsHide: true });
    let err = '';
    p.stderr.on('data', (d) => (err += d));
    p.on('error', reject);
    p.on('close', (code) => (code === 0 ? resolve() : reject(new Error('ffmpeg failed'))));
  });
}
// Direct job: parts downloaded, then copied / merged / converted.
async function directJob(job, rec, plan) {
  const totals = { done: 0, partsTotal: plan.parts.reduce((s, p) => s + (p.total || 0), 0) };
  for (const part of plan.parts) {
    if (rec.paused || rec.cancelled) return;
    await downloadPart(rec, part, totals);
  }
  if (rec.paused || rec.cancelled) return;
  sendProgress({ id: rec.id, status: 'processing', pct: 100 });
  const finalPath = uniquePath(path.join(rec.dir, sanitizeName(job.title) + (plan.nameSuffix || '') + plan.finalExt));
  if (plan.mode === 'copy') {
    fs.copyFileSync(plan.parts[0].file, finalPath);
    try { fs.unlinkSync(plan.parts[0].file); } catch {}
  } else if (plan.mode === 'merge') {
    await runFfmpeg(['-y', '-i', plan.parts[0].file, '-i', plan.parts[1].file, '-map', '0:v:0', '-map', '1:a:0', '-c', 'copy', finalPath]);
    for (const p of plan.parts) { try { fs.unlinkSync(p.file); } catch {} }
  } else if (plan.mode === 'mp3') {
    await runFfmpeg(['-y', '-i', plan.parts[0].file, '-vn', '-codec:a', 'libmp3lame', '-b:a', '320k', finalPath]);
    try { fs.unlinkSync(plan.parts[0].file); } catch {}
  }
  rec.filePath = finalPath;
}
// The phone's YouTube AUDIO route (DownloadService.downloadSmart):
// a fresh Innertube resolve (six clients in order, first playable
// wins), the best audio stream (itag 140 preferred) downloaded
// directly and converted to MP3 320. There is NO direct route for
// video on the phone - video is always the single engine call.
async function youtubeAudioDirect(job, rec) {
  const vid = youtubeId(job.url);
  const u = vid ? await freshInnertube(vid) : null;
  if (!u) throw new Error('No direct audio stream');
  const auds = u.formats.filter((f) => f.url && String(f.mimeType || '').startsWith('audio/')).sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0));
  const f = auds.find((x) => x.itag === 140) || auds[0];
  if (!f) throw new Error('No audio stream found');
  const stem = stemFor(job);
  return directJob(job, rec, { mode: 'mp3', finalExt: '.mp3', nameSuffix: '',
    parts: [{ url: f.url, file: path.join(rec.dir, `${stem}.a.m4a`), total: parseInt(f.contentLength || '0', 10), ua: u.ua }] });
}

function startDownload(job) {
  const id = job.clientId || ('job' + Date.now());
  const rec = { id, dir: downloadsDir(), title: job.title, paused: false, cancelled: false, proc: null, abort: null, baseName: '', filePath: '', lastError: '' };
  jobs.set(id, rec);
  sendProgress({ id, status: 'starting', pct: 0 });
  (async () => {
    const chain = async () => {
      if (job.directUrl) {
        const stem = stemFor(job);
        const ext = job.ext || 'mp4';
        await directJob(job, rec, { mode: 'copy', finalExt: '.' + ext, nameSuffix: '', parts: [{ url: job.directUrl, file: path.join(rec.dir, `${stem}.single.${ext}`), total: job.size || 0 }] });
      } else if (youtubeId(job.url) && (job.kind === 'audio' || job.kind === 'audio-mp3')) {
        // Phone order for YouTube audio: fresh direct stream first,
        // the single engine call only as its fallback.
        try { await youtubeAudioDirect(job, rec); }
        catch (e) {
          if (rec.paused || rec.cancelled) throw e;
          rec.engineActive = true; startWatch(rec, job);
          const ok = await enginePhone(job, rec);
          rec.engineActive = false;
          if (!ok && !rec.paused && !rec.cancelled) throw new Error(rec.lastError || 'Download failed');
        }
      } else {
        // Everything else - YouTube video and every other site - is
        // the phone's single engine call, nothing before or after it.
        rec.engineActive = true; startWatch(rec, job);
        const ok = await enginePhone(job, rec);
        rec.engineActive = false;
        if (!ok && !rec.paused && !rec.cancelled) throw new Error(rec.lastError || 'Download failed');
      }
    };
    const succeed = () => {
      sendProgress({ id, status: 'done', pct: 100, filePath: rec.filePath || '' });
      notify('Download complete ✓', rec.title || 'Your file is saved in ' + rec.dir);
    };
    try {
      await chain();
      if (rec.cancelled) return;
      if (rec.paused) { sendProgress({ id, status: 'paused' }); return; }
      succeed();
    } catch (e) {
      if (rec.cancelled) return;
      if (rec.paused || String(e && e.message) === '__aborted') { sendProgress({ id, status: 'paused' }); return; }
      // The phone shows the failure here - no silent retry, no second
      // route. The engine still self-updates daily on app open.
      sendProgress({ id, status: 'error', message: String((e && e.message) || rec.lastError || 'Download failed').slice(0, 220) });
      notify('Download failed', (rec.title || 'A download') + ' could not be completed.');
    } finally {
      rec.engineActive = false;
      if (rec.watchTimer) clearInterval(rec.watchTimer);
      jobs.delete(id);
    }
  })();
  return id;
}
function cleanupPartials(rec, job) {
  try {
    const stem = job ? stemFor(job) : null;
    for (const name of fs.readdirSync(rec.dir)) {
      const isPart = name.includes('.part') || name.includes('.ytdl') || /\.f\d+\./.test(name);
      const isTemp = stem && name.startsWith(stem);
      if ((rec.baseName && name.startsWith(rec.baseName.replace(/\.[^.]+$/, '')) && isPart) || isTemp) {
        try { fs.unlinkSync(path.join(rec.dir, name)); } catch {}
      }
    }
  } catch {}
}

// ---------- social connect (Facebook / Instagram) ----------
const SOCIAL = {
  facebook: { loginUrl: 'https://www.facebook.com/login', domains: ['facebook.com'], check: 'c_user', label: 'Facebook' },
  instagram: { loginUrl: 'https://www.instagram.com/accounts/login/', domains: ['instagram.com'], check: 'sessionid', label: 'Instagram' },
};
function connectionFile() { return path.join(app.getPath('userData'), 'connection.json'); }
function connectionState() { try { return JSON.parse(fs.readFileSync(connectionFile(), 'utf8')); } catch { return { facebook: false, instagram: false }; } }
function setConnection(site, v) { const s = connectionState(); s[site] = v; try { fs.writeFileSync(connectionFile(), JSON.stringify(s)); } catch {} }
async function writeCookiesFile() {
  const ses = session.fromPartition('persist:zynox-social');
  const all = await ses.cookies.get({});
  const wanted = all.filter((c) => /facebook\.com|instagram\.com/.test(c.domain));
  if (!wanted.length) { try { fs.unlinkSync(cookiesFile()); } catch {} return; }
  const lines = ['# Netscape HTTP Cookie File', '# Zynox social sessions (Facebook / Instagram private videos)'];
  for (const c of wanted) {
    const exp = c.expirationDate ? Math.floor(c.expirationDate) : Math.floor(Date.now() / 1000) + 31536000;
    lines.push([c.domain, c.domain.startsWith('.') ? 'TRUE' : 'FALSE', c.path || '/', c.secure ? 'TRUE' : 'FALSE', exp, c.name, c.value].join('\t'));
  }
  fs.writeFileSync(cookiesFile(), lines.join('\n'));
}
function connectSite(site) {
  return new Promise((resolve) => {
    const cfg = SOCIAL[site];
    if (!cfg) return resolve({ ok: false });
    const ses = session.fromPartition('persist:zynox-social');
    const w = new BrowserWindow({ width: 480, height: 740, title: `Connect ${cfg.label} — Zynox`, autoHideMenuBar: true,
      icon: path.join(__dirname, '..', 'assets', 'icon.ico'), webPreferences: { partition: 'persist:zynox-social', contextIsolation: true } });
    let done = false;
    const finish = async (ok) => { if (done) return; done = true; clearInterval(timer);
      if (ok) { await writeCookiesFile(); setConnection(site, true); }
      try { w.close(); } catch {}
      resolve({ ok }); };
    const timer = setInterval(async () => {
      try {
        const cs = await ses.cookies.get({});
        if (cs.some((c) => c.name === cfg.check && cfg.domains.some((d) => c.domain.includes(d)))) finish(true);
      } catch {}
    }, 1500);
    w.on('closed', () => finish(false));
    w.loadURL(cfg.loginUrl);
  });
}
async function disconnectSite(site) {
  const cfg = SOCIAL[site];
  const ses = session.fromPartition('persist:zynox-social');
  const all = await ses.cookies.get({});
  for (const c of all) {
    if (cfg.domains.some((d) => c.domain.includes(d))) {
      const url = `https://${c.domain.replace(/^\./, '')}${c.path || '/'}`;
      try { await ses.cookies.remove(url, c.name); } catch {}
    }
  }
  await writeCookiesFile();
  setConnection(site, false);
}

// ---------- engine update ----------
function runVersion(exe) {
  return new Promise((resolve) => {
    try {
      const p = spawn(exe, ['--version'], { windowsHide: true });
      let out = '';
      p.stdout.on('data', (d) => (out += d));
      p.on('error', () => resolve(null));
      p.on('close', () => resolve(out.trim().split('\n')[0] || null));
    } catch { resolve(null); }
  });
}
async function engineInfo() {
  const current = await runVersion(ytdlpExe());
  let latest = null;
  try {
    const rel = await httpsJson('api.github.com', '/repos/yt-dlp/yt-dlp/releases/latest', { Accept: 'application/vnd.github+json' }, null);
    latest = rel && rel.tag_name ? String(rel.tag_name) : null;
  } catch {}
  return { current, latest, updateAvailable: !!(current && latest && latest !== current) };
}
// Once a day, shortly after the app opens: if a newer yt-dlp exists,
// update it silently in the background. Otherwise do nothing at all.
async function autoEngineDailyCheck() {
  try {
    const today = new Date().toISOString().slice(0, 10);
    const s = loadSettings();
    if (s.engineAutoCheck === today) return;
    saveSettings({ engineAutoCheck: today });
    const info = await engineInfo();
    if (info && info.updateAvailable) await engineUpdate();
  } catch {}
}
function engineUpdate() {
  return new Promise((resolve) => {
    const dir = path.join(app.getPath('userData'), 'bin');
    try { fs.mkdirSync(dir, { recursive: true }); } catch {}
    const tmp = path.join(dir, 'yt-dlp.exe.new');
    const file = fs.createWriteStream(tmp);
    const get = (url) => https.get(url, { headers: { 'User-Agent': 'Zynox-PC' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) return get(res.headers.location);
      if (res.statusCode !== 200) { file.close(); return resolve({ ok: false, message: 'HTTP ' + res.statusCode }); }
      const total = parseInt(res.headers['content-length'] || '0', 10);
      let got = 0;
      res.on('data', (c) => { got += c.length; if (win && !win.isDestroyed()) win.webContents.send('engine-progress', total ? Math.round((got / total) * 100) : 0); });
      res.pipe(file);
      file.on('finish', () => file.close(() => {
        try { fs.renameSync(tmp, userBin('yt-dlp.exe')); } catch (e) { return resolve({ ok: false, message: e.message }); }
        runVersion(ytdlpExe()).then((v) => resolve({ ok: true, version: v }));
      }));
    }).on('error', (e) => resolve({ ok: false, message: e.message }));
    get('https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe');
  });
}

// ---------- IPC ----------
ipcMain.handle('fetch-formats', async (_e, url) => {
  url = String(url || '').trim();
  if (!/^https?:\/\//i.test(url)) url = 'https://' + url;
  if (/tiktok\.com/.test(url)) {
    try { return await fetchTikwm(url); } catch { /* engine fallback below */ }
    return parseGeneric(await runYtdlpJson(url, null), url);
  }
  const vid = youtubeId(url);
  if (vid) {
    // Phone order: Innertube race (first playable wins) for the list;
    // the engine fetch (pinned pass, then default) only if it is empty.
    const race = await innertubeRace(vid);
    if (race) {
      const result = parseInnertubeUnion(race);
      if (result.formats.some((f) => f.kind === 'video')) return result;
    }
    const eng = await engineFetchYouTube(url);
    if (eng) return eng;
    throw new Error('Could not fetch this video - check the link and try again');
  }
  return parseGeneric(await runYtdlpJson(url, null), url);
});
ipcMain.handle('start-download', (_e, job) => startDownload(job));
ipcMain.on('pause-download', (_e, id) => {
  const rec = jobs.get(id);
  if (rec) { rec.paused = true; if (rec.proc) { try { rec.proc.kill(); } catch {} } if (rec.abort) { try { rec.abort(); } catch {} } }
});
ipcMain.on('cancel-download', (_e, id, jobSnapshot) => {
  const rec = jobs.get(id);
  if (rec) {
    rec.cancelled = true;
    if (rec.proc) { try { rec.proc.kill(); } catch {} }
    if (rec.abort) { try { rec.abort(); } catch {} }
    setTimeout(() => cleanupPartials(rec, jobSnapshot), 700);
  }
});
ipcMain.handle('get-downloads-dir', () => downloadsDir());
ipcMain.handle('choose-downloads-dir', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openDirectory', 'createDirectory'] });
  if (r.canceled || !r.filePaths.length) return downloadsDir();
  saveSettings({ downloadDir: r.filePaths[0] });
  return r.filePaths[0];
});
// Real thumbnails for the Files grid: one ffmpeg frame per video or
// photo, cached in userData/thumbs so repeat visits are instant.
function thumbFor(filePath, name, size) {
  return new Promise((resolve) => {
    try {
      const key = crypto.createHash('sha1').update(name + '|' + size).digest('hex').slice(0, 16);
      const dir = path.join(app.getPath('userData'), 'thumbs');
      const out = path.join(dir, key + '.jpg');
      if (fs.existsSync(out)) { resolve('data:image/jpeg;base64,' + fs.readFileSync(out).toString('base64')); return; }
      fs.mkdirSync(dir, { recursive: true });
      const p = spawn(resolveBin('ffmpeg'), ['-ss', '2', '-i', filePath, '-frames:v', '1', '-vf', 'scale=320:-2', '-q:v', '4', '-y', out], { windowsHide: true });
      p.on('error', () => resolve(''));
      p.on('close', () => { try { resolve(fs.existsSync(out) ? 'data:image/jpeg;base64,' + fs.readFileSync(out).toString('base64') : ''); } catch { resolve(''); } });
    } catch { resolve(''); }
  });
}
const THUMB_EXTS = ['.mp4', '.mkv', '.webm', '.jpg', '.png', '.webp'];
ipcMain.handle('list-files', async () => {
  const dir = downloadsDir();
  const exts = ['.mp4', '.mkv', '.webm', '.mp3', '.m4a', '.opus', '.jpg', '.png', '.webp'];
  try {
    const list = fs.readdirSync(dir).filter((n) => exts.includes(path.extname(n).toLowerCase()) && !n.includes('.part') && !n.startsWith('zx_'))
      .map((n) => { const st = fs.statSync(path.join(dir, n)); return { name: n, path: path.join(dir, n), size: st.size, mtime: st.mtimeMs }; })
      .sort((a, b) => b.mtime - a.mtime).slice(0, 200);
    await Promise.all(list.filter((f) => THUMB_EXTS.includes(path.extname(f.name).toLowerCase()))
      .map(async (f) => { f.thumb = await thumbFor(f.path, f.name, f.size); }));
    return list;
  } catch { return []; }
});
ipcMain.handle('delete-file', async (_e, p) => {
  try {
    if (!p) return false;
    const full = path.resolve(p);
    if (!full.startsWith(path.resolve(downloadsDir()) + path.sep)) return false;
    if (!fs.existsSync(full)) return false;
    await shell.trashItem(full); // Recycle Bin - recoverable
    return true;
  } catch { return false; }
});
ipcMain.handle('get-settings', () => loadSettings());
ipcMain.handle('set-settings', (_e, patch) => saveSettings(patch || {}));
ipcMain.handle('connection-state', () => connectionState());
ipcMain.handle('connect-site', (_e, site) => connectSite(site));
ipcMain.handle('disconnect-site', async (_e, site) => { await disconnectSite(site); return connectionState(); });
ipcMain.handle('engine-info', () => engineInfo());
ipcMain.handle('engine-update', () => engineUpdate());
ipcMain.on('open-url', (_e, u) => { if (u && /^https?:\/\//.test(u)) shell.openExternal(u); });
ipcMain.on('open-file', (_e, p) => { if (p) shell.openPath(p); });
ipcMain.on('show-in-folder', (_e, p) => { if (p) shell.showItemInFolder(p); });
ipcMain.on('open-downloads-dir', () => shell.openPath(downloadsDir()));
ipcMain.handle('read-clipboard', () => clipboard.readText());
ipcMain.on('win-min', () => win && win.minimize());
ipcMain.on('win-max', () => { if (!win) return; win.isMaximized() ? win.unmaximize() : win.maximize(); });
ipcMain.on('win-close', () => win && win.close());
