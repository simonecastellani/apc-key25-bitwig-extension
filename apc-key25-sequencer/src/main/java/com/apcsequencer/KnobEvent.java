package com.apcsequencer;

/** A knob turn event decoded from the APC Key 25 MK1. Knob index 0–7. */
public record KnobEvent(int knob, int value) {}
