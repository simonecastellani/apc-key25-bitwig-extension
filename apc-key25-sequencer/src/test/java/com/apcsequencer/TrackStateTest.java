package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackStateTest {

    @Test
    void defaultTrackState_hasCorrectArrayLengths() {
        TrackState t = new TrackState(1);
        assertEquals(8, t.steps.length);
        assertEquals(8, t.notes.length);
        assertEquals(8, t.velocities.length);
        assertEquals(8, t.gateLengths.length);
        assertEquals(8, t.probabilities.length);
        assertEquals(8, t.nudges.length);
        assertEquals(8, t.ratchets.length);
        assertEquals(8, t.chordIntervals.length);
        assertEquals(8, t.ccValues.length);
    }

    @Test
    void defaultTrackState_hasCorrectDefaults() {
        TrackState t = new TrackState(3);
        assertEquals(3, t.midiChannel);
        assertEquals(8, t.patternLength);
        assertFalse(t.muted);
        assertFalse(t.melodicMode);
        assertEquals(36, t.baseNote);
        assertEquals(0, t.currentStep);
        for (int i = 0; i < 8; i++) {
            assertFalse(t.steps[i]);
            assertEquals(-1, t.notes[i]);      // sentinel: use baseNote
            assertEquals(100, t.velocities[i]);
            assertEquals(0.5, t.gateLengths[i], 0.001);
            assertEquals(1.0, t.probabilities[i], 0.001);
            assertEquals(0, t.nudges[i]);
            assertEquals(1, t.ratchets[i]);
            assertEquals(0, t.chordIntervals[i]);
            assertEquals(64, t.ccValues[i]);
        }
    }

    @Test
    void reset_restoresDefaults() {
        TrackState t = new TrackState(1);
        t.steps[0] = true;
        t.velocities[0] = 50;
        t.patternLength = 4;
        t.muted = true;
        t.reset();
        assertFalse(t.steps[0]);
        assertEquals(100, t.velocities[0]);
        assertEquals(8, t.patternLength);
        assertFalse(t.muted);
    }

    @Test
    void deepCopy_isIndependent() {
        TrackState src = new TrackState(1);
        src.steps[0] = true;
        src.patternLength = 4;
        TrackState copy = src.deepCopy();
        // Modify original - copy should not change
        src.steps[0] = false;
        src.patternLength = 8;
        assertTrue(copy.steps[0]);
        assertEquals(4, copy.patternLength);
    }
}
