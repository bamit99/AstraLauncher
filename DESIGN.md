# Astra — Design System & Handoff

**Surface:** Android TV launcher (10-foot UI), primary device XS97_S3_PRO, 1920×1080 @ 240dpi.  
**Mode:** Operate (fast task completion; expression lives in restraint, focus craft, and motion).  
**Mood:** dark, sleek, minimal. Content art provides color; chrome stays quiet.  
**Mockups:** `02-mockup-home-v1.jpg`, `03-mockup-apps-v1.jpg`, `04-mockup-quick-panel-v1.jpg`, `05-mockup-search-v1.*`

## 1. Design principles
1. **Three actions to intent.** Common destinations reachable in ≤3 D-pad presses.
2. **Content is the color.** Backgrounds/cards neutral; accent only signals focus/state/primary action.
3. **Deterministic focus.** Every screen defines entry focus, traversal order, and return focus.
4. **No dead ends.** Every screen reachable and exitable via Back; Home never traps exit ambiguously.
5. **Honest surfaces.** No fabricated recommendations; only installed apps and real intents.

## 2. Tokens

### Color
| Token | Hex | Use |
|---|---|---|
| `bg/base` | `#0B0D10` | app background |
| `bg/raised` | `#12151B` | rows, sheets, top bar |
| `bg/card` | `#1A1F27` | tiles, cards |
| `bg/card-hover` | `#212734` | focused/active card |
| `border/subtle` | `#252B36` | 1px dividers, card edges |
| `text/primary` | `#F5F7FA` | titles, labels |
| `text/secondary` | `#A9B2C3` | metadata, hints |
| `text/muted` | `#6F7A8C` | disabled, timestamps |
| `accent` | `#7CC4FF` | focus ring, active pill, primary CTA |
| `accent/pressed` | `#3D8BFF` | pressed state depth |
| `accent/soft` | `rgba(124,196,255,0.16)` | focus fill tint |
| `success` | `#3DDC97` | online, connected |
| `warning` | `#FFB020` | degraded state |
| `danger` | `#FF6B6B` | destructive actions only |
| `scrim` | `rgba(0,0,0,0.64)` | modal/panel backdrop |

Contrast: primary text on base ≥ 15:1; secondary ≥ 7:1; focus ring vs card ≥ 3:1.

### Typography
| Role | Size | Weight | Line-height |
|---|---|---|---|
| Hero title | 52 | 600 | 1.1 |
| Screen title | 40 | 600 | 1.15 |
| Row title | 28 | 600 | 1.2 |
| Card label | 22 | 500 | 1.25 |
| Body | 24 | 400 | 1.4 |
| Metadata | 18 | 400 | 1.35 |
| Micro/legal | 16 | 400 | 1.3 |

Font: system default (Roboto / Noto Sans). No display faces — legibility over personality at 10 ft.

### Spacing & layout
- Base grid 8px. Rhythm: 8 / 12 / 16 / 24 / 32 / 48 / 64 / 96.
- Safe area: 96px horizontal, 48–64px vertical.
- Card radius: 20px. Chip radius: 999px. Panel radius: 28px.
- Row gap: 24px horizontal, 32px vertical between rows.
- Content max width: full bleed with 96px safe margins; no centered narrow column except Search.

### Elevation
- Card rest: `0 1px 0 rgba(255,255,255,0.04)` top hairline + no blur.
- Card focus: ring 2px `accent` + `0 12px 32px rgba(0,0,0,0.55)` + scale 1.03.
- Panel: backdrop blur 24px + `0 24px 64px rgba(0,0,0,0.6)`.

### Motion
| Interaction | Duration | Easing |
|---|---|---|
| Focus move | 140ms | ease-out |
| Focus scale/glow | 160ms | ease-out |
| Screen transition | 220ms | ease-in-out |
| Panel slide | 240ms | cubic-bezier(0.2,0,0,1) |
| Press feedback | 90ms | linear |

Respect system “remove animations”: replace transitions with instant state, keep focus ring.

## 3. Focus system (remote/D-pad)
- **Ring:** 2px accent, 4px outer offset, always visible; never color-only (scale + shadow accompany).
- **Traversal:** spatial order matches visual grid; rows scroll horizontally only when content overflows.
- **Return focus:** Back restores the exact previously focused item per screen.
- **Long-press OK:** context menu (Pin/Unpin, App info, Move, Uninstall where allowed).
- **Menu key:** Quick Panel (when device maps it). Back on Home top-level: opens Quick Panel, never silent exit.
- **Edge behavior:** no wrap-around surprises; leftmost item moves to nav, not to the last item.

## 4. Screen specs

### Home
- **Top bar (h 72):** wordmark left; right: clock, network glyph, Search, Quick Panel. Auto-hide after 8s idle; any D-pad reveals.
- **Hero:** optional quick-start card for the first media app, action copy “Open” — never claims to resume a title that the launcher cannot actually resume. If no media app exists, collapse the hero entirely (no placeholder art).
- **Pinned row:** user-pinned apps persisted in `SharedPreferences` (`astra` / `pins`), 7–9 items, initial focus target; rows scroll horizontally so the focused tile is never clipped.
- **Streaming row:** Leanback apps from PackageManager, 220×132 cards.
- **Apps/Tools row:** remaining apps + Files/Settings/Store.
- Vertical scroll only when rows overflow; horizontal scroll per row with inset shadows.

