package com.apcsequencer;

import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Production {@link ClipWriter} backed by an array of Bitwig
 * {@link PinnableCursorClip} objects (one per track).
 *
 * <p>Writes are queued if a clip is not yet ready (exists() = false) and
 * drained the first time exists() fires true for that track.  This covers
 * the window between init() and Bitwig reporting that the auto-created clip
 * is available.</p>
 *
 * <h3>NoteStep API used</h3>
 * <ul>
 *   <li>{@code clip.setStep(channel, x, y, velocity, duration)} — activates a note.</li>
 *   <li>{@code clip.clearStep(channel, x, y)} — removes a note.</li>
 * </ul>
 */
public final class BitwigClipWriter implements ClipWriter {

    private final PinnableCursorClip[] clips;
    private final ClipLauncherSlotBank[] slotBanks;
    private final SettableBooleanValue[] muteValues;
    private final SequencerState       state;

    private final boolean[]            ready;
    private final List<Deque<PendingWrite>> pending;
    private final boolean[] trackPlaying;
    private final boolean[] trackMuted;
    private final boolean[][] slotPlaying;
    private PlaybackStateListener playbackStateListener;

    private static final class PendingWrite {
        final int       step;
        final boolean   active;
        final StepState snapshot;

        PendingWrite(int step, boolean active, StepState snapshot) {
            this.step     = step;
            this.active   = active;
            this.snapshot = snapshot;
        }
    }

    /**
     * @param clips one non-null {@link PinnableCursorClip} per track (length == TRACK_COUNT)
     * @param state used to look up per-track {@link StepDuration} for gate-length computation
     */
    public BitwigClipWriter(PinnableCursorClip[] clips, Track[] tracks, SequencerState state) {
        if (clips.length != SequencerState.TRACK_COUNT) {
            throw new IllegalArgumentException(
                    "clips array must have " + SequencerState.TRACK_COUNT + " entries");
        }
        if (tracks.length != SequencerState.TRACK_COUNT) {
            throw new IllegalArgumentException(
                    "tracks array must have " + SequencerState.TRACK_COUNT + " entries");
        }
        this.clips   = clips;
        this.slotBanks = new ClipLauncherSlotBank[SequencerState.TRACK_COUNT];
        this.muteValues = new SettableBooleanValue[SequencerState.TRACK_COUNT];
        this.state   = state;
        this.ready   = new boolean[SequencerState.TRACK_COUNT];
        this.pending = new ArrayList<>(SequencerState.TRACK_COUNT);
        this.trackPlaying = new boolean[SequencerState.TRACK_COUNT];
        this.trackMuted = new boolean[SequencerState.TRACK_COUNT];
        this.slotPlaying = new boolean[SequencerState.TRACK_COUNT][TrackState.SLOT_COUNT];

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            ready[t] = false;
            pending.add(new ArrayDeque<>());
            trackPlaying[t] = false;

            final int trackIndex = t;
            Track track = tracks[t];

            ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            slotBanks[t] = slotBank;
            slotBank.addPlaybackStateObserver((slot, playbackState, queued) -> {
                if (queued || slot < 0 || slot >= TrackState.SLOT_COUNT) {
                    return;
                }
                slotPlaying[trackIndex][slot] = playbackState != 0;
                boolean anyPlaying = false;
                for (int i = 0; i < TrackState.SLOT_COUNT; i++) {
                    if (slotPlaying[trackIndex][i]) {
                        anyPlaying = true;
                        break;
                    }
                }
                if (trackPlaying[trackIndex] != anyPlaying) {
                    trackPlaying[trackIndex] = anyPlaying;
                    if (playbackStateListener != null) {
                        playbackStateListener.onTrackPlayingChanged(trackIndex, anyPlaying);
                    }
                }
            });

            SettableBooleanValue mute = track.mute();
            mute.markInterested();
            mute.addValueObserver(muted -> {
                if (trackMuted[trackIndex] != muted) {
                    trackMuted[trackIndex] = muted;
                    if (playbackStateListener != null) {
                        playbackStateListener.onTrackMutedChanged(trackIndex, muted);
                    }
                }
            });
            muteValues[t] = mute;
            trackMuted[t] = mute.get();

            PinnableCursorClip clip = clips[t];

            clip.exists().markInterested();
            clip.exists().addValueObserver(exists -> {
                if (exists) {
                    setTrackReadyAndDrain(trackIndex, clip);
                } else {
                    ready[trackIndex] = false;
                }
            });

            if (clip.exists().get()) {
                setTrackReadyAndDrain(trackIndex, clip);
            }
        }
    }

    @Override
    public void writeStep(int track, int step, boolean active, StepState stepState) {
        PinnableCursorClip clip = clips[track];

        if (!ready[track] && clip.exists().get()) {
            setTrackReadyAndDrain(track, clip);
        }

        if (!ready[track]) {
            pending.get(track).addLast(new PendingWrite(step, active, stepState.copy()));
            return;
        }

        applyWrite(track, clip, step, active, stepState);
    }

    @Override
    public void toggleTrackClipPlayback(int track, int slot) {
        ClipLauncherSlotBank slotBank = slotBanks[track];
        if (trackPlaying[track]) {
            slotBank.stop();
        } else {
            slotBank.launch(slot);
        }
    }

    @Override
    public void stopAllTrackClips() {
        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            slotBanks[t].stop();
        }
    }

    @Override
    public boolean isTrackPlaying(int track) {
        return trackPlaying[track];
    }

    @Override
    public boolean isTrackMuted(int track) {
        return trackMuted[track];
    }

    @Override
    public void setPlaybackStateListener(PlaybackStateListener listener) {
        playbackStateListener = listener;
        if (listener == null) {
            return;
        }
        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            listener.onTrackPlayingChanged(t, trackPlaying[t]);
            listener.onTrackMutedChanged(t, trackMuted[t]);
        }
    }

    private void applyWrite(int track, PinnableCursorClip clip,
                            int step, boolean active, StepState stepState) {
        if (active) {
            double stepBeatTime = state.getTrack(track).getStepDuration().beatTime();
            double gateDuration = stepState.getGateLength() * stepBeatTime;
            clip.setStep(0, step, stepState.getPitch(), stepState.getVelocity(), gateDuration);
        } else {
            clip.clearStep(0, step, stepState.getPitch());
        }
    }

    private void setTrackReadyAndDrain(int trackIndex, PinnableCursorClip clip) {
        Deque<PendingWrite> dq = pending.get(trackIndex);
        while (!dq.isEmpty()) {
            PendingWrite w = dq.poll();
            applyWrite(trackIndex, clip, w.step, w.active, w.snapshot);
        }
        ready[trackIndex] = true;
    }
}
