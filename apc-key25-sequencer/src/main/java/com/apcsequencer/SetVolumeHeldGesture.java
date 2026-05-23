package com.apcsequencer;

/**
 * Gesture: Volume modifier hold state changed.
 *
 * @param held true when button is pressed, false when released
 */
public record SetVolumeHeldGesture(boolean held) implements Gesture {}
