package io.github.liuran001.mmliquidglass;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Neutral material for the Compose draw slot; never samples or repaints the tabs. */
final class PlayBarSurface {
    private final RectF bar = new RectF();
    private final RectF capsule = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cachedHeight;
    private int cachedAlpha;
    private boolean cachedNight;

    PlayBarSurface() {
        rim.setStyle(Paint.Style.STROKE);
    }

    void draw(Canvas canvas, float width, float height, float slot, float scale,
              float density, boolean night) {
        float inset = Math.min(4f * density, height / 12f);
        float cell = width / 5f;
        float radius = (height - 2f * inset) * .5f;
        bar.set(0f, inset, width, height - inset);
        int tintAlpha = Math.round(220f * clamp(GlassConfig.tintStrength, .85f, 1.12f));
        updateSurface(height, inset, tintAlpha, night);
        canvas.drawRoundRect(bar, radius, radius, fill);

        // Scale only the material. Native labels cannot escape an artificial tab
        // clip because their layout and draw pass never enter this canvas save.
        float cx = (slot + .5f) * cell;
        float cy = height * .5f;
        float halfW = (cell * .5f - inset) * scale;
        float halfH = (radius - inset) * scale;
        capsule.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
        float r = Math.min(halfW, halfH);
        if (r <= 0f) return;
        float press = clamp((scale - 1f) / .39f, 0f, 1f);
        wash.setColor(night ? 0xFFFFFFFF : 0xFF000000);
        wash.setAlpha(night ? 29 + Math.round(21f * press) : 17 + Math.round(10f * press));
        canvas.drawRoundRect(capsule, r, r, wash);
        shine.setColor(0xFFFFFFFF);
        shine.setAlpha(Math.round((night ? 9f : 17f) * clamp(GlassConfig.glossStrength, 0f, 2f)));
        canvas.drawRoundRect(capsule, r, r, shine);
        rim.setColor(night ? 0x48FFFFFF : 0xA8FFFFFF);
        rim.setStrokeWidth(Math.max(1f, .65f * density
                * clamp(GlassConfig.borderWidthScale, .5f, 2f)));
        canvas.drawRoundRect(capsule, r, r, rim);
    }

    private void updateSurface(float height, float inset, int alpha, boolean night) {
        if (height == cachedHeight && alpha == cachedAlpha && night == cachedNight) return;
        cachedHeight = height;
        cachedAlpha = alpha;
        cachedNight = night;
        fill.setShader(new LinearGradient(0f, inset, 0f, height - inset,
                (alpha << 24) | (night ? 0x27272A : 0xFAFAFC),
                (Math.min(255, alpha + 14) << 24) | (night ? 0x151517 : 0xEBEBEF),
                Shader.TileMode.CLAMP));
    }

    private static float clamp(float value, float low, float high) {
        return Float.isFinite(value) ? Math.max(low, Math.min(high, value)) : 1f;
    }
}