# Astra — Design System & Handoff

**Surface:** Android TV launcher (10-foot UI), primary device XS97_S3_PRO, 1920×1080 @ 240dpi
(1280×720 dp, so 1 dp = 1.5 px).  
**Mode:** Operate (fast task completion; expression lives in restraint, focus craft, and motion).  
**Mood:** dark, sleek, minimal. Content art provides color; chrome stays quiet.  
**Status:** Phase 1 (Home visual foundation) implemented. Values below describe the shipped code,
not an earlier mockup; items still aspirational are marked **deferred**.

## 1. Design principles
1. **Three actions to intent.** Common destinations reachable in ≤3 D-pad presses.
2. **Content is the color.** Backgrounds/cards neutral; accent only signals focus/state/primary action.
3. **Deterministic focus.** Every screen defines entry focus, traversal order, and return focus.
4. **No dead ends.** Every screen reachable and exitable via Back; Home never traps exit ambiguously.
5. **Honest surfaces.** No fabricated recommendations; only installed apps and real intents.
6. **Focus never mutates rest state.** A blurred surface returns to exactly the surface it had before.

## 2. Tokens

All tokens live in `android/app/src/main/java/com/astra/launcher/Palette.java`; nothing else hard-codes
a colour or a shared radius.

### Color
| Token | Hex | Use |
|---|---|---|
| `bg/base` | `#0B0D10` | app background |
| `bg/raised` | `#12151B` | panels, settings rows |
| `bg/card` | `#1A1F27` | tiles, cards |
| `bg/card-hover` | `#212734` | focused card fill |
| `border/subtle` | `#252B36` | 1px dividers, card edges |
| `text/primary` | `#F5F7FA` | titles, focused labels |
| `text/secondary` | `#A9B2C3` | rest labels, clock |
| `text/muted` | `#6F7A8C` | shelf headers, metadata, system strip |
| `accent` | `#7CC4FF` | focus ring, active pill, primary CTA |
| `accent/pressed` | `#3D8BFF` | pressed state depth (**deferred**: no pressed style yet) |
| `accent/soft` | `rgba(124,196,255,0.16)` | focus fill tint for pills |
| `accent/line` | `rgba(124,196,255,0.40)` | pill hairline |
| `success` | `#3DDC97` | online/connected (**not yet used**) |
| `warning` | `#FFB020` | degraded state (**not yet used**) |
| `danger` | `#FF6B6B` | destructive actions (**not yet used**) |
| `scrim` | `rgba(0,0,0,0.66)` | panel/dialog backdrop |

Contrast: primary text on base ≥ 15:1; secondary ≥ 7:1; focus ring vs card ≥ 3:1.

### Typography (as implemented)
| Role | Size | Weight | Where |
|---|---|---|---|
| Screen title | 30 sp | 500 | Apps, Settings, Home photo |
| Hero title | 28 sp | 500 | opt-in Home card only |
| Wordmark | 20 sp | 500 | top bar |
| Clock | 15 sp | 400 | top bar |
| Card label | 13 sp | 400 | app/action cards |
| Row/body | 15–16 sp | 400 | settings + panel rows |
| Shelf header | 12 sp, tracking 0.18 | 500 | Home shelves |
| Metadata | 11–12 sp | 400 | dates, shelf meta, system strip |

Font: system default (Roboto / Noto Sans). No display faces — legibility over personality at 10 ft.
Labels are single-line with end ellipsis; no label wraps.

### Spacing & layout
- Base grid 8 dp. Rhythm: 8 / 12 / 14 / 16 / 24 / 26 / 32 / 64.
- Safe area: 64 dp horizontal (96 px at 240 dpi), 32 dp top, 24 dp bottom.
- Radii: card 20 dp, row 14 dp, panel 24 dp, chip 999 dp, icon well 10 dp.
- Card grid: 14 dp horizontal gap; shelf header 26 dp above, 12 dp below.
- Content max width: full bleed with 64 dp safe margins; Search is the only inset column.

### Elevation
- Card rest: 1 dp `border/subtle` outline, no shadow.
- Card focus: 2 dp `accent` ring + `bg/card-hover` fill + scale 1.03 + 10 dp elevation.
- Pill/button focus: 1 dp `accent/line` + `accent/soft` fill + scale 1.06.
- Panel: scrim only. Backdrop blur is **deferred** — it is not worth a frame on RK3518.

