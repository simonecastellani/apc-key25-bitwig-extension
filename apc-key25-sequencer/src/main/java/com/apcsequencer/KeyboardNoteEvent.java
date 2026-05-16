package com.apcsequencer;

/** A keyboard note event from the APC Key 25 MK1 (MIDI channel 2). */
public record KeyboardNoteEvent(int pitch, int velocity, boolean pressed) {}
