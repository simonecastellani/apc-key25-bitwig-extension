package com.apcsequencer;

import com.bitwig.extension.controller.api.PinnableCursorClip;
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
    private final SequencerState       state;

    private final boolean[]            ready;
    private final List<Deque<PendingWrite>> pending;

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
    public BitwigClipWriter(PinnableCursorClip[] clips, SequencerState state) {
        if (clips.length != SequencerState.TRACK_COUNT) {
            throw new IllegalArgumentException(
                    "clips array must have " + SequencerState.TRACK_COUNT + " entries");
        }
        this.clips   = clips;
        this.state   = state;
        this.ready   = new boolean[SequencerState.TRACK_COUNT];
        this.pending = new ArrayList<>(SequencerState.TRACK_COUNT);

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            ready[t] = false;
            pending.add(new ArrayDeque<>());

            final int trackIndex = t;
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
