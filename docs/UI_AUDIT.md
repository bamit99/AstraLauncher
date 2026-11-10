# Astra — Phase 0 UI audit and baseline

**Date:** 2026-11-10
**Baseline commit:** `dd11b90` (`main`, clean working tree at start of audit)
**Version audited:** 1.6 (versionCode 6)
**Device:** `192.168.0.184:37787` — XS97_S3_PRO, `rk3518_box_32`, Android 14 (SDK 34),
1920×1080 px @ 240 dpi (1280×720 dp), 16 launchable apps, 7 pinned.

This document is the Phase 0 deliverable in `AstraLauncher_Phased_Implementation_Plan.md`.
No source changes were made for this phase; only this file and the screenshots in
`docs/screenshots/baseline/` were added.

---

## 1. Method

1. Read `README.md`, `DESIGN.md`, `CHANGELOG.md`, `AndroidManifest.xml`, both Gradle files.
2. Read the whole of `MainActivity.java` (2,255 lines) and `SaverView.java` (231 lines), tracing
   rendering, focus, back-navigation, app discovery, panel, settings, wallpaper and screensaver paths.
3. Clean baseline build with the repository's own toolchain (no upgrades).
4. Installed build verified byte-for-byte identical to the running 1.6 APK (sha256 match before the
   clean build).
5. Captured the required states on the device over wireless ADB at 1920×1080, driving the UI with
   `input keyevent` and verifying each focused element with `uiautomator dump`.
6. Measured cold start, PSS/RSS and frame timing.

Commands used (local toolchain — the Gradle wrapper download is broken on this machine, see §10):

```bash
cd android
ANDROID_HOME=/home/amit/android-sdk /home/amit/gradle-8.9/bin/gradle clean assembleDebug --offline
adb -s 192.168.0.184:37787 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.0.184:37787 shell screencap -p /sdcard/shot.png
adb -s 192.168.0.184:37787 shell dumpsys gfxinfo com.astra.launcher
```

---

## 2. Baseline results

### Build

| Item | Value |
|---|---|
| Command | `gradle clean assembleDebug --offline` (Gradle 8.9, AGP 8.5.2, compileSdk 34, Java 17) |
| Result | **BUILD SUCCESSFUL in 3s**, 34 tasks |
| Warnings | 1 javac note: `MainActivity.java uses or overrides a deprecated API` (`setSystemUiVisibility` path) |
| APK | `app-debug.apk` — **57,536 bytes (56.2 KiB)** |
| APK sha256 | `c0384ce7adb67c9694eb9ae26307af08cb5958507a3bbb8dff55f8cf6ec3b293` |
| Installed 1.6 APK | 57,538 bytes, sha256 `c3cc0cfa…bd6995` — identical code, different zip metadata (APK is not byte-reproducible; expected, not a defect) |

### Runtime (device)

| Metric | Baseline |
|---|---|
| Cold start (`am force-stop` → system HOME relaunch, `Displayed`) | **+2.479 s, +2.479 s, +2.502 s** (3 runs) |
| Warm resume (Home key from Settings) | not measured — no `Displayed` log for task resume |
| Memory after settle | **TOTAL PSS 73,903 KB**, RSS 144,532 KB, native heap 27,668 KB |
| Focus/scroll smoothness (12 D-pad steps across a row) | 37 frames, **11 janky (29.73%)**, 50th pct **32 ms**, 90th pct **53 ms** |
| Home tiles rendered | 21 tiles for 16 installed apps |
| Background work | CPU/RAM/Net strip every 3 s while resumed, stopped in `onPause` (verified in code); clock every 15 s; photo decode on a worker thread |

Frame budget at 60 Hz is 16.7 ms, so the measured 50th-percentile 32 ms means D-pad focus movement
is running at roughly 30 fps on this box. That is the number Phase 6 has to beat.

### Screenshots (`docs/screenshots/baseline/`)

| File | State |
|---|---|
| `01-home-first-tile-focused.png` | Home, default entry focus (first pinned tile) |
| `02-home-media-tile-focused.png` | Home, media row focused |
| `02b-home-all-apps-focused.png` | Home, "All apps" tile focused (what `Down` from the top bar lands on) |
| `03-apps-all.png` | App library, `All` (16 apps) |
| `03b-apps-system.png` | App library, `System` (8 apps) |
| `04-quick-panel-default.png` | Quick panel, Network focused |
| `04b-panel-focus-offscreen.png` | Quick panel after 12× `Down` — **focus is off-screen** |
| `04c-panel-refresh-focused.png` | Quick panel, `Refresh app list` focused (last reachable row) |
| `05-search.png` | Search overlay, empty query |
| `06-settings.png` | Astra Settings, row 0 focused |
| `06b-settings-after-focus.png` | Settings after moving focus: rows 0–1 keep a different surface than 3–8 |

