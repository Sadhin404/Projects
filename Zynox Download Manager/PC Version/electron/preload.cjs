const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('zynox', {
  fetchFormats: (url) => ipcRenderer.invoke('fetch-formats', url),
  startDownload: (job) => ipcRenderer.invoke('start-download', job),
  pauseDownload: (id) => ipcRenderer.send('pause-download', id),
  cancelDownload: (id, snapshot) => ipcRenderer.send('cancel-download', id, snapshot),
  onExtUrl: (cb) => { const h = (_e, u) => cb(u); ipcRenderer.on('ext-url', h); return () => ipcRenderer.removeListener('ext-url', h); },
  getPendingUrl: () => ipcRenderer.invoke('get-pending-url'),
  onProgress: (cb) => {
    const handler = (_e, payload) => cb(payload);
    ipcRenderer.on('dl-progress', handler);
    return () => ipcRenderer.removeListener('dl-progress', handler);
  },
  onEngineProgress: (cb) => {
    const handler = (_e, pct) => cb(pct);
    ipcRenderer.on('engine-progress', handler);
    return () => ipcRenderer.removeListener('engine-progress', handler);
  },
  getDownloadsDir: () => ipcRenderer.invoke('get-downloads-dir'),
  chooseDownloadsDir: () => ipcRenderer.invoke('choose-downloads-dir'),
  listFiles: () => ipcRenderer.invoke('list-files'),
  deleteFile: (p) => ipcRenderer.invoke('delete-file', p),
  getSettings: () => ipcRenderer.invoke('get-settings'),
  setSettings: (patch) => ipcRenderer.invoke('set-settings', patch),
  connectionState: () => ipcRenderer.invoke('connection-state'),
  connectSite: (site) => ipcRenderer.invoke('connect-site', site),
  disconnectSite: (site) => ipcRenderer.invoke('disconnect-site', site),
  engineInfo: () => ipcRenderer.invoke('engine-info'),
  engineUpdate: () => ipcRenderer.invoke('engine-update'),
  openUrl: (u) => ipcRenderer.send('open-url', u),
  openFile: (p) => ipcRenderer.send('open-file', p),
  showInFolder: (p) => ipcRenderer.send('show-in-folder', p),
  openDownloadsDir: () => ipcRenderer.send('open-downloads-dir'),
  readClipboard: () => ipcRenderer.invoke('read-clipboard'),
  winMin: () => ipcRenderer.send('win-min'),
  winMax: () => ipcRenderer.send('win-max'),
  winClose: () => ipcRenderer.send('win-close'),
});
