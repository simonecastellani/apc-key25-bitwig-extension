package com.apcsequencer;

/**
 * Gesture: hold a pad and press a keyboard key to assign pitch for that step.
 *
 * @param track 0-based track index (0 = top row, 4 = bottom row)
 * @param step  0-based step index within the track (0–7)
 * @param pitch assigned MIDI pitch (0–127)
 * @param velocity assigned MIDI velocity (0–127)
 */
public record PitchAssignGesture(int track, int step, int pitch, int velocity) implements Gesture {
}