Note: a true "no item focused" home state does not exist in the current implementation —
`buildHome()` always requests focus (`MainActivity.java:1254`). The idle capture is therefore
"default entry focus", which is what the device actually shows after boot or Home.

---

## 3. Feature map

| Feature | Where | Verdict | Notes |
|---|---|---|---|
| `HOME` + `LEANBACK_LAUNCHER` entry | `AndroidManifest.xml:26-35` | keep | singleTask, landscape, config-change handled |
| App discovery (leanback + classic, deduped) | `loadApps()` 1021, `collect()` 1035 | improve | runs twice per refresh on the UI thread; `loadIcon` per app, no cache |
| App launching | `launch()` 2051 | keep | honest `ActivityNotFoundException` toast |
| Pinning / unpinning | `loadPins()` 1261, `showAppMenu()` 2069, `savePins()` 1273 | improve | order is insertion order, no reorder, no duplicate guard |
| Categories (All/Media/Tools/System) | `categoryApps()` 1561, `isMedia()` 1061, `isTool()` 1278 | refactor | media = package-name substring list; tools = "everything not media" |
| Home shelves | `buildHome()` 1107 | improve | 3 fixed rows, duplicate content (see F-4) |
| Row sizing policy | `fitRow()` 1380 | keep | whole-cards-per-row, 120–170 dp, documented |
| App cards | `appTile()` 1404 | refactor | home card 170×104 dp/r18; grid reuses it with 112 dp height and weight width |
| Focus styling | `applyFocus()` 2146 | refactor | one hard-coded surface swap for every component (see F-2) |
| Top bar / system strip | `buildTopBar()` 1292, `updateSys()` 904 | improve | 12–14 sp, one long line, 3 s polling |
| Search | `buildSearch()` 1594, `filterApps()` 1691 | improve | flat overlay, no input focus ring, adapter ignores `convertView` |
| Quick panel | `buildPanel()` 1725, `panelActionRow()` 1802 | refactor | **overflows the screen** (F-1), several static values (F-3) |
| Astra Settings | `showSettings()` 564, `settingRow()` 836 | improve | 8 rows, Left/Right cycling with no affordance |
| Wallpaper (aurora / photo) | `GlowDrawable` 2233, `applyHomeWallpaper()` 1076, `loadPhotos()` 481 | keep | off-thread decode, 565 @ ≤1400 px, capped at 8, signature-gated |
| Photo picker | `showPhotoPicker()` 706 | keep | 4-up thumbnail grid, selection check |
| Screensaver / ambient | `SaverView.java`, `showScreensaver()` 349 | improve | per-frame `RadialGradient` allocation (F-11) |
| Sleep | `sleepDevice()` 1840 | keep | `lockNow()` behind one-time device-admin |
| Power / reboot | `showPowerDialog()` 1895, `runRoot()` 1976 | defer | root-gated, honest fallback; no UI change needed this round |
| Context menu | `showAppMenu()` 2069 | keep | Open / Pin / Unpin / App info |
| Back behaviour | `onBackPressed()` 2034 | keep | panel → search → picker/settings/apps → home → panel |
| Focus restore after rebuild | `focusTile()` 2132, `settingsIndex` 562 | improve | package tag lookup only; settings uses an int index, home always resets to tile 0 |

---

## 4. Findings

Severity: **P0** blocks Phase 1 exit, **P1** must be fixed inside its phase, **P2** polish.

### F-1 (P0) Quick panel is taller than the screen; Sleep and Power are unreachable

`buildPanel()` creates 13 rows of 52 dp plus a 60 dp profile block, two 21 dp dividers and 54 dp of
vertical padding inside a `MATCH_PARENT` `LinearLayout` with no scrolling container:

```
54 (padding) + 60 (profile) + 13×52 (rows) + 2×21 (dividers) = 832 dp  >  720 dp viewport
```

