# App discovery and category rules

**Applies to:** `AppRepository.java` (single source of truth), Home shelves and the app library.
**Device of record:** XS97_S3_PRO, Android 14 (SDK 34), 16 launchable apps.

Astra does not guess what an app is from its icon, its store listing or the network. One rule decides
every category, and every screen reads it from the repository rather than re-deriving it.

## 1. What counts as an installed app

A package appears once if it resolves either launcher category, deduplicated by package:

- `Intent.ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER` (TV apps),
- `Intent.ACTION_MAIN` + `CATEGORY_LAUNCHER` (classic apps).

The launcher's own package is excluded. The list is sorted by label, case-insensitively, and resolved
on a worker thread (`astra-apps`); the UI receives an immutable snapshot.

## 2. Categories

One function, three outcomes, evaluated in this order:

| Order | Test | Category |
|---|---|---|
| 1 | the package contains a media token (below) | **Media** |
| 2 | the package is preinstalled (`ApplicationInfo.FLAG_SYSTEM`) | **System** |
| 3 | otherwise | **Tools** |

Media deliberately wins over System: a preinstalled player is still a player. This is why
`com.rockchips.mediacenter` is Media and `com.android.tv.settings` is System.

### Media tokens

Matching is per **package-name token** — the package is lowercased and split on `.`, `_` and `-` — so
`com.google.android.youtube.tv` matches `youtube`, while `com.android.tv.settings` matches nothing
(there is no `tv` token). Token matching replaced the old substring test, which would have classified
any package containing `tv` or `prime` as media.

```
stremio plex plexapp smarttube youtube netflix prime amazonvideo disney disneyplus
vlc kodi spotify jellyfin emby mediacenter iptv tubi hulu hbo max crunchyroll
player video cinema music
```

Adding a service means adding one token here and nothing else. The list is intentionally short and
maintainable — it is a local heuristic, not a scraped catalogue, and it never contacts a network.

### Worked example (the reference device)

| App | Package | Category | Why |
|---|---|---|---|
| Disney+, Netflix, Plex, Prime Video, SmartTube, Stremio, YouTube | … | Media | token match |
| Media Center | `com.rockchips.mediacenter` | Media | token `mediacenter` |
| Play Store | `com.android.vending` | System | preinstalled |
| Settings | `com.android.tv.settings` | System | preinstalled, no token |
| Calculator, FileBrowser | `com.android.calculator2`, `com.droidlogic.FileBrower` | System | preinstalled |
| All Apps (sample), System Tracing, System Update | `com.example.all_apps`, `com.android.traceur`, `com.otaupdate.update` | System | preinstalled |
| Chrome | `com.android.chrome` | Tools | installed by the user, no media token |

`com.example.all_apps` is a preinstalled sample package whose label is "All Apps". It stays in the
library (System) because hiding it would mean guessing from a package name; the label collision with
the launcher's own `All apps` action tile is recorded in the audit (F-4).

## 3. Library sections

| Chip | Source | Shown when |
|---|---|---|
| **All** | every resolved app | always |
| **Favorites** | `pins` in pin order | at least one pin exists |
| **Recently used** | local usage stats, last 14 days, max 12, most recent first | the user has granted usage access and at least one app qualifies |
| **Media** | category Media | always |
| **Tools** | category Tools | always |
| **System** | category System | always |

Chips are hidden rather than shown empty; a section whose content disappears (e.g. no pins) falls
back to All. Each chip carries a state description for TalkBack, and Up from the first grid row lands
on the active chip.

## 4. Recently used — local, opt-in, degradable

- Reads `UsageStatsManager` for the last 14 days, keeps only packages that are currently installed,
  de-duplicates and caps at 12. Nothing is stored, uploaded or shared.
- Requires the `PACKAGE_USAGE_STATS` app-op, granted by the user in system settings. Astra declares
  the permission and links to that screen from **Settings → Recently used apps**; the chip is simply
  absent until access exists.
- The query happens in the repository's worker thread, never on the UI thread.
- Revoking access removes the chip on the next resume (the launcher compares access state in
  `onResume` and reloads once when it changes).

## 5. Home shelves

| Shelf | Source |
|---|---|
| **PINNED** | `pins`, resolved in pin order, plus the `All apps` action tile |
| **STREAMING & MEDIA** | category Media (intentionally overlaps PINNED: that row is a shortcut shelf, this one is the category; Phase 4 decides whether both stay) |
| **APPS** | everything that is neither Media nor pinned, excluding preinstalled diagnostics whose label starts with `System ` (they remain in the library's System section) |

Shelves and the library share one card size (`cardWidth()`: eight across the content width) and one
card component, so nothing is re-implemented per screen.
