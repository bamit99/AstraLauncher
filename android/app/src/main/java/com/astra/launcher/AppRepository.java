package com.astra.launcher;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single source of truth for the installed app list, its categories and the local recency list.
 *
 * <p>Everything is resolved on a worker thread and published as immutable snapshots; the UI never
 * touches {@link PackageManager} while rendering. Categories are derived from one documented rule
 * ({@link #categoryFor}) rather than from ad-hoc checks scattered across screens.
 */
final class AppRepository {

    /** Categories shown in the library and used by the Home shelves. */
    enum Category {
        MEDIA, TOOLS, SYSTEM
    }

    /**
     * Package-name tokens that identify a media app. Matching is per token (the package split on
     * `.`, `_` and `-`), so {@code com.android.tv.settings} is not media while
     * {@code com.google.android.youtube.tv} is. Keep this list short and maintainable — it is a
     * deliberate local heuristic, not a scraped catalogue.
     */
    private static final Set<String> MEDIA_TOKENS = new HashSet<>(java.util.Arrays.asList(
            "stremio", "plex", "plexapp", "smarttube", "youtube", "netflix", "prime",
            "amazonvideo", "disney", "disneyplus", "vlc", "kodi", "spotify", "jellyfin",
            "emby", "mediacenter", "iptv", "tubi", "hulu", "hbo", "max", "crunchyroll",
            "player", "video", "cinema", "music"));

    private static final long RECENT_WINDOW_MS = 14L * 24 * 60 * 60 * 1000;
    private static final int RECENT_LIMIT = 12;

    private volatile List<AppEntry> apps = Collections.emptyList();
    private volatile List<String> recentPkgs = Collections.emptyList();
    private volatile boolean loaded;
    private volatile boolean loading;

    boolean isLoaded() {
        return loaded;
    }

    List<AppEntry> apps() {
        return apps;
    }

    List<AppEntry> category(Category wanted) {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) if (e.category == wanted) out.add(e);
        return out;
    }

    /** Pinned packages, in pin order, resolved against the current list. */
    List<AppEntry> favorites(List<String> pins) {
        List<AppEntry> out = new ArrayList<>();
        for (String pkg : pins) {
            AppEntry e = byPackage(pkg);
            if (e != null) out.add(e);
        }
        return out;
    }

    List<AppEntry> recent() {
        List<AppEntry> out = new ArrayList<>();
        for (String pkg : recentPkgs) {
            AppEntry e = byPackage(pkg);
            if (e != null) out.add(e);
        }
        return out;
    }

    boolean hasRecent() {
        return !recentPkgs.isEmpty();
    }

    AppEntry byPackage(String pkg) {
        if (pkg == null) return null;
        for (AppEntry e : apps) if (e.pkg.equals(pkg)) return e;
        return null;
    }

    AppEntry firstMedia() {
        for (AppEntry e : apps) if (e.category == Category.MEDIA) return e;
        return null;
    }

    /**
     * Load the app list (and the local recency list) in the background.
     *
     * <p>{@code onReady} runs on the calling thread's looper, i.e. the UI thread.
     */
    void load(final Context context, final boolean force, final Runnable onReady) {
        if (loading) return;
        if (loaded && !force) {
            if (onReady != null) onReady.run();
            return;
        }
        loading = true;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            List<AppEntry> resolved = resolve(app);
            List<String> recent = resolveRecent(app, resolved);
            apps = resolved;
            recentPkgs = recent;
            loaded = true;
            loading = false;
            if (onReady != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(onReady);
        }, "astra-apps").start();
    }

    /* ------------------------------------------------------------------ */
    /* Resolution                                                         */
    /* ------------------------------------------------------------------ */
    private List<AppEntry> resolve(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<AppEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collect(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, out, seen);
        collect(ctx, pm, Intent.CATEGORY_LAUNCHER, out, seen);
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return Collections.unmodifiableList(out);
    }

    private void collect(Context ctx, PackageManager pm, String launcherCategory,
                         List<AppEntry> out, Set<String> seen) {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(launcherCategory);
        List<ResolveInfo> list = pm.queryIntentActivities(probe, 0);
        for (ResolveInfo ri : list) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (ai == null || ctx.getPackageName().equals(ai.packageName)) continue;
            if (!seen.add(ai.packageName)) continue;

            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(launcherCategory)
                    .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            Drawable icon = ri.loadIcon(pm);
            out.add(new AppEntry(String.valueOf(ri.loadLabel(pm)), ai.packageName, icon,
                    launch, system, categoryFor(ai.packageName, system)));
        }
    }

    /**
     * One documented rule for every app:
     * <ol>
     *   <li>a package token in {@link #MEDIA_TOKENS} means <b>media</b>;</li>
     *   <li>otherwise a preinstalled app is <b>system</b>;</li>
     *   <li>everything else is <b>tools</b>.</li>
     * </ol>
     * Media wins over system on purpose: a preinstalled player is still a player.
     */
    static Category categoryFor(String pkg, boolean system) {
        if (isMediaPackage(pkg)) return Category.MEDIA;
        return system ? Category.SYSTEM : Category.TOOLS;
    }

    static boolean isMediaPackage(String pkg) {
        for (String token : pkg.toLowerCase(Locale.ROOT).split("[._-]")) {
            if (MEDIA_TOKENS.contains(token)) return true;
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* Local recency (opt-in, never leaves the device)                    */
    /* ------------------------------------------------------------------ */
    private List<String> resolveRecent(Context ctx, List<AppEntry> installed) {
        if (!hasUsageAccess(ctx)) return Collections.emptyList();

        Set<String> known = new HashSet<>();
        for (AppEntry e : installed) known.add(e.pkg);

        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return Collections.emptyList();

        long now = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                now - RECENT_WINDOW_MS, now);
        if (stats == null || stats.isEmpty()) return Collections.emptyList();

        List<UsageStats> used = new ArrayList<>();
        for (UsageStats s : stats) {
            if (s.getLastTimeUsed() > 0 && known.contains(s.getPackageName())) used.add(s);
        }
        Collections.sort(used, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));

        List<String> out = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (UsageStats s : used) {
            if (out.size() >= RECENT_LIMIT) break;
            if (added.add(s.getPackageName())) out.add(s.getPackageName());
        }
        return Collections.unmodifiableList(out);
    }

    /** Usage access is a special app-op the user grants in system settings; default is denied. */
    static boolean hasUsageAccess(Context ctx) {
        AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), ctx.getPackageName());
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
