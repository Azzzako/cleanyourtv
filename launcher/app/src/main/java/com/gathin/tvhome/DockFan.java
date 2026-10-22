package com.gathin.tvhome;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * The dock as a hand of cards (dock_fan in config.xml): the dock's tiles drawn overlapping in a fan around the middle
 * of the screen, each tilted a little more the further it is from the middle, with no band behind them. The focused
 * card comes up out of the hand, straight, larger and in front, with an accent ring; while the dock has focus the
 * other cards are shaded. Only view transforms and a foreground: the grid's layout, focus order and navigation stay
 * those of the plain dock row.
 */
final class DockFan {

    /** The cards' size against the dock tile they are drawn from. */
    private static final float CARD_SCALE = 1.18f;
    /** Distance between neighbouring cards, as a share of a card's width: the rest is hidden under the next card. */
    private static final float SPREAD = 0.74f;
    /** Tilt per step from the middle of the hand, in degrees. */
    private static final float TILT = 5f;
    /** How far the hand dips per step squared at its ends, and how far the focused card rises, in dp. */
    private static final float ARC_DP = 3.5f, LIFT_DP = 30f;
    private static final long DURATION_MS = 200;

    private final RecyclerView grid;
    private final int columns;
    private final float density;
    private final int accent;
    private final boolean animate;
    /** Dock apps actually shown (not the empty slots), read when the hand is laid out. */
    private final java.util.function.IntSupplier dockCount;
    /** Dock position with focus, or -1. */
    private int focused = -1;

    DockFan(RecyclerView grid, int columns, float density, int accent, boolean animate,
            java.util.function.IntSupplier dockCount) {
        this.grid = grid;
        this.columns = columns;
        this.density = density;
        this.accent = accent;
        this.animate = animate;
        this.dockCount = dockCount;
    }

    /** The dock position that now has focus, or -1 when focus is anywhere else. */
    void setFocused(int position) {
        int p = position >= 0 && position < columns ? position : -1;
        if (p == focused) return;
        focused = p;
        apply();
    }

    /** Lays the hand out again, for every dock tile on screen (after a layout, a scroll or a rebind). */
    void apply() {
        for (int i = 0; i < grid.getChildCount(); i++) applyTo(grid.getChildAt(i));
    }

    /** A recycled dock card that came back as a grid tile goes back to plain; dock cards wait for apply(). */
    void onAttached(View item) {
        int pos = grid.getChildAdapterPosition(item);
        View card = item.findViewById(R.id.card);
        if (card != null && (pos < 0 || pos >= columns)) reset(item, card);
    }

    /** One item: a dock card is placed in the fan; any other item (a recycled card) is put back to plain. */
    void applyTo(View item) {
        int pos = grid.getChildAdapterPosition(item);
        View card = item.findViewById(R.id.card);
        if (card == null) return;
        if (pos < 0 || pos >= columns) {
            reset(item, card);
            return;
        }
        // Not laid out yet: positions and sizes are still 0, and a fan computed from them is wrong. The layout
        // listener calls apply() again once they are known.
        if (item.getWidth() == 0 || card.getWidth() == 0 || grid.getWidth() == 0) return;
        int n = Math.max(1, Math.min(columns, dockCount.getAsInt()));
        float mid = (n - 1) / 2f;
        float k = pos - mid;
        float cardW = card.getWidth() * CARD_SCALE;
        float layoutCenter = item.getLeft() + item.getWidth() / 2f;
        float fanCenter = grid.getWidth() / 2f + k * cardW * SPREAD;

        boolean up = pos == focused;
        float tx = fanCenter - layoutCenter;
        float ty = up ? -LIFT_DP * density : k * k * ARC_DP * density;
        float rot = up ? 0f : k * TILT;
        float scale = CARD_SCALE;   // the focused card's own focus zoom adds the rest
        float z = up ? 24 * density : 0f;

        item.setPivotX(item.getWidth() / 2f);
        item.setPivotY(card.getTop() + card.getHeight() / 2f);
        float[] target = {tx, ty, rot, scale, z};
        Object last = item.getTag(R.id.card);
        if (last instanceof float[] && java.util.Arrays.equals((float[]) last, target)) {
            // Same place as last time (a layout that moved nothing): leave a running animation alone.
        } else if (animate && last != null) {
            item.animate().translationX(tx).translationY(ty).rotation(rot).scaleX(scale).scaleY(scale)
                    .translationZ(z).setDuration(DURATION_MS).start();
        } else {
            item.setTranslationX(tx);
            item.setTranslationY(ty);
            item.setRotation(rot);
            item.setScaleX(scale);
            item.setScaleY(scale);
            item.setTranslationZ(z);
        }
        item.setTag(R.id.card, target);   // laid out in the fan: later changes animate
        ((android.widget.FrameLayout) card).setForeground(up ? ring(card) : focused >= 0 ? shade(card) : null);
    }

    private void reset(View item, View card) {
        if (item.getTag(R.id.card) == null) return;
        item.setTag(R.id.card, null);
        item.animate().cancel();
        item.setTranslationX(0);
        item.setTranslationY(0);
        item.setRotation(0);
        item.setScaleX(1);
        item.setScaleY(1);
        item.setTranslationZ(0);
        ((android.widget.FrameLayout) card).setForeground(null);
    }

    /** The focused card's accent ring, inset so the card's rounded clip does not cut it in half. */
    private Drawable ring(View card) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(AppAdapter.cornerRadius(card.getWidth(), density));
        g.setColor(0x00000000);
        g.setStroke(Math.round(3 * density), accent);
        return new InsetDrawable(g, Math.round(1.5f * density));
    }

    /** The shade over the other cards while one is up. */
    private Drawable shade(View card) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(AppAdapter.cornerRadius(card.getWidth(), density));
        g.setColor(0x73000000);
        return g;
    }
}
