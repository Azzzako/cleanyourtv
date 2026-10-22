package com.gathin.tvhome;

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
import android.widget.TextView;
import java.util.List;

/**
 * A now-playing ticker in the top bar for one media app (now_playing_package in config.xml, Spotify here): the track
 * and artist (TickerView, scrolling forever only when they do not fit), Previous / Play-Pause / Next and a thin
 * progress line. It follows the app's MediaSession, which Android only shows to an app with notification access
 * (MediaListener); without it, or while the app has no active session, the ticker just says Play, which opens the
 * app. Event driven, apart from the ticker's scroll and a one-second progress tick, both only while Home is started.
 */
final class NowPlaying {

    /** Where focus goes when it leaves the ticker's buttons. */
    interface Host {
        void onNowPlayingDown();

        void onNowPlayingRight();
    }

    private final Context ctx;
    private final View bar;
    private final TickerView ticker;
    private final View track;
    private final View progress;
    private final TextView prev;
    private final TextView play;
    private final TextView next;
    private final String pkg;
    private final boolean scroll;
    private final Host host;
    private final ComponentName listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final boolean enabled;
    private CharSequence appLabel = "";
    private MediaController controller;
    private boolean started;

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

    /** @param scroll false (reduced motion) keeps the ticker still. */
    NowPlaying(Context ctx, View bar, String pkg, boolean scroll, Host host) {
        this.ctx = ctx;
        this.bar = bar;
        this.pkg = pkg;
        this.scroll = scroll;
        this.host = host;
        ticker = bar.findViewById(R.id.npTicker);
        track = bar.findViewById(R.id.npTrack);
        progress = bar.findViewById(R.id.npProgress);
        prev = bar.findViewById(R.id.npPrev);
        play = bar.findViewById(R.id.npPlay);
        next = bar.findViewById(R.id.npNext);
        listener = new ComponentName(ctx, MediaListener.class);
        sessions = ctx.getSystemService(MediaSessionManager.class);
        enabled = !pkg.isEmpty() && sessions != null && loadApp();
        bar.setVisibility(enabled ? View.VISIBLE : View.GONE);
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
                    return true;                       // the top of the screen
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.onNowPlayingDown();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return v == firstButton();         // the ticker's text and badge take no focus
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (v != lastButton()) return false;
                    host.onNowPlayingRight();
                    return true;
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
        ticker.setRunning(scroll);
    }

    void stop() {
        if (!enabled) return;
        started = false;
        ticker.setRunning(false);
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

        if (hasTrack) {
            CharSequence by = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (by == null || by.length() == 0) by = md.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (by == null || by.length() == 0) by = md.getDescription().getSubtitle();
            ticker.setText(trackTitle + (by == null || by.length() == 0 ? "" : " — " + by));
        } else {
            ticker.setText(ctx.getString(R.string.now_playing_play));
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

    /** Focus on the ticker's rightmost button, coming from the top-bar button to its right; false when it is off. */
    boolean focusFromRight() {
        return enabled && bar.isShown() && lastButton().requestFocus();
    }
}
