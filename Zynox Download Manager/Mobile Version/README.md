<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:2a1650,50:7c3aed,100:db2777&height=190&section=header&text=ZYNOX&fontSize=68&fontColor=ffffff&animation=fadeIn&fontAlignY=36&desc=Download%20Manager%20for%20Android&descSize=19&descAlignY=56" width="100%"/>

<img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=21&duration=2800&pause=900&color=A78BFA&center=true&vCenter=true&width=660&lines=YouTube+%E2%80%A2+TikTok+%E2%80%A2+Facebook+%E2%80%A2+Instagram;Up+to+8K+quality+%2B+MP3+320+kbps;TikTok+downloads+without+watermark;Fast+Innertube+fetch+%2B+yt-dlp+engine" alt="Typing SVG" />

<br/>

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![yt-dlp](https://img.shields.io/badge/Engine-yt--dlp-FF0000?style=for-the-badge&logo=youtube&logoColor=white)
![FFmpeg](https://img.shields.io/badge/FFmpeg-007808?style=for-the-badge&logo=ffmpeg&logoColor=white)
![Version](https://img.shields.io/badge/Version-v81-8b5cf6?style=for-the-badge)
![Team](https://img.shields.io/badge/Team-Zynox-ec4899?style=for-the-badge)

**Four developers. One vision. Built with passion and innovation.**

</div>

---

## ✦ About Zynox

Zynox is built by a passionate team of innovators who believe technology should be powerful, simple, and beautiful. We craft experiences that inspire, empower, and connect people around the world.

Zynox for Android is a fast, modern download manager: paste a link from YouTube, TikTok, Facebook, Instagram or 1000+ other sites, pick exactly the quality you want, and the file lands in your `Downloads/Zynox` folder — with live progress, pause/resume, and background downloading that keeps working while you use other apps.

> *"Our mission is simple — create technology that makes life easier, faster, and more enjoyable for everyone."*

## ⚡ Features

- 🎬 **Up to 8K video** — pick the exact quality, from 360p to 8K, exactly the format you chose
- 🎵 **MP3 at 320 kbps** — one-tap audio extraction at maximum quality
- 🚀 **Innertube fast fetch** — YouTube formats are raced across multiple clients in parallel, full quality list in seconds
- 💧 **TikTok without watermark** — clean downloads, plus photo-post support
- ⏸ **Pause & resume** — paused downloads keep their partial files and resume right where they stopped, even from the notification
- 🔔 **Smart notifications** — progress, a standalone *Paused* notification with Resume, and a gentle vibration when a download completes
- 🔐 **Private video support** — optional Facebook / Instagram login stored encrypted (Android Keystore, AES-GCM) for private videos
- 🔄 **Self-updating engine** — the bundled yt-dlp engine checks for updates daily, so downloads keep working as sites change
- 🌙 **Dark violet design** — animated splash, ambient light background, light & dark themes
- 📂 **Built-in file manager** — everything you download, organised in one place

## 🧠 How it works

```
Link paste / share sheet
        │
        ▼
  Fetcher — Innertube race (ANDROID_VR / ANDROID / IOS in parallel)
        │   first playable result wins · engine fallback
        ▼
  Format picker — Video | Audio | Photos, exact itags
        │
        ▼
  DownloadService (foreground) — yt-dlp engine (Chaquopy)
        │   direct-stream route for audio · Range-resume partials
        ▼
  MediaStore → Downloads/Zynox  ✓
```

## 🗂 Project structure

```
Mobile Version/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/shadhinkhan/grabit/
│       │   ├── MainActivity.java       · home, fetch, format picker
│       │   ├── Fetcher.java            · Innertube fast fetch
│       │   ├── DownloadService.java    · download engine orchestration
│       │   ├── AudioEngine.java        · MP3 320 conversion
│       │   ├── StreamDownloader.java   · direct streams + resume
│       │   ├── SplashActivity.java · ShareActivity.java · …
│       │   └── SessionVault.java       · Keystore-encrypted sessions
│       └── res/                        · layouts, values, splash Lottie
├── build_v20.sh          · manual release build (aapt2 + d8 + zipalign)
├── build_manual.sh · build_v3.sh
└── AUDIO_ENGINE.md       · audio pipeline notes
```

## 🔨 Building

The release APK is built with the manual toolchain script (no Gradle daemon needed):

```bash
# Requires: JDK 17, Android SDK (build-tools + platform 35)
bash build_v20.sh
# Output: GrabIt_v4_Remake.apk  (package com.shadhinkhan.grabit)
```

Notes:

- `local.properties` (your SDK path) is machine-local and intentionally not committed.
- The Chaquopy Python runtime, yt-dlp payload and native libraries are build intermediates — they are assembled into `build/` by the script and are not part of this source snapshot.
- Image assets (`res/drawable-nodpi`, `res/mipmap-*`) live in the app module alongside this snapshot in the working tree.

## 📥 Download

Get the latest APK from the [**Releases**](https://github.com/Sadhin404/Projects/releases) section of this repository.

- Package: `com.shadhinkhan.grabit` · Min Android 8.0 (API 26) · Target API 35
- Current build: **v81** — the battle-tested v78 download behaviour, restored exactly.

## 👨‍💻 The Minds Behind Zynox

**FCUB • CSE Final Year** — First Capital University of Bangladesh

| Developer | Role |
|---|---|
| **Sadhin Khan** | Main Developer |
| **Ali Emam Rabby** | Frontend Developer |
| **Sristi Sorower** | Backend Developer |
| **Meharab Hosen** | Project Manager |

**Sadhin Khan** — [GitHub](https://github.com/Sadhin404) · [Facebook](https://www.facebook.com/share/1GRMfN9pwh/) · [Instagram](https://www.instagram.com/sadhin.404) · [TikTok](https://www.tiktok.com/@sadvibess_999)

**Ali Emam Rabby** — [Facebook](https://www.facebook.com/share/1E3GyCanz2/) · [Instagram](https://www.instagram.com/ali.emam.rabbi) · [TikTok](https://www.tiktok.com/@rabbi_10k)

**Sristi Sorower** — [Facebook](https://www.facebook.com/share/1C8fXfc2j3/) · [Instagram](https://www.instagram.com/sristisorower) · [TikTok](https://www.tiktok.com/@sristisorower0)

**Meharab Hosen** — [Facebook](https://www.facebook.com/share/1A4nXS2xHP/) · [Instagram](https://www.instagram.com/hosen.meharab)

---

<div align="center">

© 2026 Zynox • Crafted with ♥ by Team Zynox

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:2a1650,50:7c3aed,100:db2777&height=110&section=footer" width="100%"/>

</div>
