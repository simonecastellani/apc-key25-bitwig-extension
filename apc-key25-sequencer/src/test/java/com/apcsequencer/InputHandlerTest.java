package com.apcsequencer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InputHandlerTest {

    private TrackState[]              tracks;
    private InputState                state;
    private ScaleManager              scaleManager;
    private List<int[]>               injected;   // {trackIdx, status, d1, d2}
    private Sequencer.NoteInputPort[] ports;
    private boolean                   saved;
    private boolean                   flushed;
    private InputHandler              handler;

    @BeforeEach
    void setup() {
        tracks      = new TrackState[5];
        for (int i = 0; i < 5; i++) tracks[i] = new TrackState(i + 1);
        state        = new InputState();
        scaleManager = new ScaleManager();
        injected     = new ArrayList<>();
        saved        = false;
        flushed      = false;

        ports = new Sequencer.NoteInputPort[5];
        for (int i = 0; i < 5; i++) {
            final int ti = i;
            ports[i] = (s, d1, d2) -> injected.add(new int[]{ti, s, d1, d2});
        }

        handler = new InputHandler(
            tracks, state, scaleManager, ports,
            () -> saved  = true,
            () -> flushed = true
        );
    }

    // ── Pad gestures ────────────────────────────────────────────────────────

    @Test
    void padQuickTap_togglesStepOn() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertTrue(tracks[0].steps[0]);
        assertTrue(saved);
        assertTrue(flushed);
    }

    @Test
    void padQuickTap_togglesStepOff() {
        tracks[0].steps[1] = true;
        handler.onPadMidi(0x90, Config.PADS[0][1], 100);
        handler.onPadMidi(0x80, Config.PADS[0][1], 0);
        assertFalse(tracks[0].steps[1]);
    }

    @Test
    void padHoldWithKnob_setsVelocityAndDoesNotToggle() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100); // press step
        handler.onPadMidi(0xB0, Config.KNOB_2, 80);      // velocity knob
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);   // release step
        assertFalse(tracks[0].steps[0]);                  // NOT toggled
        assertEquals(80, tracks[0].velocities[0]);
    }

    @Test
    void padHoldWithKnob3_setsGateLength() {
        handler.onPadMidi(0x90, Config.PADS[1][3], 100);
        handler.onPadMidi(0xB0, Config.KNOB_3, 64);      // gate = 64/127 ≈ 0.504
        handler.onPadMidi(0x80, Config.PADS[1][3], 0);
        assertEquals(64.0 / 127.0, tracks[1].gateLengths[3], 0.001);
    }

    @Test
    void padHoldWithKnob4_setsProbability() {
        handler.onPadMidi(0x90, Config.PADS[2][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_4, 63);      // prob = 63/127 ≈ 0.496
        handler.onPadMidi(0x80, Config.PADS[2][0], 0);
        assertEquals(63.0 / 127.0, tracks[2].probabilities[0], 0.001);
    }

    @Test
    void padHoldWithKnob5_setsNudge() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_5, 85);  // (85-64)/21 = 1
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(1, tracks[0].nudges[0]);
    }

    @Test
    void padHoldWithKnob6_setsRatchet() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_6, 96);  // (96/32)+1 = 4
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(4, tracks[0].ratchets[0]);
    }

    @Test
    void padHoldWithKnob7_melodicMode_setsChord() {
        tracks[0].melodicMode = true;
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_7, 64);  // 64/32 = 2
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(2, tracks[0].chordIntervals[0]);
    }

    @Test
    void padHoldWithKnob7_drumMode_doesNothing() {
        tracks[0].melodicMode = false;
        int originalChord = tracks[0].chordIntervals[0];
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_7, 64);
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(originalChord, tracks[0].chordIntervals[0]); // unchanged
    }

    @Test
    void padHoldWithKnob1_melodic_setsPitch() {
        tracks[0].melodicMode = true;
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_1, 64);
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(scaleManager.getPitch(64), tracks[0].notes[0]);
    }

    @Test
    void padHoldWithKnob1_drum_setsVelocity() {
        tracks[0].melodicMode = false;
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_1, 90);  // direct velocity
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(90, tracks[0].velocities[0]);
    }

    // ── Pattern length ───────────────────────────────────────────────────────

    @Test
    void shiftPad_setsPatternLength() {
        state.shiftHeld = true;
        handler.onPadMidi(0x90, Config.PADS[2][4], 100);  // row 2, col 4 → length 5
        assertEquals(5, tracks[2].patternLength);
        assertTrue(saved);
    }

    // ── Scale selection ──────────────────────────────────────────────────────

    @Test
    void stopAllClipsHeld_row0Pad_selectsScale() {
        state.stopAllClipsHeld = true;
        handler.onPadMidi(0x90, Config.PADS[0][3], 100);
        assertEquals(3, scaleManager.getScaleIndex());
        assertTrue(saved);
    }

    @Test
    void stopAllClipsHeld_nonRow0Pad_ignored() {
        state.stopAllClipsHeld = true;
        handler.onPadMidi(0x90, Config.PADS[1][3], 100);
        assertEquals(0, scaleManager.getScaleIndex()); // unchanged
    }

    // ── Mode toggle ──────────────────────────────────────────────────────────

    @Test
    void recordButton_togglesMelodicOnActiveTrack() {
        state.activeTrack = 2;
        assertFalse(tracks[2].melodicMode);
        handler.onPadMidi(0x90, Config.RECORD, 100);
        assertTrue(tracks[2].melodicMode);
        handler.onPadMidi(0x90, Config.RECORD, 100);
        assertFalse(tracks[2].melodicMode);
    }

    // ── Mute / track selection ───────────────────────────────────────────────

    @Test
    void sceneLaunchTap_mutesAndUnmutes() {
        handler.onPadMidi(0x90, Config.SCENE_LAUNCH[1], 100);
        handler.onPadMidi(0x80, Config.SCENE_LAUNCH[1], 0);
        assertTrue(tracks[1].muted);
        handler.onPadMidi(0x90, Config.SCENE_LAUNCH[1], 100);
        handler.onPadMidi(0x80, Config.SCENE_LAUNCH[1], 0);
        assertFalse(tracks[1].muted);
    }

    // ── Keyboard gestures ────────────────────────────────────────────────────

    @Test
    void shiftKeyboard_setsRootNote() {
        state.shiftHeld = true;
        handler.onKeyboardMidi(0x90, 65, 100); // F (65 % 12 = 5)
        assertEquals(5, scaleManager.getRootNote());
        assertTrue(saved);
    }

    @Test
    void keyboard_livePlay_injectsToActiveTrack() {
        state.activeTrack = 3;
        handler.onKeyboardMidi(0x90, 60, 100);
        assertEquals(1, injected.size());
        int[] ev = injected.get(0);
        assertEquals(3, ev[0]);                    // track 3
        assertEquals(0x90 | 3, ev[1]);             // NoteOn ch 4 (0-indexed ch 3)
        assertEquals(60, ev[2]);
        assertEquals(100, ev[3]);
    }

    @Test
    void keyboard_noteOff_injectsNoteOff() {
        state.activeTrack = 0;
        handler.onKeyboardMidi(0x80, 60, 0);
        assertEquals(1, injected.size());
        assertEquals(0x80 | 0, injected.get(0)[1]); // NoteOff ch 1
    }

    @Test
    void padHoldKeyboard_setsStepNote_noToggle() {
        handler.onPadMidi(0x90, Config.PADS[1][2], 100); // hold step
        handler.onKeyboardMidi(0x90, 64, 100);            // press E
        handler.onPadMidi(0x80, Config.PADS[1][2], 0);   // release step
        assertFalse(tracks[1].steps[2]);                  // not toggled
        assertEquals(64, tracks[1].notes[2]);
    }

    @Test
    void shiftSceneLaunchPlusKeyboard_setsBaseNote() {
        state.shiftHeld = true;
        handler.onPadMidi(0x90, Config.SCENE_LAUNCH[2], 100); // hold scene 2
        handler.onKeyboardMidi(0x90, 48, 100);                 // press C3
        assertEquals(48, tracks[2].baseNote);
    }
}
