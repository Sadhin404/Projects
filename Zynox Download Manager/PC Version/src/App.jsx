import React, { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence, useMotionValue, useSpring, useTransform } from 'framer-motion';
import { siYoutube, siFacebook, siInstagram, siTiktok, siX, siVimeo, siTwitch, siGithub } from 'simple-icons';
import devM1 from './assets/dev_m1.jpg';
import devM2 from './assets/dev_m2.jpg';
import devM3 from './assets/dev_m3.jpg';
import devM4 from './assets/dev_m4.jpg';

const HEADLINES = [
  ['Download from', 'Anywhere.'], ['One Link,', 'Every Platform.'], ['Fast Downloads,', 'Zero Hassle.'],
  ['Your Media,', 'Your Way.'], ['Just Paste,', 'And Download.'], ['Music & Videos,', 'One Place.'],
  ['From Online,', 'To Offline.'], ['Less Waiting,', 'More Watching.'], ['Your Favorites,', 'Always Ready.'],
  ['Download Smart,', 'Stay Simple.'],
];
const PLATFORMS = [
  { icon: siYoutube, name: 'YouTube', url: 'https://www.youtube.com' },
  { icon: siFacebook, name: 'Facebook', url: 'https://www.facebook.com' },
  { icon: siInstagram, name: 'Instagram', url: 'https://www.instagram.com' },
  { icon: siTiktok, name: 'TikTok', url: 'https://www.tiktok.com' },
  { icon: siX, name: 'X', url: 'https://x.com' },
  { icon: siVimeo, name: 'Vimeo', url: 'https://vimeo.com' },
  { icon: siTwitch, name: 'Twitch', url: 'https://www.twitch.tv' },
];
const TEAM = [
  { name: 'Sadhin Khan', role: 'Main Developer', accent: '#8b5cf6', photo: devM1,
    bio: 'Leads Zynox end-to-end — the engine, the design, and the vision behind the app.',
    socials: { fb: 'https://www.facebook.com/share/1GRMfN9pwh/', ig: 'https://www.instagram.com/sadhin.404', tt: 'https://www.tiktok.com/@sadvibess_999', gh: 'https://github.com/Sadhin404' } },
  { name: 'Ali Emam Rabby', role: 'Frontend Developer', accent: '#3b82f6', photo: devM2,
    bio: 'Crafts the interfaces you touch — clean, fast and beautiful, pixel by pixel.',
    socials: { fb: 'https://www.facebook.com/share/1E3GyCanz2/', ig: 'https://www.instagram.com/ali.emam.rabbi', tt: 'https://www.tiktok.com/@rabbi_10k' } },
  { name: 'Sristi Sorower', role: 'Backend Developer', accent: '#ec4899', photo: devM3,
    bio: 'Builds the backbone — the systems that keep every download reliable.',
    socials: { fb: 'https://www.facebook.com/share/1C8fXfc2j3/', ig: 'https://www.instagram.com/sristisorower', tt: 'https://www.tiktok.com/@sristisorower0' } },
  { name: 'Meharab Hosen', role: 'Project Manager', accent: '#f59e0b', photo: devM4,
    bio: 'Keeps the team, the plan and the deadlines on track — start to finish.',
    socials: { fb: 'https://www.facebook.com/share/1A4nXS2xHP/', ig: 'https://www.instagram.com/hosen.meharab', tt: '' } },
];
const FEATURES = [
  { icon: '⚡', title: 'Lightning Fast', text: 'Innertube fetch lists formats in 1–2 seconds' },
  { icon: '🎬', title: 'Up to 8K Quality', text: 'Full HD, 4K and 8K — exactly the quality you pick' },
  { icon: '🎵', title: 'MP3 320 kbps', text: 'Music downloads at the highest audio quality' },
  { icon: '🛡️', title: 'Safe & Private', text: 'No tracking, no accounts needed for public videos' },
];

