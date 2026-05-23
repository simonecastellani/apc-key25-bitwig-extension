package com.apcsequencer;

/**
 * Gesture: hold Scene Launch for a track and tap a step to set Loop End Point.
 *
 * @param track 0-based track index (0-4)
 * @param loopEndPoint 1-based step count / end-point column (1-8)
 */
public record TrackLoopEndPointGesture(int track, int loopEndPoint) implements Gesture {}