### Motion
| Interaction | Duration | Easing |
|---|---|---|
| Focus in | 150 ms | ease-out |
| Focus out | 120 ms | ease-out |
| Panel slide | 220 ms in / 180 ms out | decelerate |
| Screensaver fade | 700 ms in / 260 ms out | ease-out |

Respect system “remove animations”: replace transitions with instant state, keep the focus ring.

## 3. Focus system (remote/D-pad)
- **Ring:** 2 dp accent, no outer offset (a 4 px offset was specified but reads as a halo at this
  density and was dropped); never colour-only — scale and elevation accompany it.
- **Rest restore:** `FocusSurface` captures the rest background and elevation on attach and restores
  them verbatim on blur. A settings row that rests on `raised` stays `raised` after focus passes.
- **Traversal:** spatial order matches the visual grid; shelves scroll horizontally only when the row
  has more cards than fit.
- **Return focus:** Back restores the exact previously focused item per screen.
- **Long-press OK:** context menu (Open, Pin/Unpin, App info).
- **Back on Home:** opens the Quick Panel, never a silent exit.
- **Edge behavior:** focus clamps at row ends; no wrap-around. Horizontal scrolling keeps the focused
  card fully inside the content area.
- **Known gap:** dialogs currently open without a focused child, so the first D-pad press lands with
  no prior visible ring (audit finding F-12, Phase 5).

## 4. Screen specs

### Home (implemented)
- **Top bar (52 dp):** 8 dp accent dot + `Astra` wordmark left. Right, in order: date (`Tue 10 Nov`,
  12 sp muted), clock (15 sp secondary), live system strip (11 sp muted: `Wi-Fi · Net ↓21 Kb/s ·
  CPU 7% · RAM 0.7/1.5G · 215M free`), search glyph, panel glyph (40 dp targets). The strip samples
  every 3 s and stops whenever the launcher is not resumed.
- **Shelves:** `PINNED` (with a real count as header meta), `STREAMING & MEDIA`, `TOOLS` (only when it
  has items). Each shelf uses the same card size: eight cards across the content width, 14 dp apart,
  104 dp tall. A row with fewer cards ends earlier and leaves the remaining slots empty — cards never
  stretch to fill the row. Rows longer than eight scroll.
- **Card:** 20 dp radius, `bg/card` fill, 1 dp border; 40 dp icon clipped to 10 dp, 8 dp below it the
  label. The label is `text/secondary` at rest and `text/primary` while focused.
- **Hero:** opt-in quick-start card (Settings → Home card, default off). No resume claims, no
  fabricated art; collapses entirely when there is no media app.
- **First focus:** first pinned card on entry; the first shelf is immediately navigable.
- **Wallpaper:** aurora glow or a dimmed photo (70% + vertical scrim 0x7A→0xB8) behind everything.

### Apps (implemented, Phase 2 will restructure)
- Title + installed count; chips All / Media / Tools / System; 7-column grid at 104 dp tile height
  with 16 dp row gaps, using the same card component as Home.
- Long-press: Open, Pin/Unpin, App info. Entry focus: first tile.

### Quick Panel (implemented, Phase 3 will fix)
- Right sheet 340 dp wide, scrim `0xA8000000`, 52 dp rows: profile, Network, Ethernet, Date & time,
  HDMI input, Display, Sound, Storage, Launcher settings, Refresh app list, System settings,
  Screensaver, Sleep, Power.
- **Known defect (F-1):** 13 rows plus chrome exceed the 720 dp viewport; Sleep and Power are drawn
  off-screen while focus can still reach them. No scroll container yet.

### Search (implemented, Phase 5 will restyle)
- Full-screen overlay, single 20 sp field, one-column result list (icon, label, package), empty state
  “No apps match that search”. Live filtering; Down/OK moves into results; Back returns to Home.
- **Known gaps (F-6):** flat background (no backdrop), no focus ring on the field, no row separators.

## 5. Components

