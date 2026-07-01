# Kalpkari Watermark

A native Android app that overlays the Kalpkari brand logo onto photos and videos, tuned for posting to Instagram Reels and Stories. Pick media, pick one of the bundled logos, place it with Instagram-safe margins, preview, and export at source quality.

Not a Play Store app — it is built as a sideloadable debug APK and distributed directly to the people who need it.

## Features

- **Media input** — pick from the **Gallery** (Android photo picker) or **Browse files** (Storage Access Framework, for videos a gallery never indexed). Images: JPG/PNG/WebP. Videos: MP4 (H.264) and anything the device decoder supports.
- **Bundled logos** — the Kalpkari logo set ships inside the app (`app/src/main/assets/logos`). One is applied at a time.
- **Logo colour** — use the original artwork or recolour it to brand rust `#7D3208` (alpha preserved).
- **Placement** — five presets (top-left, top-right, center-top, bottom-left, bottom-right) plus a **size** slider and **horizontal/vertical nudge** sliders. Margins are Instagram-safe by default and centralised for easy tuning.
- **Live preview** — WYSIWYG: the logo is drawn over the source image / first video frame exactly where it will export. Placement changes update instantly without re-decoding.
- **Export**
  - *Images* — composited at full source resolution. PNG/WebP stay lossless; JPEG exports at quality 100.
  - *Videos* — GPU-composited and re-encoded (unavoidable for an overlay) at the source resolution, frame rate, aspect ratio and duration. Encoder bitrate is matched to the source (with a quality floor), and **audio is copied through unchanged**.
- Output is written to the shared **MediaStore** (`Pictures/KalpkariWatermark`, `Movies/KalpkariWatermark`) and can be shared straight to Instagram. Export runs off the UI thread with progress and a cancel button.

## Requirements

- Android 8.0 (API 26) or newer.
- No runtime permissions — media in comes via the system pickers, media out via scoped MediaStore.

## Build

Uses the Gradle wrapper (pinned to Gradle 8.9). The Android Gradle Plugin needs a JDK in the 17–21 range; Android Studio's bundled JBR 21 works well:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (also copied to `dist/` for distribution).

## Install

```bash
adb install -r dist/KalpkariWatermark-v1.0-debug.apk
```

Or send the APK to a phone and open it (enable "install unknown apps" for the file manager).

## Project layout

```
app/src/main/
  assets/logos/          bundled logo PNGs (source of truth for the picker)
  java/com/kalpkari/watermark/
    domain/              models, positions, tunable Instagram margins, logo tint
    data/                logo repository (reads assets)
    media/               Placement, ImageWatermarker, VideoWatermarker, MediaSaver
    ui/                  Compose screen, ViewModel, theme
  res/                   brand colours, launcher/splash (Logo 4)
```

See [AGENTS.md](AGENTS.md) for developer notes.
