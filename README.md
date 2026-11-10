# Astra — a dark, minimal Android TV launcher

Astra is a remote-first (D-pad) launcher for Android TV / Android TV boxes. It replaces the stock
Google TV home with a calm, dark surface: pinned apps, a detected media row and a tools row over your
own photo wallpaper, a live system strip, and fast access to everything through a quick panel and
search.

**Current version: 1.6** · [Changelog](CHANGELOG.md)

Built for and tested on a **XS97_S3_PRO** box (Rockchip `rk3518_box_32`, Android 14 / SDK 34,
1920×1080 @ 240 dpi) — a generic Android TV device with Google TV Launcher preinstalled.

![Astra home on device](docs/screenshots/phase-1/01-home-rest.png)

---

## Contents

- [Why](#why)
- [Screens](#screens)
- [Features](#features)
- [How it works](#how-it-works)
- [Design system](#design-system)
- [Build](#build)
- [Install](#install)
- [Make it the default home](#make-it-the-default-home)
- [Keep the screen awake](#keep-the-screen-awake)
- [Interactive prototype](#interactive-prototype)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)
- [Known limitations](#known-limitations)
- [Changelog](CHANGELOG.md)
- [License](#license)

---

## Why

Stock TV launchers work well for a TV with Google services wired into every row, but they are noisy:
recommendation rails mix with ads, the app list is buried, and the remote has to travel far for basic
tasks. Astra is the opposite — an explicit, honest surface:

- **Three presses to intent.** Pinned apps, media apps and the app list are one row away.
- **Calm chrome.** Near-black surfaces, one restrained accent, no autoplay billboards.
- **Deterministic focus.** Visible focus ring, predictable traversal, Back always returns somewhere.
- **Honest.** No fabricated recommendations. Installed apps and real intents only.

## Screens

| Concept | Prototype | On device |
|---|---|---|
| ![](docs/screenshots/01-concept-home.jpg) | ![](docs/screenshots/06-prototype-home.png) | ![](docs/screenshots/phase-1/01-home-rest.png) |
| ![](docs/screenshots/02-concept-apps.jpg) | ![](docs/screenshots/07-prototype-apps.png) | ![](docs/screenshots/phase-2/02-library-all.png) |
| ![](docs/screenshots/03-concept-quick-panel.jpg) | ![](docs/screenshots/09-prototype-panel.png) | ![](docs/screenshots/16-device-panel-network.png) |
| ![](docs/screenshots/04-concept-search.jpg) | ![](docs/screenshots/08-prototype-search.png) | ![](docs/screenshots/12-device-search.png) |

- **Home** — pinned row, streaming/media row and a tools row, with a live system strip (throughput, CPU, RAM, free storage), today's date, clock, search and panel in the top bar. The quick-start card is opt-in (Settings → Home card, default off) because the pinned row already covers launch targets. Rows always end on a gap, never through a card.
- **Apps** — sections for **All**, **Favorites** (pinned, in pin order), **Recently used** (local usage stats, only after the user grants usage access), **Media**, **Tools** and **System**; an eight-across grid using the same card as Home; long-press for a context menu (Open / Pin / Unpin / App info). Category rules are documented in [docs/APP_CATEGORIES.md](docs/APP_CATEGORIES.md).
- **Quick panel** — a scrollable sheet grouped into Connection, Device, Launcher, Power and System. Network, date/time, display mode, audio output and storage are read from the device and refresh while the sheet is open; a control the device cannot service is dimmed and says “Not available” instead of pretending. Every row closes the panel when it acts, Back returns focus to the tile that opened it, and nothing is ever clipped off the bottom.
- **Search** — filters installed apps as you type, launches them, and shows an empty state when nothing matches.
- **Screensaver / ambient** — after an idle delay: a thin clock over the Astra aurora or your own photo folder, optional pure-black mode; any key returns to the launcher.
- **Settings** — screensaver timeout and style, 12/24-hour clock, wallpaper source, preview and reset.

Screensaver and settings:

| Aurora | Black | Settings | Photo folder |
|---|---|---|---|
| ![](docs/screenshots/18-saver-aurora.png) | ![](docs/screenshots/19-saver-black.png) | ![](docs/screenshots/20-settings.png) | ![](docs/screenshots/21-saver-photos.png) |

| Home photo picker |
|---|
| ![](docs/screenshots/23-home-photo-picker.png) |

The same photo source backs the launcher itself:

| Home with a photo wallpaper |
|---|
| ![](docs/screenshots/22-home-wallpaper.png) |

Sample wallpapers (drop them into `Pictures/Astra`):
[`aurora-lake.jpg`](docs/wallpapers/aurora-lake.jpg) · [`fog-ridges.jpg`](docs/wallpapers/fog-ridges.jpg)

Before/after:

| Before (Google TV Launcher) | After (Astra, default home) |
|---|---|
| ![](docs/screenshots/00-before-google-tv-launcher.png) | ![](docs/screenshots/13-device-default-home.png) |

## Features

- Registers as `HOME` + `LEANBACK_LAUNCHER`, so it can replace the stock launcher.
- Enumerates **both** Leanback and classic launcher activities (`CATEGORY_LEANBACK_LAUNCHER` and
  `CATEGORY_LAUNCHER`), deduplicated per package, so side-loaded apps such as Stremio, SmartTube,
  Plex, Prime Video, Disney+ and Netflix all appear.
- Pinned apps and their order are persisted in `SharedPreferences` (`astra` / `pins`); long-press any
  tile to pin or unpin it, and the home row updates immediately.
- Real app icons, rendered at 40 dp inside rounded containers; every Home shelf and the library use the same card size and focus treatment.
- D-pad focus ring (2 px accent + 1.03 scale + elevation), D-pad focus restore after a context menu or
  screen rebuild, rows scroll horizontally to keep the focused tile in view.
- **System strip in the top bar** — connection type plus `Net ↓` throughput, CPU load, available/total RAM and free
  data storage, refreshed every 3 s and stopped whenever the launcher is not visible. Throughput is device-wide
  (`TrafficStats` totals), which is why it is labelled `Net` rather than Wi-Fi; every value degrades to `—` if the
  kernel or provider refuses, and a spoken description is attached for TalkBack.
- **Idle screensaver** after a configurable delay (Off / 1 / 2 / 5 / 10 / 20 min): a thin clock and date over a
  photo slideshow or a procedural ice-blue aurora, with a pure-black OLED mode. Any key or tap returns and
  restores focus. Timers only run while Astra is in front, so it never interrupts a playing app.
- **Photo wallpaper** — the same source drives the launcher backdrop *and* the screensaver: a static, dimmed
  scenic photo behind the rows (choose it with Settings → Home photo, a D-pad thumbnail grid; defaults to the
  first file), or the procedural ice-blue aurora. Images are read from
  `Pictures/Astra` (or `DCIM/Astra`) through MediaStore, decoded to 16-bit at ≤1400 px and capped at 8; the aurora is the
  fallback when the folder is empty or unreadable.
- **Astra Settings** — home photo picker, home card on/off, screensaver timeout and style, 12/24-hour clock,
  wallpaper source, preview, reset.
- Quick panel opens on `Back` from Home (or the `≡` button) and restores focus when closed.
- Search overlay with live filtering, a designed empty state, and launch on Enter.
- `FLAG_KEEP_SCREEN_ON` while the launcher is visible.
- **Sleep** turns the display off via `DevicePolicyManager.lockNow()` — one-time device-admin activation,
  no root. **Power** opens a Reboot / Shut down / System settings dialog; reboot and shutdown run through
  `su` and report honestly when root is unavailable.

## How it works

Single Activity, no AndroidX, no third-party dependencies — plain framework views, which keeps the
APK at ~58 KB. Design tokens live in `Palette`, focus behaviour in `FocusSurface`, and the app card in
`AppCardView`, so Home and the app library share one look without growing `MainActivity` further.

| Concern | Implementation |
|---|---|
| App discovery | `AppRepository`: both launcher categories deduped and sorted on a worker thread, categories and local recency resolved once per load; screens read immutable snapshots |
| Categories | one rule — media package token, else preinstalled = system, else tools ([docs/APP_CATEGORIES.md](docs/APP_CATEGORIES.md)) |
| Home list | `ScrollView` → vertical `LinearLayout`; rows are `HorizontalScrollView` + `LinearLayout` |
| App grid | eight tiles per row at the Home card size, weight-based, inside a `ScrollView` (no `GridView` focus quirks); same `AppCardView` as Home |
| Home shelves | Fixed card width — eight across the content width — so short rows keep the same card size; rows scroll only beyond that |
| Focus | `FocusSurface`: 2 dp accent ring, `card-hi` fill, 1.03 scale and 10 dp elevation; the rest background is captured and restored exactly on blur |
| Quick panel | `FrameLayout` overlay: scrim + right sheet with a fixed header and a scrollable body; `QuickPanelRow` per line, `translationX` animation |
| Sleep | `DevicePolicyManager.lockNow()` with a minimal `force-lock` device admin |
| Power | dialog → `su 0 sh -c reboot` / `reboot -p`, system-settings fallback |
| Search | `EditText` + `ListView` with a filtering inner adapter |
| Background | Custom `Drawable` painting a near-black fill plus one radial ice-blue glow |

Screens are rebuilt on demand (`buildHome()`, `showApps()`), and a `screen` field keeps Back
deterministic: panel → search → apps → home → panel.

## Design system

Full tokens, components and focus rules live in [DESIGN.md](DESIGN.md).

| Token | Value | Use |
|---|---|---|
| `bg` | `#0B0D10` | background |
| `raised` | `#12151B` | panels, top bar |
| `card` | `#1A1F27` | tiles |
| `card-hi` | `#212734` | focused tile |
| `line` | `#252B36` | hairline borders |
| `text` | `#F5F7FA` | primary |
| `text-2` | `#A9B2C3` | secondary |
| `text-3` | `#6F7A8C` | muted |
| `accent` | `#7CC4FF` | focus ring, active state only |
| `accent-pressed` | `#3D8BFF` | pressed depth |

Ten-foot typography: 30 sp screen titles, 28 sp hero title, 13–15 sp labels, 11–12 sp meta.
Focus always carries three signals (ring + scale + elevation), never colour alone.

## Build

Requirements: JDK 17+, Android SDK with `platforms;android-34` and `build-tools;34.0.0`.

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug
# or, with a Gradle wrapper you generate once:
# gradle wrapper --gradle-version 8.9 && ./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

## Install

```bash
adb connect <device-ip>:<port>
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.astra.launcher/.MainActivity
```

The app also appears in the app list as **Astra**.

## Make it the default home

Changing the preferred home activity alone is not enough on Android TV — the home task stays bound
to the stock launcher until it is disabled. Reversible sequence used on the test device:

```bash
adb shell cmd package set-home-activity com.astra.launcher/.MainActivity
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Revert:

```bash
adb shell pm enable com.google.android.tvlauncher
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

Astra must remain installed while the stock launcher is disabled; if you uninstall it, re-enable
Google TV Launcher first.

## Keep the screen awake

While iterating, keep the box from sleeping:

```bash
adb shell settings put global stay_on_while_plugged_in 15
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 2147483647
```

Astra additionally holds `FLAG_KEEP_SCREEN_ON` while visible.

## Interactive prototype

`prototype/index.html` is a dependency-free HTML prototype of the same design, useful for reviewing
focus behaviour on a desktop before touching a device.

```bash
xdg-open prototype/index.html      # Linux
# or serve it: python3 -m http.server 8080
```

Routes: `#home`, `#apps`, `#search`, `#panel`.
Keys: arrows move (geometric nearest-focus), `Enter` select, `Esc`/`Backspace` back, `P` quick panel,
right-click opens a context menu.

## Project structure

```
AstraLauncher/
├── README.md
├── DESIGN.md                     # tokens, components, screen specs, Android notes
├── docs/APP_CATEGORIES.md        # discovery and category rules
├── LICENSE
├── android/                      # Android Studio / Gradle project (Java, no AndroidX)
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/astra/launcher/MainActivity.java
│           ├── java/com/astra/launcher/AppRepository.java # discovery, categories, recent
│           ├── java/com/astra/launcher/AppEntry.java      # immutable resolved app
│           ├── java/com/astra/launcher/Palette.java       # tokens + shared drawables
│           ├── java/com/astra/launcher/FocusSurface.java   # focus ring/scale, rest restore
│           ├── java/com/astra/launcher/AppCardView.java    # Home/library app card
│           ├── java/com/astra/launcher/SaverView.java
│           └── res/values/{colors,styles}.xml, res/drawable/banner.xml
├── prototype/index.html          # D-pad-navigable HTML prototype
└── docs/screenshots/             # concept, prototype and on-device captures
```

## Roadmap

Phase 1 and Phase 2 of the UI/UX review are done (persisted pins, Pin/Unpin menu, focus restore, live
network status, empty states, Astra Settings, ambient screensaver). Next:

- Reorder pinned apps with the D-pad, and show/hide individual Home rows.
- Search across content providers (deep links into Stremio/Plex/YouTube) instead of apps only.
- Optional "Continue" row driven by `UsageStatsManager` (with graceful degradation when access is denied).
- Read-only home-lab status row (NAS, server, AdGuard, VPN).
- Compose for TV rewrite once the interaction model is frozen.

## Known limitations

- Media detection is package-token based (`AppRepository.MEDIA_TOKENS`) — documented, maintainable, but still a local heuristic. A preinstalled sample package labelled “All Apps” (`com.example.all_apps`) shares a name with the launcher's All apps tile.
- **Recently used** needs the system usage-access app-op (Settings → Recently used apps); without it the section is hidden. Data stays on the device and is read on a background thread.
- The Home media shelf deliberately repeats pinned apps: PINNED is a shortcut shelf, the media row is the category. Phase 4 decides whether both stay.
- Sleep needs a one-time device-admin activation; it is inert while `stay_on_while_plugged_in` is non-zero.
- Reboot/shut down need a usable `su` for the app UID. On the reference box `/system/xbin/su` is
  `root:shell 4750`, so app-initiated root fails and the dialog falls back to system settings.
- Home rows are not user-reorderable yet; pins can be pinned/unpinned but not rearranged.
- Photo wallpapers need `READ_MEDIA_IMAGES` and must be indexed by MediaStore — copy them with a file manager or
  trigger a media scan (`adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///...`),
  because `adb push` alone does not index. Without photos the aurora is used. Photos are downscaled to 1280 px
  and capped at 8 (roughly 17 MB for the whole set) to bound memory. The set is only re-decoded when the MediaStore
  collection changes, not on every resume.
- The screensaver is an in-activity overlay, not a system dream service, so it only appears while Astra is in front;
  it is torn down on pause so it can never linger over another app.
- Quick panel exposes system settings intents, which vary by OEM; on the reference box `HDMI input` has no handler and is shown as unavailable, and the panel reports the display's real mode (30 Hz on this TV).
- Disabling Google TV Launcher also removes its search/shop surfaces; revert if you want them back.

## License

MIT © 2026 Amit Bhatnagar — see [LICENSE](LICENSE).
