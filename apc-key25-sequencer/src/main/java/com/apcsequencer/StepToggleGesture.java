package com.apcsequencer;

/**
 * Gesture: tap a pad with no modifier held → toggle the active flag of the
 * addressed step.
 *
 * @param track 0-based track index (0 = top row, 4 = bottom row)
 * @param step  0-based step index within the track (0–7)
 */
public record StepToggleGesture(int track, int step) implements Gesture {
}
