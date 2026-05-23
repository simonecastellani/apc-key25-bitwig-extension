package com.apcsequencer;

/**
 * Gesture: turn a Send level for the currently Focused Track.
 *
 * @param knob  0-based send index (0..7)
 * @param delta signed relative increment
 */
public record SendLevelTurnGesture(int knob, int delta) implements Gesture {}
