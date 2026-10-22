package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * One line of text. When it fits, it simply sits at the start. When it does not, it scrolls right to left forever like
 * a news ticker (the text, a gap, the text again, so the loop has no seam and no pause) with faded ends, and only while
 * running (NowPlaying runs it while Home is started); stopped, it holds still. Its own small layer, so a scrolling
 * frame redraws the bar and nothing else.
 */
public class TickerView extends View {

    /** Scroll speed, in dp per second. */
    private static final float SPEED_DP = 55f;
    private static final String GAP = "      •      ";

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fade = new Paint();
    private final float speed;
    private final float fadeWidth;
    private String text = "";
    /** The text plus the gap: one period of the loop, in px. */
    private float period;
    /** The text alone, in px: wider than the view means it scrolls. */
    private float textWidth;
    private float offset;
    private boolean running;
    private long lastFrame;

    public TickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        speed = SPEED_DP * density;
        fadeWidth = 18 * density;
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, getResources().getDisplayMetrics()));
        paint.setLetterSpacing(0.04f);
        paint.setColor(context.getColor(R.color.skin_ticker_text));
        paint.setTypeface(getResources().getFont(R.font.outfit_medium));
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        setLayerType(LAYER_TYPE_HARDWARE, null);   // the fades erase the layer's edges, not the bar behind it
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setText(String value) {
        if (value.equals(text)) return;
        text = value;
        period = paint.measureText(text + GAP);
        textWidth = paint.measureText(text);
        offset = 0f;
        setContentDescription(text);
        invalidate();
    }

    void setRunning(boolean run) {
        if (run == running) return;
        running = run;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int clear = 0x00000000, solid = 0xFF000000;
        fade.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{solid, clear, clear, solid},
                new float[]{0f, fadeWidth / Math.max(1, w), 1f - fadeWidth / Math.max(1, w), 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (text.isEmpty() || period <= 0) return;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = (getHeight() - fm.ascent - fm.descent) / 2f;
        if (textWidth <= getWidth()) {
            offset = 0f;
            canvas.drawText(text, 0, baseline, paint);
            return;
        }
        String loop = text + GAP;
        for (float x = -offset; x < getWidth(); x += period) canvas.drawText(loop, x, baseline, paint);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fade);
        if (!running || !isShown()) return;
        long now = SystemClock.uptimeMillis();
        offset = (offset + speed * (now - lastFrame) / 1000f) % period;
        lastFrame = now;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        lastFrame = SystemClock.uptimeMillis();   // no jump for the time spent hidden
        if (visibility == VISIBLE) invalidate();
    }
}
