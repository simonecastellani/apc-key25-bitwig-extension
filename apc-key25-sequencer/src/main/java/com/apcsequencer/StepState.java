package com.apcsequencer;

/**
 * Mutable state for a single step within a track's sequence.
 *
 * <p>All fields have documented defaults matching the CONTEXT.md knob mapping table.
 * Package-private setters allow {@link SequencerState} to mutate fields while
 * returning {@link StateDiff} to callers.</p>
 */
public final class StepState {

    // -------------------------------------------------------------------
    // Fields — defaults defined by CONTEXT.md / issue #3 acceptance criteria
    // -------------------------------------------------------------------

    private boolean active = false;
    /** MIDI pitch 0–127. Default C3 = 60. */
    private int pitch = 60;
    /** MIDI velocity 0–127. Default 100. */
    private int velocity = 100;
    /** Gate length as a fraction of Step Duration (0.01–1.0). Default 0.5. */
    private double gateLength = 0.5;
    /** Note-fire probability (0.0–1.0). Default 1.0 (always fires). */
    private double probability = 1.0;
    /** Chord voicing applied to this step. Default ROOT_ONLY. */
    private ChordVoicing chordVoicing = ChordVoicing.ROOT_ONLY;
    /** Scale-degree offset (–7..+7). Default 0 (no transposition). */
    private int scaleDegreeOffset = 0;
    /** Number of ratchet repeats within the step's duration (1–8). Default 1. */
    private int ratchetCount = 1;
    /** Velocity drop per ratchet hit (0.0–1.0). Default 0.0 (no decay). */
    private double ratchetDecay = 0.0;
    /** Step condition (fire on every nth loop pass). Default ALWAYS. */
    private StepCondition stepCondition = StepCondition.ALWAYS;

    // -------------------------------------------------------------------
    // Public read API
    // -------------------------------------------------------------------

    public boolean isActive()             { return active; }
    public int getPitch()                 { return pitch; }
    public int getVelocity()              { return velocity; }
    public double getGateLength()         { return gateLength; }
    public double getProbability()        { return probability; }
    public ChordVoicing getChordVoicing() { return chordVoicing; }
    public int getScaleDegreeOffset()     { return scaleDegreeOffset; }
    public int getRatchetCount()          { return ratchetCount; }
    public double getRatchetDecay()       { return ratchetDecay; }
    public StepCondition getStepCondition() { return stepCondition; }

    // -------------------------------------------------------------------
    // Package-private mutation (only SequencerState may call these)
    // -------------------------------------------------------------------

    void setActive(boolean active)                   { this.active = active; }
    void setPitch(int pitch)                         { this.pitch = pitch; }
    void setVelocity(int velocity)                   { this.velocity = velocity; }
    void setGateLength(double gateLength)            { this.gateLength = gateLength; }
    void setProbability(double probability)          { this.probability = probability; }
    void setChordVoicing(ChordVoicing chordVoicing)  { this.chordVoicing = chordVoicing; }
    void setScaleDegreeOffset(int scaleDegreeOffset) { this.scaleDegreeOffset = scaleDegreeOffset; }
    void setRatchetCount(int ratchetCount)           { this.ratchetCount = ratchetCount; }
    void setRatchetDecay(double ratchetDecay)        { this.ratchetDecay = ratchetDecay; }
    void setStepCondition(StepCondition stepCondition) { this.stepCondition = stepCondition; }

    /** Returns a deep copy of this StepState (used for Sequence Bank snapshots). */
    StepState copy() {
        StepState c = new StepState();
        c.active           = this.active;
        c.pitch            = this.pitch;
        c.velocity         = this.velocity;
        c.gateLength       = this.gateLength;
        c.probability      = this.probability;
        c.chordVoicing     = this.chordVoicing;
        c.scaleDegreeOffset = this.scaleDegreeOffset;
        c.ratchetCount     = this.ratchetCount;
        c.ratchetDecay     = this.ratchetDecay;
        c.stepCondition    = this.stepCondition;
        return c;
    }
}