| Component | Where | Spec |
|---|---|---|
| `Palette` | `Palette.java` | tokens, `round()`, `focusCard()`, `focusPill()`, `clip()` |
| `FocusSurface` | `FocusSurface.java` | ring + scale + elevation, rest-surface capture/restore |
| `AppCardView` | `AppCardView.java` | 20 dp radius card, 40 dp icon well, 13 sp label; used by Home and library |
| Action tile | `MainActivity.actionTile()` | same card geometry, 40 dp glyph well |
| `RowHeader` | `MainActivity.sectionHeader()` | 12 sp/0.18 tracking title + hairline + optional right meta |
| `Chip` | `MainActivity.chipView()` | pill, 999 radius, active = accent soft + accent text |
| `Panel` | `MainActivity.buildPanel()` | 340 dp sheet, 52 dp rows, 24 dp radius |
| `Dialog` | `MainActivity.showAppMenu()` etc. | 400 dp card, 24 dp radius, 54 dp buttons |
| `Toast` | `MainActivity.toast()` | system toast (no custom styling yet) |

## 6. Android implementation notes
- **Stack:** Java, plain framework `View`s, single `Activity` (no AndroidX, no Compose). Debug APK is
  58 KB; cold start on RK3518 is ~2.5 s (baseline, unchanged by Phase 1).
- **Manifest:** `HOME` + `DEFAULT`; also `LEANBACK_LAUNCHER` for TV rail discovery. Keep Google TV
  Launcher installed; the user switches HOME explicitly.
- **Data:** `PackageManager` for apps and labels, `Intent` resolution for launch; prefs in
  `SharedPreferences` (`astra`).
- **Performance:** icons are drawn from the package manager's drawables at 40 dp; keep the UI thread
  free of package queries where possible (Phase 2 work), avoid per-frame allocations (screensaver
  vignette still allocates — F-11).
- **TV specifics:** overscan-safe margins; D-pad key handling (`KEYCODE_DPAD_*`, `BACK`); no touch
  dependency for any action.
- **Home rows:** fixed card width = eight across the content width; short rows keep the size and end
  early, long rows scroll with the clip in the gap between cards.
- **Empty/error states:** explicit and actionable (“No apps match that search”, “No photos in
  Pictures/Astra”).
- **Accessibility:** content descriptions on cards and settings rows; focus order = reading order;
  chips and the system strip still need state descriptions (F-10).

### Ambient / screensaver
- **Trigger:** idle delay from Settings (Off / 1 / 2 / 5 / 10 / 20 min). Timer resets on every
  key/touch and only runs while Astra is in front — never over a playing app.
- **Layers:** wallpaper (photo slideshow or procedural aurora) → scrim → wordmark, 104 sp thin clock,
  date; “Press any key to return” at the bottom.
- **Home backdrop:** the same wallpaper source, static — one photo at 70% opacity under a vertical
  scrim (0x7A→0xB8) so headers and the top bar keep contrast; the aurora glow when there are no photos.
- **Home photo picker:** Settings → Home photo, 4-up thumbnail grid, OK stores a MediaStore id
  (`home_photo_id`); the screensaver keeps cycling every photo. Missing file falls back to the first.
- **Motion:** 700 ms fade in, ~46 s aurora drift, 22 s photo hold with 2.6 s cross-fade and 5% zoom.
- **Exit:** any key or tap, 260 ms fade out, focus restored.
- **Wallpaper sources:** `Pictures/Astra` or `DCIM/Astra`, queried through MediaStore (path access is
  blocked by scoped storage); `READ_MEDIA_IMAGES` requested on demand; aurora when nothing is indexed.
- **Black mode:** pure `#000000` with the clock only, for OLED.

## 7. Deferred from this spec
- Panel backdrop blur and card drop shadows (cost, no visible benefit on this panel).
- 4 px focus-ring offset (replaced by the 2 dp ring).
- Favorites / Recently used library sections, provider content search, deep links.
- Hero card as a default surface (stays opt-in).
- Panel scrolling (Phase 3), search restyle (Phase 5), dialog entry focus (Phase 5).

## 8. Deliverables status
- [x] Baseline captures (device) — `docs/screenshots/baseline/`
- [x] Design proposal, mockups, HTML prototype
- [x] Android launcher (`android/`)
- [x] Astra Settings, screensaver / ambient, photo wallpapers
- [x] Phase 1 Home visual foundation — `docs/screenshots/phase-1/`
- [ ] Phase 2 app library restructure
- [ ] Phase 3 quick panel
- [ ] Phase 4 contextual information
- [ ] Phase 5 consistency, accessibility, dialogs
