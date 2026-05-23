package com.apcsequencer;

/**
 * Gesture: while holding Volume, tap Scene Launch for a track to toggle mute.
 *
 * @param track 0-based track index (0-4)
 */
public record ToggleTrackMuteGesture(int track) implements Gesture {}
