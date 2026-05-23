package com.apcsequencer;

/**
 * Gesture: hold Scene Launch for a track and turn one of the per-track knobs.
 *
 * @param track 0-based track index (0-4)
 * @param parameter target per-track parameter
 * @param delta signed knob movement
 */
public record PerTrackKnobTurnGesture(int track, PerTrackParameter parameter, int delta)
        implements Gesture {}
