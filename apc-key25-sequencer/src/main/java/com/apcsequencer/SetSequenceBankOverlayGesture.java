package com.apcsequencer;

/** Gesture: control Sequence Bank overlay visibility and clear-mode state. */
public record SetSequenceBankOverlayGesture(boolean active, boolean clearMode) implements Gesture {
}
