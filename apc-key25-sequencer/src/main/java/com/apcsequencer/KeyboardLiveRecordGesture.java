package com.apcsequencer;

/** Keyboard event routed for Live Record processing. */
public record KeyboardLiveRecordGesture(KeyboardNoteEvent event) implements Gesture {}
