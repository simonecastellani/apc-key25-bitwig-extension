package com.apcsequencer;

/** Gesture: clear all steps for one track while Clear mode is active. */
public record ClearTrackGesture(int track) implements Gesture {
}
