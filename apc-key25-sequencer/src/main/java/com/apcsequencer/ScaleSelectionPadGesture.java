package com.apcsequencer;

/** Gesture: tap a pad while Scale Selection overlay is active. */
public record ScaleSelectionPadGesture(int track, int step) implements Gesture {
}
