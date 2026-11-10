package com.astra.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One app card: icon in a rounded well, label underneath.
 *
 * <p>Used by the Home shelves and by the app library so both grids share one set of dimensions,
 * truncation rules and focus treatment.
 */
final class AppCardView extends LinearLayout {

    private final float density;
    private final ImageView icon;
    private final TextView label;
    private Runnable open;
    private Runnable menu;

    AppCardView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(8), dp(12), dp(8), dp(10));
        setFocusable(true);
        setBackground(Palette.round(density, Palette.CARD, Palette.RADIUS_CARD, Palette.LINE, 1));

        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, new LayoutParams(dp(40), dp(40)));

        label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTextColor(Palette.TX2);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        label.setPadding(dp(4), dp(8), dp(4), 0);
        addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FocusSurface.attach(this, Palette.RADIUS_CARD, (v, focused) ->
                label.setTextColor(focused ? Palette.TX : Palette.TX2));

        setOnClickListener(v -> { if (open != null) open.run(); });
        setOnLongClickListener(v -> {
            if (menu == null) return false;
            menu.run();
            return true;
        });
    }

    /** Set the card content: display name, package tag and icon. */
    void setApp(String name, String pkg, Drawable appIcon) {
        label.setText(name);
        setContentDescription(name);
        setTag(pkg);
        icon.setImageDrawable(appIcon);
        Palette.clip(icon, 10);
    }

    void setOpen(Runnable action) {
        open = action;
    }

    void setMenu(Runnable action) {
        menu = action;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
