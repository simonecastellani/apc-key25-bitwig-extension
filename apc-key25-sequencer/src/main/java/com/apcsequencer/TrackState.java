package com.apcsequencer;

/**
 * Mutable state for one track, including its 8-step sequence, track-level parameters,
 * and a Sequence Bank of 8 saved pattern slots.
 *
 * <p>Track-level parameters correspond to the per-track knob mapping in CONTEXT.md.</p>
 */
public final class TrackState {

    static final int STEP_COUNT = 8;
    static final int SLOT_COUNT = 8;

    // -------------------------------------------------------------------
    // Live step sequence
    // -------------------------------------------------------------------
    private final StepState[] steps = new StepState[STEP_COUNT];

    // -------------------------------------------------------------------
    // Track-level parameters (CONTEXT.md per-track knob mapping)
    // -------------------------------------------------------------------
    /** Which step duration subdivision is active for this track. Default 1/16. */
    private StepDuration stepDuration = StepDuration.S16;
    /** Number of active steps (1–8). Determines clip length. Default 8. */
    private int loopEndPoint = 8;
    /** Active Sequence Slot index (0–7). */
    private int activeSlot = 0;
    /** Pattern rotation offset (0 to loopEndPoint−1). Default 0. */
    private int patternRotation = 0;
    /** Swing percentage (50–75). Default 50 (no swing). */
    private int swing = 50;
    /** Semitone transpose for all notes in this track (–12..+12). Default 0. */
    private int transpose = 0;
    /** Per-track global fire probability (0.0–1.0). Default 1.0. */
    private double trackProbability = 1.0;
    /** Static pan applied to all notes in this track (-1.0..1.0). Default center. */
    private double staticPan = 0.0;
    /** Velocity spread humanisation amount applied to all notes (0.0..1.0). */
    private double velocitySpread = 0.0;
    /** Loop length multiplier. Default ONE (no multiplication). */
    private LoopMultiplier loopMultiplier = LoopMultiplier.ONE;
    /** Euclidean distribution pulse count (0..loopEndPoint). Default 0. */
    private int euclideanDistribution = 0;
    /** Phase offset as fraction of loop length (0.0–1.0). Default 0.0. */
    private double phaseOffset = 0.0;

    // -------------------------------------------------------------------
    // Sequence Bank: 8 slots; null = empty (never saved)
    // -------------------------------------------------------------------
    private final StepState[][] bank = new StepState[SLOT_COUNT][];

    // -------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------

    TrackState() {
        for (int i = 0; i < STEP_COUNT; i++) {
            steps[i] = new StepState();
        }
    }

    // -------------------------------------------------------------------
    // Public read API
    // -------------------------------------------------------------------

    public StepState getStep(int stepIndex) { return steps[stepIndex]; }

    public StepDuration getStepDuration()       { return stepDuration; }
    public int getLoopEndPoint()                { return loopEndPoint; }
    public int getActiveSlot()                  { return activeSlot; }
    public int getPatternRotation()             { return patternRotation; }
    public int getSwing()                       { return swing; }
    public int getTranspose()                   { return transpose; }
    public double getTrackProbability()         { return trackProbability; }
    public double getStaticPan()                { return staticPan; }
    public double getVelocitySpread()           { return velocitySpread; }
    public LoopMultiplier getLoopMultiplier()   { return loopMultiplier; }
    public int getEuclideanDistribution()       { return euclideanDistribution; }
    public double getPhaseOffset()              { return phaseOffset; }

    /** Returns {@code true} if the given slot index has saved content. */
    public boolean isSlotPopulated(int slotIndex) { return bank[slotIndex] != null; }

    // -------------------------------------------------------------------
    // Package-private mutation
    // -------------------------------------------------------------------

    void setStepDuration(StepDuration stepDuration)     { this.stepDuration = stepDuration; }
    void setPatternRotation(int patternRotation)        { this.patternRotation = patternRotation; }
    void setSwing(int swing)                            { this.swing = swing; }
    void setTranspose(int transpose)                    { this.transpose = transpose; }
    void setTrackProbability(double trackProbability)   { this.trackProbability = trackProbability; }
    void setStaticPan(double staticPan)                 { this.staticPan = staticPan; }
    void setVelocitySpread(double velocitySpread)       { this.velocitySpread = velocitySpread; }
    void setLoopMultiplier(LoopMultiplier loopMultiplier) { this.loopMultiplier = loopMultiplier; }
    void setEuclideanDistribution(int euclideanDistribution) { this.euclideanDistribution = euclideanDistribution; }
    void setPhaseOffset(double phaseOffset)             { this.phaseOffset = phaseOffset; }

    /**
     * Sets loopEndPoint, clamped to [1, 8].
     * @return the value actually stored (after clamping)
     */
    int setLoopEndPoint(int value) {
        loopEndPoint = Math.max(1, Math.min(STEP_COUNT, value));
        return loopEndPoint;
    }

    /**
     * Switches to {@code slotIndex}.
     *
     * <ol>
     *   <li>The current live steps are always saved back to {@code activeSlot} first, so edits
     *       are never lost when navigating away.</li>
     *   <li>If the destination slot is empty: the just-saved snapshot is also copied into it,
     *       giving the performer a starting point (copy-on-first-select). Live steps are
     *       unchanged — the performer continues editing in the new slot context.</li>
     *   <li>If the destination slot is populated: its saved content is restored into the live
     *       steps.</li>
     * </ol>
     */
    void switchSlot(int slotIndex) {
        // Persist current live state when it has meaningful content or when slot already exists.
        if (bank[activeSlot] != null || hasAnyStepData()) {
            bank[activeSlot] = snapshotSteps();
        }

        if (bank[slotIndex] == null) {
            // Empty destination — copy current slot's content so the performer has a base
            bank[slotIndex] = snapshotSteps();
            // Live steps stay the same (the copy IS the current live state)
        } else {
            // Populated destination — restore its saved content
            restoreSteps(bank[slotIndex]);
        }
        activeSlot = slotIndex;
    }

    /** Clears a Sequence Slot. If active, live steps reset to defaults. */
    void clearSlot(int slotIndex) {
        bank[slotIndex] = null;
        if (slotIndex == activeSlot) {
            for (int i = 0; i < STEP_COUNT; i++) {
                steps[i] = new StepState();
            }
        }
    }

    // -------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------

    private StepState[] snapshotSteps() {
        StepState[] snapshot = new StepState[STEP_COUNT];
        for (int i = 0; i < STEP_COUNT; i++) {
            snapshot[i] = steps[i].copy();
        }
        return snapshot;
    }

    private boolean hasAnyStepData() {
        for (int i = 0; i < STEP_COUNT; i++) {
            StepState step = steps[i];
            if (step.isActive()) {
                return true;
            }
            if (step.getPitch() != 60
                    || step.getVelocity() != 100
                    || Double.compare(step.getGateLength(), 0.5) != 0
                    || Double.compare(step.getProbability(), 1.0) != 0
                    || step.getChordVoicing() != ChordVoicing.ROOT_ONLY
                    || step.getScaleDegreeOffset() != 0
                    || step.getRatchetCount() != 1
                    || Double.compare(step.getRatchetDecay(), 0.0) != 0
                    || step.getStepCondition() != StepCondition.ALWAYS) {
                return true;
            }
        }
        return false;
    }

    private void restoreSteps(StepState[] snapshot) {
        for (int i = 0; i < STEP_COUNT; i++) {
            steps[i] = snapshot[i].copy();
        }
    }
}
