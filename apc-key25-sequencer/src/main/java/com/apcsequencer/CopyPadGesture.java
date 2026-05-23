package com.apcsequencer;

/** Gesture: pick a step during Copy mode. */
public record CopyPadGesture(int track, int step) implements Gesture {
}
