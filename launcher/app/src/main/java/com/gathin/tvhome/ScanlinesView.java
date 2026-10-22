package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * CRT scanlines over the whole of Home for the hacker skin (skin_scanline in skin.xml): one darker pixel row in every
 * three. A 1x3 bitmap repeated by a shader, so each frame that redraws it is a single rect, never one line at a time.
 * Takes no focus and no touches.
 */
public class ScanlinesView extends View {

    private final Paint paint = new Paint();

    public ScanlinesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Bitmap tile = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888);
        tile.setPixel(0, 2, context.getColor(R.color.skin_scanline));
        paint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPaint(paint);
    }
}
