package com.astra.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One Quick Panel row: label on the left, the live value on the right.
 *
 * <p>Rows share one height, one focus treatment and one typographic scale. A row whose action the
 * device cannot resolve is shown as unavailable (dimmed, not focusable) instead of pretending to
 * work.
 */
final class QuickPanelRow extends LinearLayout {

    static final int HEIGHT_DP = 46;

    private final float density;
    private final TextView label;
    private final TextView value;
    private android.content.Intent intent;
    private Runnable action;
    private boolean status;

    QuickPanelRow(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(12), 0, dp(12), 0);
        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(HEIGHT_DP)));
        setFocusable(true);
        FocusSurface.attach(this, 12);

        label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTextColor(Palette.TX);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        addView(label, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        value.setTextColor(Palette.TX2);
        value.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        value.setIncludeFontPadding(false);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.END);
        LayoutParams vlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(12);
        addView(value, vlp);
    }

    /** Row target, resolved lazily when the panel is first opened (never on the startup path). */
    void bindIntent(android.content.Intent intent) {
        this.intent = intent;
    }

    void bindAction(Runnable action) {
        this.action = action;
    }

    /** A status reading whose value matters even when its shortcut does not resolve. */
    void markStatus() {
        status = true;
    }

    android.content.Intent intent() {
        return intent;
    }

    Runnable action() {
        return action;
    }

    boolean isStatus() {
        return status;
    }

    /** Read-only presentation: the value stays, the row leaves the focus chain. */
    void setInfo() {
        setFocusable(false);
        setClickable(false);
        label.setTextColor(Palette.TX);
        value.setTextColor(Palette.TX2);
        describe();
    }

    QuickPanelRow set(String labelText, String valueText) {
        label.setText(labelText);
        value.setText(valueText);
        describe();
        return this;
    }

    TextView valueView() {
        return value;
    }

    void setValue(String valueText) {
        value.setText(valueText);
        describe();
    }

    /** Unavailable rows keep their label visible but leave the focus chain and say why. */
    void setAvailable(boolean available) {
        setFocusable(available);
        setClickable(available);
        if (available) return;
        label.setTextColor(Palette.TX3);
        value.setTextColor(Palette.TX3);
        value.setText("Not available");
        describe();
    }

    private void describe() {
        setContentDescription(label.getText() + ", " + value.getText());
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
