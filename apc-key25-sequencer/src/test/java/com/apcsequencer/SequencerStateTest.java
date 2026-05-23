package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SequencerStateTest {

    // -------------------------------------------------------------------------
    // Slice 1 — tracer bullet: toggleStep activates an inactive step
    // -------------------------------------------------------------------------

    @Test
    void toggleStep_on_inactive_step_activates_it_and_returns_nonempty_diff() {
        SequencerState state = new SequencerState();

        assertFalse(state.getStep(0, 0).isActive(), "step should start inactive");

        StateDiff diff = state.toggleStep(0, 0);

        assertTrue(state.getStep(0, 0).isActive(), "step should be active after toggle");
        assertFalse(diff.isEmpty(), "diff must not be empty when state changed");
    }

    // -------------------------------------------------------------------------
    // Slice 2 — idempotent write: setStepActive with same value → empty diff
    // -------------------------------------------------------------------------

    @Test
    void setStepActive_with_same_value_returns_empty_diff() {
        SequencerState state = new SequencerState();

        // step starts inactive; setting inactive again → no change
        StateDiff diff = state.setStepActive(0, 0, false);

        assertTrue(diff.isEmpty(), "diff must be empty when active flag did not change");
        assertFalse(state.getStep(0, 0).isActive(), "step must still be inactive");
    }

    @Test
    void toggleStep_on_active_step_deactivates_it_and_only_that_step_changes() {
        SequencerState state = new SequencerState();
        state.toggleStep(1, 3); // activate track 1 step 3

        StateDiff diff = state.toggleStep(1, 3); // deactivate

        assertFalse(state.getStep(1, 3).isActive(), "step should be inactive after second toggle");
        assertFalse(diff.isEmpty(), "diff must not be empty — active changed");
        // The diff must reference only track 1 step 3, not other steps
        assertEquals(1, diff.stepChanges().size(), "diff should contain exactly one step change");
        assertEquals(new StateDiff.StepChange(1, 3), diff.stepChanges().get(0));
    }

    // -------------------------------------------------------------------------
    // Slice 3 — default values: all StepState fields are at documented defaults
    // -------------------------------------------------------------------------

    @Test
    void freshSequencerState_has_correct_step_defaults_on_all_tracks() {
        SequencerState state = new SequencerState();

        for (int t = 0; t < 5; t++) {
            for (int s = 0; s < 8; s++) {
                StepState step = state.getStep(t, s);
                int track = t, stepIdx = s;
                assertAll("track " + t + " step " + s,
                    () -> assertFalse(step.isActive(),                          "active default false"),
                    () -> assertEquals(60, step.getPitch(),                     "pitch default 60 (C3)"),
                    () -> assertEquals(100, step.getVelocity(),                 "velocity default 100"),
                    () -> assertEquals(0.5, step.getGateLength(), 1e-9,         "gateLength default 0.5"),
                    () -> assertEquals(1.0, step.getProbability(), 1e-9,        "probability default 1.0"),
                    () -> assertEquals(ChordVoicing.ROOT_ONLY, step.getChordVoicing(), "chordVoicing default ROOT_ONLY"),
                    () -> assertEquals(0, step.getScaleDegreeOffset(),          "scaleDegreeOffset default 0"),
                    () -> assertEquals(1, step.getRatchetCount(),               "ratchetCount default 1"),
                    () -> assertEquals(0.0, step.getRatchetDecay(), 1e-9,       "ratchetDecay default 0.0"),
                    () -> assertEquals(StepCondition.ALWAYS, step.getStepCondition(), "stepCondition default ALWAYS")
                );
            }
        }
    }

    @Test
    void freshSequencerState_has_correct_track_defaults() {
        SequencerState state = new SequencerState();

        for (int t = 0; t < 5; t++) {
            TrackState track = state.getTrack(t);
            int idx = t;
            assertAll("track " + t,
                () -> assertEquals(StepDuration.S16, track.getStepDuration(),  "stepDuration default S16"),
                () -> assertEquals(8, track.getLoopEndPoint(),                 "loopEndPoint default 8"),
                () -> assertEquals(0, track.getActiveSlot(),                   "activeSlot default 0"),
                () -> assertEquals(0, track.getPatternRotation(),              "patternRotation default 0"),
                () -> assertEquals(50, track.getSwing(),                       "swing default 50"),
                () -> assertEquals(0, track.getTranspose(),                    "transpose default 0"),
                () -> assertEquals(1.0, track.getTrackProbability(), 1e-9,     "trackProbability default 1.0"),
                () -> assertEquals(LoopMultiplier.ONE, track.getLoopMultiplier(), "loopMultiplier default ONE"),
                () -> assertEquals(0.0, track.getPhaseOffset(), 1e-9,          "phaseOffset default 0.0")
            );
        }
    }

    @Test
    void freshSequencerState_globalScale_is_cMajor_and_focusedTrack_is_zero() {
        SequencerState state = new SequencerState();
        assertEquals(new GlobalScale(0, Mode.MAJOR), state.getGlobalScale(), "default globalScale C Major");
        assertEquals(0, state.getFocusedTrack(), "default focusedTrack 0");
    }

    // -------------------------------------------------------------------------
    // Slice 4 — loopEndPoint clamped to [1, 8]
    // -------------------------------------------------------------------------

    @Test
    void setLoopEndPoint_clamps_below_minimum_to_1() {
        SequencerState state = new SequencerState();
        state.setLoopEndPoint(0, 0); // below min
        assertEquals(1, state.getTrack(0).getLoopEndPoint(), "should be clamped to 1");
    }

    @Test
    void setLoopEndPoint_clamps_above_maximum_to_8() {
        SequencerState state = new SequencerState();
        state.setLoopEndPoint(0, 9); // above max
        assertEquals(8, state.getTrack(0).getLoopEndPoint(), "should be clamped to 8");
    }

    @Test
    void setLoopEndPoint_with_same_value_returns_empty_diff() {
        SequencerState state = new SequencerState(); // default loopEndPoint = 8
        StateDiff diff = state.setLoopEndPoint(0, 8);
        assertTrue(diff.isEmpty(), "no change → diff must be empty");
    }

    @Test
    void setStepDuration_changes_track_step_duration_and_returns_nonempty_diff() {
        SequencerState state = new SequencerState();

        StateDiff diff = state.setStepDuration(0, StepDuration.S8);

        assertEquals(StepDuration.S8, state.getTrack(0).getStepDuration());
        assertFalse(diff.isEmpty());
    }

    @Test
    void setStepDuration_with_same_value_returns_empty_diff() {
        SequencerState state = new SequencerState();

        StateDiff diff = state.setStepDuration(0, StepDuration.S16);

        assertTrue(diff.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Slice 5 — switchSlot to an empty slot copies current content (copy-on-first-select)
    // -------------------------------------------------------------------------

    @Test
    void switchSlot_to_empty_slot_copies_current_step_pattern() {
        SequencerState state = new SequencerState();

        // Mark step 0,3 active in slot 0 (the live state)
        state.toggleStep(0, 3);
        assertTrue(state.getStep(0, 3).isActive(), "step 0,3 active before switch");

        // Slot 1 is empty — switch copies content
        state.switchSlot(0, 1);

        // We are now on slot 1; the step pattern should still be active
        assertTrue(state.getStep(0, 3).isActive(), "step 0,3 still active after switch to empty slot");
        assertEquals(1, state.getTrack(0).getActiveSlot(), "activeSlot should now be 1");
        assertTrue(state.getTrack(0).isSlotPopulated(1), "slot 1 should now be populated");
    }

    // -------------------------------------------------------------------------
    // Slice 6 — switchSlot to a populated slot switches without over-copying
    // -------------------------------------------------------------------------

    @Test
    void switchSlot_to_populated_slot_restores_its_saved_content() {
        SequencerState state = new SequencerState();

        // Slot 0: activate step 0,0
        state.toggleStep(0, 0);

        // Switch to slot 1 (empty → copies slot 0 content)
        state.switchSlot(0, 1);

        // Now on slot 1; modify step 0,0 to be inactive, mark step 0,5 active
        state.toggleStep(0, 0); // deactivate 0,0
        state.toggleStep(0, 5); // activate 0,5

        assertFalse(state.getStep(0, 0).isActive(), "slot1 live: step 0 inactive");
        assertTrue( state.getStep(0, 5).isActive(), "slot1 live: step 5 active");

        // Save slot 1 and switch back to slot 0
        state.switchSlot(0, 0);

        // Slot 0 was originally step 0,0 active, step 0,5 inactive
        assertTrue( state.getStep(0, 0).isActive(), "slot0 restored: step 0 active");
        assertFalse(state.getStep(0, 5).isActive(), "slot0 restored: step 5 inactive");
        assertEquals(0, state.getTrack(0).getActiveSlot(), "activeSlot back to 0");
    }

    // -------------------------------------------------------------------------
    // Slice 7 — Euclidean bitmask (Bjorklund): 3-in-8, 5-in-8, 7-in-16
    // Expected patterns (standard Bjorklund output, canonical form):
    //   3 in 8  → [1,0,0,1,0,0,1,0]
    //   5 in 8  → [1,0,1,1,0,1,1,0]  (or [1,0,1,0,1,0,1,1] — verify below)
    //   7 in 16 → evenly spread, 7 active bits
    // -------------------------------------------------------------------------

    @Test
    void euclidean_3_in_8_produces_correct_bitmask() {
        boolean[] pattern = EuclideanBitmask.generate(3, 8);
        assertEquals(8, pattern.length, "pattern length should be 8");
        int active = countActive(pattern);
        assertEquals(3, active, "exactly 3 active steps");
        // canonical Bjorklund 3-in-8: [1,0,0,1,0,0,1,0]
        assertArrayEquals(new boolean[]{ true,false,false,true,false,false,true,false }, pattern,
            "3-in-8 should be [1,0,0,1,0,0,1,0]");
    }

    @Test
    void euclidean_5_in_8_produces_correct_bitmask() {
        boolean[] pattern = EuclideanBitmask.generate(5, 8);
        assertEquals(8, pattern.length, "pattern length should be 8");
        assertEquals(5, countActive(pattern), "exactly 5 active steps");
        // canonical Bjorklund 5-in-8: [1,0,1,1,0,1,1,0]
        assertArrayEquals(new boolean[]{ true,false,true,true,false,true,true,false }, pattern,
            "5-in-8 should be [1,0,1,1,0,1,1,0]");
    }

    @Test
    void euclidean_7_in_16_produces_7_evenly_distributed_active_steps() {
        boolean[] pattern = EuclideanBitmask.generate(7, 16);
        assertEquals(16, pattern.length, "pattern length should be 16");
        assertEquals(7, countActive(pattern), "exactly 7 active steps");
    }

    @Test
    void euclidean_degenerate_all_active_fills_all_steps() {
        boolean[] pattern = EuclideanBitmask.generate(8, 8);
        assertEquals(8, countActive(pattern), "8-in-8: all steps active");
    }

    @Test
    void euclidean_degenerate_zero_active_fills_no_steps() {
        boolean[] pattern = EuclideanBitmask.generate(0, 8);
        assertEquals(0, countActive(pattern), "0-in-8: no steps active");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int countActive(boolean[] pattern) {
        int n = 0;
        for (boolean b : pattern) if (b) n++;
        return n;
    }
}
