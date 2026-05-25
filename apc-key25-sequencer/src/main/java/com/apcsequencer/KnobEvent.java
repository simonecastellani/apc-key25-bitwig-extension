package com.apcsequencer;

/**
 * A relative knob turn event decoded from the APC Key 25 MK1.
 *
 * @param knob  0-based knob index (0..7)
 * @param delta signed relative movement (positive/negative)
 */
public record KnobEvent(int knob, int delta) {}
