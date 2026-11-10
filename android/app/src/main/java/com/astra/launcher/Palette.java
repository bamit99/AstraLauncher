package com.astra.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * Single source of truth for Astra's design tokens and the few drawable shapes the views share.
 *
 * <p>{@code DESIGN.md} is the specification; this class is the code. Nothing here allocates unless
 * a caller asks for a drawable.
 */
final class Palette {

    static final int BG = 0xFF0B0D10;
    static final int RAISED = 0xFF12151B;
    static final int CARD = 0xFF1A1F27;
    static final int CARD_HI = 0xFF212734;
    static final int LINE = 0xFF252B36;
    static final int TX = 0xFFF5F7FA;
    static final int TX2 = 0xFFA9B2C3;
    static final int TX3 = 0xFF6F7A8C;
    static final int ACCENT = 0xFF7CC4FF;
    static final int ACCENT_SOFT = 0x2E7CC4FF;
    static final int ACCENT_LINE = 0x667CC4FF;
    static final int SUCCESS = 0xFF3DDC97;
    static final int WARNING = 0xFFFFB020;
    static final int DANGER = 0xFFFF6B6B;

    /** Radii used by more than one component. */
    static final float RADIUS_CARD = 20f;
    static final float RADIUS_ROW = 14f;
    static final float RADIUS_PANEL = 24f;
    static final float RADIUS_PILL = 999f;

    private Palette() { }

    static GradientDrawable round(float density, int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusDp * density);
        if (stroke != 0 && strokeDp > 0) g.setStroke(Math.round(strokeDp * density), stroke);
        return g;
    }

    /** Focused card surface: raised fill plus a 2 dp accent ring. */
    static GradientDrawable focusCard(float density, float radiusDp) {
        return round(density, CARD_HI, radiusDp, ACCENT, 2);
    }

    /** Focused pill/button surface: accent tint with an accent hairline. */
    static GradientDrawable focusPill(float density, float radiusDp) {
        return round(density, ACCENT_SOFT, radiusDp, ACCENT_LINE, 1);
    }

    /** Clip a child (icon, thumbnail) to a rounded rectangle. */
    static void clip(final View v, final float radiusDp) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        radiusDp * view.getResources().getDisplayMetrics().density);
            }
        });
    }
}
