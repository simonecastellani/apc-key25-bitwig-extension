package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MidiDecoderTest {

    // -------------------------------------------------------------------------
    // Slice A — tracer bullet: pad note → PadEvent
    // Pad notes: 0x00–0x27 on status 0x90 (press) / 0x80 (release)
    // note = (4 - track) * 8 + step   →   track 0 step 0 = 0x20 (32)
    // -------------------------------------------------------------------------

    @Test
    void pad_press_at_0x20_decodes_to_track0_step0_pressed() {
        PadEvent evt = MidiDecoder.decodePad(0x90, 0x20, 0x7f);
        assertNotNull(evt, "should decode to a PadEvent");
        assertEquals(0, evt.track());
        assertEquals(0, evt.step());
        assertTrue(evt.pressed());
    }

    @Test
    void pad_release_at_0x20_decodes_to_track0_step0_released() {
        PadEvent evt = MidiDecoder.decodePad(0x80, 0x20, 0x00);
        assertNotNull(evt);
        assertFalse(evt.pressed());
    }

    @Test
    void pad_at_bottom_row_decodes_to_track4() {
        // Track 4 (bottom row): notes 0x00–0x07
        PadEvent evt = MidiDecoder.decodePad(0x90, 0x00, 0x7f);
        assertNotNull(evt);
        assertEquals(4, evt.track(), "note 0x00 → track 4");
        assertEquals(0, evt.step(),  "note 0x00 → step 0");
    }

    @Test
    void pad_at_top_row_last_step_decodes_to_track0_step7() {
        PadEvent evt = MidiDecoder.decodePad(0x90, 0x27, 0x7f);
        assertNotNull(evt);
        assertEquals(0, evt.track(), "note 0x27 → track 0");
        assertEquals(7, evt.step(),  "note 0x27 → step 7");
    }

    @Test
    void non_pad_note_returns_null_from_decodePad() {
        // Note 0x42 (Left button) is not a pad
        PadEvent evt = MidiDecoder.decodePad(0x90, 0x42, 0x7f);
        assertNull(evt, "non-pad note should return null");
    }

    // -------------------------------------------------------------------------
    // Slice B — button note → ButtonEvent
    // LEFT=0x42, RIGHT=0x43, UP=0x40, DOWN=0x41
    // Scene Launch 0=0x52, 1=0x53, 2=0x54, 3=0x55, 4=0x56
    // Shift=0x62, Stop All=0x51, Play/Pause=0x5b, Rec=0x5d
    // -------------------------------------------------------------------------

    @Test
    void left_button_press_decodes_to_ButtonEvent_LEFT() {
        ButtonEvent evt = MidiDecoder.decodeButton(0x90, 0x42, 0x7f);
        assertNotNull(evt, "should decode 0x42 as LEFT");
        assertEquals(ButtonId.LEFT, evt.id());
        assertTrue(evt.pressed());
    }

    @Test
    void right_button_press_decodes_to_ButtonEvent_RIGHT() {
        ButtonEvent evt = MidiDecoder.decodeButton(0x90, 0x43, 0x7f);
        assertNotNull(evt);
        assertEquals(ButtonId.RIGHT, evt.id());
    }

    @Test
    void scene_launch_0_press_decodes_correctly() {
        ButtonEvent evt = MidiDecoder.decodeButton(0x90, 0x52, 0x7f);
        assertNotNull(evt);
        assertEquals(ButtonId.SCENE_LAUNCH_0, evt.id());
        assertTrue(evt.pressed());
    }

    @Test
    void scene_launch_4_press_decodes_correctly() {
        ButtonEvent evt = MidiDecoder.decodeButton(0x90, 0x56, 0x7f);
        assertNotNull(evt);
        assertEquals(ButtonId.SCENE_LAUNCH_4, evt.id());
    }

    @Test
    void shift_button_release_decodes_correctly() {
        ButtonEvent evt = MidiDecoder.decodeButton(0x80, 0x62, 0x7f);
        assertNotNull(evt);
        assertEquals(ButtonId.SHIFT, evt.id());
        assertFalse(evt.pressed());
    }

    @Test
    void pad_note_returns_null_from_decodeButton() {
        ButtonEvent evt = MidiDecoder.decodeButton(0x90, 0x20, 0x7f);
        assertNull(evt, "pad note should return null from decodeButton");
    }

    // -------------------------------------------------------------------------
    // Knob event
    // -------------------------------------------------------------------------

    @Test
    void knob1_turn_decodes_to_KnobEvent_index0() {
        KnobEvent evt = MidiDecoder.decodeKnob(0xb0, 0x30, 0x40);
        assertNotNull(evt);
        assertEquals(0, evt.knob(), "CC 0x30 → knob index 0");
        assertEquals(0x40, evt.value());
    }

    @Test
    void knob8_turn_decodes_to_KnobEvent_index7() {
        KnobEvent evt = MidiDecoder.decodeKnob(0xb0, 0x37, 0x7f);
        assertNotNull(evt);
        assertEquals(7, evt.knob(), "CC 0x37 → knob index 7");
    }

    @Test
    void non_knob_cc_returns_null() {
        KnobEvent evt = MidiDecoder.decodeKnob(0xb0, 0x10, 0x40);
        assertNull(evt, "CC 0x10 is not a knob");
    }
}
