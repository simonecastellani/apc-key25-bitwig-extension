package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for {@link InputModifierTracker}.
 *
 * Slice C — no-modifier gestures:
 *   pad tap (no modifier held)   → StepToggleGesture(track, step)
 *   pad press                    → null (tap/hold resolved later)
 *   pad release                  → emits StepToggle only if no hold-action happened
 *   LEFT press                   → UndoGesture
 *   RIGHT press                  → RedoGesture
 *   LEFT/RIGHT release           → null
 */
class InputModifierTrackerTest {

    // -----------------------------------------------------------------------
    // Slice C — no modifier + pad → StepToggleGesture
    // -----------------------------------------------------------------------

    @Test
    void no_modifier_pad_press_produces_no_immediate_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handlePad(new PadEvent(0, 0, true));
        assertNull(g, "pad press defers tap/hold decision until release");
    }

    @Test
    void no_modifier_pad_release_after_press_encodes_correct_track_and_step() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handlePad(new PadEvent(3, 5, true));
        Gesture g = tracker.handlePad(new PadEvent(3, 5, false));
        assertInstanceOf(StepToggleGesture.class, g);
        StepToggleGesture toggle = (StepToggleGesture) g;
        assertEquals(3, toggle.track(), "track must be preserved");
        assertEquals(5, toggle.step(),  "step must be preserved");
    }

    @Test
    void pad_release_without_matching_press_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handlePad(new PadEvent(0, 0, false));
        assertNull(g, "release without held pad should do nothing");
    }

    // -----------------------------------------------------------------------
    // Navigation: LEFT → UndoGesture, RIGHT → RedoGesture
    // -----------------------------------------------------------------------

    @Test
    void left_button_press_produces_undo_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.LEFT, true));
        assertInstanceOf(UndoGesture.class, g, "LEFT press → UndoGesture");
    }

    @Test
    void right_button_press_produces_redo_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.RIGHT, true));
        assertInstanceOf(RedoGesture.class, g, "RIGHT press → RedoGesture");
    }

    @Test
    void left_button_release_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.LEFT, false));
        assertNull(g, "LEFT release → no gesture");
    }

    @Test
    void right_button_release_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.RIGHT, false));
        assertNull(g, "RIGHT release → no gesture");
    }

    @Test
    void scene_launch_press_without_modifier_produces_launch_clip_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_3, true));

        assertInstanceOf(LaunchClipGesture.class, g);
        assertEquals(3, ((LaunchClipGesture) g).track());
    }

    @Test
    void scene_launch_press_with_shift_held_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_1, true));

        assertNull(g);
    }

    @Test
    void play_pause_press_without_modifier_produces_toggle_transport_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.PLAY_PAUSE, true));

        assertInstanceOf(ToggleTransportGesture.class, g);
    }

    @Test
    void stop_all_clips_press_without_modifier_produces_stop_all_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.STOP_ALL_CLIPS, true));

        assertInstanceOf(StopAllGesture.class, g);
    }

    // -----------------------------------------------------------------------
    // Modifier buttons do NOT produce gestures (they update held-state only)
    // -----------------------------------------------------------------------

    @Test
    void shift_button_press_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));
        assertNull(g, "SHIFT press → no gesture (modifier state update only)");
    }

    @Test
    void scene_launch_press_with_modifier_context_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handleButton(new ButtonEvent(ButtonId.REC, true));
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_0, true));

        assertNull(g, "SCENE_LAUNCH press → no gesture (modifier state update only)");
    }

    @Test
    void keyboard_press_with_held_pad_produces_pitch_assign_for_held_step() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(1, 3, true));
        Gesture g = tracker.handleKeyboard(new KeyboardNoteEvent(64, 92, true));

        assertInstanceOf(PitchAssignGesture.class, g);
        PitchAssignGesture assign = (PitchAssignGesture) g;
        assertEquals(1, assign.track());
        assertEquals(3, assign.step());
        assertEquals(64, assign.pitch());
        assertEquals(92, assign.velocity());
    }

    @Test
    void keyboard_release_with_held_pad_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(1, 3, true));
        Gesture g = tracker.handleKeyboard(new KeyboardNoteEvent(64, 0, false));

        assertNull(g);
    }

    @Test
    void no_modifier_pad_tap_produces_step_toggle_on_release() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(0, 0, true));
        Gesture g = tracker.handlePad(new PadEvent(0, 0, false));

        assertInstanceOf(StepToggleGesture.class, g);
        StepToggleGesture toggle = (StepToggleGesture) g;
        assertEquals(0, toggle.track());
        assertEquals(0, toggle.step());
    }

    @Test
    void pad_hold_plus_keyboard_does_not_emit_toggle_on_release() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(1, 3, true));
        tracker.handleKeyboard(new KeyboardNoteEvent(64, 100, true));
        Gesture release = tracker.handlePad(new PadEvent(1, 3, false));

        assertNull(release, "pitch-assign hold should not also toggle active state");
    }

    @Test
    void keyboard_press_without_held_pad_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture g = tracker.handleKeyboard(new KeyboardNoteEvent(64, 127, true));

        assertNull(g);
    }

    @Test
    void multiple_keyboard_presses_assign_last_key_pressed() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(1, 3, true));
        tracker.handleKeyboard(new KeyboardNoteEvent(64, 90, true));
        Gesture g = tracker.handleKeyboard(new KeyboardNoteEvent(69, 110, true));

        assertInstanceOf(PitchAssignGesture.class, g);
        PitchAssignGesture assign = (PitchAssignGesture) g;
        assertEquals(69, assign.pitch(), "last key pressed should win");
        assertEquals(110, assign.velocity(), "last key velocity should win");
    }

    @Test
    void releasing_pad_without_keyboard_leaves_no_pending_assignment() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(1, 3, true));
        tracker.handlePad(new PadEvent(1, 3, false));
        Gesture g = tracker.handleKeyboard(new KeyboardNoteEvent(64, 127, true));

        assertNull(g);
    }

    @Test
    void held_pad_plus_supported_knob_emits_per_step_knob_turn_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(2, 6, true));
        Gesture g = tracker.handleKnob(new KnobEvent(0, 1));

        assertInstanceOf(PerStepKnobTurnGesture.class, g);
        PerStepKnobTurnGesture turn = (PerStepKnobTurnGesture) g;
        assertEquals(2, turn.track());
        assertEquals(6, turn.step());
        assertEquals(PerStepParameter.VELOCITY, turn.parameter());
        assertEquals(1, turn.delta());
    }

    @Test
    void held_pad_plus_unsupported_knob_emits_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(0, 1, true));
        Gesture g = tracker.handleKnob(new KnobEvent(3, 1));

        assertNull(g);
    }

    @Test
    void knob_turn_without_held_pad_emits_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture g = tracker.handleKnob(new KnobEvent(0, 1));

        assertNull(g);
    }

    @Test
    void held_pad_plus_knob_turn_suppresses_tap_toggle_on_release() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(4, 0, true));
        tracker.handleKnob(new KnobEvent(1, 1));
        Gesture release = tracker.handlePad(new PadEvent(4, 0, false));

        assertNull(release);
    }

    @Test
    void held_scene_launch_plus_knob1_emits_track_step_duration_turn() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_2, true));
        Gesture g = tracker.handleKnob(new KnobEvent(0, 1));

        assertInstanceOf(TrackStepDurationTurnGesture.class, g);
        TrackStepDurationTurnGesture turn = (TrackStepDurationTurnGesture) g;
        assertEquals(2, turn.track());
        assertEquals(1, turn.delta());
    }

    @Test
    void held_scene_launch_plus_pad_same_track_sets_loop_end_point() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_1, true));
        Gesture g = tracker.handlePad(new PadEvent(1, 4, true));

        assertInstanceOf(TrackLoopEndPointGesture.class, g);
        TrackLoopEndPointGesture loop = (TrackLoopEndPointGesture) g;
        assertEquals(1, loop.track());
        assertEquals(5, loop.loopEndPoint());
    }

    @Test
    void held_scene_launch_plus_pad_other_track_emits_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_4, true));
        Gesture g = tracker.handlePad(new PadEvent(3, 2, true));

        assertNull(g);
    }
}
