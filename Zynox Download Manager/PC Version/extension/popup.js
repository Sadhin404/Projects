// Zynox Download Helper — popup (the one and only interface, v3.2).
//
//  • YouTube / Instagram / Facebook: the link box is auto-filled
//    with the playing video's link — and stays EDITABLE, so a wrong
//    detection can be corrected (or a link pasted) before sending.
//  • TikTok: NO auto-detection (the owner asked for manual paste —
//    TikTok's feed made detection unreliable). The box starts empty
//    with "Insert video link here"; paste the video's own link.
let pageUrl = '';
let host = '';

// Launch via the page (content script) so Chrome's "Open Zynox?"
// prompt is anchored to the tab and waits for the user's answer —
// launched from this popup, the prompt died when the popup closed.
function sendToZynox(url, tabId, noteEl) {
  if (!/^https?:\/\//.test(url)) return;
  const NOTE = 'Opening Zynox… If Chrome asks, click "Open Zynox" — tick "Always allow" so it stops asking.';
  const fallback = () => {
    const a = document.createElement('a');
    a.href = 'zynox://download?url=' + encodeURIComponent(url);
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    noteEl.textContent = NOTE;
    // No auto-close on this path: closing the popup can dismiss the prompt.
  };
  if (tabId != null) {
    try {
      chrome.tabs.sendMessage(tabId, { type: 'zx-open', url }, (res) => {
        void chrome.runtime.lastError;
        if (res && res.ok) { noteEl.textContent = NOTE; setTimeout(() => window.close(), 900); }
        else fallback();
      });
      return;
    } catch {}
  }
  fallback();
}

chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
  const tab = tabs && tabs[0];
  pageUrl = (tab && tab.url) || '';
  try { host = new URL(pageUrl).hostname.replace(/^www\./, ''); } catch { host = ''; }
  const urlEl = document.getElementById('url');
  const noteEl = document.getElementById('note');
  const isTikTok = /tiktok\.com/.test(host);

  urlEl.placeholder = isTikTok ? 'Insert video link here' : 'Video link';

  // Ask the content script which video is playing right now —
  // everywhere EXCEPT TikTok (manual paste there, by request).
  let answered = false;
  if (!isTikTok && tab && tab.id != null) {
    try {
      chrome.tabs.sendMessage(tab.id, { type: 'zx-get-url' }, (res) => {
        void chrome.runtime.lastError;
        answered = true;
        if (res && res.url && !urlEl.value) urlEl.value = res.url;
      });
    } catch {}
  }
  if (isTikTok) setTimeout(() => { try { urlEl.focus(); } catch {} }, 60);

  document.getElementById('go').addEventListener('click', () => {
    const url = (urlEl.value || '').trim();
    if (!/^https?:\/\//.test(url)) {
      noteEl.textContent = isTikTok ? 'Paste the TikTok video link first' : 'Paste a valid video link first';
      return;
    }
    // Feeds keep the site root in the address bar — never send a
    // bare root; the app would fetch the homepage instead.
    const needsVideo = /tiktok\.com|instagram\.com/.test(host);
    const looksVideo = /\/(video|photo|reels?|p|tv)\//.test(url) || /^(https?:\/\/)?(vt|vm)\.tiktok\.com\//i.test(url);
    if (needsVideo && !looksVideo) {
      noteEl.textContent = isTikTok
        ? 'That is not a video link — open the video, copy its link, paste it here'
        : (answered
          ? 'Play a video on this page first, or paste its link here'
          : 'Reload this tab once so the helper can see the video, or paste its link here');
      return;
    }
    sendToZynox(url, tab && tab.id, noteEl);
  });
});
