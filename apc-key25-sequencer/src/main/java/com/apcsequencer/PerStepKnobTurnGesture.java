package com.apcsequencer;

/**
 * Gesture: hold a step pad and turn a supported per-step Parameter Knob.
 *
 * @param track 0-based track index
 * @param step  0-based step index
 * @param parameter resolved per-step parameter targeted by this knob
 * @param delta signed relative knob movement (positive/negative)
 */
public record PerStepKnobTurnGesture(int track, int step, PerStepParameter parameter, int delta)
        implements Gesture {
}