112 dp (~168 px) of content is cut off. On device, `Screensaver` is clipped and `Sleep` / `Power`
are not drawn at all, yet focus still moves into them — `04b-panel-focus-offscreen.png` shows focus
on an invisible row (no ring anywhere on screen). The panel is therefore the only route to Sleep and
Power, and both are unreachable by remote. This directly violates "No dead ends" and "Focus never
becomes lost behind or outside the panel" (Phase 3 exit criteria).

### F-2 (P0) Blurring a row permanently repaints it with the wrong surface

`applyFocus()` (`2146`) always writes `rounded(focused ? CARD_HI : CARD, …)`. Rows that are built with
a different rest surface — settings rows use `RAISED` (`836`) and panel rows have no background
(`1802`) — are repainted with `CARD` the first time they lose focus. After one pass over Settings,
rows 0–1 are visibly darker than 3–8 (`06b-settings-after-focus.png`), and every visited panel row
gains a filled background. This is a state bug, not a taste issue: focus must not mutate rest state.

### F-3 (P1) Quick panel shows stale and invented values

`buildPanel()` runs once in `onCreate()`, so:

- `Date & time` is rendered at process start and never refreshed — the capture shows
  `Sat 28 Mar · 11:36` while the top bar clock reads `19:17`.
- `Display 1080p · 60 Hz`, `Sound TV speakers` and `Storage Manage space` are string literals with no
  read of the real display mode, audio route or storage figure, although the same data is already
  measured for the top bar (`updateSys()`).
- `Ethernet · Not connected` is real but only refreshed on clock ticks/panel build.

This is the "honest surfaces" rule from `DESIGN.md` §1.5, and Phase 3 requires every visible control
to have a verified action or a clear unavailable state.

### F-4 (P1) Home content is duplicated, and the "Tools" row is not tools

With 16 installed apps, Home renders 21 tiles: 7 pinned + 8 media + 5 tools + "All apps".

- All 7 pinned apps reappear in `STREAMING & MEDIA` (the row is built from `isMedia()` over the same
  list), so the first two rows show the same seven icons in a different order plus Media Center.
- `TOOLS` contains Play Store, Chrome and Settings — general apps, not utilities.
- A package installed as `com.example.all_apps` is labelled **"All Apps"** and appears in the library
  and search results next to the launcher's own `All apps` tile, so two different things share a name.

### F-5 (P1) Cold start is 2.5 s, and the UI thread does the work

`onCreate()` calls `loadApps()` (`168`), which performs two `queryIntentActivities` passes,
`loadLabel` and `loadIcon` for every package, then sorts, then `buildHome()` constructs all 21 tiles
with fresh `GradientDrawable` backgrounds and `ImageView`s. `categoryApps()` repeats a
`getApplicationInfo` call per app on every `showApps()`. Nothing is cached between refreshes and there
is no icon cache. 2.5 s to first frame on a low-end box is the largest single UX liability.

### F-6 (P1) Search is visually disconnected from the rest of the product

`searchOverlay` is painted flat `BG` (`1597`), dropping the aurora/photo backdrop and the top bar; the
`EditText` has no focus treatment at all (it is the entry focus, `1713`), rows have no separators
(`1665`), and the adapter ignores `convertView` (`1635`). There is no hint that `Back` exits.

### F-7 (P1) App library geometry and focus order drift from Home

- 7 columns are hard-coded twice (`1526`, `1533`); tiles are `112 dp` tall with `weight=1` widths,
  versus Home's `170×104 dp` fixed cards, so the two grids do not look like the same component.
- `Up` from the first library tile lands on the **Media** chip (spatially nearest), not `All`.
- Category chips are plain `TextView`s (`1581`); the active state is colour-only.

### F-8 (P2) Design-system drift between `DESIGN.md` and the implementation

| Token / spec | `DESIGN.md` | Code |
|---|---|---|
| Screen title | 40 sp | 30 sp (`1493`, `581`) |
| Metadata | 18 sp | 11–14 sp (`1314`, `1816`, `1344`) |
| Panel radius | 28 px | 24 dp (`2201`) |
| Card radius | 20 px | 18 dp on tiles (`1412`), 14/16 elsewhere |
| Focus ring | 2 px + 4 px offset | 2 dp stroke, no offset |
| Panel backdrop | 24 px blur | flat scrim `0xA8000000` (`1730`) |
| `success` / `warning` / `danger` | defined | unused |
| TalkBack description on the system strip | claimed in `README.md` | no `contentDescription` is set |

