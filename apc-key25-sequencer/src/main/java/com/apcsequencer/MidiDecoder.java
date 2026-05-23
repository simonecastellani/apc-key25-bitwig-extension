package com.apcsequencer;

/**
 * Stateless MIDI decoder for the APC Key 25 MK1.
 *
 * <p>Translates raw MIDI (status, data1, data2) into typed domain events.
 * All methods are static; no Bitwig dependency — fully unit-testable.</p>
 *
 * <h3>MIDI note layout (from hardware MIDI sniff):</h3>
 * <pre>
 *   Pad grid (5 × 8):
 *     Track 0 (top row): notes 0x20–0x27
 *     Track 1          : notes 0x18–0x1f
 *     Track 2          : notes 0x10–0x17
 *     Track 3          : notes 0x08–0x0f
 *     Track 4 (bot row): notes 0x00–0x07
 *   Formula: note = (4 − track) * 8 + step
 *
 *   Buttons: notes 0x40–0x62 (ch 1, status 0x90/0x80)
 *   Knobs:   CC  0x30–0x37  (ch 1, status 0xb0)
 *   Keyboard: ch 2 (status 0x91/0x81)
 *   Sustain:  CC 0x40 ch 2 (status 0xb1)
 * </pre>
 */
public final class MidiDecoder {

    private MidiDecoder() {}

    // -----------------------------------------------------------------------
    // Constants — pad address range
    // -----------------------------------------------------------------------
    static final int PAD_NOTE_MIN = 0x00;
    static final int PAD_NOTE_MAX = 0x27;  // 39

    // -----------------------------------------------------------------------
    // Public decode methods
    // -----------------------------------------------------------------------

    /**
     * Decodes a pad event. Returns {@code null} if the note is outside the pad range.
     * Only handles channel-1 note-on/off (status 0x90 / 0x80).
     */
    public static PadEvent decodePad(int status, int data1, int data2) {
        if (!isNoteOnOff(status)) return null;
        if (data1 < PAD_NOTE_MIN || data1 > PAD_NOTE_MAX) return null;

        int track = 4 - (data1 >> 3);
        int step  = data1 & 0x07;
        boolean pressed = isNoteOn(status, data2);
        return new PadEvent(track, step, pressed);
    }

    /**
     * Decodes a button event. Returns {@code null} if the note doesn't map to a known button.
     * Only handles channel-1 note-on/off (status 0x90 / 0x80).
     */
    public static ButtonEvent decodeButton(int status, int data1, int data2) {
        if (!isNoteOnOff(status)) return null;
        if (data1 >= PAD_NOTE_MIN && data1 <= PAD_NOTE_MAX) return null; // it's a pad

        ButtonId id = NOTE_TO_BUTTON[data1 < NOTE_TO_BUTTON.length ? data1 : 0];
        if (id == null) return null;

        boolean pressed = isNoteOn(status, data2);
        return new ButtonEvent(id, pressed);
    }

    /**
     * Decodes a knob event from CC messages. Returns {@code null} if CC is not a knob.
     * Handles channel-1 CC (status 0xb0); knobs are CC 0x30–0x37 (knobs 1–8).
     */
    public static KnobEvent decodeKnob(int status, int data1, int data2) {
        if ((status & 0xF0) != 0xB0) return null;
        if (data1 < 0x30 || data1 > 0x37) return null;
        return new KnobEvent(data1 - 0x30, decodeRelativeDelta(data2));
    }

    private static int decodeRelativeDelta(int value) {
        // APC Key 25 MK1 knob stream commonly reports binary-offset values:
        // 0x7f for clockwise, 0x00 for counter-clockwise.
        if (value == 0x7f) return 1;
        if (value == 0x00) return -1;
        if (value == 0x40) return 0;

        // Fallback: two's-complement style relative encoder values.
        if (value < 0x40) return value;
        return -(0x80 - value);
    }

    /**
     * Decodes a keyboard note event (channel 2, status 0x91/0x81).
     * Returns {@code null} if not a keyboard event.
     */
    public static KeyboardNoteEvent decodeKeyboard(int status, int data1, int data2) {
        if (status != 0x91 && status != 0x81) return null;
        boolean pressed = (status == 0x91) && data2 > 0;
        return new KeyboardNoteEvent(data1, data2, pressed);
    }

    // -----------------------------------------------------------------------
    // Button note lookup table
    // -----------------------------------------------------------------------

    /** Sparse array: index = MIDI note number → ButtonId (null = not a button). */
    private static final ButtonId[] NOTE_TO_BUTTON = new ButtonId[256];

    static {
        NOTE_TO_BUTTON[0x40] = ButtonId.UP;
        NOTE_TO_BUTTON[0x41] = ButtonId.DOWN;
        NOTE_TO_BUTTON[0x42] = ButtonId.LEFT;
        NOTE_TO_BUTTON[0x43] = ButtonId.RIGHT;
        NOTE_TO_BUTTON[0x44] = ButtonId.VOLUME;
        NOTE_TO_BUTTON[0x45] = ButtonId.PAN;
        NOTE_TO_BUTTON[0x46] = ButtonId.SEND;
        NOTE_TO_BUTTON[0x47] = ButtonId.DEVICE;
        NOTE_TO_BUTTON[0x51] = ButtonId.STOP_ALL_CLIPS;
        NOTE_TO_BUTTON[0x52] = ButtonId.SCENE_LAUNCH_0;
        NOTE_TO_BUTTON[0x53] = ButtonId.SCENE_LAUNCH_1;
        NOTE_TO_BUTTON[0x54] = ButtonId.SCENE_LAUNCH_2;
        NOTE_TO_BUTTON[0x55] = ButtonId.SCENE_LAUNCH_3;
        NOTE_TO_BUTTON[0x56] = ButtonId.SCENE_LAUNCH_4;
        NOTE_TO_BUTTON[0x5b] = ButtonId.PLAY_PAUSE;
        NOTE_TO_BUTTON[0x5d] = ButtonId.REC;
        NOTE_TO_BUTTON[0x62] = ButtonId.SHIFT;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** True for channel-1 note-on (0x90) or note-off (0x80). */
    private static boolean isNoteOnOff(int status) {
        return status == 0x90 || status == 0x80;
    }

    /** True if the message is a note-on (active press). */
    private static boolean isNoteOn(int status, int data2) {
        return status == 0x90 && data2 > 0;
    }
}
