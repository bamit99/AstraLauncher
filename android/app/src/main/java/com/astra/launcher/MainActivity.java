package com.astra.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0B0D10;
    private static final int RAISED = 0xFF12151B;
    private static final int CARD = 0xFF1A1F27;
    private static final int CARD_HI = 0xFF212734;
    private static final int LINE = 0xFF252B36;
    private static final int TX = 0xFFF5F7FA;
    private static final int TX2 = 0xFFA9B2C3;
    private static final int TX3 = 0xFF6F7A8C;
    private static final int ACCENT = 0xFF7CC4FF;
    private static final int ACCENT_SOFT = 0x2E7CC4FF;

    private static final String[] PINNED = {
            "org.smarttube.stable", "com.stremio.one", "com.plexapp.android",
            "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.google.android.youtube.tv", "com.clap.netflix"
    };

    private float d;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private FrameLayout contentHost;
    private LinearLayout searchOverlay;
    private FrameLayout panelOverlay;
    private LinearLayout panel;
    private View panelScrim;
    private TextView clockView;
    private EditText searchInput;
    private ListView searchList;
    private List<AppEntry> apps = new ArrayList<>();
    private List<AppEntry> filterSource = new ArrayList<>();
    private View homeFirstFocus;
    private View panelTrigger;
    private int category = 0;
    private BaseAdapter gridAdapter;
    private List<AppEntry> gridData = new ArrayList<>();
    private boolean panelOpen = false;
    private boolean searchOpen = false;
    private String screen = "home";

    /* ------------------------------------------------------------------ */
    /* App model                                                          */
    /* ------------------------------------------------------------------ */
    static class AppEntry {
        String label;
        String pkg;
        Drawable icon;
        Intent intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        apps = loadApps(getPackageManager());

        root = new FrameLayout(this);
        root.setBackground(new GlowDrawable());

        contentHost = new FrameLayout(this);
        root.addView(contentHost, match());

        buildSearch();
        buildPanel();
        root.addView(searchOverlay, match());
        root.addView(panelOverlay, match());

        buildHome();
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tickClock();
        handler.postDelayed(clockTick, 15000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            tickClock();
            handler.postDelayed(this, 15000);
        }
    };

    private void tickClock() {
        if (clockView != null) {
            clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /* ------------------------------------------------------------------ */
    /* App loading                                                        */
    /* ------------------------------------------------------------------ */
    private List<AppEntry> loadApps(PackageManager pm) {
        List<AppEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, out, seen);
        collect(pm, Intent.CATEGORY_LAUNCHER, out, seen);
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        filterSource = new ArrayList<>(out);
        return out;
    }

    private void collect(PackageManager pm, String category, List<AppEntry> out, Set<String> seen) {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list = pm.queryIntentActivities(probe, 0);
        for (ResolveInfo ri : list) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (ai == null || getPackageName().equals(ai.packageName)) continue;
            if (seen.contains(ai.packageName)) continue;
            seen.add(ai.packageName);
            AppEntry e = new AppEntry();
            e.pkg = ai.packageName;
            e.label = String.valueOf(ri.loadLabel(pm));
            e.icon = ri.loadIcon(pm);
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(category);
            i.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            e.intent = i;
            out.add(e);
        }
    }

    private AppEntry byPkg(String pkg) {
        for (AppEntry e : apps) if (e.pkg.equals(pkg)) return e;
        return null;
    }

    private boolean isMedia(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        String[] keys = {"stremio", "plex", "smarttube", "youtube", "netflix", "prime",
                "amazonvideo", "disney", "vlc", "kodi", "spotify", "jellyfin", "emby", "mediacenter"};
        for (String k : keys) if (p.contains(k)) return true;
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* Home                                                               */
    /* ------------------------------------------------------------------ */
    private void buildHome() {
        contentHost.removeAllViews();
        screen = "home";

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(30), dp(64), dp(24));
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(buildTopBar());

        // Hero / continue
        AppEntry heroApp = byPkg("org.smarttube.stable");
        if (heroApp == null) heroApp = firstMedia();
        FrameLayout hero = new FrameLayout(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(148));
        hlp.topMargin = dp(22);
        hero.setLayoutParams(hlp);
        hero.setFocusable(true);
        hero.setBackground(heroBg(false));
        hero.setPadding(dp(30), dp(20), dp(30), dp(20));

        LinearLayout heroCol = new LinearLayout(this);
        heroCol.setOrientation(LinearLayout.VERTICAL);
        heroCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView kicker = text("CONTINUE WATCHING", 11, ACCENT, Typeface.BOLD);
        kicker.setLetterSpacing(0.18f);
        heroCol.addView(kicker);

        TextView title = text(heroApp == null ? "Your media, one press away" : heroApp.label + " · Subscriptions", 28, TX, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = dp(6);
        heroCol.addView(title, tlp);

        TextView sub = text("Pick up where you left off", 14, TX2, Typeface.NORMAL);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(4);
        heroCol.addView(sub, slp);

        TextView resume = text("▶  Resume", 13, ACCENT, Typeface.BOLD);
        resume.setBackground(rounded(ACCENT_SOFT, 999, 0x667CC4FF, 1));
        resume.setPadding(dp(16), dp(9), dp(16), dp(9));
        LinearLayout.LayoutParams rlp = wrap();
        rlp.topMargin = dp(10);
        heroCol.addView(resume, rlp);

        hero.addView(heroCol, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final AppEntry heroTarget = heroApp;
        hero.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 20));
        hero.setOnClickListener(v -> {
            if (heroTarget != null) launch(heroTarget);
            else showApps();
        });
        col.addView(hero);

        // Pinned row
        int pinnedCount = 0;
        for (String pkg : PINNED) if (byPkg(pkg) != null) pinnedCount++;
        col.addView(sectionHeader("PINNED", pinnedCount + " apps"));
        LinearLayout pinnedRow = new LinearLayout(this);
        pinnedRow.setOrientation(LinearLayout.HORIZONTAL);
        pinnedRow.setPadding(0, dp(2), 0, dp(6));

        boolean first = true;
        for (String pkg : PINNED) {
            AppEntry e = byPkg(pkg);
            if (e == null) continue;
            View tile = appTile(e, true);
            pinnedRow.addView(tile);
            if (first) { homeFirstFocus = tile; first = false; }
        }
        View allTile = actionTile("All apps", "⌗", this::showApps);
        pinnedRow.addView(allTile);
        if (homeFirstFocus == null) homeFirstFocus = allTile;
        col.addView(hscroll(pinnedRow));

        // Streaming row
        col.addView(sectionHeader("STREAMING & MEDIA", ""));
        LinearLayout mediaRow = new LinearLayout(this);
        mediaRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaRow.setPadding(0, dp(2), 0, dp(6));
        int count = 0;
        for (AppEntry e : apps) {
            if (count >= 8) break;
            if (!isMedia(e.pkg)) continue;
            mediaRow.addView(appTile(e, true));
            count++;
        }
        if (count == 0) {
            for (AppEntry e : apps) {
                if (count >= 8) break;
                mediaRow.addView(appTile(e, true));
                count++;
            }
        }
        col.addView(hscroll(mediaRow));

        contentHost.addView(scroll, match());
        contentHost.post(() -> { if (homeFirstFocus != null) homeFirstFocus.requestFocus(); });
    }

    private boolean isPinned(String pkg) {
        for (String p : PINNED) if (p.equals(pkg)) return true;
        return false;
    }

    private AppEntry firstMedia() {
        for (AppEntry e : apps) if (isMedia(e.pkg)) return e;
        return apps.isEmpty() ? null : apps.get(0);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        View dot = new View(this);
        dot.setBackground(rounded(ACCENT, 999, 0, 0));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(18), dp(18));
        dlp.rightMargin = dp(10);
        bar.addView(dot, dlp);

        TextView brand = text("Astra", 20, TX, Typeface.BOLD);
        bar.addView(brand);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        clockView = text("--:--", 14, TX2, Typeface.NORMAL);
        bar.addView(clockView);

        TextView wifi = text("Wi-Fi", 14, TX2, Typeface.NORMAL);
        LinearLayout.LayoutParams wlp = wrap();
        wlp.leftMargin = dp(18);
        bar.addView(wifi, wlp);

        TextView search = glyphButton("⌕", v -> showSearch());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(40), dp(40));
        slp.leftMargin = dp(14);
        bar.addView(search, slp);

        panelTrigger = glyphButton("≡", v -> showPanel());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(40), dp(40));
        plp.leftMargin = dp(10);
        bar.addView(panelTrigger, plp);

        tickClock();
        return bar;
    }

    private View sectionHeader(String title, String meta) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(10);
        row.setLayoutParams(lp);

        TextView t = text(title, 12, TX3, Typeface.BOLD);
        t.setLetterSpacing(0.16f);
        row.addView(t);

        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, dp(1), 1f);
        llp.leftMargin = dp(14);
        row.addView(line, llp);

        if (!TextUtils.isEmpty(meta)) {
            TextView m = text(meta, 12, TX3, Typeface.NORMAL);
            LinearLayout.LayoutParams mlp = wrap();
            mlp.leftMargin = dp(12);
            row.addView(m, mlp);
        }
        return row;
    }

    private HorizontalScrollView hscroll(View content) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.setClipToPadding(false);
        h.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        h.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return h;
    }

    private View appTile(AppEntry e, boolean withIcon) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(12), dp(8), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(170), dp(104));
        lp.rightMargin = dp(14);
        tile.setLayoutParams(lp);
        tile.setBackground(rounded(CARD, 18, LINE, 1));
        tile.setFocusable(true);

        if (withIcon && e.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(e.icon);
            roundedOutline(iv, 10);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
            tile.addView(iv, ilp);
        } else {
            TextView g = text("▶", 20, ACCENT, Typeface.BOLD);
            g.setGravity(Gravity.CENTER);
            g.setBackground(rounded(CARD_HI, 10, LINE, 1));
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(38), dp(38));
            tile.addView(g, glp);
        }

        TextView label = text(e.label, 13, TX2, Typeface.NORMAL);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(4), dp(7), dp(4), 0);
        tile.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tile.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 18));
        tile.setOnClickListener(v -> launch(e));
        tile.setOnLongClickListener(v -> { appInfo(e); return true; });
        return tile;
    }

    private View actionTile(String label, String glyph, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(12), dp(8), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(170), dp(104));
        lp.rightMargin = dp(14);
        tile.setLayoutParams(lp);
        tile.setBackground(rounded(CARD, 18, LINE, 1));
        tile.setFocusable(true);

        TextView g = text(glyph, 20, ACCENT, Typeface.BOLD);
        g.setGravity(Gravity.CENTER);
        g.setBackground(rounded(CARD_HI, 10, LINE, 1));
        tile.addView(g, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView name = text(label, 13, TX2, Typeface.NORMAL);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(7), 0, 0);
        tile.addView(name);

        tile.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 18));
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    /* ------------------------------------------------------------------ */
    /* Apps screen                                                        */
    /* ------------------------------------------------------------------ */
    private void showApps() {
        contentHost.removeAllViews();
        screen = "apps";

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(28), dp(64), dp(24));
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Apps", 30, TX, Typeface.BOLD);
        head.addView(title);
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView count = text(apps.size() + " installed", 13, TX3, Typeface.NORMAL);
        head.addView(count);
        col.addView(head);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(16);
        chips.setLayoutParams(clp);
        String[] names = {"All", "Media", "Tools", "System"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = chipView(names[i], idx == category);
            chip.setOnClickListener(v -> {
                category = idx;
                showApps();
            });
            LinearLayout.LayoutParams lp = wrap();
            lp.rightMargin = dp(10);
            chips.addView(chip, lp);
        }
        col.addView(chips);

        View gap = new View(this);
        col.addView(gap, new LinearLayout.LayoutParams(1, dp(18)));

        List<AppEntry> data = categoryApps();
        final View[] firstTile = new View[1];
        for (int i = 0; i < data.size(); i += 7) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(14);
            row.setLayoutParams(rlp);
            for (int j = 0; j < 7; j++) {
                int p = i + j;
                if (p >= data.size()) {
                    View filler = new View(this);
                    row.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
                    continue;
                }
                View tile = appTile(data.get(p), true);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, dp(112), 1f);
                tl.rightMargin = j < 6 ? dp(14) : 0;
                tile.setLayoutParams(tl);
                row.addView(tile);
                if (firstTile[0] == null) firstTile[0] = tile;
            }
            col.addView(row);
        }

        if (data.isEmpty()) {
            TextView empty = text("No apps in this category", 16, TX3, Typeface.NORMAL);
            LinearLayout.LayoutParams elp = wrap();
            elp.topMargin = dp(24);
            col.addView(empty, elp);
        }

        contentHost.addView(scroll, match());
        contentHost.post(() -> { if (firstTile[0] != null) firstTile[0].requestFocus(); });
    }

    private List<AppEntry> categoryApps() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) {
            boolean system;
            try {
                system = (getPackageManager().getApplicationInfo(e.pkg, 0).flags
                        & ApplicationInfo.FLAG_SYSTEM) != 0;
            } catch (PackageManager.NameNotFoundException ex) {
                system = false;
            }
            switch (category) {
                case 1: if (isMedia(e.pkg)) out.add(e); break;
                case 2: if (!system && !isMedia(e.pkg)) out.add(e); break;
                case 3: if (system) out.add(e); break;
                default: out.add(e);
            }
        }
        return out;
    }

    private TextView chipView(String label, boolean active) {
        TextView c = text(label, 13, active ? ACCENT : TX2, Typeface.BOLD);
        c.setBackground(rounded(active ? ACCENT_SOFT : RAISED, 999,
                active ? 0x667CC4FF : LINE, 1));
        c.setPadding(dp(18), dp(9), dp(18), dp(9));
        c.setFocusable(true);
        c.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 999));
        return c;
    }

    /* ------------------------------------------------------------------ */
    /* Search overlay                                                     */
    /* ------------------------------------------------------------------ */
    private void buildSearch() {
        searchOverlay = new LinearLayout(this);
        searchOverlay.setOrientation(LinearLayout.VERTICAL);
        searchOverlay.setBackgroundColor(BG);
        searchOverlay.setPadding(dp(180), dp(40), dp(180), dp(30));
        searchOverlay.setVisibility(View.GONE);
        searchOverlay.setFocusableInTouchMode(true);

        searchInput = new EditText(this);
        searchInput.setHint("Search apps & content");
        searchInput.setHintTextColor(TX3);
        searchInput.setTextColor(TX);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        searchInput.setSingleLine(true);
        searchInput.setBackground(rounded(RAISED, 999, LINE, 1));
        searchInput.setPadding(dp(26), dp(16), dp(26), dp(16));
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchOverlay.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchList = new ListView(this);
        searchList.setItemsCanFocus(true);
        searchList.setDivider(null);
        searchList.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        searchList.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        llp.topMargin = dp(20);
        searchList.setLayoutParams(llp);
        searchOverlay.addView(searchList);

        filterSource = new ArrayList<>(apps);
        searchList.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return filterSource.size(); }
            @Override public Object getItem(int p) { return filterSource.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View convert, ViewGroup parent) {
                return searchRow(filterSource.get(p));
            }
        });
        searchList.setOnItemClickListener((parent, view, position, id) ->
                launch(filterSource.get(position)));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        searchInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER)) {
                if (searchList.getChildCount() > 0) {
                    searchList.requestFocus();
                    searchList.setSelection(0);
                }
                return true;
            }
            return false;
        });
    }

    private View searchRow(AppEntry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setFocusable(true);
        row.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 14));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(e.icon);
        roundedOutline(iv, 10);
        row.addView(iv, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(14);
        TextView name = text(e.label, 17, TX, Typeface.NORMAL);
        TextView pkg = text(e.pkg, 12, TX3, Typeface.NORMAL);
        col.addView(name);
        col.addView(pkg);
        row.addView(col, clp);
        return row;
    }

    private void filterApps(String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        filterSource = new ArrayList<>();
        for (AppEntry e : apps) {
            if (query.isEmpty() || e.label.toLowerCase(Locale.getDefault()).contains(query)) {
                filterSource.add(e);
            }
        }
        if (searchList != null) {
            ((BaseAdapter) searchList.getAdapter()).notifyDataSetChanged();
        }
    }

    private void showSearch() {
        if (searchOpen) return;
        searchOpen = true;
        searchOverlay.setVisibility(View.VISIBLE);
        searchInput.setText("");
        filterApps("");
        searchInput.requestFocus();
    }

    private void hideSearch() {
        searchOpen = false;
        searchOverlay.setVisibility(View.GONE);
        if (homeFirstFocus != null) homeFirstFocus.requestFocus();
    }

    /* ------------------------------------------------------------------ */
    /* Quick panel                                                        */
    /* ------------------------------------------------------------------ */
    private void buildPanel() {
        panelOverlay = new FrameLayout(this);
        panelOverlay.setVisibility(View.GONE);

        panelScrim = new View(this);
        panelScrim.setBackgroundColor(0xA8000000);
        panelScrim.setOnClickListener(v -> hidePanel());
        panelOverlay.addView(panelScrim, match());

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(shaped(RAISED, LINE, 2));
        panel.setPadding(dp(22), dp(30), dp(22), dp(24));
        panel.setElevation(dp(24));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(340),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        panelOverlay.addView(panel, plp);

        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.HORIZONTAL);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setPadding(0, 0, 0, dp(16));

        TextView avatar = text("A", 18, 0xFF06121D, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(ACCENT, 999, 0, 0));
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(12);
        names.addView(text("Living room", 16, TX, Typeface.BOLD));
        names.addView(text("XS97 · Astra", 12, TX3, Typeface.NORMAL));
        profile.addView(names, nlp);
        panel.addView(profile);

        panel.addView(panelRow("Wi-Fi", "Connected · 5 GHz", intentAction(Settings.ACTION_WIFI_SETTINGS)));
        panel.addView(panelRow("Ethernet", "Not connected", null));
        panel.addView(panelRow("Date & time", new SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(new Date()), intentAction(Settings.ACTION_DATE_SETTINGS)));
        panel.addView(divider());
        panel.addView(panelRow("HDMI input", "Switch source", intentAction("android.settings.TV_INPUT_SETTINGS")));
        panel.addView(panelRow("Display", "1080p · 60 Hz", intentAction(Settings.ACTION_DISPLAY_SETTINGS)));
        panel.addView(panelRow("Sound", "TV speakers", intentAction(Settings.ACTION_SOUND_SETTINGS)));
        panel.addView(panelRow("Storage", "Manage space", intentAction(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        panel.addView(divider());
        panel.addView(panelRow("Launcher settings", "Astra", null));
        panel.addView(panelRow("System settings", "Android TV", intentAction(Settings.ACTION_SETTINGS)));
        panel.addView(panelRow("Sleep", "Turn display off", null));
        panel.addView(panelRow("Power", "Restart or shut down", null));
    }

    private View panelRow(String label, String value, final Intent intent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        row.setLayoutParams(lp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(label, 15, TX, Typeface.NORMAL);
        TextView v = text(value, 11, TX3, Typeface.NORMAL);
        col.addView(t);
        col.addView(v);
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnFocusChangeListener((view, f) -> applyFocus(view, f, 12));
        row.setOnClickListener(view -> {
            if (intent != null) {
                try { startActivity(intent); }
                catch (ActivityNotFoundException ex) { toast("Not available"); }
            } else {
                toast(label);
            }
        });
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    private void showPanel() {
        if (panelOpen) return;
        panelOpen = true;
        panelOverlay.setVisibility(View.VISIBLE);
        panel.setTranslationX(dp(360));
        panel.animate().translationX(0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
        panelScrim.setAlpha(0f);
        panelScrim.animate().alpha(1f).setDuration(220).start();
        panel.post(() -> {
            if (panel.getChildCount() > 1) panel.getChildAt(1).requestFocus();
        });
    }

    private void hidePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        panel.animate().translationX(dp(360)).setDuration(180).start();
        panelScrim.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            panelOverlay.setVisibility(View.GONE);
            if (panelTrigger != null) panelTrigger.requestFocus();
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) { hidePanel(); return; }
        if (searchOpen) { hideSearch(); return; }
        if ("apps".equals(screen)) { showHome(); return; }
        showPanel();
    }

    private void showHome() {
        buildHome();
    }

    /* ------------------------------------------------------------------ */
    /* Utilities                                                          */
    /* ------------------------------------------------------------------ */
    private void launch(AppEntry e) {
        try {
            startActivity(e.intent);
        } catch (ActivityNotFoundException ex) {
            toast("Cannot open " + e.label);
        }
    }

    private void appInfo(AppEntry e) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + e.pkg));
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            toast("No app info");
        }
    }

    private Intent intentAction(String action) {
        try { return new Intent(action); } catch (Exception e) { return null; }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void applyFocus(View v, boolean focused, float radius) {
        v.setBackground(rounded(focused ? CARD_HI : CARD, radius,
                focused ? ACCENT : LINE, focused ? 2 : 1));
        v.animate().scaleX(focused ? 1.03f : 1f)
                .scaleY(focused ? 1.03f : 1f)
                .setDuration(focused ? 160 : 120)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        if (v.getParent() instanceof View) v.setElevation(focused ? dp(10) : 0);
    }

    private TextView glyphButton(String glyph, View.OnClickListener click) {
        TextView t = text(glyph, 20, TX2, Typeface.NORMAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(0x00000000, 12, 0x00000000, 0));
        t.setFocusable(true);
        t.setOnFocusChangeListener((v, f) -> applyFocusButton(v, f));
        t.setOnClickListener(click);
        return t;
    }

    private void applyFocusButton(View v, boolean focused) {
        v.setBackground(rounded(focused ? ACCENT_SOFT : 0x00000000, 12,
                focused ? 0x667CC4FF : 0x00000000, 1));
        v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                .setDuration(140).start();
    }

    private TextView text(String s, float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL), style);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0 && strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private GradientDrawable heroBg(boolean focused) {
        GradientDrawable g = new GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF151A22, 0xFF0E1218});
        g.setCornerRadius(dp(20));
        g.setStroke(dp(focused ? 2 : 1), focused ? ACCENT : LINE);
        return g;
    }

    private Drawable shaped(int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(24));
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private void roundedOutline(View v, final float radiusDp) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radiusDp));
            }
        });
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Background: near-black with a restrained ice-blue glow. */
    private class GlowDrawable extends Drawable {
        private final Paint base = new Paint();
        private final Paint glow = new Paint();

        GlowDrawable() {
            base.setColor(BG);
            glow.setAntiAlias(true);
        }

        @Override public void draw(Canvas c) {
            c.drawRect(getBounds(), base);
            float w = getBounds().width(), h = getBounds().height();
            glow.setShader(new RadialGradient(w * 0.78f, -h * 0.18f, w * 0.62f,
                    new int[]{0x1A7CC4FF, 0x0A3D8BFF, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(getBounds(), glow);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
