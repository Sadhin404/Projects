// Zynox Download Helper v3.1 — popup-only. NO floating button (the
// owner removed it: one system everywhere, the toolbar popup). This
// script's only job: when the popup asks, answer with the link of
// the video that is playing RIGHT NOW.
//
// Feeds never change the page URL, so the link is resolved from the
// page. Two hard lessons are baked in:
//  • Instagram's Reels viewer lives at /reels/<code> (PLURAL) — a
//    /reel/-only check misses it and the popup blocked the download.
//  • TikTok stacks feed items on the same spot, so several /video/
//    links can sit at distance ~0 from the playing video. The winner
//    must be the link that lives in the SAME item container as the
//    playing video — otherwise the first mounted item's link wins
//    forever (the "stuck link" bug).
(function () {
  if (window.__zynoxHelper) return;
  window.__zynoxHelper = true;

  const HOST = location.hostname.replace(/^www\./, '');

  function visibleArea(v) {
    const r = v.getBoundingClientRect();
    const w = Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0));
    const h = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
    return w * h;
  }
  function playingVideo() {
    const vids = [...document.querySelectorAll('video')].filter((v) => visibleArea(v) > 120 * 120);
    if (!vids.length) return null;
    vids.sort((a, b) => {
      const va = visibleArea(b) - visibleArea(a);
      if (va) return va;
      return (b.paused ? 0 : 1) - (a.paused ? 0 : 1);
    });
    return vids[0];
  }
  function rectDist(a, b) {
    const dx = Math.max(0, Math.max(a.left - b.right, b.left - a.right));
    const dy = Math.max(0, Math.max(a.top - b.bottom, b.top - a.bottom));
    return Math.hypot(dx, dy);
  }
  function rendered(a) {
    try {
      const s = getComputedStyle(a);
      if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity || '1') === 0) return false;
    } catch {}
    return true;
  }
  // Does any ancestor of `a` (up to 25 levels) also contain `video`?
  // That's the signature of "same feed item".
  function sameItem(a, video) {
    let n = a;
    for (let i = 0; n && i < 25; i++, n = n.parentElement) {
      if (n.contains && n.contains(video)) return true;
    }
    return false;
  }
  function proximityLink(video, re) {
    if (!video) return '';
    const anchors = [...document.querySelectorAll('a[href]')].filter((x) => re.test(x.href) && rendered(x));
    if (!anchors.length) return '';
    const vr = video.getBoundingClientRect();
    let best = '', bd = Infinity, bestContained = false;
    for (const a of anchors) {
      const contained = sameItem(a, video);
      const d = contained ? 0 : rectDist(a.getBoundingClientRect(), vr);
      if ((contained && !bestContained) || (contained === bestContained && d < bd)) {
        best = a.href; bd = d; bestContained = contained;
      }
    }
    return bestContained || bd < 700 ? best : '';
  }
  // Last TikTok resort: scope to the playing video's own item card
  // and read the video id + author handle out of that subtree only.
  function scanItemCard(video) {
    let node = video;
    for (let depth = 0; node && depth < 20; depth++, node = node.parentElement) {
      if (!node.querySelectorAll) continue;
      const r = node.getBoundingClientRect ? node.getBoundingClientRect() : { height: 0 };
      if (depth > 0 && r.height > innerHeight * 1.6) break; // escaped into the whole feed list
      const html = node.innerHTML || '';
      const m = html.match(/\/(?:video|photo)\/(\d{10,})/);
      if (m) {
        const author = [...node.querySelectorAll('a[href]')]
          .map((x) => (x.getAttribute('href') || '').match(/^\/@([\w.]+)\/?$/))
          .find(Boolean);
        if (author) return 'https://www.tiktok.com/@' + author[1] + '/video/' + m[1];
        return 'https://www.tiktok.com/video/' + m[1];
      }
    }
    return '';
  }

  function resolveVideoUrl() {
    const h = HOST, p = location.pathname;
    try {
      if (h.includes('youtube.com')) {
        if (p.startsWith('/watch') || p.startsWith('/shorts/')) return location.href;
        return '';
      }
      if (h.includes('tiktok.com')) {
        if (/\/(video|photo)\/\d+/.test(p)) return location.href;
        const v = playingVideo();
        const u = proximityLink(v, /\/(video|photo)\/\d+/);
        if (u) return u;
        if (v) { const s = scanItemCard(v); if (s) return s; }
        const og = document.querySelector('meta[property="og:url"]');
        if (og && /\/(video|photo)\/\d+/.test(og.content || '')) return og.content;
        const c = document.querySelector('link[rel="canonical"]');
        if (c && /\/(video|photo)\/\d+/.test(c.href)) return c.href;
        return '';
      }
      if (h.includes('instagram.com')) {
        if (/\/(reels?|p|tv)\/[A-Za-z0-9_-]+/.test(p)) return location.href;
        return proximityLink(playingVideo(), /\/(reels?|p)\/[A-Za-z0-9_-]+/);
      }
      if (h.includes('facebook.com')) {
        if (/\/(watch|reel|videos)/.test(p) || /[?&]v=\d+/.test(location.search)) return location.href;
        return proximityLink(playingVideo(), /\/(reel|watch|videos)\//) || location.href;
      }
    } catch {}
    return location.href;
  }

  // ---------- popup messages ----------
  try {
    chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
      if (msg && msg.type === 'zx-get-url') sendResponse({ url: resolveVideoUrl() });
      // The popup hands the final URL here so the zynox:// launch
      // happens in the PAGE's context: Chrome's "Open Zynox?" prompt
      // then belongs to the tab and stays up until the user answers.
      // (Launched from the popup itself, the prompt vanished when the
      // popup closed — before the user could allow it.)
      if (msg && msg.type === 'zx-open' && /^https?:\/\//.test(msg.url || '')) {
        const a = document.createElement('a');
        a.href = 'zynox://download?url=' + encodeURIComponent(msg.url);
        a.style.display = 'none';
        (document.body || document.documentElement).appendChild(a);
        a.click();
        a.remove();
        sendResponse({ ok: true });
      }
    });
  } catch {}
})();
