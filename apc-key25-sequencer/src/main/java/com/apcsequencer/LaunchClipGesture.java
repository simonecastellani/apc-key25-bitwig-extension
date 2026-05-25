package com.apcsequencer;

/**
 * Gesture: tap Scene Launch on a track to launch/stop that track clip.
 *
 * @param track 0-based track index (0 = top row, 4 = bottom row)
 */
public record LaunchClipGesture(int track) implements Gesture {
}
