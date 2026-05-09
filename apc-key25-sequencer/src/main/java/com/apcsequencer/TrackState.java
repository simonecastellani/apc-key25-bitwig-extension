package com.apcsequencer;

import java.util.Arrays;

public class TrackState {
    public boolean[] steps;
    public int[]     notes;          // -1 = use baseNote (drum sentinel)
    public int[]     velocities;
    public double[]  gateLengths;
    public double[]  probabilities;
    public int[]     nudges;
    public int[]     ratchets;
    public int[]     chordIntervals;
    public int[]     ccValues;
    public int       patternLength;
    public boolean   muted;
    public boolean   melodicMode;
    public int       baseNote;
    public int       currentStep;
    public int       midiChannel;    // 1-indexed (1–5)

    public TrackState(int midiChannel) {
        this.midiChannel    = midiChannel;
        this.steps          = new boolean[Config.NUM_STEPS];
        this.notes          = new int[Config.NUM_STEPS];
        this.velocities     = new int[Config.NUM_STEPS];
        this.gateLengths    = new double[Config.NUM_STEPS];
        this.probabilities  = new double[Config.NUM_STEPS];
        this.nudges         = new int[Config.NUM_STEPS];
        this.ratchets       = new int[Config.NUM_STEPS];
        this.chordIntervals = new int[Config.NUM_STEPS];
        this.ccValues       = new int[Config.NUM_STEPS];
        reset();
    }

    public void reset() {
        Arrays.fill(steps,         false);
        Arrays.fill(notes,         Config.NOTE_SENTINEL);
        Arrays.fill(velocities,    Config.DEFAULT_VELOCITY);
        Arrays.fill(gateLengths,   Config.DEFAULT_GATE);
        Arrays.fill(probabilities, Config.DEFAULT_PROBABILITY);
        Arrays.fill(nudges,        Config.DEFAULT_NUDGE);
        Arrays.fill(ratchets,      Config.DEFAULT_RATCHET);
        Arrays.fill(chordIntervals,Config.DEFAULT_CHORD);
        Arrays.fill(ccValues,      Config.DEFAULT_CC_VALUE);
        patternLength = Config.NUM_STEPS;
        muted         = false;
        melodicMode   = false;
        baseNote      = Config.DEFAULT_BASE_NOTE;
        currentStep   = 0;
    }

    public TrackState deepCopy() {
        TrackState c = new TrackState(this.midiChannel);
        c.steps          = Arrays.copyOf(this.steps,          this.steps.length);
        c.notes          = Arrays.copyOf(this.notes,          this.notes.length);
        c.velocities     = Arrays.copyOf(this.velocities,     this.velocities.length);
        c.gateLengths    = Arrays.copyOf(this.gateLengths,    this.gateLengths.length);
        c.probabilities  = Arrays.copyOf(this.probabilities,  this.probabilities.length);
        c.nudges         = Arrays.copyOf(this.nudges,         this.nudges.length);
        c.ratchets       = Arrays.copyOf(this.ratchets,       this.ratchets.length);
        c.chordIntervals = Arrays.copyOf(this.chordIntervals, this.chordIntervals.length);
        c.ccValues       = Arrays.copyOf(this.ccValues,       this.ccValues.length);
        c.patternLength  = this.patternLength;
        c.muted          = this.muted;
        c.melodicMode    = this.melodicMode;
        c.baseNote       = this.baseNote;
        c.currentStep    = this.currentStep;
        return c;
    }
}
