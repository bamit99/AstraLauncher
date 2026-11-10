package com.astra.launcher;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Focus treatment shared by every focusable surface: accent ring, small scale and elevation.
 *
 * <p>The rest background is captured once and restored exactly on blur, so a row that rests on
 * {@code raised} or on nothing at all never keeps the card surface after the focus passes through
 * it.
 */
final class FocusSurface {

    private static final float SCALE = 1.03f;
    private static final float SCALE_PILL = 1.06f;

    private FocusSurface() { }

    static void attach(View v, float radiusDp) {
        attach(v, radiusDp, false, null);
    }

    static void attach(View v, float radiusDp, View.OnFocusChangeListener extra) {
        attach(v, radiusDp, false, extra);
    }

    static void attachPill(View v, float radiusDp) {
        attach(v, radiusDp, true, null);
    }

    static void attachPill(View v, float radiusDp, View.OnFocusChangeListener extra) {
        attach(v, radiusDp, true, extra);
    }

    private static void attach(final View v, final float radiusDp, final boolean pill,
                               final View.OnFocusChangeListener extra) {
        final Drawable rest = v.getBackground();
        final float restElevation = v.getElevation();
        final float density = v.getResources().getDisplayMetrics().density;
        final float scale = pill ? SCALE_PILL : SCALE;

        v.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(focused
                    ? (pill ? Palette.focusPill(density, radiusDp)
                            : Palette.focusCard(density, radiusDp))
                    : rest);
            view.animate()
                    .scaleX(focused ? scale : 1f)
                    .scaleY(focused ? scale : 1f)
                    .setDuration(focused ? 150 : 120)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            view.setElevation(focused ? 10 * density : restElevation);
            if (extra != null) extra.onFocusChange(view, focused);
        });
    }
}
