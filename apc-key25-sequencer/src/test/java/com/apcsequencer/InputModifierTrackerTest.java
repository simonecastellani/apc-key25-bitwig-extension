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
    void held_pad_plus_zero_delta_knob_emits_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(0, 1, true));
        Gesture g = tracker.handleKnob(new KnobEvent(4, 0));

        assertNull(g);
    }

    @Test
    void held_pad_plus_knob4_emits_scale_degree_offset_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(1, 2, true));
        Gesture g = tracker.handleKnob(new KnobEvent(3, 1));

        assertInstanceOf(PerStepKnobTurnGesture.class, g);
        PerStepKnobTurnGesture turn = (PerStepKnobTurnGesture) g;
        assertEquals(1, turn.track());
        assertEquals(2, turn.step());
        assertEquals(PerStepParameter.SCALE_DEGREE_OFFSET, turn.parameter());
        assertEquals(1, turn.delta());
    }

    @Test
    void held_pad_plus_knob5_emits_chord_voicing_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handlePad(new PadEvent(3, 4, true));
        Gesture g = tracker.handleKnob(new KnobEvent(4, 1));

        assertInstanceOf(PerStepKnobTurnGesture.class, g);
        PerStepKnobTurnGesture turn = (PerStepKnobTurnGesture) g;
        assertEquals(3, turn.track());
        assertEquals(4, turn.step());
        assertEquals(PerStepParameter.CHORD_VOICING, turn.parameter());
        assertEquals(1, turn.delta());
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

    @Test
    void held_scene_launch_plus_knob2_emits_pattern_rotation_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_0, true));
        Gesture g = tracker.handleKnob(new KnobEvent(1, 1));

        assertInstanceOf(PerTrackKnobTurnGesture.class, g);
        PerTrackKnobTurnGesture turn = (PerTrackKnobTurnGesture) g;
        assertEquals(0, turn.track());
        assertEquals(PerTrackParameter.PATTERN_ROTATION, turn.parameter());
        assertEquals(1, turn.delta());
    }

    @Test
    void held_scene_launch_plus_knob7_emits_euclidean_distribution_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_0, true));
        Gesture g = tracker.handleKnob(new KnobEvent(6, 1));

        assertInstanceOf(PerTrackKnobTurnGesture.class, g);
        PerTrackKnobTurnGesture turn = (PerTrackKnobTurnGesture) g;
        assertEquals(0, turn.track());
        assertEquals(PerTrackParameter.EUCLIDEAN_DISTRIBUTION, turn.parameter());
        assertEquals(1, turn.delta());
    }

    @Test
    void shift_plus_volume_press_toggles_scale_selection_overlay() {
        InputModifierTracker tracker = new InputModifierTracker();

        tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.VOLUME, true));

        assertInstanceOf(ToggleScaleSelectionOverlayGesture.class, g);
    }

    @Test
    void pad_press_while_scale_selection_active_emits_scale_selection_pad_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));
        tracker.handleButton(new ButtonEvent(ButtonId.VOLUME, true));

        Gesture g = tracker.handlePad(new PadEvent(0, 3, true));

        assertInstanceOf(ScaleSelectionPadGesture.class, g);
        ScaleSelectionPadGesture select = (ScaleSelectionPadGesture) g;
        assertEquals(0, select.track());
        assertEquals(3, select.step());
    }

    @Test
    void shift_release_while_scale_selection_active_emits_dismiss_overlay_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));
        tracker.handleButton(new ButtonEvent(ButtonId.VOLUME, true));

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, false));

        assertInstanceOf(DismissScaleSelectionOverlayGesture.class, g);
    }

    @Test
    void volume_press_and_release_emit_volume_hold_gestures() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture press = tracker.handleButton(new ButtonEvent(ButtonId.VOLUME, true));
        Gesture release = tracker.handleButton(new ButtonEvent(ButtonId.VOLUME, false));

        assertInstanceOf(SetVolumeHeldGesture.class, press);
        assertTrue(((SetVolumeHeldGesture) press).held());
        assertInstanceOf(SetVolumeHeldGesture.class, release);
        assertFalse(((SetVolumeHeldGesture) release).held());
    }

    @Test
    void pan_press_and_release_emit_pan_hold_gestures() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture press = tracker.handleButton(new ButtonEvent(ButtonId.PAN, true));
        Gesture release = tracker.handleButton(new ButtonEvent(ButtonId.PAN, false));

        assertInstanceOf(SetPanHeldGesture.class, press);
        assertTrue(((SetPanHeldGesture) press).held());
        assertInstanceOf(SetPanHeldGesture.class, release);
        assertFalse(((SetPanHeldGesture) release).held());
    }

    @Test
    void send_press_and_release_emit_send_hold_gestures() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture press = tracker.handleButton(new ButtonEvent(ButtonId.SEND, true));
        Gesture release = tracker.handleButton(new ButtonEvent(ButtonId.SEND, false));

        assertInstanceOf(SetSendHeldGesture.class, press);
        assertTrue(((SetSendHeldGesture) press).held());
        assertInstanceOf(SetSendHeldGesture.class, release);
        assertFalse(((SetSendHeldGesture) release).held());
    }

    @Test
    void device_press_and_release_emit_device_hold_gestures() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture press = tracker.handleButton(new ButtonEvent(ButtonId.DEVICE, true));
        Gesture release = tracker.handleButton(new ButtonEvent(ButtonId.DEVICE, false));

        assertInstanceOf(SetDeviceHeldGesture.class, press);
        assertTrue(((SetDeviceHeldGesture) press).held());
        assertInstanceOf(SetDeviceHeldGesture.class, release);
        assertFalse(((SetDeviceHeldGesture) release).held());
    }

    @Test
    void hold_volume_plus_rotate_knob_maps_to_clip_volume_for_track() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.VOLUME, true));

        Gesture g = tracker.handleKnob(new KnobEvent(1, 1));

        assertInstanceOf(PerTrackKnobTurnGesture.class, g);
        PerTrackKnobTurnGesture turn = (PerTrackKnobTurnGesture) g;
        assertEquals(1, turn.track());
        assertEquals(PerTrackParameter.CLIP_VOLUME, turn.parameter());
        assertEquals(1, turn.delta());
    }

    @Test
    void hold_pan_plus_rotate_knob_maps_to_static_pan_for_track() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.PAN, true));

        Gesture g = tracker.handleKnob(new KnobEvent(2, -1));

        assertInstanceOf(PerTrackKnobTurnGesture.class, g);
        PerTrackKnobTurnGesture turn = (PerTrackKnobTurnGesture) g;
        assertEquals(2, turn.track());
        assertEquals(PerTrackParameter.STATIC_PAN, turn.parameter());
        assertEquals(-1, turn.delta());
    }

    @Test
    void shift_plus_pan_plus_rotate_knob_maps_to_velocity_spread_for_track() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));
        tracker.handleButton(new ButtonEvent(ButtonId.PAN, true));

        Gesture g = tracker.handleKnob(new KnobEvent(0, 1));

        assertInstanceOf(PerTrackKnobTurnGesture.class, g);
        PerTrackKnobTurnGesture turn = (PerTrackKnobTurnGesture) g;
        assertEquals(0, turn.track());
        assertEquals(PerTrackParameter.VELOCITY_SPREAD, turn.parameter());
        assertEquals(1, turn.delta());
    }

    @Test
    void hold_device_plus_rotate_knob_maps_to_device_macro_turn() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.DEVICE, true));

        Gesture g = tracker.handleKnob(new KnobEvent(6, 1));

        assertInstanceOf(DeviceMacroTurnGesture.class, g);
        DeviceMacroTurnGesture turn = (DeviceMacroTurnGesture) g;
        assertEquals(6, turn.knob());
        assertEquals(1, turn.delta());
    }

    @Test
    void hold_send_plus_rotate_knob_maps_to_send_level_turn() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SEND, true));

        Gesture g = tracker.handleKnob(new KnobEvent(7, -1));

        assertInstanceOf(SendLevelTurnGesture.class, g);
        SendLevelTurnGesture turn = (SendLevelTurnGesture) g;
        assertEquals(7, turn.knob());
        assertEquals(-1, turn.delta());
    }

    @Test
    void hold_volume_plus_scene_launch_emits_toggle_track_mute_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.VOLUME, true));

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_1, true));

        assertInstanceOf(ToggleTrackMuteGesture.class, g);
        assertEquals(1, ((ToggleTrackMuteGesture) g).track());
    }

    @Test
    void rec_press_toggles_sequence_bank_overlay_on_and_off() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture on = tracker.handleButton(new ButtonEvent(ButtonId.REC, true));
        Gesture off = tracker.handleButton(new ButtonEvent(ButtonId.REC, true));

        assertInstanceOf(SetSequenceBankOverlayGesture.class, on);
        assertTrue(((SetSequenceBankOverlayGesture) on).active());
        assertFalse(((SetSequenceBankOverlayGesture) on).clearMode());

        assertInstanceOf(SetSequenceBankOverlayGesture.class, off);
        assertFalse(((SetSequenceBankOverlayGesture) off).active());
    }

    @Test
    void shift_plus_rec_press_enables_sequence_bank_clear_mode() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.REC, true));

        assertInstanceOf(SetSequenceBankOverlayGesture.class, g);
        SetSequenceBankOverlayGesture overlay = (SetSequenceBankOverlayGesture) g;
        assertTrue(overlay.active());
        assertTrue(overlay.clearMode());
    }

    @Test
    void pad_press_while_sequence_bank_overlay_active_emits_sequence_bank_pad_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.REC, true));

        Gesture g = tracker.handlePad(new PadEvent(4, 7, true));

        assertInstanceOf(SequenceBankPadGesture.class, g);
        SequenceBankPadGesture pad = (SequenceBankPadGesture) g;
        assertEquals(4, pad.track());
        assertEquals(7, pad.slot());
    }

    @Test
    void up_and_down_press_emit_move_all_tracks_sequence_slot_gestures() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture up = tracker.handleButton(new ButtonEvent(ButtonId.UP, true));
        Gesture down = tracker.handleButton(new ButtonEvent(ButtonId.DOWN, true));

        assertInstanceOf(MoveAllTracksSequenceSlotGesture.class, up);
        assertEquals(1, ((MoveAllTracksSequenceSlotGesture) up).delta());
        assertInstanceOf(MoveAllTracksSequenceSlotGesture.class, down);
        assertEquals(-1, ((MoveAllTracksSequenceSlotGesture) down).delta());
    }

    @Test
    void sustain_press_toggles_copy_overlay_on_and_off() {
        InputModifierTracker tracker = new InputModifierTracker();

        Gesture on = tracker.handleButton(new ButtonEvent(ButtonId.SUSTAIN, true));
        Gesture off = tracker.handleButton(new ButtonEvent(ButtonId.SUSTAIN, true));

        assertInstanceOf(SetCopyOverlayGesture.class, on);
        assertTrue(((SetCopyOverlayGesture) on).active());

        assertInstanceOf(SetCopyOverlayGesture.class, off);
        assertFalse(((SetCopyOverlayGesture) off).active());
    }

    @Test
    void pad_press_while_copy_overlay_active_emits_copy_pad_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SUSTAIN, true));

        Gesture g = tracker.handlePad(new PadEvent(3, 6, true));

        assertInstanceOf(CopyPadGesture.class, g);
        CopyPadGesture copy = (CopyPadGesture) g;
        assertEquals(3, copy.track());
        assertEquals(6, copy.step());
    }

    @Test
    void scene_launch_press_while_copy_overlay_active_emits_copy_track_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        tracker.handleButton(new ButtonEvent(ButtonId.SUSTAIN, true));

        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_2, true));

        assertInstanceOf(CopyTrackGesture.class, g);
        assertEquals(2, ((CopyTrackGesture) g).track());
    }
}