### Apps
- **Sections:** Favorites (≤12), Recent (≤8), All (A–Z grid, 7 columns, 220×150 tiles).
- **Search field** at top; filter chips: All / Media / Tools / System.
- **Context menu:** Open, Pin, Unpin, App info, Uninstall (only if `DELETE` intent resolves).
- **Entry focus:** first favorite, else first app.
- Non-Leanback apps included via `CATEGORY_LAUNCHER` intents; hide non-launchable packages.

### Quick Panel
- **Presentation:** right sheet 440px, scrim 64%, over dimmed/blurred Home.
- **Rows (h 64):** profile, network (live Wi-Fi/Ethernet + status), date/time, HDMI input, display mode, audio output, storage (thin bar), divider, refresh app list, Launcher settings, Android TV settings, Sleep, Power.
- **Behavior:** toggles/rows activate on OK; Back closes and returns focus to the trigger.
- **Destructive:** Power/Sleep require confirm dialog (single-step confirm, default = Cancel).

### Search
- **Entry:** top-bar Search or remote voice/search key.
- **Field:** centered, 720px wide, 64px tall, voice icon, placeholder “Search apps & content”.
- **Chips:** Apps / Movies / Shows / YouTube; recent searches below.
- **Results:** two-column mix — apps left, content right — each item shows source badge. Empty state: recent + suggestions, never a blank screen.
- **Handoff:** app results launch app; content results deep-link to provider where supported, else open provider app search.

## 5. Components
| Component | Spec |
|---|---|
| `AppTile` | 220×150, icon 64, label 22/500 below, radius 20, focus ring+scale |
| `PinTile` | 240×140, icon 56, label 22, same focus |
| `ContentCard` | 320×180 art, 20 radius, title 22, source badge 16 muted |
| `Chip` | pill, 44h, 22/500, active = accent soft fill + accent text |
| `RowHeader` | 28/600 + optional “See all” action |
| `TopBar` | 72h, bg/base 92% opacity + 8px blur, dividers none |
| `Panel` | 440w, bg/raised 96%, 28 radius, 24 blur, rows 64h |
| `Toast` | bottom-center, bg/card, 24 radius, 18/400, 2s |
| `Dialog` | 560w, bg/raised, 24 radius, title 32, body 24, actions right |

## 6. Android implementation notes
- **Stack:** Java, plain framework `View`s, single `Activity` (no AndroidX, no Compose) — ~70 KB APK, instant cold start on RK3518.
- **Manifest:** `HOME` + `DEFAULT`; also `LEANBACK_LAUNCHER` for TV rail discovery. Keep Google TV Launcher installed; user switches HOME explicitly.
- **Data:** `PackageManager` for apps and labels, `Intent` resolution for launch; prefs in `SharedPreferences` (`astra`).
- **Performance:** cache icons at target dp; prefetch row 0/1; avoid main-thread IO; target 60 fps focus; wallpaper layers pre-rendered once and animated with a `Matrix`.
- **TV specifics:** overscan-safe 5% margins; D-pad key handling (`KEYCODE_DPAD_*`, `MENU`, `BACK`), no touch dependencies.
- **Home rows:** card width computed so a whole number of cards ends exactly on the right margin; rows only scroll when the count exceeds the 120 dp minimum, and then the clip falls in the gap between cards.
- **Empty/error states:** explicit, actionable (e.g., “No apps match that search”, “No photos in Pictures/Astra”).
- **Accessibility:** content descriptions on tiles and actions; focus order = reading order; min contrast met; respect font scale.

### Ambient / screensaver
- **Trigger:** idle delay from Settings (Off / 1 / 2 / 5 / 10 / 20 min). Timer resets on every key/touch and only runs while Astra is in front — never over a playing app.
- **Layers:** wallpaper (photo slideshow or procedural aurora) → 35% scrim → wordmark, 104 sp thin clock, date; “Press any key to return” at the bottom.
- **Home backdrop:** the same wallpaper source, static — one photo at 70% opacity under a vertical scrim (0x7A→0xB8) so row headers and the top bar keep contrast; the aurora glow when there are no photos.
- **Home photo picker:** Settings → Home photo, 4-up thumbnail grid, OK pins a MediaStore id (`home_photo_id`); the screensaver keeps cycling every photo. Missing file falls back to the first image.
- **Motion:** 700 ms fade in, ~46 s aurora drift, 22 s photo hold with 2.6 s cross-fade and 5% slow zoom. No motion that blocks input.
- **Exit:** any key or tap, 260 ms fade out, focus restored to where the user left it.
- **Wallpaper sources:** `Pictures/Astra` or `DCIM/Astra`, queried through MediaStore (path access is blocked by scoped storage); `READ_MEDIA_IMAGES` requested on demand; built-in aurora when no photos are indexed.
- **Black mode:** pure `#000000` with the clock only, for OLED.

## 7. Deliverables status
- [x] Baseline captures (device)
- [x] Design proposal (`DESIGN-PROPOSAL.md`)
- [x] Home mockup
- [x] Apps mockup
- [x] Quick Panel mockup
- [x] Search mockup
- [x] Android launcher (`android/`)
- [x] Astra Settings, screensaver / ambient, photo wallpapers
- [ ] Provider deep links and content search
