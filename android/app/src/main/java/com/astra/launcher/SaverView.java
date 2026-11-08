package com.astra.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Ambient wallpaper for the screensaver.
 *
 * <p>Two modes, chosen by whichever content is available:
 * a photo slideshow (cross-fade + slow Ken Burns drift) when the user has images
 * in their Astra folder, otherwise a procedural aurora built from radial gradients.
 * Both stay deliberately dark so the clock stays legible.
 */
public class SaverView extends View {

    private static final int PHOTO_HOLD_MS = 22000;
    private static final int PHOTO_FADE_MS = 2600;
    private static final int AURORA_PERIOD_MS = 46000;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint scrim = new Paint();
    private final Matrix matrix = new Matrix();

    private final List<Bitmap> photos = new ArrayList<>();
    private final List<float[]> stars = new ArrayList<>();

    private Bitmap auroraA;
    private Bitmap auroraB;

    private boolean running;
    private boolean black;
    private long origin;
    private int index;
    private long switchedAt;

    public SaverView(Context context) {
        super(context);
        paint.setFilterBitmap(true);
        scrim.setColor(0x66070A0F);
        setBackgroundColor(0xFF07090C);
        buildStars();
    }

    public void setPhotos(List<Bitmap> list) {
        photos.clear();
        if (list != null) photos.addAll(list);
        index = 0;
        switchedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    public boolean hasPhotos() {
        return !photos.isEmpty();
    }

    public void setBlack(boolean value) {
        black = value;
        invalidate();
    }

    public void start() {
        running = true;
        origin = SystemClock.uptimeMillis();
        switchedAt = origin;
        postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
    }

    /* ------------------------------------------------------------------ */
    /* Drawing                                                            */
    /* ------------------------------------------------------------------ */
    @Override
    protected void onDraw(Canvas c) {
        long t = SystemClock.uptimeMillis() - origin;

        if (!black) {
            if (!photos.isEmpty()) drawPhoto(c, t);
            else drawAurora(c, t);
        } else {
            c.drawColor(0xFF000000);
        }
        drawVignette(c);

        if (running) postInvalidateOnAnimation();
    }

    private void drawAurora(Canvas c, long t) {
        if (auroraA == null) {
            auroraA = aurora(0x9F3E5C, 0x3B, 0x2A, 0x7CC4FF, 0x2B3F8F);
            auroraB = aurora(0x6E2A5B, 0x74, 0x6C, 0x1E7A8A, 0x5A3E8C);
        }

        float phase = (float) ((t % AURORA_PERIOD_MS) / (double) AURORA_PERIOD_MS);
        float breath = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2));

        paint.setAlpha(255);
        paint.setShader(null);
        c.drawColor(0xFF070A0F);

        drawLayer(c, auroraA, t, 1.14f);
        paint.setAlpha((int) (70 + 120 * breath));
        drawLayer(c, auroraB, t + 15000, 1.20f);
        paint.setAlpha(255);

        drawStars(c, t);
    }

    private void drawLayer(Canvas c, Bitmap bmp, long t, float zoom) {
        float w = getWidth(), h = getHeight();
        float scale = Math.max(w / bmp.getWidth(), h / bmp.getHeight()) * zoom;
        float dx = (float) Math.sin(t / 21000.0) * w * 0.045f;
        float dy = (float) Math.cos(t / 27000.0) * h * 0.05f;

        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate((w - bmp.getWidth() * scale) / 2f + dx,
                (h - bmp.getHeight() * scale) / 2f + dy);
        c.drawBitmap(bmp, matrix, paint);
    }

    private void drawStars(Canvas c, long t) {
        paint.setShader(null);
        float w = getWidth(), h = getHeight();
        for (int i = 0; i < stars.size(); i++) {
            float[] s = stars.get(i);
            float drift = (float) Math.sin(t / 34000.0 + i) * 6f;
            paint.setColor(Color.argb(s[2] > 0.8f ? 120 : 70, 220, 232, 245));
            c.drawCircle(s[0] * w + drift, s[1] * h, s[2] * 1.6f + 0.5f, paint);
        }
        paint.setAlpha(255);
    }

    private void drawPhoto(Canvas c, long t) {
        if (t - switchedAt > PHOTO_HOLD_MS) {
            switchedAt = t;
            index = (index + 1) % photos.size();
        }
        long since = t - switchedAt;
        float fade = Math.min(1f, since / (float) PHOTO_FADE_MS);

        int prev = (index - 1 + photos.size()) % photos.size();
        drawPhoto(c, photos.get(prev), 1f, 1.05f);
        drawPhoto(c, photos.get(index), fade, 1f + 0.05f * (since / (float) PHOTO_HOLD_MS));

        paint.setAlpha(255);
        scrim.setColor(0x5A070A0F);
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
    }

    private void drawPhoto(Canvas c, Bitmap bmp, float alpha, float zoom) {
        float w = getWidth(), h = getHeight();
        float scale = Math.max(w / bmp.getWidth(), h / bmp.getHeight()) * zoom;

        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate((w - bmp.getWidth() * scale) / 2f,
                (h - bmp.getHeight() * scale) / 2f);
        paint.setAlpha((int) (255 * alpha));
        c.drawBitmap(bmp, matrix, paint);
    }

    private void drawVignette(Canvas c) {
        float w = getWidth(), h = getHeight();
        RadialGradient g = new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.72f,
                new int[]{0x00000000, 0x00000000, 0x99000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        paint.setAlpha(255);
        paint.setShader(g);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    /* ------------------------------------------------------------------ */
    /* Procedural layer                                                   */
    /* ------------------------------------------------------------------ */
    private Bitmap aurora(int c1, int x1, int y1, int c2, int c3) {
        int w = 480, h = 270;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF05070A);

        blob(c, w * (x1 / 255f), h * (y1 / 255f), h * 1.05f, c1, 90);
        blob(c, w * 0.82f, h * 0.22f, h * 0.78f, c2, 64);
        blob(c, w * 0.18f, h * 0.88f, h * 0.62f, c3, 52);
        return b;
    }

    private void blob(Canvas c, float x, float y, float r, int color, int alpha) {
        int core = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        RadialGradient g = new RadialGradient(x, y, r, core, 0x00000000, Shader.TileMode.CLAMP);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(g);
        c.drawRect(0, 0, c.getWidth(), c.getHeight(), p);
    }

    private void buildStars() {
        long seed = 0x5EED1A5L;
        for (int i = 0; i < 110; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float x = ((seed >>> 16) & 0xFFFF) / 65535f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float y = ((seed >>> 16) & 0xFFFF) / 65535f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float b = ((seed >>> 24) & 0xFF) / 255f;
            stars.add(new float[]{x, y, b});
        }
    }
}
