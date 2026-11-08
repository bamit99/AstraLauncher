# Changelog

All notable changes to Astra. The version below matches `versionName` in
`android/app/build.gradle`; the app shows it in Settings → About.

## [1.6] — 2026-11-08

Reliability pass from the v1.5 source review (priority 0 items).

### Changed
- Wallpapers decode to RGB_565 at ≤1400 px and cap at 8 — roughly 17 MB for a full set instead of ~34 MB.
- The photo set is only re-decoded when the MediaStore collection changes (id + `DATE_MODIFIED` signature),
  so resuming no longer rescans or re-decodes.
- The throughput figure is labelled `Net ↓` because `TrafficStats` is device-wide, and the strip carries a
  spoken description for TalkBack.
- CPU needs three measurement windows before it displays, so app-start work no longer shows as a fake 100%.

### Fixed
- Android 14 selected-photo access counts as access: `READ_MEDIA_VISUAL_USER_SELECTED` is requested and
  honoured, and a partial grant re-queries instead of looping on the permission prompt.
- Revoking photo access while backgrounded falls back to the aurora and clears stale photo state.
- The screensaver is torn down on pause — overlay, animation and clock — so it cannot linger over another app.
- `TrafficStats` counter resets no longer produce negative or absurd rates.

## [1.5] — 2026-11-08

### Added
- Live system strip in the top bar: connection type, receive rate (`TrafficStats` deltas), CPU load
  (`/proc/stat`), available/total RAM (`ActivityManager`) and free `/data` storage (`StatFs`),
  refreshed every 3 s and stopped while the screensaver is up.

### Fixed
- The first CPU sample always read ~100% because it included app startup; the strip now shows `—`
  until two measurement windows exist, then smooths with a 0.6/0.4 EMA.
- Values that the kernel or a provider refuses degrade to `—` instead of showing a false number.

## [1.4] — 2026-11-08

### Added
- Static photo wallpaper behind the launcher, driven by the same source as the screensaver.
- Home photo picker (`Settings → Home photo`): D-pad thumbnail grid, selection stored as a MediaStore id.
- `TOOLS` row on Home (utilities only — no media apps, no vendor `System *` entries).
- Sample wallpapers in `docs/wallpapers/`.

### Changed
- Quick-start card is opt-in (`Settings → Home card`, default off); the pinned row already covers
  launch targets.
- Photo mode uses a lighter scrim and vignette than the aurora so the art is actually visible.

### Fixed
- Photos were read through `java.io.File`, which returns *Permission denied* under scoped storage, so
  the folder always looked empty. They are now read through MediaStore.
- Screensaver mixed absolute and elapsed time, so the cross-fade never completed and a half-dimmed
  layer stayed on screen.

## [1.3] — 2026-11-08

### Added
- Idle screensaver / ambient mode: thin clock, date and wordmark over a photo slideshow or a
  procedural ice-blue aurora; `Off / 1 / 2 / 5 / 10 / 20 min`; any key or tap returns.
- Black OLED mode for the screensaver.
- Astra Settings: home card, screensaver timeout and style, 12/24-hour clock, wallpaper source,
  preview, reset.
- Quick Panel rows for refreshing the app list and starting the screensaver.

### Fixed
- Quick Panel stayed open over launcher screens and covered their values; every action row now
  closes it.
- The aurora's base layer was exactly 16:9, so its slow drift exposed a black band at the top; the
  layers are now over-scanned.

## [1.0] — 2026-11-08

### Added
- First public build: Home, Apps, Search and Quick Panel; `HOME` + `LEANBACK_LAUNCHER` registration;
  enumeration of both leanback and classic launcher activities; real app icons.
- Pinned apps persisted in `SharedPreferences`, with an `Open / Pin / Unpin / App info` context menu
  on long-press.
- Live connection status (top bar and Quick Panel), search empty state, refresh app list.
- Sleep via a minimal `force-lock` device admin; Power dialog with Reboot / Shut down / System
  settings, root-gated and honest about it.
- Home rows sized so a whole number of cards ends on the right margin, plus horizontal scrolling
  that keeps the focused tile in view.

> Versions 1.1 and 1.2 were never tagged — that work landed inside the 1.0 development line before
> the first tagged build, 1.3.
