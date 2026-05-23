package com.apcsequencer;

/**
 * Single source of truth for all sequencer data. No Bitwig dependency.
 *
 * <p>Every mutation method returns a {@link StateDiff} describing exactly what changed.
 * An idempotent write (no actual state change) returns an empty diff.</p>
 *
 * <p>Contains:</p>
 * <ul>
 *   <li>5 {@link TrackState} objects (one per pad-grid row)</li>
 *   <li>A {@link GlobalScale} (shared root + mode)</li>
 *   <li>The index of the currently focused track (0–4)</li>
 * </ul>
 */
public final class SequencerState {

    static final int TRACK_COUNT = 5;

    private final TrackState[] tracks = new TrackState[TRACK_COUNT];
    private GlobalScale globalScale = new GlobalScale(0, Mode.MAJOR); // default: C Major
    private int focusedTrack = 0;

    public SequencerState() {
        for (int i = 0; i < TRACK_COUNT; i++) {
            tracks[i] = new TrackState();
        }
    }

    // -------------------------------------------------------------------
    // Public read API
    // -------------------------------------------------------------------

    public TrackState getTrack(int trackIndex)          { return tracks[trackIndex]; }
    public StepState getStep(int trackIndex, int stepIndex) {
        return tracks[trackIndex].getStep(stepIndex);
    }
    public GlobalScale getGlobalScale()                 { return globalScale; }
    public int getFocusedTrack()                        { return focusedTrack; }

    // -------------------------------------------------------------------
    // Step-level mutations
    // -------------------------------------------------------------------

    /**
     * Toggles the {@code active} flag of the given step.
     * Always produces a non-empty diff (active always changes).
     */
    public StateDiff toggleStep(int trackIndex, int stepIndex) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        step.setActive(!step.isActive());
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    /**
     * Sets the {@code active} flag of the given step.
     * Returns an empty diff if the value is already equal to {@code active}.
     */
    public StateDiff setStepActive(int trackIndex, int stepIndex, boolean active) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (step.isActive() == active) return StateDiff.builder().build();
        step.setActive(active);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepPitch(int trackIndex, int stepIndex, int pitch) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (step.getPitch() == pitch) return StateDiff.builder().build();
        step.setPitch(pitch);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepVelocity(int trackIndex, int stepIndex, int velocity) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (step.getVelocity() == velocity) return StateDiff.builder().build();
        step.setVelocity(velocity);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepGateLength(int trackIndex, int stepIndex, double gateLength) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (Double.compare(step.getGateLength(), gateLength) == 0) return StateDiff.builder().build();
        step.setGateLength(gateLength);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepProbability(int trackIndex, int stepIndex, double probability) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (Double.compare(step.getProbability(), probability) == 0) return StateDiff.builder().build();
        step.setProbability(probability);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepChordVoicing(int trackIndex, int stepIndex, ChordVoicing voicing) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (step.getChordVoicing() == voicing) return StateDiff.builder().build();
        step.setChordVoicing(voicing);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepScaleDegreeOffset(int trackIndex, int stepIndex, int offset) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (step.getScaleDegreeOffset() == offset) return StateDiff.builder().build();
        step.setScaleDegreeOffset(offset);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepRatchetCount(int trackIndex, int stepIndex, int count) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (step.getRatchetCount() == count) return StateDiff.builder().build();
        step.setRatchetCount(count);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepRatchetDecay(int trackIndex, int stepIndex, double decay) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (Double.compare(step.getRatchetDecay(), decay) == 0) return StateDiff.builder().build();
        step.setRatchetDecay(decay);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    public StateDiff setStepCondition(int trackIndex, int stepIndex, StepCondition condition) {
        StepState step = tracks[trackIndex].getStep(stepIndex);
        if (step.getStepCondition() == condition) return StateDiff.builder().build();
        step.setStepCondition(condition);
        return StateDiff.builder().addStepChange(trackIndex, stepIndex).build();
    }

    // -------------------------------------------------------------------
    // Track-level mutations
    // -------------------------------------------------------------------

    /**
     * Sets the loop end point for the given track, clamped to [1, 8].
     * Returns an empty diff if the clamped value equals the current value.
     */
    public StateDiff setLoopEndPoint(int trackIndex, int value) {
        TrackState track = tracks[trackIndex];
        int before = track.getLoopEndPoint();
        int after  = track.setLoopEndPoint(value);
        int maxRotation = Math.max(0, after - 1);
        if (track.getPatternRotation() > maxRotation) {
            track.setPatternRotation(maxRotation);
        }
        if (track.getEuclideanDistribution() > after) {
            track.setEuclideanDistribution(after);
        }
        if (before == after) return StateDiff.builder().build();
        return StateDiff.builder().addStepChange(trackIndex, after - 1).build();
    }

