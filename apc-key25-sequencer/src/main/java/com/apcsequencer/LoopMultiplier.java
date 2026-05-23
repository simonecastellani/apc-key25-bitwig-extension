package com.apcsequencer;

/**
 * Loop length multiplier relative to the base loop (Step Count × Step Duration).
 */
public enum LoopMultiplier {
    HALF(0.5),
    ONE(1.0),
    TWO(2.0),
    FOUR(4.0);

    private final double factor;

    LoopMultiplier(double factor) {
        this.factor = factor;
    }

    public double factor() {
        return factor;
    }
}
