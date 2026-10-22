package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * An app's icon as the full-bleed square an icon tile shows (icon_tiles in config.xml); the tile's outline rounds it.
 * Drawn once into a bitmap when the tiles load, so the grid scrolls over plain bitmaps, as it does over banners.
 */
final class IconArt {
    private IconArt() { }

    /** Share of the tile a legacy icon with its own shape (transparent corners) takes, centred on the backdrop. */
    private static final float INSET_ICON = 0.72f;

    /**
     * @param icon     the app's icon, best loaded at a high density
     * @param size     the bitmap's side in px
     * @param backdrop drawn behind an icon that does not cover the square by itself
     */
    static Bitmap render(Drawable icon, int size, int backdrop) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        if (icon instanceof AdaptiveIconDrawable) {
            // Both layers are 108dp and launchers show their inner 72dp: grow the bounds by a quarter of the tile on
            // every side so that inner part fills it, with no system mask (a circle on some TVs) cutting it.
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            int pad = size / 4;
            Rect bounds = new Rect(-pad, -pad, size + pad, size + pad);
            Drawable bg = a.getBackground();
            Drawable fg = a.getForeground();
            if (bg != null) {
                bg.setBounds(bounds);
                bg.draw(c);
            } else {
                c.drawColor(backdrop);
            }
            if (fg != null) {
                fg.setBounds(bounds);
                fg.draw(c);
            }
            return bmp;
        }
        // A legacy icon: full bleed when it is a square already (opaque corners), otherwise its own shape (a circle,
        // a rounded square with margins) centred on the backdrop so the tile keeps one shape for every app.
        icon.setBounds(0, 0, size, size);
        icon.draw(c);
        if (opaqueCorners(bmp)) return bmp;
        bmp.eraseColor(backdrop);
        int inset = Math.round(size * (1 - INSET_ICON) / 2);
        icon.setBounds(inset, inset, size - inset, size - inset);
        icon.draw(c);
        return bmp;
    }

    private static boolean opaqueCorners(Bitmap bmp) {
        int m = bmp.getWidth() - 1;
        int k = Math.max(1, bmp.getWidth() / 32);   // a little in from the edge, past any anti-aliased rim
        return Color.alpha(bmp.getPixel(k, k)) > 200 && Color.alpha(bmp.getPixel(m - k, k)) > 200
                && Color.alpha(bmp.getPixel(k, m - k)) > 200 && Color.alpha(bmp.getPixel(m - k, m - k)) > 200;
    }
}
