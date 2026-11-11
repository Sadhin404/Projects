package com.shadhinkhan.grabit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;

/** Minimal Lottie player built on LottieDrawable directly. The stock
 *  LottieAnimationView extends AppCompatImageView, and this app's
 *  manual build does not carry AppCompat - LottieDrawable needs only
 *  the Lottie core + Okio, both bundled. Used by the splash screen. */
public class LottieView extends View {
    private final LottieDrawable drawable = new LottieDrawable();

    public LottieView(Context ctx) {
        super(ctx);
        drawable.setCallback(this);
    }

    /** Loads a Lottie JSON from res/raw. Returns false on any problem
     *  (caller falls back to a static logo). */
    public boolean loadRaw(int resId) {
        try {
            LottieResult<LottieComposition> r = LottieCompositionFactory.fromRawResSync(getContext(), resId);
            if (r == null || r.getValue() == null) return false;
            drawable.setComposition(r.getValue());
            invalidate();
            return true;
        } catch (Throwable t) { return false; }
    }

    public void play() { try { drawable.start(); } catch (Throwable ignored) {} }
    public void stopAnim() { try { drawable.stop(); } catch (Throwable ignored) {} }
    public float durationMs() { try { LottieComposition c = drawable.getComposition(); return c == null ? 0 : c.getDuration(); } catch (Throwable t) { return 0; } }

    @Override protected boolean verifyDrawable(Drawable who) {
        return who == drawable || super.verifyDrawable(who);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawable.getComposition() == null) return;
        drawable.setBounds(0, 0, getWidth(), getHeight());
        drawable.draw(canvas);
    }

    @Override protected void onDetachedFromWindow() {
        stopAnim();
        super.onDetachedFromWindow();
    }
}
