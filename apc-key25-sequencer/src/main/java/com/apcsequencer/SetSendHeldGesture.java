package com.apcsequencer;

/**
 * Gesture: Send modifier hold state changed.
 *
 * @param held true when button is pressed, false when released
 */
public record SetSendHeldGesture(boolean held) implements Gesture {}
