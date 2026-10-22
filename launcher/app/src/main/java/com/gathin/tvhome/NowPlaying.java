package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/**
 * A player card on Home for one media app (now_playing_package in config.xml, Spotify here): the app and whether it
 * plays, the track, artist and progress, and Previous / Play-Pause / Next. It follows the app's MediaSession, which
 * Android only shows to an app with notification access (MediaListener); without it, or while the app has no active
 * session, the card names the app and its Play button opens it. Event driven: the only timer is the one-second
 * progress tick, and only while Home is started and a track plays. The card is as wide as the room the top shelf
 * leaves beside it (show).
 */
final class NowPlaying {

    /** Where focus goes when it leaves the card up or down. */
    interface Host {
        void onNowPlayingUp();

        void onNowPlayingDown();
    }

    private final Context ctx;
    private final View card;
    private final TextView source;
    private final TextView title;
    private final TextView artist;
    private final View track;
    private final View progress;
    private final TextView prev;
    private final TextView play;
    private final TextView next;
    private final String pkg;
    private final boolean animate;
    private final Host host;
    private final ComponentName listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final boolean enabled;
    private CharSequence appLabel = "";
    private MediaController controller;
    private boolean started;
    private boolean shown;
    private ValueAnimator widthAnimator;
    private int targetWidth;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::onSessions;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (started && isPlaying()) handler.postDelayed(this, 1000);
        }
    };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            setController(null);
            render();
        }
    };

    NowPlaying(Context ctx, View card, String pkg, boolean animate, Host host) {
        this.ctx = ctx;
        this.card = card;
        this.pkg = pkg;
        this.animate = animate;
        this.host = host;
        source = card.findViewById(R.id.npSource);
        title = card.findViewById(R.id.npTitle);
        artist = card.findViewById(R.id.npArtist);
        track = card.findViewById(R.id.npTrack);
        progress = card.findViewById(R.id.npProgress);
        prev = card.findViewById(R.id.npPrev);
        play = card.findViewById(R.id.npPlay);
        next = card.findViewById(R.id.npNext);
        listener = new ComponentName(ctx, MediaListener.class);
        sessions = ctx.getSystemService(MediaSessionManager.class);
        enabled = !pkg.isEmpty() && sessions != null && loadApp();
        card.setVisibility(View.GONE);
        if (!enabled) return;
        progress.setPivotX(0f);

        prev.setOnClickListener(v -> {
            if (controller != null) controller.getTransportControls().skipToPrevious();
        });
        next.setOnClickListener(v -> {
            if (controller != null) controller.getTransportControls().skipToNext();
        });
        play.setOnClickListener(v -> {
            if (controller == null) openApp();
            else if (isPlaying()) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        });
        play.setOnLongClickListener(v -> {
            openApp();
            return true;
        });
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    host.onNowPlayingUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.onNowPlayingDown();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return v == firstButton();     // the card's left end: do not fall out to the shelf or a tile
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return v == lastButton();
                default:
                    return false;
            }
        };
        prev.setOnKeyListener(keys);
        play.setOnKeyListener(keys);
        next.setOnKeyListener(keys);
    }

    /** The app's name; false when it is not installed (or not visible to the launcher). */
    private boolean loadApp() {
        PackageManager pm = ctx.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            appLabel = pm.getApplicationLabel(info);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    void start() {
        if (!enabled) return;
        started = true;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
            onSessions(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            // No notification access: show the app, Play opens it.
            setController(null);
        }
        render();
    }

    void stop() {
        if (!enabled) return;
        started = false;
        handler.removeCallbacks(tick);
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        setController(null);
    }

    private void onSessions(List<MediaController> active) {
        MediaController found = null;
        if (active != null) {
            for (MediaController c : active) {
                if (pkg.equals(c.getPackageName())) {
                    found = c;
                    break;
                }
            }
        }
        if (found == null) setController(null);
        else if (controller == null || !controller.getSessionToken().equals(found.getSessionToken())) setController(found);
        render();
    }

    private void setController(MediaController c) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = c;
        if (c != null) c.registerCallback(callback, handler);
    }

    private boolean isPlaying() {
        PlaybackState st = controller == null ? null : controller.getPlaybackState();
        if (st == null) return false;
        int s = st.getState();
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING;
    }

    private void render() {
        if (!enabled) return;
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        CharSequence trackTitle = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (md != null && (trackTitle == null || trackTitle.length() == 0)) trackTitle = md.getDescription().getTitle();
        boolean hasTrack = trackTitle != null && trackTitle.length() > 0;
        boolean playing = isPlaying();
        String app = appLabel.toString().toUpperCase(Locale.getDefault());

        if (hasTrack) {
            source.setText(app + "  ·  " + ctx.getString(playing ? R.string.now_playing_playing : R.string.now_playing_paused)
                    .toUpperCase(Locale.getDefault()));
            title.setText(trackTitle);
            CharSequence by = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (by == null || by.length() == 0) by = md.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (by == null || by.length() == 0) by = md.getDescription().getSubtitle();
            artist.setText(by == null ? "" : by);
        } else {
            source.setText(app);
            title.setText(ctx.getString(R.string.now_playing_idle));
            artist.setText(ctx.getString(R.string.now_playing_open));
        }

        // Previous and Next only mean something with a session; move focus off them before they go.
        int skip = hasTrack ? View.VISIBLE : View.GONE;
        if (skip == View.GONE && (prev.isFocused() || next.isFocused())) play.requestFocus();
        prev.setVisibility(skip);
        next.setVisibility(skip);
        play.setCompoundDrawablesRelativeWithIntrinsicBounds(playing ? R.drawable.ic_pause : R.drawable.ic_play, 0, 0, 0);

        handler.removeCallbacks(tick);
        if (started && playing) handler.post(tick);
        else updateProgress();
    }

    private void updateProgress() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        PlaybackState st = controller == null ? null : controller.getPlaybackState();
        long duration = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (st == null || duration <= 0 || st.getPosition() < 0) {
            track.setVisibility(View.INVISIBLE);
            return;
        }
        long pos = st.getPosition();
        if (isPlaying()) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        track.setVisibility(View.VISIBLE);
        progress.setScaleX(Math.max(0f, Math.min(1f, pos / (float) duration)));
    }

    private void openApp() {
        PackageManager pm = ctx.getPackageManager();
        Intent open = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = pm.getLaunchIntentForPackage(pkg);
        if (open == null) return;
        try {
            ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private View firstButton() {
        return prev.getVisibility() == View.VISIBLE ? prev : play;
    }

    private View lastButton() {
        return next.getVisibility() == View.VISIBLE ? next : play;
    }

    /**
     * Shows the card with its bottom at bottomY and its left edge at leftX (both in the root's coordinates): the right
     * edge stays where the layout puts it, so the card stretches over whatever the top shelf's name leaves free.
     */
    void show(float bottomY, float leftX) {
        if (!enabled) return;
        card.setVisibility(View.VISIBLE);
        fitWidth(leftX);
        if (card.getHeight() == 0) {
            card.post(() -> {
                if (shown) card.setY(bottomY - card.getHeight());
            });
        } else {
            card.setY(bottomY - card.getHeight());
        }
        if (shown) return;
        shown = true;
        card.animate().cancel();
        if (animate) card.animate().alpha(1f).setDuration(260).start();
        else card.setAlpha(1f);
    }

    /** Width from leftX to the card's fixed right edge, never under MIN_WIDTH_DP; animated while the card shows. */
    private void fitWidth(float leftX) {
        View parent = (View) card.getParent();
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        if (parent == null || parent.getWidth() == 0) {
            card.post(() -> fitWidth(leftX));
            return;
        }
        float density = ctx.getResources().getDisplayMetrics().density;
        int width = Math.max(Math.round(MIN_WIDTH_DP * density),
                Math.round(parent.getWidth() - lp.getMarginEnd() - leftX));
        if (width == targetWidth) return;
        targetWidth = width;
        if (widthAnimator != null) widthAnimator.cancel();
        if (!animate || !shown || lp.width <= 0) {
            lp.width = width;
            card.setLayoutParams(lp);
            return;
        }
        widthAnimator = ValueAnimator.ofInt(lp.width, width);
        widthAnimator.setDuration(220);
        widthAnimator.addUpdateListener(a -> {
            lp.width = (int) a.getAnimatedValue();
            card.setLayoutParams(lp);
        });
        widthAnimator.start();
    }

    private static final float MIN_WIDTH_DP = 300;

    /** Hides the card with the shelf; it takes no focus while hidden. */
    void hide() {
        if (!enabled || !shown) return;
        shown = false;
        card.animate().cancel();
        if (animate) {
            card.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                if (!shown) card.setVisibility(View.INVISIBLE);
            }).start();
        } else {
            card.setAlpha(0f);
            card.setVisibility(View.INVISIBLE);
        }
    }

    /** Puts focus on Play when the card is on screen; false leaves focus where the caller would send it. */
    boolean focus() {
        if (!enabled || !shown) return false;
        return play.requestFocus();
    }
}
