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
    private boolean liveRecordActive;
    private Integer copySourceTrack;
    private Integer copySourceStep;
    private Integer copySourceSceneTrack;

    /** Returns the currently active overlay mode. */
    public OverlayMode getMode() {
        return currentMode;
    }

    /** True when Sequence Bank overlay is in clear mode (Shift + Rec). */
    public boolean isSequenceBankClearMode() {
        return sequenceBankClearMode;
    }

    /** True when Live Record mode is active. */
    public boolean isLiveRecordActive() {
        return liveRecordActive;
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
        currentMode = OverlayMode.COPY_SOURCE;
        clearCopySelection();
    }

    /** Chooses a step as copy source and waits for a target selection. */
    public void selectCopySourceStep(int track, int step) {
        copySourceTrack = track;
        copySourceStep = step;
        copySourceSceneTrack = null;
        currentMode = OverlayMode.COPY_TARGET;
    }

    /** Chooses a track as copy source and waits for a target selection. */
    public void selectCopySourceTrack(int track) {
        copySourceSceneTrack = track;
        copySourceTrack = null;
        copySourceStep = null;
        currentMode = OverlayMode.COPY_TARGET;
    }

    public boolean hasCopyStepSource() {
        return copySourceTrack != null && copySourceStep != null;
    }

    public boolean hasCopyTrackSource() {
        return copySourceSceneTrack != null;
    }

    public int copySourceTrack() {
        return copySourceTrack;
    }

    public int copySourceStep() {
        return copySourceStep;
    }

    public int copySourceSceneTrack() {
        return copySourceSceneTrack;
    }

    /** Activates Clear mode (Shift + Sustain). */
    public void enterClear() {
        currentMode = OverlayMode.CLEAR;
    }

    /** Returns to the Normal step-edit view. */
    public void returnToNormal() {
        currentMode = OverlayMode.NORMAL;
        sequenceBankClearMode = false;
        clearCopySelection();
    }

    /** Toggles Live Record mode flag. */
    public void setLiveRecordActive(boolean active) {
        liveRecordActive = active;
    }

    private void clearCopySelection() {
        copySourceTrack = null;
        copySourceStep = null;
        copySourceSceneTrack = null;
    }
}