The tokens themselves are correct in code (`84-93`) but are duplicated as integer literals in ~40
call sites instead of one palette object.

### F-9 (P2) `MainActivity` is 2,255 lines and duplicates its own primitives

There are three near-identical "row" builders (`settingRow`, `panelActionRow`, `dialogButton`), two
tile builders (`appTile` used twice with different layout params), three background factories
(`rounded`, `shaped`, `heroBg`) and two focus handlers (`applyFocus`, `applyFocusButton`). Ten UI
sections share one class. This is where Phase 1's component extraction should start — but only for
components that are used by more than one screen.

### F-10 (P2) Accessibility gaps

`contentDescription` exists on tiles and settings rows only; chips, panel rows and the system strip
have none. No state is announced (pinned / selected / current value). Focus is conveyed by ring,
scale and elevation, which is correct, but the active chip and the selected photo rely on colour
alone.

### F-11 (P2) Screensaver allocates in `onDraw`

`drawVignette()` (`SaverView.java:185`) constructs a new `RadialGradient` on every frame while the
screensaver is up (22 s per photo, indefinitely), and `drawStars()` issues 110 `drawCircle` calls per
frame. Both are cheap to hoist.

---

## 5. Risks for later phases

- **No focusable-view inventory.** Focus traversal is entirely framework-implicit; every phase must
  re-verify with `uiautomator dump` (element bounds + `focused="true"`), not by eye.
- **Panel is a `FrameLayout` overlay owned by the activity**, not a screen, so panel work touches
  shared state (`panelOpen`, `hidePanel(false)` from `showSettings`) — refactor carefully.
- **Home is rebuilt wholesale** on every return (`showHome()` → `buildHome()`), so any state added to
  Home (scroll position, focused row) needs an explicit restore path or it will be lost.
- **Photo wallpaper memory** is the current PSS floor (73.9 MB) — do not add a second full-size
  bitmap path; reuse `saverPhotos`.
- **Device build fingerprint is spoofed** (`ro.build.display.id` reports `google/redfin/redfin:13/…`
  while `ro.build.version.release` is 14), so OEM-specific settings intents and vendor apps cannot be
  inferred from the fingerprint.
- **Gradle wrapper is unusable offline** (release download 504s); every build must use
  `/home/amit/gradle-8.9/bin/gradle` with `ANDROID_HOME=/home/amit/android-sdk`.

---

## 6. Files likely to change (by phase)

| Phase | Files |
|---|---|
| 1 Home | `MainActivity.java` (`buildHome`, `buildTopBar`, `sectionHeader`, `appTile`, `applyFocus`, `fitRow`), new `ui/` component classes (e.g. `AppCardView`, `RowHeaderView`), `DESIGN.md` |
| 2 Library | `MainActivity.java` (`loadApps`, `collect`, `categoryApps`, `showApps`, `chipView`, `showAppMenu`), new `AppRepository`-style helper, `docs/UI_AUDIT.md` category rules |
| 3 Panel | `MainActivity.java` (`buildPanel`, `panelRow`, `panelActionRow`, `showPanel`, `hidePanel`, `updateNetwork`), new `QuickPanelRowView` |
| 4 Context | `MainActivity.java` (focus info view, shelf construction), `DESIGN.md` |
| 5 Consistency | all of the above, `SaverView.java` (vignette/stars), `settings.xml`-level resources if tokens move to `colors.xml` |
| 6 Validation | `README.md`, `DESIGN.md`, `CHANGELOG.md`, `app/build.gradle` (version bump only) |

---

## 7. Phase 0 exit criteria

- [x] Repository, build configuration, manifest, resources and docs inspected.
- [x] Home rendering and focus/navigation paths traced.
- [x] Existing implementations located for every feature in the phase plan (§3).
- [x] Main-thread work, repeated package queries, repeated icon decoding and view recreation identified (§4 F-5, F-9).
- [x] Clean baseline build with the existing toolchain recorded (§2).
- [x] Baseline screenshots at 1920×1080 for Home (idle + focused), library, panel, search, settings.
- [x] Baseline APK size and build warnings recorded.
- [x] No source changes beyond audit documentation and the screenshot folder.

**Recommended first Phase 1 slice:** F-2 and F-1 are defects, not design work, and both are visible
in the very first frames of any new visual pass — fix them at the start of Phase 1 (focus primitives)
and Phase 3 (panel), and keep the rest of Phase 1 to Home surfaces only.
