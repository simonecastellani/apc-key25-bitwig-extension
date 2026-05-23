package com.apcsequencer;

/**
 * Gesture: Pan modifier hold state changed.
 *
 * @param held true when button is pressed, false when released
 */
public record SetPanHeldGesture(boolean held) implements Gesture {}
