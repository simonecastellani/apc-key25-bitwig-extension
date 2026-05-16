package com.apcsequencer;

/**
 * Pure function that computes the 5×8 LED colour grid for the Normal step-edit view.
 *
 * <h3>LED colour codes (APC Key 25 MK1)</h3>
 * <pre>
 *   0 = off
 *   1 = green
 *   2 = green blink
 *   3 = red
 *   4 = red blink
 *   5 = yellow
 *   6 = yellow blink
 * </pre>
 *
 * <h3>Normal view rules (CONTEXT.md LED Mapping)</h3>
 * <pre>
 *   Priority order (highest first): playhead > loop-end-point marker > active/inactive
 *
 *   Playhead on inactive step                → red      (3)
 *   Playhead on active step                  → yellow   (5)
 *   Loop End Point marker + step inactive    → red blink   (4)
 *   Loop End Point marker + step active      → yellow blink (6)
 *   Step active                              → green    (1)
 *   Step inactive                            → off      (0)
 * </pre>
 *
 * <p>The Loop End Point marker is placed at step index {@code loopEndPoint − 1}
 * (the last step in the active loop).  Steps at index ≥ loopEndPoint are outside
 * the loop and rendered as off (inactive), regardless of their stored active state.</p>
 *
 * <p>No Bitwig dependency — fully unit-testable.</p>
 */
public final class LedRenderer {

    // LED colour constants — matches APC Key 25 MK1 velocity values
    public static final int OFF          = 0;
    public static final int GREEN        = 1;
    public static final int GREEN_BLINK  = 2;
    public static final int RED          = 3;
    public static final int RED_BLINK    = 4;
    public static final int YELLOW       = 5;
    public static final int YELLOW_BLINK = 6;

    private LedRenderer() {}

    /**
     * Compute the LED colour grid for the Normal view.
     *
     * @param state           current sequencer state (5 tracks × 8 steps)
     * @param playheadPerTrack array of length 5; each element is the 0-based
     *                         step index currently under the playhead for that
     *                         track, or −1 if the clip is not playing
     * @return a {@code [track][step]} int array of LED colour codes (0–6)
     */
    public static int[][] render(SequencerState state, int[] playheadPerTrack) {
        int[][] leds = new int[SequencerState.TRACK_COUNT][TrackState.STEP_COUNT];

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            TrackState track     = state.getTrack(t);
            int loopEnd          = track.getLoopEndPoint();   // 1-based count, marker at loopEnd-1
            int loopEndStep      = loopEnd - 1;               // 0-based index of the marker
            int playhead         = playheadPerTrack[t];

            for (int s = 0; s < TrackState.STEP_COUNT; s++) {
                boolean active = track.getStep(s).isActive();

                // Steps outside the loop are always off
                if (s >= loopEnd) {
                    leds[t][s] = OFF;
                    continue;
                }

                boolean isPlayhead  = (s == playhead);
                boolean isLoopEnd   = (s == loopEndStep);

                if (isPlayhead) {
                    // Playhead has highest priority — no blinking
                    leds[t][s] = active ? YELLOW : RED;
                } else if (isLoopEnd) {
                    // Loop End Point marker — blinking colours
                    leds[t][s] = active ? YELLOW_BLINK : RED_BLINK;
                } else {
                    // Normal step
                    leds[t][s] = active ? GREEN : OFF;
                }
            }
        }

        return leds;
    }
}
