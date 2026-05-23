package com.apcsequencer;

/**
 * Tracks which overlay is currently active and provides the current
 * {@link OverlayMode} for the LED renderer and gesture dispatcher.
 *
 * <p>Only {@link OverlayMode#NORMAL} is implemented in this slice.
 * Transitions to other modes are stubs that will be wired in later slices.</p>
 */
public final class OverlayController {

    private OverlayMode currentMode = OverlayMode.NORMAL;
    private boolean sequenceBankClearMode;

    /** Returns the currently active overlay mode. */
    public OverlayMode getMode() {
        return currentMode;
    }

    /** True when Sequence Bank overlay is in clear mode (Shift + Rec). */
    public boolean isSequenceBankClearMode() {
        return sequenceBankClearMode;
    }

    // ------------------------------------------------------------------
    // Transition stubs — to be implemented in later slices
    // ------------------------------------------------------------------

    /** Activates the Sequence Bank overlay (Rec button). */
    public void enterSequenceBank(boolean clearMode) {
        currentMode = OverlayMode.SEQUENCE_BANK;
        sequenceBankClearMode = clearMode;
    }

    /** Activates the Scale Selection overlay (Shift + Volume). */
    public void enterScaleSelection() {
        currentMode = OverlayMode.SCALE_SELECTION;
    }

    /** Activates Copy mode (Sustain pedal). */
    public void enterCopy() {
        currentMode = OverlayMode.COPY;
    }

    /** Activates Clear mode (Shift + Sustain). */
    public void enterClear() {
        currentMode = OverlayMode.CLEAR;
    }

    /** Returns to the Normal step-edit view. */
    public void returnToNormal() {
        currentMode = OverlayMode.NORMAL;
        sequenceBankClearMode = false;
    }
}
