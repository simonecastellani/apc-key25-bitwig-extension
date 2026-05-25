package com.apcsequencer;

/** Gesture: clear one step while Clear mode is active. */
public record ClearPadGesture(int track, int step) implements Gesture {
}