    /**
     * Sets Step Duration for the given track.
     * Returns an empty diff if unchanged.
     */
    public StateDiff setStepDuration(int trackIndex, StepDuration stepDuration) {
        TrackState track = tracks[trackIndex];
        if (track.getStepDuration() == stepDuration) return StateDiff.builder().build();
        track.setStepDuration(stepDuration);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setPatternRotation(int trackIndex, int patternRotation) {
        TrackState track = tracks[trackIndex];
        int max = Math.max(0, track.getLoopEndPoint() - 1);
        int clamped = Math.max(0, Math.min(max, patternRotation));
        if (track.getPatternRotation() == clamped) return StateDiff.builder().build();
        track.setPatternRotation(clamped);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setSwing(int trackIndex, int swing) {
        int clamped = Math.max(50, Math.min(75, swing));
        TrackState track = tracks[trackIndex];
        if (track.getSwing() == clamped) return StateDiff.builder().build();
        track.setSwing(clamped);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setTranspose(int trackIndex, int transpose) {
        int clamped = Math.max(-12, Math.min(12, transpose));
        TrackState track = tracks[trackIndex];
        if (track.getTranspose() == clamped) return StateDiff.builder().build();
        track.setTranspose(clamped);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setTrackProbability(int trackIndex, double probability) {
        double clamped = Math.max(0.0, Math.min(1.0, probability));
        TrackState track = tracks[trackIndex];
        if (Double.compare(track.getTrackProbability(), clamped) == 0) return StateDiff.builder().build();
        track.setTrackProbability(clamped);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setStaticPan(int trackIndex, double pan) {
        double clamped = Math.max(-1.0, Math.min(1.0, pan));
        TrackState track = tracks[trackIndex];
        if (Double.compare(track.getStaticPan(), clamped) == 0) return StateDiff.builder().build();
        track.setStaticPan(clamped);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setVelocitySpread(int trackIndex, double amount) {
        double clamped = Math.max(0.0, Math.min(1.0, amount));
        TrackState track = tracks[trackIndex];
        if (Double.compare(track.getVelocitySpread(), clamped) == 0) return StateDiff.builder().build();
        track.setVelocitySpread(clamped);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setLoopMultiplier(int trackIndex, LoopMultiplier loopMultiplier) {
        TrackState track = tracks[trackIndex];
        if (track.getLoopMultiplier() == loopMultiplier) return StateDiff.builder().build();
        track.setLoopMultiplier(loopMultiplier);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setPhaseOffset(int trackIndex, double phaseOffset) {
        double clamped = Math.max(0.0, Math.min(1.0, phaseOffset));
        TrackState track = tracks[trackIndex];
        if (Double.compare(track.getPhaseOffset(), clamped) == 0) return StateDiff.builder().build();
        track.setPhaseOffset(clamped);
        return StateDiff.builder().addStepChange(trackIndex, 0).build();
    }

    public StateDiff setEuclideanDistribution(int trackIndex, int pulses) {
        TrackState track = tracks[trackIndex];
        int loopEndPoint = track.getLoopEndPoint();
        int clamped = Math.max(0, Math.min(loopEndPoint, pulses));
        track.setEuclideanDistribution(clamped);

        boolean[] bitmask = EuclideanBitmask.generate(clamped, loopEndPoint);
        StateDiff.Builder builder = StateDiff.builder();
        for (int step = 0; step < loopEndPoint; step++) {
            StepState stepState = track.getStep(step);
            boolean next = bitmask[step];
            if (stepState.isActive() != next) {
                stepState.setActive(next);
                builder.addStepChange(trackIndex, step);
            }
        }

        StateDiff diff = builder.build();
        if (!diff.isEmpty()) {
            return diff;
        }
        return StateDiff.builder().build();
    }

    /**
     * Switches the given track to {@code slotIndex}.
     * See {@link TrackState#switchSlot} for copy-on-first-select semantics.
     */
    public StateDiff switchSlot(int trackIndex, int slotIndex) {
        tracks[trackIndex].switchSlot(slotIndex);
        // All steps may have changed; mark every step as changed
        StateDiff.Builder b = StateDiff.builder();
        for (int s = 0; s < TrackState.STEP_COUNT; s++) {
            b.addStepChange(trackIndex, s);
        }
        return b.build();
    }

    /** Clears the given slot and marks all steps changed when active slot is cleared. */
    public StateDiff clearSlot(int trackIndex, int slotIndex) {
        TrackState track = tracks[trackIndex];
        boolean activeBefore = track.getActiveSlot() == slotIndex;
        track.clearSlot(slotIndex);
        if (!activeBefore) {
            return StateDiff.builder().build();
        }
        StateDiff.Builder b = StateDiff.builder();
        for (int s = 0; s < TrackState.STEP_COUNT; s++) {
            b.addStepChange(trackIndex, s);
        }
        return b.build();
    }

    // -------------------------------------------------------------------
    // Global mutations
    // -------------------------------------------------------------------

    /** Sets the GlobalScale. Returns empty diff if scale is unchanged. */
    public StateDiff setGlobalScale(GlobalScale scale) {
        if (scale.equals(globalScale)) return StateDiff.builder().build();
        globalScale = scale;
        return StateDiff.builder().build(); // scale change; ClipWriter re-resolves pitches separately
    }

    /** Sets the focused track index (0–4). Returns empty diff if unchanged. */
    public StateDiff setFocusedTrack(int trackIndex) {
        if (focusedTrack == trackIndex) return StateDiff.builder().build();
        focusedTrack = trackIndex;
        return StateDiff.builder().build();
    }
}