function fmtBytes(b) {
  if (!b) return '0 MB';
  const u = ['B', 'KB', 'MB', 'GB', 'TB']; let i = 0, v = b;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(v >= 100 ? 0 : 1)} ${u[i]}`;
}
function fmtSpeed(bps) { return bps ? fmtBytes(bps) + '/s' : ''; }
function fmtEta(sec) {
  if (sec == null || !Number.isFinite(sec)) return '';
  sec = Math.round(sec);
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;
}
function fmtDur(s) {
  if (!s) return '';
  s = Math.round(s);
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
  return (h ? h + ':' : '') + String(m).padStart(h ? 2 : 1, '0') + ':' + String(x).padStart(2, '0');
}
function qualityTone(h) {
  if (!h) return 'q-audio';
  if (h >= 2160) return 'q-4k';
  if (h >= 1080) return 'q-1080';
  if (h >= 720) return 'q-720';
  return 'q-low';
}

function Typewriter() {
  const [text, setText] = useState(['', '']);
  useEffect(() => {
    let li = 0, ci = 0, deleting = false, timer;
    const render = (s) => {
      const [plain] = HEADLINES[li];
      if (s.length <= plain.length) setText([s, '']);
      else setText([plain + ' ', s.slice(plain.length + 1)]);
    };
    const tick = () => {
      const [plain, accent] = HEADLINES[li];
      const full = plain + ' ' + accent;
      if (!deleting) {
        ci++;
        if (ci >= full.length) { deleting = true; timer = setTimeout(tick, 1800); render(full); return; }
        timer = setTimeout(tick, 42);
      } else {
        ci -= 3;
        if (ci <= 0) { ci = 0; deleting = false; li = (li + 1) % HEADLINES.length; timer = setTimeout(tick, 350); render(''); return; }
        timer = setTimeout(tick, 16);
      }
      render(full.slice(0, ci));
    };
    timer = setTimeout(tick, 400);
    return () => clearTimeout(timer);
  }, []);
  return (
    <div className="typewriter" aria-hidden>
      <span className="tw-plain">{text[0]}</span><span className="tw-accent">{text[1]}</span><span className="tw-cursor">|</span>
    </div>
  );
}

function Aurora() {
  return (
    <div className="aurora" aria-hidden>
      <div className="blob b1" /><div className="blob b2" /><div className="blob b3" /><div className="blob b4" />
      <div className="grid-overlay" /><div className="grid-light" /><div className="noise" />
    </div>
  );
}

function Splash({ done, onSkip }) {
  return (
    <AnimatePresence>
      {!done && (
        <motion.div className="splash" onClick={onSkip}
          exit={{ opacity: 0, scale: 1.08, filter: 'blur(6px)' }} transition={{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }}>
          <div className="splash-stage">
            <motion.div className="splash-ring r1" initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.15, duration: 0.8 }} />
            <motion.div className="splash-ring r2" initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.3, duration: 0.8 }} />
            <motion.div className="splash-logo"
              initial={{ rotateY: -110, rotateX: 24, opacity: 0, scale: 0.7 }}
              animate={{ rotateY: 0, rotateX: 0, opacity: 1, scale: 1 }}
              transition={{ type: 'spring', stiffness: 120, damping: 14, delay: 0.1 }}>
              Z
              <motion.span className="splash-shine" initial={{ x: '-130%' }} animate={{ x: '230%' }} transition={{ delay: 0.75, duration: 0.7, ease: 'easeInOut' }} />
            </motion.div>
          </div>
          <div className="splash-name">
            {'ZYNOX'.split('').map((ch, i) => (
              <motion.span key={i} initial={{ opacity: 0, y: 26, rotateX: 90 }} animate={{ opacity: 1, y: 0, rotateX: 0 }}
                transition={{ delay: 0.45 + i * 0.07, type: 'spring', stiffness: 260, damping: 20 }}>{ch}</motion.span>
            ))}
          </div>
          <motion.div className="splash-sub" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.0 }}>
            Download anything. Beautifully.
          </motion.div>
          <motion.div className="splash-bar" initial={{ width: 0 }} animate={{ width: 200 }} transition={{ delay: 0.35, duration: 1.5, ease: [0.65, 0, 0.35, 1] }} />
        </motion.div>
      )}
    </AnimatePresence>
  );
}

// Soft light that follows the cursor (spring-lagged), screen-blended
// over the background layers - part of the ambient mouse effect.
function CursorGlow() {
  const x = useMotionValue(-400), y = useMotionValue(-400);
  const sx = useSpring(x, { stiffness: 120, damping: 20, mass: 0.6 });
  const sy = useSpring(y, { stiffness: 120, damping: 20, mass: 0.6 });
  useEffect(() => {
    const move = (e) => { x.set(e.clientX); y.set(e.clientY); };
    window.addEventListener('mousemove', move, { passive: true });
    return () => window.removeEventListener('mousemove', move);
  }, [x, y]);
  return <motion.div className="cursor-glow" style={{ left: sx, top: sy }} aria-hidden />;
}

function Tilt({ children, className, max = 5 }) {
  const rx = useMotionValue(0), ry = useMotionValue(0);
  const srx = useSpring(rx, { stiffness: 180, damping: 20 }), sry = useSpring(ry, { stiffness: 180, damping: 20 });
  const transform = useTransform([srx, sry], ([x, y]) => `perspective(1100px) rotateX(${x}deg) rotateY(${y}deg)`);
  return (
    <motion.div className={className} style={{ transform }}
      onMouseMove={(e) => {
        const r = e.currentTarget.getBoundingClientRect();
        ry.set(((e.clientX - r.left) / r.width - 0.5) * max * 2);
        rx.set(-((e.clientY - r.top) / r.height - 0.5) * max * 2);
      }}
      onMouseLeave={() => { rx.set(0); ry.set(0); }}>
      {children}
    </motion.div>
  );
}

let clientSeq = 1;

export default function App() {
  const [tab, setTab] = useState('home');
  const [theme, setTheme] = useState('dark');
  const [url, setUrl] = useState('');
  const [fetching, setFetching] = useState(false);
  const [meta, setMeta] = useState(null);
  const [fetchError, setFetchError] = useState('');
  const [jobs, setJobs] = useState([]);
  const [files, setFiles] = useState([]);
  const [dlDir, setDlDir] = useState('');
  const [splashDone, setSplashDone] = useState(false);
  const [toast, setToast] = useState('');
  const [conn, setConn] = useState({ facebook: false, instagram: false });
  const [engine, setEngine] = useState(null);
  const [engineBusy, setEngineBusy] = useState('');
  const [enginePct, setEnginePct] = useState(0);
  const [closeToTray, setCloseToTray] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [liteBg, setLiteBg] = useState(() => { try { return localStorage.getItem('zx_lite') === '1'; } catch { return false; } });
  const [handleDragX, setHandleDragX] = useState(null);
  const handleDragRef = useRef(null);
  const [activeMember, setActiveMember] = useState(null);
  const [doneModal, setDoneModal] = useState(null);
  const jobsRef = useRef([]);
  useEffect(() => { jobsRef.current = jobs; }, [jobs]);
  const [, setTickNow] = useState(0);
  const inputRef = useRef(null);

  useEffect(() => { const t = setTimeout(() => setSplashDone(true), 2300); return () => clearTimeout(t); }, []);
  useEffect(() => {
    window.zynox.getDownloadsDir().then(setDlDir).catch(() => {});
    window.zynox.getSettings().then((s) => {
      if (s && s.theme) setTheme(s.theme);
      if (s && s.closeToTray === false) setCloseToTray(false);
    }).catch(() => {});
    window.zynox.connectionState().then(setConn).catch(() => {});
    refreshFiles();
  }, []);
  useEffect(() => { document.documentElement.dataset.theme = theme; }, [theme]);
  useEffect(() => {
    const off = window.zynox.onProgress((p) => {
      if (p.status === 'done' && p.filePath) {
        const j = jobsRef.current.find((x) => x.id === p.id);
        setDoneModal({ title: (j && j.title) || 'Download', filePath: p.filePath, total: p.total || (j && (j.totalBytes || j.estSize)) || 0, label: (j && j.label) || '' });
      }
      setJobs((prev) => prev.map((j) => {
        if (j.id !== p.id) return j;
        if (p.status === 'done') { setTimeout(refreshFiles, 700); flash('Download complete ✓'); }
        if (p.status === 'error') flash('Download failed');
        return { ...j, ...p };
      }));
    });
    const offE = window.zynox.onEngineProgress((pct) => setEnginePct(pct));
    return () => { off(); offE(); };
  }, []);
  // Links arriving from the browser extension (zynox:// protocol)
  useEffect(() => {
    const handle = (u) => { if (!u) return; setSplashDone(true); setTab('home'); setUrl(u); doFetch(u); flash('Link received from your browser ⚡'); };
    const off = window.zynox.onExtUrl ? window.zynox.onExtUrl(handle) : null;
    if (window.zynox.getPendingUrl) window.zynox.getPendingUrl().then((u) => { if (u) handle(u); }).catch(() => {});
    return () => { if (off) off(); };
  }, []);
  useEffect(() => {
    const anyActive = jobs.some((j) => ['starting', 'downloading', 'processing'].includes(j.status));
    if (!anyActive) return;
    const t = setInterval(() => setTickNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [jobs]);
  // Auto-fetch when a link is pasted anywhere on Home
  useEffect(() => {
    const onPaste = (e) => {
      if (tab !== 'home') return;
      const text = (e.clipboardData && e.clipboardData.getData('text')) || '';
      if (/https?:\/\/\S+/.test(text)) { const link = text.match(/https?:\/\/\S+/)[0]; setUrl(link); doFetch(link); }
    };
    window.addEventListener('paste', onPaste);
    return () => window.removeEventListener('paste', onPaste);
  }, [tab, url]);

  const flash = (msg) => { setToast(msg); setTimeout(() => setToast(''), 2600); };
  const refreshFiles = () => { window.zynox.listFiles().then(setFiles).catch(() => {}); };

  const doFetch = async (value) => {
    const link = (value ?? url).trim();
    if (!link || fetching) return;
    setFetching(true); setFetchError(''); setMeta(null);
    try {
      const m = await window.zynox.fetchFormats(link);
      if (!m.formats || !m.formats.length) throw new Error('No downloadable formats found for this link.');
      setMeta({ ...m, url: link });
    } catch (e) {
      setFetchError(String(e.message || e).replace(/^Error invoking remote method '[^']+':\s*/, '').replace(/^Error:\s*/, ''));
    } finally { setFetching(false); }
  };
  const pasteFromClipboard = async () => {
    const t = await window.zynox.readClipboard().catch(() => '');
    if (t && /https?:\/\//.test(t)) { const link = t.match(/https?:\/\/\S+/)[0]; setUrl(link); doFetch(link); }
    else if (inputRef.current) inputRef.current.focus();
  };
  const startJob = async (f) => {
    const id = 'job' + Date.now() + '_' + clientSeq++;
    const payload = { clientId: id, url: meta.url, title: meta.title, label: f.kind.startsWith('audio') || f.kind === 'direct-audio' ? f.label + ' Audio' : f.label, kind: f.kind, itag: f.itag, combined: f.combined, directUrl: f.directUrl || '', ext: f.ext || '', size: f.size || 0 };
    setJobs((prev) => [{ id, url: meta.url, title: meta.title, thumb: meta.thumbnail, label: payload.label, fmt: f, estSize: f.size || 0, status: 'starting', pct: 0, startedAt: Date.now() }, ...prev]);
    await window.zynox.startDownload(payload).catch(() => {});
    setTab('downloads');
  };
  const resumeJob = async (j) => {
    setJobs((prev) => prev.map((x) => (x.id === j.id ? { ...x, status: 'starting', pct: x.pct || 0, startedAt: Date.now(), note: '' } : x)));
    await window.zynox.startDownload({ clientId: j.id, url: j.url, title: j.title, label: j.label, kind: j.fmt.kind, itag: j.fmt.itag, combined: j.fmt.combined, directUrl: j.fmt.directUrl || '', ext: j.fmt.ext || '', size: j.fmt.size || 0 }).catch(() => {});
  };
  const checkEngine = async () => {
    setEngineBusy('checking'); setEngine(null);
    try { setEngine(await window.zynox.engineInfo()); } catch { setEngine({ current: null, latest: null }); }
    setEngineBusy('');
  };
  const updateEngine = async () => {
    setEngineBusy('updating'); setEnginePct(0);
    const r = await window.zynox.engineUpdate().catch((e) => ({ ok: false, message: String(e) }));
    setEngineBusy('');
    if (r.ok) { flash('Engine updated to ' + (r.version || 'latest')); checkEngine(); }
    else flash('Engine update failed');
  };
  const toggleTheme = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    window.zynox.setSettings({ theme: next }).catch(() => {});
  };
  const activeCount = jobs.filter((j) => ['starting', 'downloading', 'processing'].includes(j.status)).length;

  const videoFmts = meta ? meta.formats.filter((f) => f.kind === 'video' || f.kind === 'direct-video') : [];
  const audioFmts = meta ? meta.formats.filter((f) => ['audio', 'audio-mp3', 'direct-audio'].includes(f.kind)) : [];
  const imageFmts = meta ? meta.formats.filter((f) => f.kind === 'image') : [];

  const fmtRow = (f, i) => (
    <motion.button key={f.id} className="fmt-row"
      initial={{ opacity: 0, x: -18 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.05 * i }}
      whileHover={{ scale: 1.012 }} whileTap={{ scale: 0.97 }} onClick={() => startJob(f)}>
      <span className={`q-badge ${qualityTone(f.height)}`}>{f.kind === 'video' || f.kind === 'direct-video' ? f.label : f.kind === 'image' ? '🖼' : '♪'}</span>
      <span className="fmt-main">
        <span className="fmt-label">{f.kind === 'video' || f.kind === 'direct-video' ? (/\d/.test(f.label) ? `${f.label} Video` : f.label) : f.kind === 'image' ? f.label : `${f.label} Audio`}</span>
        <span className="fmt-detail">{f.detail}</span>
      </span>
      {f.size > 0 && <span className="fmt-size">{fmtBytes(f.size)}</span>}
      <span className="fmt-dl">⬇</span>
    </motion.button>
  );

  return (
    <div className={`${sidebarOpen ? 'app' : 'app collapsed'}${liteBg ? ' lite' : ''}`}>
      <Aurora />
      <CursorGlow />
      <Splash done={splashDone} onSkip={() => setSplashDone(true)} />

      <header className="titlebar">
        <div className="brand">
          <div className="brand-mark">Z</div>
          <div className="brand-name">ZYNOX</div>
          <div className="brand-tag">for Windows</div>
        </div>
        <div className="titlebar-drag" />
        <button className={`theme-switch ${theme}`} onClick={toggleTheme} title="Switch dark / light mode" aria-label="Toggle theme">
          <span className="ts-icon sun">☀</span>
          <span className="ts-icon moon">🌙</span>
          <motion.span className="ts-knob" layout transition={{ type: 'spring', stiffness: 500, damping: 32 }} />
        </button>
        <div className="win-controls">
          <button onClick={() => window.zynox.winMin()} aria-label="Minimize">—</button>
          <button onClick={() => window.zynox.winMax()} aria-label="Maximize">▢</button>
          <button className="close" onClick={() => window.zynox.winClose()} aria-label="Close">✕</button>
        </div>
      </header>

      <motion.button className="side-handle" style={{ x: handleDragX || 0 }}
        title="Click or drag to show / hide the sidebar"
        onPointerDown={(e) => { handleDragRef.current = { x0: e.clientX, dx: 0, moved: false }; e.currentTarget.setPointerCapture(e.pointerId); }}
        onPointerMove={(e) => { const d = handleDragRef.current; if (!d) return; const dx = e.clientX - d.x0; d.dx = dx; if (Math.abs(dx) > 4) d.moved = true; if (d.moved) setHandleDragX(Math.max(-240, Math.min(240, dx))); }}
        onPointerUp={() => { const d = handleDragRef.current; handleDragRef.current = null; setHandleDragX(null); if (!d) return; if (!d.moved) setSidebarOpen(!sidebarOpen); else if (d.dx > 30) setSidebarOpen(true); else if (d.dx < -30) setSidebarOpen(false); }}>
        {sidebarOpen ? '‹' : '›'}
      </motion.button>

      <nav className="sidebar">
        {[['home', '⌂', 'Home'], ['downloads', '⬇', `Downloads${activeCount ? ` (${activeCount})` : ''}`], ['files', '🗂', 'Files'], ['settings', '⚙', 'Settings'], ['about', '✦', 'About Us']].map(([key, icon, label]) => (
          <button key={key} className={tab === key ? 'nav-item active' : 'nav-item'} onClick={() => { setTab(key); if (key === 'files') refreshFiles(); }}>
            <span className="nav-icon">{icon}</span><span className="nav-label">{label}</span>
            {tab === key && <motion.span className="nav-glow" layoutId="navGlow" transition={{ type: 'spring', stiffness: 420, damping: 32 }} />}
          </button>
        ))}
      </nav>

      <main className="content">
        <AnimatePresence mode="wait">
          {tab === 'home' && (
            <motion.section key="home" className="page" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.22 }}>
              <Typewriter />
              <p className="subtitle">Paste a link — fetching starts automatically. YouTube, Facebook, Instagram, TikTok and 1000+ sites.</p>

              <motion.div className="link-box">
                <span className="link-icon">🔗</span>
                <input ref={inputRef} value={url} placeholder="Paste your link here…"
                  onChange={(e) => setUrl(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && doFetch()} />
                {url && <button className="clear-btn" onClick={() => { setUrl(''); setMeta(null); }}>✕</button>}
                <button className="paste-btn" onClick={pasteFromClipboard}>Paste</button>
                <motion.button className="fetch-btn" whileTap={{ scale: 0.94 }} onClick={() => doFetch()} disabled={fetching}>
                  {fetching ? 'Fetching…' : '⬇ Fetch'}
                </motion.button>
              </motion.div>

              <div className="feature-row">
                {FEATURES.map((f, i) => (
                  <motion.div key={f.title} className="feature-card" initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.08 * i }}>
                    <div className="feature-icon">{f.icon}</div>
                    <div className="feature-title">{f.title}</div>
                    <div className="feature-text">{f.text}</div>
                  </motion.div>
                ))}
              </div>

              <div className="platforms">
                <div className="section-label">Supported Platforms — click to open</div>
                <div className="platform-row">
                  {PLATFORMS.map((p, i) => (
                    <motion.button key={p.name} className="platform-chip" title={p.name}
                      initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.05 * i }}
                      whileHover={{ scale: 1.14, y: -4 }} whileTap={{ scale: 0.92 }}
                      onClick={() => window.zynox.openUrl(p.url)}>
                      <svg viewBox="0 0 24 24" width="22" height="22" fill={'#' + p.icon.hex}><path d={p.icon.path} /></svg>
                      <span>{p.name}</span>
                    </motion.button>
                  ))}
                  <motion.span className="platform-chip more-chip" initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.4 }}>
                    ✦ +1000 more sites
                  </motion.span>
                </div>
              </div>

              {fetching && (
                <div className="skeleton-wrap">{[0, 1, 2].map((i) => <div key={i} className="skeleton" style={{ animationDelay: `${i * 0.15}s` }} />)}</div>
              )}
              {fetchError && <motion.div className="error-card" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>⚠ {fetchError}</motion.div>}

              <AnimatePresence>
                {meta && (
                  <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
                    <Tilt className="result-card" max={3.5}>
                      <div className="result-head">
                        {meta.thumbnail && <img src={meta.thumbnail} alt="" />}
                        <div>
                          <div className="result-title">{meta.title}</div>
                          <div className="result-sub">{meta.author}{meta.duration ? ` • ${fmtDur(meta.duration)}` : ''} • fetched via {meta.source === 'innertube-fast' ? '⚡ fast fetch' : 'engine'}</div>
                          {meta.noWatermark && <div className="nowm-chip">✓ TikTok — watermark removed automatically</div>}
                        </div>
                      </div>
                      <div className="result-cols">
                        <div className="result-col">
                          {videoFmts.length > 0 && <div className="fmt-section">🎬 Video</div>}
                          <div className="fmt-list">{videoFmts.map(fmtRow)}</div>
                        </div>
                        <div className="result-col">
                          {audioFmts.length > 0 && <div className="fmt-section">🎵 Audio</div>}
                          <div className="fmt-list">{audioFmts.map(fmtRow)}</div>
                        </div>
                      </div>
                      {imageFmts.length > 0 && <>
                        <div className="fmt-section">🖼 Photos</div>
                        <div className="fmt-list">{imageFmts.map(fmtRow)}</div>
                      </>}
                    </Tilt>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.section>
          )}

          {tab === 'downloads' && (
            <motion.section key="downloads" className="page" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.22 }}>
              <h2 className="page-title">Downloads</h2>
              {jobs.length === 0 && <div className="empty">No downloads yet. Paste a link on Home to start one.</div>}
              <div className="job-list">
                <AnimatePresence>
                  {jobs.map((j) => {
                    const total = j.total || j.estSize || 0;
                    const downloaded = j.downloaded != null ? j.downloaded : (total && j.pct ? (total * j.pct) / 100 : 0);
                    const preparing = j.status === 'starting' || (j.status === 'downloading' && !j.downloaded && !j.pct);
                    const elapsed = Math.max(0, Math.round((Date.now() - (j.startedAt || Date.now())) / 1000));
                    return (
                      <motion.div key={j.id} className={`job-card ${j.status}`} layout
                        initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.96 }}>
                        {j.thumb && <img className="job-thumb" src={j.thumb} alt="" />}
                        <div className="job-main">
                          <div className="job-title">{j.title}</div>
                          <div className="job-sub">
                            {j.status === 'done' ? `✓ Saved${total ? ` • ${fmtBytes(total)}` : ''}` :
                              j.status === 'error' ? `Failed — ${j.message || ''}` :
                              j.status === 'processing' ? 'Merging / converting…' :
                              j.status === 'paused' ? 'Paused — resume keeps your progress' :
                              preparing ? `${j.note ? j.note + ' ' : 'Preparing engine… '}${elapsed}s` :
                              total > 0 ? `${fmtBytes(downloaded)} of ${fmtBytes(total)} • ${(j.pct || 0).toFixed(1)}%${j.speedBps ? ` • ${fmtSpeed(j.speedBps)}` : ''}${j.etaSec ? ` • ETA ${fmtEta(j.etaSec)}` : ''}` :
                              `${fmtBytes(downloaded)} downloaded${j.speedBps ? ` • ${fmtSpeed(j.speedBps)}` : ''}`}
                          </div>
                          <div className={preparing ? 'seg-track pulse' : 'seg-track'}>
                            {Array.from({ length: 40 }).map((_, i) => (
                              <span key={i} className={i < Math.round(((j.status === 'done' ? 100 : (j.pct || 0)) / 100) * 40) ? 'seg on' : 'seg'} />
                            ))}
                          </div>
                          <div className="job-meta">{j.label}</div>
                        </div>
                        <div className="job-actions">
                          {['downloading', 'starting', 'processing'].includes(j.status) && <button onClick={() => window.zynox.pauseDownload(j.id)}>⏸</button>}
                          {j.status === 'paused' && <button onClick={() => resumeJob(j)}>▶</button>}
                          {j.status === 'done' && j.filePath && <button title="Open file" onClick={() => window.zynox.openFile(j.filePath)}>▶</button>}
                          {j.status === 'done' && j.filePath && <button title="Open folder" onClick={() => window.zynox.showInFolder(j.filePath)}>📂</button>}
                          <button className="danger" onClick={() => { if (j.status !== 'done') window.zynox.cancelDownload(j.id, { url: j.url, itag: j.fmt.itag, label: j.label }); setJobs((p) => p.filter((x) => x.id !== j.id)); }}>✕</button>
                        </div>
                      </motion.div>
                    );
                  })}
                </AnimatePresence>
              </div>
            </motion.section>
          )}

          {tab === 'files' && (
            <motion.section key="files" className="page" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.22 }}>
              <div className="files-head">
                <h2 className="page-title">Files</h2>
                <div className="dir-row">
                  <span className="dir-path" title={dlDir}>{dlDir}</span>
                  <button className="small-btn" onClick={async () => setDlDir(await window.zynox.chooseDownloadsDir())}>Change folder</button>
                  <button className="small-btn" onClick={() => window.zynox.openDownloadsDir()}>Open folder</button>
                </div>
              </div>
              {files.length === 0 && <div className="empty">Nothing here yet — finished downloads will appear in this folder.</div>}
              <div className="file-grid">
                {files.map((f, i) => (
                  <motion.div key={f.path} className="file-card" initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(i * 0.04, 0.4) }} whileHover={{ y: -4, scale: 1.02 }}>
                    {f.thumb
                      ? <img className="file-thumb" src={f.thumb} alt="" loading="lazy" />
                      : <div className="file-icon">{/\.(mp3|m4a|opus)$/i.test(f.name) ? '🎵' : /\.(jpg|png|webp)$/i.test(f.name) ? '🖼' : '🎬'}</div>}
                    <div className="file-name" title={f.name}>{f.name}</div>
                    <div className="file-size">{fmtBytes(f.size)}</div>
                    <div className="file-actions">
                      <button onClick={() => window.zynox.openFile(f.path)}>Play</button>
                      <button onClick={() => window.zynox.showInFolder(f.path)}>Folder</button>
                      <button className="del" title="Move to Recycle Bin" onClick={async () => { const ok = await window.zynox.deleteFile(f.path); if (ok) { flash('Moved to Recycle Bin'); refreshFiles(); } else flash('Could not delete that file'); }}>🗑</button>
                    </div>
                  </motion.div>
                ))}
              </div>
            </motion.section>
          )}

          {tab === 'settings' && (
            <motion.section key="settings" className="page" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.22 }}>
              <h2 className="page-title">Settings</h2>

              <div className="setting-card">
                <div className="setting-head">🔗 Connection <span className="setting-sub">— for private Facebook / Instagram videos</span></div>
                {[['facebook', 'Facebook'], ['instagram', 'Instagram']].map(([site, name]) => (
                  <div key={site} className="setting-row">
                    <div>
                      <div className="setting-name">{name}</div>
                      <div className="setting-desc">{conn[site] ? 'Connected — private videos will download with your session' : 'Not connected — only public videos'}</div>
                    </div>
                    {conn[site]
                      ? <button className="small-btn danger-btn" onClick={async () => setConn(await window.zynox.disconnectSite(site))}>Disconnect</button>
                      : <button className="small-btn accent-btn" onClick={async () => { const r = await window.zynox.connectSite(site); if (r.ok) { setConn(await window.zynox.connectionState()); flash(name + ' connected ✓'); } }}>Connect</button>}
                  </div>
                ))}
              </div>

              <div className="setting-card">
                <div className="setting-head">🎬 Download Engine <span className="setting-sub">— yt-dlp</span></div>
                <div className="setting-row">
                  <div>
                    <div className="setting-name">Engine version</div>
                    <div className="setting-desc">
                      {engine ? <>Installed: <b>{engine.current || 'unknown'}</b>{engine.latest ? <> • Latest: <b>{engine.latest}</b></> : ''}{engine.updateAvailable ? ' • Update available!' : engine.latest ? ' • Up to date ✓' : ''}</>
                        : 'Tap Check for update to see the installed and latest versions.'}
                    </div>
                    {engineBusy === 'updating' && <div className="mini-progress"><div style={{ width: enginePct + '%' }} /></div>}
                  </div>
                  <div className="row-btns">
                    <button className="small-btn" onClick={checkEngine} disabled={!!engineBusy}>{engineBusy === 'checking' ? 'Checking…' : 'Check for update'}</button>
                    {engine && engine.updateAvailable && <button className="small-btn accent-btn" onClick={updateEngine} disabled={!!engineBusy}>{engineBusy === 'updating' ? `Updating ${enginePct}%` : 'Update now'}</button>}
                  </div>
                </div>
              </div>

              <div className="setting-card">
                <div className="setting-head">🎨 Appearance & Behaviour</div>
                <div className="setting-row">
                  <div><div className="setting-name">Theme</div><div className="setting-desc">Dark violet, or clean light mode</div></div>
                  <button className="small-btn" onClick={toggleTheme}>{theme === 'dark' ? '☀️ Switch to Light' : '🌙 Switch to Dark'}</button>
                </div>
                <div className="setting-row">
                  <div><div className="setting-name">Run in background</div><div className="setting-desc">Closing the window keeps Zynox in the tray, downloads continue</div></div>
                  <button className={`switch ${closeToTray ? 'on' : ''}`} onClick={() => { const v = !closeToTray; setCloseToTray(v); window.zynox.setSettings({ closeToTray: v }); }}><span /></button>
                </div>
                <div className="setting-row">
                  <div><div className="setting-name">Lite mode (lowest GPU)</div><div className="setting-desc">Freezes all background animation — the lightest mode for any laptop</div></div>
                  <button className={`switch ${liteBg ? 'on' : ''}`} onClick={() => { const v = !liteBg; setLiteBg(v); try { localStorage.setItem('zx_lite', v ? '1' : '0'); } catch {} }}><span /></button>
                </div>
                <div className="setting-row">
                  <div><div className="setting-name">Downloads folder</div><div className="setting-desc dir-path2">{dlDir}</div></div>
                  <button className="small-btn" onClick={async () => setDlDir(await window.zynox.chooseDownloadsDir())}>Change</button>
                </div>
              </div>
            </motion.section>
          )}

          {tab === 'about' && (
            <motion.section key="about" className="page" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.22 }}>
              <div className="about-overline">FCUB • CSE FINAL YEAR</div>
              <h2 className="about-title">The Minds <span className="about-title-anim">Behind Zynox</span></h2>
              <p className="about-tag">Four developers. One vision. Built with passion and innovation.</p>
              <Tilt className="about-intro" max={3}>
                <p>Zynox is built by a passionate team of innovators who believe technology should be powerful, simple, and beautiful. We craft experiences that inspire, empower, and connect people around the world.</p>
                <p className="about-hint">Tap any member card to see it up close ✦</p>
              </Tilt>
              <div className="team-grid">
                {TEAM.map((m, i) => (
                  <motion.div key={m.name} className="team-card" style={{ '--acc': m.accent }}
                    initial={{ opacity: 0, y: 22 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 * i }}
                    whileHover={{ y: -5 }} whileTap={{ scale: 0.97 }} onClick={() => setActiveMember(m)}>
                    <div className="team-photo-wrap" style={{ boxShadow: `0 12px 30px ${m.accent}55` }}>
                      <img src={m.photo} alt={m.name} />
                    </div>
                    <div className="team-name">{m.name}</div>
                    <div className="team-role" style={{ color: m.accent }}>{m.role}</div>
                    <p className="team-bio">{m.bio}</p>
                    <div className="team-socials">
                      <button title="Facebook" onClick={(e) => { e.stopPropagation(); window.zynox.openUrl(m.socials.fb); }}><svg viewBox="0 0 24 24" width="16" height="16" fill={'#' + siFacebook.hex}><path d={siFacebook.path} /></svg></button>
                      {m.socials.gh && <button title="GitHub" onClick={(e) => { e.stopPropagation(); window.zynox.openUrl(m.socials.gh); }}><svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d={siGithub.path} /></svg></button>}
                      <button title="Instagram" onClick={(e) => { e.stopPropagation(); window.zynox.openUrl(m.socials.ig); }}><svg viewBox="0 0 24 24" width="16" height="16" fill={'#' + siInstagram.hex}><path d={siInstagram.path} /></svg></button>
                      {m.socials.tt && <button title="TikTok" onClick={(e) => { e.stopPropagation(); window.zynox.openUrl(m.socials.tt); }}><svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d={siTiktok.path} /></svg></button>}
                    </div>
                  </motion.div>
                ))}
              </div>
              <div className="about-quote">“Our mission is simple — create technology that makes life easier, faster, and more enjoyable for everyone.”</div>
              <div className="about-foot">© 2026 Zynox • Crafted with ♥ by Team Zynox • First Capital University of Bangladesh</div>
            </motion.section>
          )}
        </AnimatePresence>
      </main>

      <AnimatePresence>
        {doneModal && (
          <motion.div className="modal-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setDoneModal(null)}>
            <motion.div className="done-card" initial={{ opacity: 0, scale: 0.85, y: 30 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.92 }}
              transition={{ type: 'spring', stiffness: 300, damping: 24 }} onClick={(e) => e.stopPropagation()}>
              <div className="done-icon">✓</div>
              <div className="done-title">Download complete</div>
              <div className="done-name" title={doneModal.title}>{doneModal.title}</div>
              {doneModal.total > 0 && <div className="done-size">{fmtBytes(doneModal.total)}{doneModal.label ? ` • ${doneModal.label}` : ''}</div>}
              <div className="done-path" title={doneModal.filePath}>{doneModal.filePath}</div>
              <div className="done-actions">
                <button className="accent-btn" onClick={() => { window.zynox.openFile(doneModal.filePath); setDoneModal(null); }}>▶ Open</button>
                <button className="small-btn" onClick={() => { window.zynox.showInFolder(doneModal.filePath); setDoneModal(null); }}>📂 Open folder</button>
                <button className="small-btn" onClick={() => setDoneModal(null)}>Close</button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {activeMember && (
          <motion.div className="modal-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setActiveMember(null)}>
            <motion.div className="modal-card" style={{ '--acc': activeMember.accent }}
              initial={{ opacity: 0, scale: 0.82, y: 44, rotateX: 14 }} animate={{ opacity: 1, scale: 1, y: 0, rotateX: 0 }}
              exit={{ opacity: 0, scale: 0.9, y: 24 }} transition={{ type: 'spring', stiffness: 260, damping: 24 }}
              onClick={(e) => e.stopPropagation()}>
              <button className="modal-close" onClick={() => setActiveMember(null)}>✕</button>
              <div className="modal-photo"><img src={activeMember.photo} alt={activeMember.name} /></div>
              <div className="modal-info">
                <div className="team-name big">{activeMember.name}</div>
                <div className="team-role" style={{ color: activeMember.accent }}>{activeMember.role}</div>
                <p className="team-bio big">{activeMember.bio}</p>
                <div className="modal-uni">FCUB • CSE Final Year</div>
                <div className="team-socials left">
                  <button title="Facebook" onClick={() => window.zynox.openUrl(activeMember.socials.fb)}><svg viewBox="0 0 24 24" width="17" height="17" fill={'#' + siFacebook.hex}><path d={siFacebook.path} /></svg></button>
                  {activeMember.socials.gh && <button title="GitHub" onClick={() => window.zynox.openUrl(activeMember.socials.gh)}><svg viewBox="0 0 24 24" width="17" height="17" fill="currentColor"><path d={siGithub.path} /></svg></button>}
                  <button title="Instagram" onClick={() => window.zynox.openUrl(activeMember.socials.ig)}><svg viewBox="0 0 24 24" width="17" height="17" fill={'#' + siInstagram.hex}><path d={siInstagram.path} /></svg></button>
                  {activeMember.socials.tt && <button title="TikTok" onClick={() => window.zynox.openUrl(activeMember.socials.tt)}><svg viewBox="0 0 24 24" width="17" height="17" fill="currentColor"><path d={siTiktok.path} /></svg></button>}
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {toast && <motion.div className="toast" initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>{toast}</motion.div>}
      </AnimatePresence>
    </div>
  );
}
