package com.apcsequencer;

/**
 * Per-track step duration — the time interval between consecutive steps.
 *
 * <p>Maps to Bitwig {@code CursorClip.setStepSize()} values.</p>
 *
 * <p>Beat-time values (quarter note = 1.0):</p>
 * <pre>
 *   1/32 = 0.125    1/16 = 0.25    1/16T ≈ 0.1667
 *   1/8  = 0.5      1/8T ≈ 0.3333  3/16  = 0.75   1/4 = 1.0
 * </pre>
 */
public enum StepDuration {
    /** 1/32 note        */ S32  (1.0 / 8),
    /** 1/16 note        */ S16  (1.0 / 4),
    /** 1/16 triplet     */ S16T (1.0 / 6),
    /** 1/8 note         */ S8   (1.0 / 2),
    /** 1/8 triplet      */ S8T  (1.0 / 3),
    /** Dotted 1/8 (3/16)*/ S316 (3.0 / 4),
    /** 1/4 note         */ S4   (1.0);

    private final double beatTime;

    StepDuration(double beatTime) {
        this.beatTime = beatTime;
    }

    /**
     * Returns the duration of one step in beat time (quarter note = 1.0).
     * This value is passed to {@code CursorClip.setStepSize()}.
     */
    public double beatTime() {
        return beatTime;
    }
}
