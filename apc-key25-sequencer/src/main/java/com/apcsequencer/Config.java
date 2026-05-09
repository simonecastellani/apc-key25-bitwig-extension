package com.apcsequencer;

public final class Config {
    private Config() {}

    // MIDI port indices
    public static final int PORT_KEYBOARD = 0;  // Port 0: keyboard in
    public static final int PORT_PADS     = 1;  // Port 1: pads/buttons/knobs in
    public static final int PORT_OUT      = 0;  // Port 0: LEDs out

    // Pad note numbers: pads[row][col]
    // Row 0 (top) = Track 0, Row 4 (bottom) = Track 4
    // Col 0 (left) = Step 0, Col 7 (right) = Step 7
    public static final int[][] PADS = {
        {0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27}, // Row 0
        {0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F}, // Row 1
        {0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17}, // Row 2
        {0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F}, // Row 3
        {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}, // Row 4
    };

    // Scene Launch buttons (one per track row)
    public static final int[] SCENE_LAUNCH = {0x52, 0x53, 0x54, 0x55, 0x56};

    // Special buttons
    public static final int STOP_ALL_CLIPS = 0x51;
    public static final int PLAY           = 0x5B;
    public static final int RECORD         = 0x5D;
    public static final int SHIFT          = 0x62;

    // Knob CC numbers (absolute 0–127, channel 0, port 1)
    public static final int KNOB_1 = 0x30;
    public static final int KNOB_2 = 0x31;
    public static final int KNOB_3 = 0x32;
    public static final int KNOB_4 = 0x33;
    public static final int KNOB_5 = 0x34;
    public static final int KNOB_6 = 0x35;
    public static final int KNOB_7 = 0x36;
    public static final int KNOB_8 = 0x37;

    // LED velocities (mk1 three-color system)
    public static final int LED_OFF         = 0;
    public static final int LED_GREEN       = 1;
    public static final int LED_GREEN_BLINK = 2;
    public static final int LED_RED         = 3;
    public static final int LED_RED_BLINK   = 4;
    public static final int LED_ORANGE      = 5;

    // MIDI status byte high nibbles
    public static final int NOTE_ON  = 0x90;
    public static final int NOTE_OFF = 0x80;
    public static final int CC       = 0xB0;

    // CC 123 = All Notes Off
    public static final int CC_ALL_NOTES_OFF = 123;

    // Sequencer dimensions
    public static final int NUM_TRACKS = 5;
    public static final int NUM_STEPS  = 8;
    public static final int NUM_SCALES = 8;

    // Default step parameters
    public static final int    DEFAULT_VELOCITY    = 100;
    public static final double DEFAULT_GATE        = 0.5;
    public static final double DEFAULT_PROBABILITY = 1.0;
    public static final int    DEFAULT_NUDGE       = 0;
    public static final int    DEFAULT_RATCHET     = 1;
    public static final int    DEFAULT_CHORD       = 0;
    public static final int    DEFAULT_CC_VALUE    = 64;
    public static final int    DEFAULT_BASE_NOTE   = 36;

    // notes[i] = -1 means "use baseNote" (drum sentinel)
    public static final int NOTE_SENTINEL = -1;

    // Gesture timing
    public static final long DOUBLE_TAP_MS  = 400;
    public static final long HOLD_THRESH_MS = 200;
}
