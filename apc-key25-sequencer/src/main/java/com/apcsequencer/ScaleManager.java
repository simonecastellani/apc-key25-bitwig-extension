package com.apcsequencer;

public class ScaleManager {
    public static final int BASE_OCTAVE = 3;  // C3 = lowest reachable note
    public static final int NUM_OCTAVES = 3;  // knob spans C3–B5

    public static final String[] SCALE_NAMES = {
        "Cromatica",
        "Maggiore",
        "Minore Naturale",
        "Dorian",
        "Mixolydian",
        "Pentatonica Maggiore",
        "Pentatonica Minore",
        "Blues"
    };

    public static final int[][] SCALES = {
        {0,1,2,3,4,5,6,7,8,9,10,11},  // Cromatica
        {0,2,4,5,7,9,11},              // Maggiore
        {0,2,3,5,7,8,10},             // Minore Naturale
        {0,2,3,5,7,9,10},             // Dorian
        {0,2,4,5,7,9,10},             // Mixolydian
        {0,2,4,7,9},                  // Pentatonica Maggiore
        {0,3,5,7,10},                 // Pentatonica Minore
        {0,3,5,6,7,10},              // Blues
    };

    private int scaleIndex = 0;
    private int rootNote   = 0;  // pitch class 0–11 (C=0)

    public void setScaleIndex(int idx) {
        if (idx >= 0 && idx < Config.NUM_SCALES) scaleIndex = idx;
    }

    public int getScaleIndex() { return scaleIndex; }

    /** Sets root note from a MIDI note number (extracts pitch class). */
    public void setRootNote(int midiNote) {
        rootNote = midiNote % 12;
    }

    public int getRootNote() { return rootNote; }

    public String getScaleName() { return SCALE_NAMES[scaleIndex]; }

    /**
     * Maps an absolute knob value (0–127) to a MIDI note.
     * Covers BASE_OCTAVE to (BASE_OCTAVE + NUM_OCTAVES - 1) inclusive.
     */
    public int getPitch(int knobValue) {
        int[] scale = SCALES[scaleIndex];
        int totalDegrees = scale.length * NUM_OCTAVES;
        int degree = (knobValue * totalDegrees) / 128;
        int oct    = degree / scale.length;
        int semi   = scale[degree % scale.length];
        return (BASE_OCTAVE + oct) * 12 + rootNote + semi;
    }

    /**
     * Returns the MIDI note of a chord tone above basePitch.
     * interval: 0=none (-1), 1=major 3rd (+4), 2=5th (+7), 3=octave (+12)
     */
    public static int applyChordInterval(int basePitch, int interval) {
        return switch (interval) {
            case 1  -> basePitch + 4;
            case 2  -> basePitch + 7;
            case 3  -> basePitch + 12;
            default -> -1;
        };
    }
}
