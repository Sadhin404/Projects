package com.shadhinkhan.grabit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * v65: full-screen moving ambient light for the whole app - soft
 * purple/blue/pink light sources orbit slowly behind every screen
 * (clockwise, seamless loop), like the reference GIF he sent.
 * Pure Android Canvas radial gradients - the native equivalent of
 * the WebGL/Three.js aurora effect he asked for; no layout changes,
 * this view only ever sits BEHIND the real UI.
 *
 * Each blob's gradient shader is built once per size; per frame we
 * only translate the canvas and redraw, so it stays smooth.
 */
public class AuroraView extends View {

    // blob definitions: rgb, base alpha (dark theme), orbit centre
    // (fractions of w/h), orbit radius (fractions), gradient radius
    // (fraction of w), loops per cycle, phase offset, direction
    private static class Blob {
        int rgb, alpha;
        float cx, cy, ox, oy, gr;
        int loops;
        float phase;
        boolean ccw;
        Paint paint;
        Blob(int rgb, int alpha, float cx, float cy, float ox, float oy,
             float gr, int loops, float phase, boolean ccw) {
            this.rgb = rgb; this.alpha = alpha;
            this.cx = cx; this.cy = cy; this.ox = ox; this.oy = oy;
            this.gr = gr; this.loops = loops; this.phase = phase; this.ccw = ccw;
        }
    }

    private final Blob[] blobs;
    private final boolean light;
    private ValueAnimator anim;
    private float t; // 0..1 cycle phase

    public AuroraView(Context ctx, boolean lightTheme) {
        super(ctx);
        light = lightTheme;
        setClickable(false);
        setFocusable(false);
        blobs = new Blob[]{
            // main violet light, slow clockwise orbit across the top
            new Blob(Color.rgb(139, 92, 246), 165, 0.30f, 0.20f, 0.40f, 0.13f, 0.62f, 1, 0.00f, false),
            // deep blue light, counter-orbit lower half
            new Blob(Color.rgb(76, 110, 245), 130, 0.72f, 0.68f, 0.32f, 0.16f, 0.55f, 1, 0.45f, true),
            // pink-magenta accent, smaller, two loops per cycle
            new Blob(Color.rgb(217, 70, 239), 85, 0.62f, 0.14f, 0.26f, 0.09f, 0.40f, 2, 0.20f, false),
            // soft white-violet sheen sweeping near the top (reflection)
            new Blob(Color.rgb(196, 181, 253), 55, 0.50f, 0.06f, 0.46f, 0.05f, 0.46f, 1, 0.70f, false),
        };
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0) return;
        for (Blob b : blobs) {
            float r = b.gr * w;
            int start = Color.argb(255, Color.red(b.rgb), Color.green(b.rgb), Color.blue(b.rgb));
            int end = Color.argb(0, Color.red(b.rgb), Color.green(b.rgb), Color.blue(b.rgb));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(0, 0, r, start, end, Shader.TileMode.CLAMP));
            b.paint = p;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float alphaScale = light ? 0.45f : 1f;
        for (Blob b : blobs) {
            if (b.paint == null) continue;
            double ang = 2 * Math.PI * (t * b.loops + b.phase);
            if (b.ccw) ang = -ang;
            float x = (b.cx + b.ox * (float) Math.cos(ang)) * w;
            float y = (b.cy + b.oy * (float) Math.sin(ang)) * h;
            // gentle breathing: alpha pulses +-20% over the cycle
            float pulse = 0.85f + 0.30f * (float) Math.sin(2 * Math.PI * (t * b.loops + b.phase));
            b.paint.setAlpha(Math.max(0, Math.min(255, (int) (b.alpha * alphaScale * pulse))));
            float r = b.gr * w;
            canvas.save();
            canvas.translate(x, y);
            canvas.drawRect(-r, -r, r, r, b.paint);
            canvas.restore();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(18000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { t = (Float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) { anim.cancel(); anim = null; }
        super.onDetachedFromWindow();
    }
}
