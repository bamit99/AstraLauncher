package com.astra.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
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

import java.io.File;
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
    private List<String> pins = new ArrayList<>();
    private SharedPreferences prefs;
    private TextView netView;
    private TextView panelNetValue;
    private TextView panelEthValue;
    private TextView searchEmpty;
    private View homeFirstFocus;
    private View panelTrigger;
    private int category = 0;
    private BaseAdapter gridAdapter;
    private List<AppEntry> gridData = new ArrayList<>();
    private boolean panelOpen = false;
    private boolean searchOpen = false;
    private String screen = "home";

    /* Screensaver / ambient */
    private FrameLayout saverOverlay;
    private SaverView saverView;
    private TextView saverClock;
    private TextView saverDate;
    private boolean saverVisible = false;
    private View focusBeforeSaver;
    private final ArrayList<Bitmap> saverPhotos = new ArrayList<>();
    private boolean photosLoading = false;

    private static final String[] PHOTO_DIRS = {
            "/sdcard/Pictures/Astra", "/sdcard/DCIM/Astra", "/sdcard/Astra"
    };

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
        prefs = getSharedPreferences("astra", MODE_PRIVATE);
        pins = loadPins();

        root = new FrameLayout(this);
        root.setBackground(new GlowDrawable());

        contentHost = new FrameLayout(this);
        root.addView(contentHost, match());

        buildSearch();
        buildPanel();
        buildSaver();
        root.addView(searchOverlay, match());
        root.addView(panelOverlay, match());
        root.addView(saverOverlay, match());

        buildHome();
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tickClock();
        updateNetwork();
        handler.postDelayed(clockTick, 15000);
        resetIdle();
        if ("photos".equals(prefs.getString("wallpaper", "astra"))) loadPhotos();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(idleRunnable);
        handler.removeCallbacks(saverTick);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (saverVisible) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) hideScreensaver();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) resetIdle();
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (saverVisible) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) hideScreensaver();
            return true;
        }
        resetIdle();
        return super.dispatchTouchEvent(e);
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            tickClock();
            handler.postDelayed(this, 15000);
        }
    };

    private void tickClock() {
        if (clockView != null) {
            clockView.setText(new SimpleDateFormat(clockPattern(), Locale.getDefault()).format(new Date()));
        }
        updateNetwork();
    }

    private String clockPattern() {
        return prefs.getBoolean("clock24", true) ? "HH:mm" : "h:mm";
    }

    /* ------------------------------------------------------------------ */
    /* Screensaver / ambient                                              */
    /* ------------------------------------------------------------------ */
    private final Runnable idleRunnable = new Runnable() {
        @Override public void run() { showScreensaver(); }
    };

    private final Runnable saverTick = new Runnable() {
        @Override public void run() {
            updateSaverClock();
            handler.postDelayed(this, 5000);
        }
    };

    private void buildSaver() {
        saverOverlay = new FrameLayout(this);
        saverOverlay.setBackgroundColor(0xFF000000);
        saverOverlay.setVisibility(View.GONE);

        saverView = new SaverView(this);
        saverOverlay.addView(saverView, match());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView brand = text("ASTRA", 12, 0x887CC4FF, Typeface.BOLD);
        brand.setLetterSpacing(0.42f);
        brand.setGravity(Gravity.CENTER);
        col.addView(brand);

        saverClock = text("--:--", 104, TX, Typeface.NORMAL);
        saverClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        saverClock.setGravity(Gravity.CENTER);
        saverClock.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams clp = wrap();
        clp.topMargin = dp(6);
        col.addView(saverClock, clp);

        saverDate = text("", 19, TX2, Typeface.NORMAL);
        saverDate.setGravity(Gravity.CENTER);
        saverDate.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams dlp = wrap();
        dlp.topMargin = dp(2);
        col.addView(saverDate, dlp);

        FrameLayout.LayoutParams cfp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cfp.gravity = Gravity.CENTER;
        saverOverlay.addView(col, cfp);

        TextView hint = text("Press any key to return", 12, TX3, Typeface.NORMAL);
        hint.setLetterSpacing(0.06f);
        FrameLayout.LayoutParams hfp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hfp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hfp.bottomMargin = dp(34);
        saverOverlay.addView(hint, hfp);
    }

    private int saverMinutes() {
        return prefs.getInt("saver_minutes", 2);
    }

    private void resetIdle() {
        handler.removeCallbacks(idleRunnable);
        int min = saverMinutes();
        if (min > 0 && !saverVisible) {
            handler.postDelayed(idleRunnable, min * 60000L);
        }
    }

    private void updateSaverClock() {
        Date now = new Date();
        if (saverClock != null) {
            saverClock.setText(new SimpleDateFormat(clockPattern(), Locale.getDefault()).format(now));
        }
        if (saverDate != null) {
            saverDate.setText(new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now));
        }
    }

    private void showScreensaver() {
        if (saverVisible || saverOverlay == null) return;
        saverVisible = true;
        handler.removeCallbacks(idleRunnable);
        focusBeforeSaver = getCurrentFocus();

        boolean black = "black".equals(prefs.getString("saver_style", "wallpaper"));
        saverView.setBlack(black);
        saverView.setPhotos(saverPhotos);
        updateSaverClock();

        saverOverlay.setAlpha(0f);
        saverOverlay.setVisibility(View.VISIBLE);
        saverOverlay.animate().alpha(1f).setDuration(700).start();
        saverView.start();
        handler.postDelayed(saverTick, 5000);
    }

    private void hideScreensaver() {
        if (!saverVisible) return;
        saverVisible = false;
        handler.removeCallbacks(saverTick);
        saverView.stop();
        saverOverlay.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            if (!saverVisible) saverOverlay.setVisibility(View.GONE);
        }).start();
        if (focusBeforeSaver != null && focusBeforeSaver.isShown()) {
            focusBeforeSaver.requestFocus();
        } else if (homeFirstFocus != null) {
            homeFirstFocus.requestFocus();
        }
        resetIdle();
    }

    /* ------------------------------------------------------------------ */
    /* Photo wallpapers                                                   */
    /* ------------------------------------------------------------------ */
    private List<File> photoFiles() {
        List<File> out = new ArrayList<>();
        for (String dir : PHOTO_DIRS) {
            File d = new File(dir);
            File[] files = d.listFiles();
            if (files == null) continue;
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.getDefault());
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                        || n.endsWith(".webp")) {
                    out.add(f);
                }
            }
            if (!out.isEmpty()) break;
        }
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private boolean hasPhotoPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPhotoPermission() {
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{perm}, 71);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != 71) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            loadPhotos();
        } else {
            prefs.edit().putString("wallpaper", "astra").apply();
            toast("Photo access denied — using Astra wallpaper");
            if ("settings".equals(screen)) showSettings();
        }
    }

    private void loadPhotos() {
        if (photosLoading) return;
        if (!hasPhotoPermission()) {
            requestPhotoPermission();
            return;
        }
        photosLoading = true;
        new Thread(() -> {
            List<Bitmap> out = new ArrayList<>();
            for (File f : photoFiles()) {
                if (out.size() >= 10) break;
                Bitmap b = decodeWallpaper(f);
                if (b != null) out.add(b);
            }
            final List<Bitmap> loaded = out;
            runOnUiThread(() -> {
                photosLoading = false;
                saverPhotos.clear();
                saverPhotos.addAll(loaded);
                if (saverView != null) saverView.setPhotos(saverPhotos);
                if (loaded.isEmpty()) {
                    toast("No photos in Pictures/Astra — using Astra wallpaper");
                }
            });
        }, "astra-photos").start();
    }

    private Bitmap decodeWallpaper(File f) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Settings                                                           */
    /* ------------------------------------------------------------------ */
    private static final int[] SAVER_CHOICES = {0, 1, 2, 5, 10, 20};

    private int settingsIndex = 0;

    private void showSettings() {
        if (panelOpen) hidePanel(false);
        contentHost.removeAllViews();
        screen = "settings";

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(28), dp(64), dp(24));
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Settings", 30, TX, Typeface.BOLD));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(text("Astra", 13, TX3, Typeface.NORMAL));
        col.addView(head);

        TextView sub = text("Changes are saved immediately.", 13, TX3, Typeface.NORMAL);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(4);
        col.addView(sub, slp);

        final View[] rows = new View[6];

        // Screensaver timeout
        final int min = saverMinutes();
        rows[0] = settingRow("Screensaver", saverLabel(min), (v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int step = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                    : code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
            if (step == 0) return false;
            int next = cycleSaver(min, step);
            prefs.edit().putInt("saver_minutes", next).apply();
            settingsIndex = 0;
            showSettings();
            return true;
        }, null);

        // Style
        final boolean black = "black".equals(prefs.getString("saver_style", "wallpaper"));
        rows[1] = settingRow("Screensaver style", black ? "Black" : "Wallpaper", (v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            prefs.edit().putString("saver_style", black ? "wallpaper" : "black").apply();
            settingsIndex = 1;
            showSettings();
            return true;
        }, null);

        // Clock format
        final boolean clock24 = prefs.getBoolean("clock24", true);
        rows[2] = settingRow("Clock", clock24 ? "24-hour" : "12-hour", (v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            prefs.edit().putBoolean("clock24", !clock24).apply();
            tickClock();
            settingsIndex = 2;
            showSettings();
            return true;
        }, null);

        // Wallpaper source
        final String source = prefs.getString("wallpaper", "astra");
        rows[3] = settingRow("Wallpaper", "photos".equals(source) ? "Photo folder" : "Astra",
                (v, code, e) -> {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                    prefs.edit().putString("wallpaper", "photos".equals(source) ? "astra" : "photos").apply();
                    if ("photos".equals(prefs.getString("wallpaper", "astra"))) loadPhotos();
                    settingsIndex = 3;
                    showSettings();
                    return true;
                }, null);

        // Preview
        rows[4] = settingRow("Preview screensaver", "Start now", null, v -> {
            settingsIndex = 4;
            showScreensaver();
        });

        // Reset
        rows[5] = settingRow("Reset preferences", "Restore defaults", null, v -> {
            prefs.edit()
                    .remove("saver_minutes")
                    .remove("saver_style")
                    .remove("clock24")
                    .remove("wallpaper")
                    .apply();
            settingsIndex = 5;
            toast("Preferences reset");
            showSettings();
        });

        for (int i = 0; i < rows.length; i++) {
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
            rlp.topMargin = i == 0 ? dp(24) : dp(10);
            col.addView(rows[i], rlp);
        }

        TextView about = text("Astra " + versionName() + " · MIT · github.com/bamit99/AstraLauncher",
                12, TX3, Typeface.NORMAL);
        LinearLayout.LayoutParams alp = wrap();
        alp.topMargin = dp(26);
        col.addView(about, alp);

        contentHost.addView(scroll, match());
        final int focus = settingsIndex;
        contentHost.post(() -> {
            View target = focus >= 0 && focus < rows.length ? rows[focus] : rows[0];
            if (target != null) target.requestFocus();
        });
    }

    private String saverLabel(int minutes) {
        if (minutes <= 0) return "Off";
        return minutes + " min";
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private int cycleSaver(int current, int step) {
        int idx = 0;
        for (int i = 0; i < SAVER_CHOICES.length; i++) {
            if (SAVER_CHOICES[i] == current) { idx = i; break; }
        }
        idx = (idx + step + SAVER_CHOICES.length) % SAVER_CHOICES.length;
        return SAVER_CHOICES[idx];
    }

    private View settingRow(String label, String value,
                            View.OnKeyListener keys, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(dp(20), 0, dp(20), 0);
        row.setBackground(rounded(RAISED, 14, LINE, 1));
        row.setContentDescription(label + ", " + value);

        TextView l = text(label, 16, TX, Typeface.NORMAL);
        row.addView(l);
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(text(value, 14, ACCENT, Typeface.NORMAL));

        row.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 14));
        if (keys != null) row.setOnKeyListener(keys);
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    /* ------------------------------------------------------------------ */
    /* Network status                                                     */
    /* ------------------------------------------------------------------ */
    private NetworkCapabilities activeCaps() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network n = cm.getActiveNetwork();
        if (n == null) return null;
        return cm.getNetworkCapabilities(n);
    }

    private String networkLabel() {
        NetworkCapabilities c = activeCaps();
        if (c == null) return "Offline";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile";
        return "Online";
    }

    private String networkDetail() {
        NetworkCapabilities c = activeCaps();
        if (c == null) return "Not connected";
        String label = networkLabel();
        return "Connected · " + label;
    }

    private void updateNetwork() {
        String label = networkLabel();
        if (netView != null) netView.setText(label);
        if (panelNetValue != null) panelNetValue.setText(networkDetail());
        if (panelEthValue != null) {
            panelEthValue.setText("Ethernet".equals(label) ? "Connected" : "Not connected");
        }
    }

    private void refreshApps() {
        apps = loadApps(getPackageManager());
        pins = loadPins();
        toast("App list refreshed");
        if ("apps".equals(screen)) showApps(); else buildHome();
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(178));
        hlp.topMargin = dp(22);
        hero.setLayoutParams(hlp);
        hero.setFocusable(true);
        hero.setBackground(heroBg(false));
        hero.setPadding(dp(30), dp(20), dp(30), dp(20));

        LinearLayout heroCol = new LinearLayout(this);
        heroCol.setOrientation(LinearLayout.VERTICAL);
        heroCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView kicker = text("QUICK START", 11, ACCENT, Typeface.BOLD);
        kicker.setLetterSpacing(0.18f);
        heroCol.addView(kicker);

        TextView title = text(heroApp == null ? "Your apps" : heroApp.label, 28, TX, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = dp(6);
        heroCol.addView(title, tlp);

        TextView sub = text("Open your media apps in one press", 14, TX2, Typeface.NORMAL);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(4);
        heroCol.addView(sub, slp);

        TextView resume = text("▶  Open", 13, ACCENT, Typeface.BOLD);
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
        for (String pkg : pins) if (byPkg(pkg) != null) pinnedCount++;
        col.addView(sectionHeader("PINNED", pinnedCount + " apps"));
        LinearLayout pinnedRow = new LinearLayout(this);
        pinnedRow.setOrientation(LinearLayout.HORIZONTAL);
        pinnedRow.setPadding(0, dp(2), 0, dp(6));

        boolean first = true;
        for (String pkg : pins) {
            AppEntry e = byPkg(pkg);
            if (e == null) continue;
            View tile = appTile(e, true);
            pinnedRow.addView(tile);
            if (first) { homeFirstFocus = tile; first = false; }
        }
        if (pinnedRow.getChildCount() == 0) {
            View hint = actionTile("Pin apps", "+", this::showApps);
            pinnedRow.addView(hint);
            homeFirstFocus = hint;
            first = false;
        }
        View allTile = actionTile("All apps", "⌗", this::showApps);
        pinnedRow.addView(allTile);
        if (homeFirstFocus == null) homeFirstFocus = allTile;
        fitRow(pinnedRow);
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
        fitRow(mediaRow);
        col.addView(hscroll(mediaRow));

        contentHost.addView(scroll, match());
        contentHost.post(() -> { if (homeFirstFocus != null) homeFirstFocus.requestFocus(); });
    }

    private boolean isPinned(String pkg) {
        return pins.contains(pkg);
    }

    private List<String> loadPins() {
        String raw = prefs.getString("pins", null);
        List<String> out = new ArrayList<>();
        if (raw == null) {
            for (String p : PINNED) if (byPkg(p) != null) out.add(p);
            savePins(out);
        } else if (!raw.isEmpty()) {
            for (String p : raw.split(",")) if (byPkg(p) != null) out.add(p);
        }
        return out;
    }

    private void savePins(List<String> list) {
        prefs.edit().putString("pins", TextUtils.join(",", list)).apply();
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

        netView = text(networkLabel(), 14, TX2, Typeface.NORMAL);
        LinearLayout.LayoutParams wlp = wrap();
        wlp.leftMargin = dp(18);
        bar.addView(netView, wlp);

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

    /**
     * Size a home row so a whole number of cards ends exactly at the right margin.
     *
     * <p>Fixed-width cards plus a fixed gap left a partial card clipped by the row viewport,
     * which reads as a chopped edge on a TV. Cards shrink only as far as 120 dp; beyond that
     * the row scrolls, and the clip lands in the gap between cards.
     */
    private void fitRow(LinearLayout row) {
        int n = row.getChildCount();
        if (n == 0) return;

        int avail = getResources().getDisplayMetrics().widthPixels - dp(128);
        int gap = dp(14);
        int minW = dp(120);
        int maxW = dp(170);

        int visible = n;
        while (visible > 1 && (avail - (visible - 1) * gap) / visible < minW) visible--;

        int width = Math.min((avail - (visible - 1) * gap) / visible, maxW);
        for (int i = 0; i < n; i++) {
            View v = row.getChildAt(i);
            ViewGroup.LayoutParams raw = v.getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) continue;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.width = width;
            lp.rightMargin = i == n - 1 ? 0 : gap;
            v.setLayoutParams(lp);
        }
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
        tile.setContentDescription(e.label);
        tile.setTag(e.pkg);

        tile.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 18));
        tile.setOnClickListener(v -> launch(e));
        tile.setOnLongClickListener(v -> { showAppMenu(v, e); return true; });
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
        tile.setContentDescription(label);
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

        searchEmpty = text("No apps match that search", 15, TX3, Typeface.NORMAL);
        searchEmpty.setPadding(dp(16), dp(6), 0, 0);
        searchEmpty.setVisibility(View.GONE);
        searchOverlay.addView(searchEmpty);

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
        if (searchEmpty != null) {
            searchEmpty.setVisibility(filterSource.isEmpty() ? View.VISIBLE : View.GONE);
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

        View netRow = panelRow("Network", networkDetail(), intentAction(Settings.ACTION_WIFI_SETTINGS));
        panelNetValue = rowValue(netRow);
        panel.addView(netRow);

        View ethRow = panelRow("Ethernet", "Not connected", null);
        panelEthValue = rowValue(ethRow);
        panel.addView(ethRow);
        panel.addView(panelRow("Date & time", new SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(new Date()), intentAction(Settings.ACTION_DATE_SETTINGS)));
        panel.addView(divider());
        panel.addView(panelRow("HDMI input", "Switch source", intentAction("android.settings.TV_INPUT_SETTINGS")));
        panel.addView(panelRow("Display", "1080p · 60 Hz", intentAction(Settings.ACTION_DISPLAY_SETTINGS)));
        panel.addView(panelRow("Sound", "TV speakers", intentAction(Settings.ACTION_SOUND_SETTINGS)));
        panel.addView(panelRow("Storage", "Manage space", intentAction(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        panel.addView(divider());
        panel.addView(panelActionRow("Launcher settings", "Astra", this::showSettings));
        panel.addView(panelActionRow("Refresh app list", "Rescan installed apps", this::refreshApps));
        panel.addView(panelRow("System settings", "Android TV", intentAction(Settings.ACTION_SETTINGS)));
        panel.addView(panelActionRow("Screensaver", "Start now", this::showScreensaver));
        panel.addView(panelActionRow("Sleep", "Turn display off", this::sleepDevice));
        panel.addView(panelActionRow("Power", "Restart or shut down", this::showPowerDialog));
    }

    private View panelRow(String label, String value, final Intent intent) {
        if (intent == null) return panelActionRow(label, value, null);
        return panelActionRow(label, value, () -> {
            try { startActivity(intent); }
            catch (ActivityNotFoundException ex) { toast("Not available"); }
        });
    }

    /** The secondary (value) TextView of a panel row built by {@link #panelActionRow}. */
    private TextView rowValue(View row) {
        try {
            return (TextView) ((LinearLayout) ((LinearLayout) row).getChildAt(0)).getChildAt(1);
        } catch (Exception e) {
            return null;
        }
    }

    private View panelActionRow(String label, String value, final Runnable action) {
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
            hidePanel(false);
            if (action != null) {
                action.run();
            } else {
                toast(label);
            }
        });
        return row;
    }

    /* ------------------------------------------------------------------ */
    /* Power actions                                                      */
    /* ------------------------------------------------------------------ */
    private static final ComponentName ADMIN_COMPONENT =
            new ComponentName("com.astra.launcher", "com.astra.launcher.AstraAdmin");

    private void sleepDevice() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (dpm == null) { toast("Sleep unavailable"); return; }
        if (!dpm.isAdminActive(ADMIN_COMPONENT)) { showAdminPrompt(); return; }
        try {
            dpm.lockNow();
        } catch (SecurityException ex) {
            toast("Sleep unavailable");
        }
    }

    private void showAdminPrompt() {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shaped(RAISED, LINE, 2));
        card.setPadding(dp(26), dp(24), dp(26), dp(20));
        card.addView(text("Enable sleep", 24, TX, Typeface.BOLD));

        TextView sub = text("Astra needs device-admin access to turn the screen off. It uses only the screen-lock policy.", 13, TX2, Typeface.NORMAL);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(8);
        card.addView(sub, slp);

        TextView activate = dialogButton("Activate", true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        alp.topMargin = dp(18);
        activate.setOnClickListener(v -> {
            dlg.dismiss();
            openAdminSettings();
        });
        card.addView(activate, alp);

        TextView cancel = dialogButton("Cancel", false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        clp.topMargin = dp(10);
        cancel.setOnClickListener(v -> dlg.dismiss());
        card.addView(cancel, clp);

        presentDialog(dlg, card, cancel);
    }

    private void openAdminSettings() {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ADMIN_COMPONENT);
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Astra uses device admin only to turn the screen off.");
        try { startActivity(i); }
        catch (ActivityNotFoundException ex) { toast("No device admin support"); }
    }

    private void showPowerDialog() {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shaped(RAISED, LINE, 2));
        card.setPadding(dp(26), dp(24), dp(26), dp(20));

        card.addView(text("Power", 24, TX, Typeface.BOLD));
        TextView sub = text("Reboot and shut down need root on this device.", 13, TX2, Typeface.NORMAL);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(6);
        card.addView(sub, slp);

        TextView reboot = dialogButton("Reboot", false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        blp.topMargin = dp(18);
        reboot.setOnClickListener(v -> {
            dlg.dismiss();
            runRoot("reboot", "Reboot needs root");
        });
        card.addView(reboot, blp);

        TextView off = dialogButton("Shut down", false);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        olp.topMargin = dp(10);
        off.setOnClickListener(v -> {
            dlg.dismiss();
            runRoot("reboot -p", "Shut down needs root");
        });
        card.addView(off, olp);

        TextView settings = dialogButton("System settings", false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        glp.topMargin = dp(10);
        settings.setOnClickListener(v -> {
            dlg.dismiss();
            try { startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)); }
            catch (ActivityNotFoundException ex) { toast("Not available"); }
        });
        card.addView(settings, glp);

        TextView cancel = dialogButton("Cancel", true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        clp.topMargin = dp(18);
        cancel.setOnClickListener(v -> dlg.dismiss());
        card.addView(cancel, clp);

        presentDialog(dlg, card, cancel);
    }

    private void presentDialog(Dialog dlg, LinearLayout card, View focus) {
        dlg.setContentView(card);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.65f;
            w.setAttributes(lp);
        }
        dlg.show();
        if (w != null) w.setLayout(dp(400), WindowManager.LayoutParams.WRAP_CONTENT);
        focus.post(focus::requestFocus);
    }

    private TextView dialogButton(String label, boolean primary) {
        TextView t = text(label, 16, primary ? ACCENT : TX, primary ? Typeface.BOLD : Typeface.NORMAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(primary ? ACCENT_SOFT : CARD, 14,
                primary ? 0x667CC4FF : LINE, 1));
        t.setFocusable(true);
        t.setOnFocusChangeListener((v, f) -> applyFocus(v, f, 14));
        return t;
    }

    private void runRoot(final String command, final String failMessage) {
        new Thread(() -> {
            boolean ok = false;
            String[] suCandidates = {"/system/xbin/su", "/system/bin/su", "su"};
            for (String su : suCandidates) {
                try {
                    Process p = Runtime.getRuntime().exec(
                            new String[]{su, "0", "sh", "-c", command});
                    ok = p.waitFor() == 0;
                    break;
                } catch (Exception ignored) {
                    ok = false;
                }
            }
            if (!ok) runOnUiThread(() -> toast(failMessage));
        }, "astra-root").start();
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
        hidePanel(true);
    }

    private void hidePanel(boolean restoreFocus) {
        if (!panelOpen) return;
        panelOpen = false;
        panel.animate().translationX(dp(360)).setDuration(180).start();
        panelScrim.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            panelOverlay.setVisibility(View.GONE);
            if (restoreFocus && panelTrigger != null) panelTrigger.requestFocus();
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (saverVisible) { hideScreensaver(); return; }
        if (panelOpen) { hidePanel(); return; }
        if (searchOpen) { hideSearch(); return; }
        if ("settings".equals(screen)) { showHome(); return; }
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

    private void showAppMenu(View anchor, final AppEntry e) {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shaped(RAISED, LINE, 2));
        card.setPadding(dp(26), dp(24), dp(26), dp(20));

        card.addView(text(e.label, 22, TX, Typeface.BOLD));
        TextView sub = text(e.pkg, 12, TX3, Typeface.NORMAL);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(4);
        card.addView(sub, slp);

        TextView open = dialogButton("Open", true);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        olp.topMargin = dp(18);
        open.setOnClickListener(v -> {
            dlg.dismiss();
            launch(e);
        });
        card.addView(open, olp);

        final boolean pinned = isPinned(e.pkg);
        TextView pin = dialogButton(pinned ? "Unpin from Home" : "Pin to Home", false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        plp.topMargin = dp(10);
        pin.setOnClickListener(v -> {
            dlg.dismiss();
            List<String> next = new ArrayList<>(pins);
            if (next.contains(e.pkg)) next.remove(e.pkg); else next.add(e.pkg);
            pins = next;
            savePins(pins);
            toast(next.contains(e.pkg) ? "Pinned " + e.label : "Unpinned " + e.label);
            if ("apps".equals(screen)) showApps(); else buildHome();
            contentHost.post(() -> focusTile(e.pkg));
        });
        card.addView(pin, plp);

        TextView info = dialogButton("App info", false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        ilp.topMargin = dp(10);
        info.setOnClickListener(v -> {
            dlg.dismiss();
            appInfo(e);
        });
        card.addView(info, ilp);

        TextView cancel = dialogButton("Cancel", false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        clp.topMargin = dp(18);
        cancel.setOnClickListener(v -> dlg.dismiss());
        card.addView(cancel, clp);

        presentDialog(dlg, card, cancel);
    }

    /** Restore focus to the tile of a package after a screen rebuild. */
    private void focusTile(String pkg) {
        if (pkg == null || contentHost == null) return;
        View v = contentHost.findViewWithTag(pkg);
        if (v != null) v.requestFocus();
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
