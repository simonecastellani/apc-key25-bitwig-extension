package com.apcsequencer;

/**
 * Gesture: turn a Device macro for the currently Focused Track.
 *
 * @param knob  0-based macro index (0..7)
 * @param delta signed relative increment
 */
public record DeviceMacroTurnGesture(int knob, int delta) implements Gesture {}
