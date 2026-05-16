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
    /** Loop length multiplier. Default ONE (no multiplication). */
    private LoopMultiplier loopMultiplier = LoopMultiplier.ONE;
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
    public LoopMultiplier getLoopMultiplier()   { return loopMultiplier; }
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
    void setLoopMultiplier(LoopMultiplier loopMultiplier) { this.loopMultiplier = loopMultiplier; }
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
        // Always persist the current live state to the current slot before moving away.
        bank[activeSlot] = snapshotSteps();

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

    private void restoreSteps(StepState[] snapshot) {
        for (int i = 0; i < STEP_COUNT; i++) {
            steps[i] = snapshot[i].copy();
        }
    }
}
