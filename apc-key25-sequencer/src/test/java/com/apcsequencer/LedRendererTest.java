package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for {@link LedRenderer}.
 *
 * Slice D — Normal view LED rules (from CONTEXT.md LED Mapping table):
 *
 *   Step inactive (not playhead, not loop end)  → off      (0)
 *   Step active   (not playhead, not loop end)  → green    (1)
 *   Playhead on inactive step                   → red      (3)
 *   Playhead on active step                     → yellow   (5)
 *   Loop End Point marker — step inactive       → red blink   (4)
 *   Loop End Point marker — step active         → yellow blink (6)
 *
 * Priority: playhead > loop-end-point marker > active/inactive.
 *
 * LED color constants: 0=off, 1=green, 2=green-blink, 3=red,
 *                      4=red-blink, 5=yellow, 6=yellow-blink.
 * Pad LED MIDI note formula: (4 − track) * 8 + step.
 */
class LedRendererTest {

    private static final int[] NO_PLAYHEAD = {-1, -1, -1, -1, -1};

    // -----------------------------------------------------------------------
    // Basic active / inactive
    // -----------------------------------------------------------------------

    @Test
    void inactive_step_shows_off() {
        SequencerState state = new SequencerState(); // all inactive
        // loopEndPoint default = 8; step 0 is not the loop end point
        int[][] leds = LedRenderer.render(state, NO_PLAYHEAD);
        assertEquals(0, leds[0][0], "inactive step, no playhead → off");
    }

    @Test
    void active_step_shows_green() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 0);  // activate track 0 step 0
        int[][] leds = LedRenderer.render(state, NO_PLAYHEAD);
        assertEquals(1, leds[0][0], "active step, no playhead → green");
    }

    // -----------------------------------------------------------------------
    // Playhead
    // -----------------------------------------------------------------------

    @Test
    void playhead_on_inactive_step_shows_red() {
        SequencerState state = new SequencerState(); // all inactive
        // playhead at step 0 for track 0; note step 0 is not the loop end point (loopEndPoint=8)
        int[] playhead = {0, -1, -1, -1, -1};
        int[][] leds = LedRenderer.render(state, playhead);
        assertEquals(3, leds[0][0], "playhead on inactive → red");
    }

    @Test
    void playhead_on_active_step_shows_yellow() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 2); // activate track 0 step 2
        int[] playhead = {2, -1, -1, -1, -1};
        int[][] leds = LedRenderer.render(state, playhead);
        assertEquals(5, leds[0][2], "playhead on active → yellow");
    }

    @Test
    void playhead_step_above_visible_grid_wraps_within_track_loop() {
        SequencerState state = new SequencerState();
        int[] playhead = {47, -1, -1, -1, -1};

        int[][] leds = LedRenderer.render(state, playhead);

        assertEquals(3, leds[0][7], "playhead 47 should map to visible step 7");
    }

    @Test
    void wrapped_playhead_uses_current_loop_end_point_not_fixed_8() {
        SequencerState state = new SequencerState();
        state.setLoopEndPoint(0, 4);
        int[] playhead = {10, -1, -1, -1, -1};

        int[][] leds = LedRenderer.render(state, playhead);

        assertEquals(3, leds[0][2], "playhead 10 should map to step 2 in 4-step loop");
    }

    @Test
    void non_playhead_steps_are_unaffected_by_playhead_position() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 1); // step 1 active
        int[] playhead = {3, -1, -1, -1, -1}; // playhead at step 3
        int[][] leds = LedRenderer.render(state, playhead);
        assertEquals(1, leds[0][1], "step 1 active, not playhead → green");
        assertEquals(0, leds[0][0], "step 0 inactive, not playhead → off");
    }

    // -----------------------------------------------------------------------
    // Loop End Point marker
    // -----------------------------------------------------------------------

    @Test
    void loop_end_point_on_inactive_step_shows_red_blink() {
        // Default loopEndPoint = 8 → marker at step index 7
        SequencerState state = new SequencerState();
        int[][] leds = LedRenderer.render(state, NO_PLAYHEAD);
        assertEquals(4, leds[0][7], "loop end point, inactive → red blink");
    }

    @Test
    void loop_end_point_on_active_step_shows_yellow_blink() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 7); // activate the loop end point step
        int[][] leds = LedRenderer.render(state, NO_PLAYHEAD);
        assertEquals(6, leds[0][7], "loop end point, active → yellow blink");
    }

    @Test
    void loop_end_point_marker_at_custom_position() {
        // Set loopEndPoint = 4 → marker at step 3; step 7 is now outside the loop
        SequencerState state = new SequencerState();
        state.setLoopEndPoint(0, 4);
        int[][] leds = LedRenderer.render(state, NO_PLAYHEAD);
        assertEquals(4, leds[0][3], "custom loop end at step 3 → red blink");
        // Step 7 is outside the loop — should be off (inactive, not the marker)
        assertEquals(0, leds[0][7], "step outside loop → off");
    }

    // -----------------------------------------------------------------------
    // Priority: playhead beats loop-end-point marker
    // -----------------------------------------------------------------------

    @Test
    void playhead_on_loop_end_point_inactive_shows_red_not_blink() {
        // Default loopEndPoint = 8 → marker at step 7; playhead also at step 7
        SequencerState state = new SequencerState();
        int[] playhead = {7, -1, -1, -1, -1};
        int[][] leds = LedRenderer.render(state, playhead);
        assertEquals(3, leds[0][7], "playhead on loop-end, inactive → red (not red blink)");
    }

    @Test
    void playhead_on_loop_end_point_active_shows_yellow_not_blink() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 7);
        int[] playhead = {7, -1, -1, -1, -1};
        int[][] leds = LedRenderer.render(state, playhead);
        assertEquals(5, leds[0][7], "playhead on loop-end, active → yellow (not yellow blink)");
    }

    // -----------------------------------------------------------------------
    // Multi-track independence
    // -----------------------------------------------------------------------

    @Test
    void each_track_rendered_independently() {
        SequencerState state = new SequencerState();
        state.toggleStep(1, 3); // track 1 step 3 active
        int[] playhead = {-1, 3, -1, -1, -1}; // playhead on track 1 step 3
        int[][] leds = LedRenderer.render(state, playhead);
        assertEquals(5, leds[1][3], "track 1 step 3: active + playhead → yellow");
        assertEquals(0, leds[0][3], "track 0 step 3: inactive, no playhead → off");
    }

    // -----------------------------------------------------------------------
    // Return value dimensions
    // -----------------------------------------------------------------------

    @Test
    void render_returns_5_by_8_grid() {
        SequencerState state = new SequencerState();
        int[][] leds = LedRenderer.render(state, NO_PLAYHEAD);
        assertEquals(5, leds.length,    "5 tracks");
        assertEquals(8, leds[0].length, "8 steps per track");
    }
}
