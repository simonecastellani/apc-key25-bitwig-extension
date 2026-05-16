package com.apcsequencer;

/** A pad press or release event decoded from the APC Key 25 MK1. */
public record PadEvent(int track, int step, boolean pressed) {}
