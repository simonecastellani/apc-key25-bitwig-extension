package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceManagerTest {

    private TrackState[] freshTracks() {
        TrackState[] t = new TrackState[5];
        for (int i = 0; i < 5; i++) t[i] = new TrackState(i + 1);
        return t;
    }

    @Test
    void serialize_producesNonEmptyJson() {
        String json = PersistenceManager.serialize(freshTracks(), 2, 5, 1);
        assertNotNull(json);
        assertFalse(json.isBlank());
        assertTrue(json.contains("scaleIndex"));
        assertTrue(json.contains("tracks"));
    }

    @Test
    void roundtrip_scaleIndexAndRootNote() {
        TrackState[] src = freshTracks();
        String json = PersistenceManager.serialize(src, 3, 7, 2);

        TrackState[] dst = freshTracks();
        int[] scaleOut = {0}, rootOut = {0}, activeOut = {0};
        PersistenceManager.deserialize(json, dst, scaleOut, rootOut, activeOut);

        assertEquals(3, scaleOut[0]);
        assertEquals(7, rootOut[0]);
        assertEquals(2, activeOut[0]);
    }

    @Test
    void roundtrip_stepData() {
        TrackState[] src = freshTracks();
        src[0].steps[3]         = true;
        src[0].notes[3]         = 64;
        src[0].velocities[3]    = 80;
        src[0].gateLengths[3]   = 0.75;
        src[0].probabilities[3] = 0.5;
        src[0].nudges[3]        = 2;
        src[0].ratchets[3]      = 3;
        src[0].chordIntervals[3]= 1;
        src[0].ccValues[3]      = 100;
        src[0].patternLength    = 6;
        src[0].melodicMode      = true;
        src[0].baseNote         = 48;
        src[0].muted            = true;

        String json = PersistenceManager.serialize(src, 0, 0, 0);

        TrackState[] dst = freshTracks();
        int[] si = {0}, ri = {0}, ai = {0};
        PersistenceManager.deserialize(json, dst, si, ri, ai);

        TrackState t = dst[0];
        assertTrue(t.steps[3]);
        assertEquals(64,   t.notes[3]);
        assertEquals(80,   t.velocities[3]);
        assertEquals(0.75, t.gateLengths[3],   0.001);
        assertEquals(0.5,  t.probabilities[3], 0.001);
        assertEquals(2,    t.nudges[3]);
        assertEquals(3,    t.ratchets[3]);
        assertEquals(1,    t.chordIntervals[3]);
        assertEquals(100,  t.ccValues[3]);
        assertEquals(6,    t.patternLength);
        assertTrue(t.melodicMode);
        assertEquals(48,   t.baseNote);
        assertTrue(t.muted);
    }

    @Test
    void deserialize_emptyString_leavesDefaults() {
        TrackState[] dst = freshTracks();
        int[] si = {0}, ri = {0}, ai = {0};
        PersistenceManager.deserialize("", dst, si, ri, ai);
        // Should leave everything at defaults
        assertFalse(dst[0].steps[0]);
        assertEquals(0, si[0]);
    }

    @Test
    void deserialize_corruptJson_leavesDefaults() {
        TrackState[] dst = freshTracks();
        int[] si = {0}, ri = {0}, ai = {0};
        PersistenceManager.deserialize("{not valid json!!!", dst, si, ri, ai);
        assertFalse(dst[0].steps[0]);
    }
}
