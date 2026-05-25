package com.apcsequencer;

/**
 * Gesture: hold Scene Launch for a track and turn knob 1 to cycle Step Duration.
 *
 * @param track 0-based track index (0-4)
 * @param delta signed knob movement; only sign is used for direction
 */
public record TrackStepDurationTurnGesture(int track, int delta) implements Gesture {}
