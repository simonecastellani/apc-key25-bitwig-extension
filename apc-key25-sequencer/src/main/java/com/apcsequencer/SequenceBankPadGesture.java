package com.apcsequencer;

/** Gesture: select or clear a Sequence Slot from the Sequence Bank overlay. */
public record SequenceBankPadGesture(int track, int slot) implements Gesture {
}
