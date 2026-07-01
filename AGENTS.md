# AGENTS.md

Developer notes for the Kalpkari Watermark app. Aimed at anyone (human or agent) picking up the codebase.

## Build & run

```bash
# AGP 8.5.x needs a JDK in the 17–21 range. A system Java that is newer (22+)
# will fail the build — point JAVA_HOME at a 17–21 JDK. Android Studio's JBR works:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :app:assembleDebug          # debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- The Gradle wrapper is pinned to **8.9**; run builds through `./gradlew`, not a globally installed `gradle`.
- Debug builds are self-signed and sideloadable. There is no release signing config — this app is distributed as an APK, not shipped to Play.
- Handy loop while iterating: `./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Single-activity Jetpack Compose, MVVM. One screen, one `ViewModel` exposing a single immutable `UiState` via `StateFlow`.

```
domain/   Pure model + config. No Android UI deps.
data/     LogoRepository — lists/decodes bundled logos from assets.
media/    All pixel work. Placement (geometry), ImageWatermarker (Canvas),
          VideoWatermarker (video pipeline), MediaSaver (MediaStore output).
ui/       WatermarkScreen (Compose), WatermarkViewModel, Theme.
```

Data flow: pick media → pick logo/colour → choose position/size/nudge → live preview → export → MediaStore + share sheet.

## Conventions

- **Kotlin, Compose, Material 3.** Keep new UI in Compose; keep `media/` free of Compose/`Context`-heavy leakage where practical (pass `Context` in, return bitmaps/URIs out).
- **State is single-source.** UI reads `UiState`; the ViewModel is the only writer (`_state.update { ... }`). Don't add parallel mutable state in composables beyond transient UI (scroll, etc.).
- **Heavy work off the main thread.** Image compositing and file I/O run on `Dispatchers.Default`/`IO`. The one exception is the video `Transformer`, which is driven from the main thread because it needs a `Looper`.
- **Fail soft.** Export paths catch exceptions and surface an error in `UiState` rather than crashing. When adding a new export step, keep it inside the existing try/catch.

## Things worth knowing (learned the hard way)

- **Placement is centralised.** `media/Placement.kt` is the single source of truth for where the logo lands (position + size fraction + x/y offset). The Compose preview, the image compositor, and the video overlay must all agree with it, so change geometry there, not in three places. Instagram-safe margins live in `domain/InstagramMargins` as fractions of the media dimensions — tweak those, not hardcoded pixels.
- **Logo assets are pre-trimmed to their alpha bounding box.** The brand source files are art centered inside a large transparent canvas; if you drop in a new logo with big transparent padding, placement and the size slider will look "off" (art floats toward center). Trim transparent borders first (`magick logo.png -trim +repage out.png`) and keep it PNG with alpha.
- **Don't resample logos with a ringing filter.** Downscaling logo art with a sharpening/windowed-sinc filter (e.g. Lanczos) leaves a visible halo/"border" on edges. Prefer the originals, or a bilinear/triangle filter. Runtime scaling already uses bilinear.
- **Video overlay anchors are normalized to `[-1, 1]`.** The video overlay pins the logo using anchor coordinates that must stay within that range or the media library throws. Any new offset/placement math must clamp — see `VideoWatermarker.buildOverlay`.
- **Logo Crispness and Downscaling.** Downscaling high-resolution logo assets (like the 3520px PNGs) directly to small target sizes (e.g. 237px) in a single step causes aliasing and blurriness. We use `BitmapScaler.scaleHighQuality` to progressively downscale the logo on the CPU (halving the dimensions iteratively, acting like mipmapping) to the *exact* target dimensions before drawing.
  - **Images (`ImageWatermarker.kt`)**: The logo is scaled to target dimensions, and its coordinates are rounded to integer pixels (`Math.round(...)`) before drawing. This ensures 1-to-1 pixel alignment to the canvas and avoids subpixel rendering blur.
  - **Videos (`VideoWatermarker.kt`)**: The logo is scaled to the exact output dimensions on the CPU and passed to `BitmapOverlay` with a scale factor of `1.0f`. This forces a 1-to-1 pixel copy in the OpenGL shader, completely avoiding minification blurriness/aliasing (since Media3's rendering pipeline does not generate mipmaps for dynamic overlays).
- **Video re-encoding is required** (you cannot overlay without rendering). The goal is to preserve resolution / fps / aspect / duration / audio and keep visual loss minimal — audio is transmuxed (copied), not re-encoded. If you touch the encoder settings, keep that contract.
- **Edge-to-edge insets.** The activity draws edge-to-edge; content uses `safeDrawingPadding()` so controls clear the status bar and the gesture-nav area. Remember insets when adding bottom-anchored UI.
- **Preview never re-decodes on placement change.** The source bitmap / first video frame is decoded once when media is picked; size/position/colour/nudge only redraw the Compose overlay. Keep it that way — re-decoding on every slider tick caused layout jumps.

## Adding things

- **A new logo:** trim it, drop the PNG into `app/src/main/assets/logos/`. `LogoRepository` lists the folder automatically.
- **A new logo colour:** add an entry to the `LogoTint` enum in `domain/Models.kt` (label + ARGB int). UI and both export paths pick it up.
- **A new position:** add to the `WatermarkPosition` enum and handle it in `Placement.compute` and `VideoWatermarker.buildOverlay` (the preview reads Placement).
