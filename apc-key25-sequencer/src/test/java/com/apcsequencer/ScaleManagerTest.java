package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScaleManagerTest {

    @Test
    void getPitch_chromatic_C3_atKnob0() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(0); // Chromatic (12 notes)
        sm.setRootNote(0);   // C root
        // knob=0: degree=0, oct=0, semitone=0 → C3 = 3*12+0 = 36
        assertEquals(36, sm.getPitch(0));
    }

    @Test
    void getPitch_chromatic_spans3Octaves() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(0); // Chromatic: 12 notes/octave
        sm.setRootNote(0);
        // knob=127: totalDegrees = 12*3=36, degree = (127*36)/128 = 35
        // oct=35/12=2, semitone=35%12=11 → B5 = (3+2)*12 + 0 + 11 = 71
        int pitch = sm.getPitch(127);
        assertTrue(pitch >= 36 && pitch <= 84, "Pitch " + pitch + " not in C3–B5 range");
    }

    @Test
    void getPitch_major_rootC() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(1); // Major: {0,2,4,5,7,9,11} = 7 notes
        sm.setRootNote(0);   // C
        // knob=0: totalDegrees=7*3=21, degree=(0*21)/128=0, oct=0, semi=0 → C3=36
        assertEquals(36, sm.getPitch(0));
    }

    @Test
    void getPitch_major_rootA() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(1); // Major: {0,2,4,5,7,9,11}
        sm.setRootNote(69);  // A (69%12=9)
        // knob=0: degree=0, oct=0, semi=0 → (3+0)*12 + 9 + 0 = 45 = A3
        assertEquals(45, sm.getPitch(0));
    }

    @Test
    void setRootNote_extractsPitchClass() {
        ScaleManager sm = new ScaleManager();
        sm.setRootNote(69); // A4 = MIDI 69
        assertEquals(9, sm.getRootNote()); // 69 % 12 = 9 = A
    }

    @Test
    void applyChordInterval_none_returnsNegative() {
        assertEquals(-1, ScaleManager.applyChordInterval(60, 0));
    }

    @Test
    void applyChordInterval_third() {
        assertEquals(64, ScaleManager.applyChordInterval(60, 1)); // C + 4 semitones = E
    }

    @Test
    void applyChordInterval_fifth() {
        assertEquals(67, ScaleManager.applyChordInterval(60, 2)); // C + 7 = G
    }

    @Test
    void applyChordInterval_octave() {
        assertEquals(72, ScaleManager.applyChordInterval(60, 3)); // C + 12 = C+1
    }

    @Test
    void getScaleName_returnsCorrectNames() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(0);
        assertEquals("Cromatica", sm.getScaleName());
        sm.setScaleIndex(5);
        assertEquals("Pentatonica Maggiore", sm.getScaleName());
    }
}
