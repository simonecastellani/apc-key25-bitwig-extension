package com.apcsequencer;

/**
 * Routes pad taps and scene launch presses to the appropriate {@link Sequencer}
 * and manages Scene Launch button LEDs.
 *
 * <p>All 5 sequencers run (and play) simultaneously; this class only controls
 * which one's pattern is shown and edited on the pad row.</p>
 */
public class TrackRouter {

    /** Sink for Scene Launch button LEDs (5 buttons, rows 0–4). */
    public interface SceneLedOutput {
        /** Set Scene Launch button {@code row} (0-based) to the given LED color. */
        void setSceneLed(int row, int color);
    }

    private final Sequencer[]    sequencers;
    private final SceneLedOutput sceneLeds;
    private       int            selectedTrack = 0;

    /**
     * @param sequencers  array of exactly 5 sequencers, one per Bitwig track
     * @param sceneLeds   LED sink for the 5 Scene Launch buttons
     */
    public TrackRouter(Sequencer[] sequencers, SceneLedOutput sceneLeds) {
        this.sequencers = sequencers;
        this.sceneLeds  = sceneLeds;
    }

    /**
     * Emit initial Scene Launch LEDs: track 0 green, all others off.
     * Call once from {@code Extension.init()} after all sequencers are ready.
     */
    public void initLeds() {
        for (int i = 0; i < sequencers.length; i++) {
            sceneLeds.setSceneLed(i, i == selectedTrack ? Sequencer.LED_GREEN : Sequencer.LED_OFF);
        }
    }

    /** Route a pad note-on to the currently selected sequencer. */
    public void padTapped(int noteNumber) {
        sequencers[selectedTrack].padTapped(noteNumber);
    }

    /**
     * Switch the active track to {@code row}, update Scene Launch LEDs,
     * and refresh the pad LEDs to show the new track's pattern.
     * Out-of-range values are silently ignored.
     */
    public void sceneLaunchPressed(int row) {
        if (row < 0 || row >= sequencers.length) return;
        selectedTrack = row;
        for (int i = 0; i < sequencers.length; i++) {
            sceneLeds.setSceneLed(i, i == selectedTrack ? Sequencer.LED_GREEN : Sequencer.LED_OFF);
        }
        sequencers[selectedTrack].refreshLeds();
    }

    /** Returns the 0-based index of the currently selected track. */
    public int selectedTrack() {
        return selectedTrack;
    }
}
