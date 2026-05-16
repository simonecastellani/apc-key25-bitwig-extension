package com.apcsequencer;

import com.bitwig.extension.controller.api.PinnableCursorClip;

/**
 * Production {@link ClipWriter} backed by an array of Bitwig
 * {@link PinnableCursorClip} objects (one per track).
 *
 * <p>Writes a single note at the step's assigned pitch.  Chord voicing
 * (multiple simultaneous notes) and scale-degree offset transposition are
 * deferred to a later slice; this implementation is correct for the tracer-
 * bullet step-toggle use case.</p>
 *
 * <h3>NoteStep API used</h3>
 * <ul>
 *   <li>{@code clip.setStep(channel=0, x=step, y=pitch, velocity, duration)}
 *       — activates a step note.</li>
 *   <li>{@code clip.clearStep(channel=0, x=step, y=pitch)}
 *       — removes a step note.</li>
 * </ul>
 *
 * <p>Gate duration (insert duration) is computed as
 * {@code gateLength × stepDurationInBeatTime} using the track's current
 * {@link StepDuration}.</p>
 */
public final class BitwigClipWriter implements ClipWriter {

    private final PinnableCursorClip[] clips;
    private final SequencerState       state;

    /**
     * @param clips one {@link PinnableCursorClip} per track (length must be 5)
     * @param state used to look up per-track {@link StepDuration} for gate-length computation
     */
    public BitwigClipWriter(PinnableCursorClip[] clips, SequencerState state) {
        if (clips.length != SequencerState.TRACK_COUNT) {
            throw new IllegalArgumentException("clips array must have " + SequencerState.TRACK_COUNT + " entries");
        }
        this.clips = clips;
        this.state = state;
    }

    @Override
    public void writeStep(int track, int step, boolean active, StepState stepState) {
        PinnableCursorClip clip  = clips[track];
        int                pitch = stepState.getPitch();

        if (active) {
            double stepBeatTime  = state.getTrack(track).getStepDuration().beatTime();
            double gateDuration  = stepState.getGateLength() * stepBeatTime;
            int    velocity      = stepState.getVelocity();
            clip.setStep(0, step, pitch, velocity, gateDuration);
        } else {
            clip.clearStep(0, step, pitch);
        }
    }
}
