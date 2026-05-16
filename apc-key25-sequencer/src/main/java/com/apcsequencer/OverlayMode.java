package com.apcsequencer;

/**
 * Overlay display modes.  Only {@code NORMAL} is active in this slice;
 * other modes are stubbed and will be fleshed out in later slices.
 */
public enum OverlayMode {
    /** Default step-edit view — full LED grid shows track/step state. */
    NORMAL,
    /** Sequence Bank selector overlay (activated by Rec button). */
    SEQUENCE_BANK,
    /** Scale Selection overlay (activated by Shift + Volume). */
    SCALE_SELECTION,
    /** Copy mode overlay (activated by Sustain pedal). */
    COPY,
    /** Clear mode overlay (activated by Shift + Sustain). */
    CLEAR,
}
