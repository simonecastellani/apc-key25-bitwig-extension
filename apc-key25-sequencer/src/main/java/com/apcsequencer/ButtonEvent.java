package com.apcsequencer;

/** A non-pad button press or release event decoded from the APC Key 25 MK1. */
public record ButtonEvent(ButtonId id, boolean pressed) {}
