package com.apcsequencer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LedManagerTest {

    private LedManager ledManager;
    private List<int[]> sentMidi;
    private LedManager.MidiSender sender;

    @BeforeEach
    void setup() {
        ledManager = new LedManager();
        sentMidi   = new ArrayList<>();
        sender     = (status, d1, d2) -> sentMidi.add(new int[]{status, d1, d2});
    }

    private TrackState[] freshTracks() {
        TrackState[] t = new TrackState[5];
        for (int i = 0; i < 5; i++) t[i] = new TrackState(i + 1);
        return t;
    }

    @Test
    void updateSequencerView_activeStep_showsGreen() {
        TrackState[] tracks = freshTracks();
        tracks[0].steps[2] = true;  // step active, not playhead
        tracks[0].currentStep = 5;  // playhead elsewhere

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[0][2] && m[2] == Config.LED_GREEN));
    }

    @Test
    void updateSequencerView_playheadOnEmpty_showsRed() {
        TrackState[] tracks = freshTracks();
        tracks[1].currentStep = 3;
        tracks[1].steps[3] = false;

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[1][3] && m[2] == Config.LED_RED));
    }

    @Test
    void updateSequencerView_playheadOnActive_showsOrange() {
        TrackState[] tracks = freshTracks();
        tracks[2].currentStep = 0;
        tracks[2].steps[0] = true;

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[2][0] && m[2] == Config.LED_ORANGE));
    }

    @Test
    void updateSequencerView_mutedRow_showsGreenBlink() {
        TrackState[] tracks = freshTracks();
        tracks[3].muted = true;
        tracks[3].steps[4] = true;  // active step but muted overrides

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        // All pads in row 3 should be GREEN_BLINK
        for (int col = 0; col < Config.NUM_STEPS; col++) {
            final int c = col;
            assertTrue(sentMidi.stream()
                .anyMatch(m -> m[1] == Config.PADS[3][c] && m[2] == Config.LED_GREEN_BLINK),
                "Col " + col + " should be GREEN_BLINK");
        }
    }

    @Test
    void flush_dirtyAfterUpdate_notDirtyAfterFlush() {
        TrackState[] tracks = freshTracks();
        ledManager.updateSequencerView(tracks);
        assertTrue(ledManager.isDirty());
        ledManager.flush(sender);
        assertFalse(ledManager.isDirty());
    }

    @Test
    void flush_doesNotResendUnchangedLeds() {
        TrackState[] tracks = freshTracks();
        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);
        int firstCount = sentMidi.size();

        ledManager.flush(sender);  // nothing changed
        assertEquals(firstCount, sentMidi.size());
    }

    @Test
    void updateScaleView_activeScale_showsGreenBlink() {
        ledManager.updateScaleView(4, 0);
        ledManager.flush(sender);

        // Pad at row 0, col 4 = active scale = GREEN_BLINK
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[0][4] && m[2] == Config.LED_GREEN_BLINK));
        // Other scale pads should be GREEN
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[0][0] && m[2] == Config.LED_GREEN));
    }

    @Test
    void updateScaleView_nonRow0Pads_areOff() {
        ledManager.updateScaleView(0, 0);
        ledManager.flush(sender);

        // All row 1–4 pads should be off
        for (int row = 1; row < Config.NUM_TRACKS; row++) {
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                final int r = row, c = col;
                assertTrue(sentMidi.stream()
                    .anyMatch(m -> m[1] == Config.PADS[r][c] && m[2] == Config.LED_OFF),
                    "Row " + row + " col " + col + " should be OFF");
            }
        }
    }

    @Test
    void updateRecordLed_melodic_sendsRed() {
        ledManager.updateRecordLed(true);
        ledManager.flush(sender);
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.RECORD && m[2] == Config.LED_RED));
    }

    @Test
    void updateRecordLed_drum_sendsOff() {
        // Set RED first, then switch to drum (OFF)
        ledManager.updateRecordLed(true);
        ledManager.flush(sender);
        sentMidi.clear();

        ledManager.updateRecordLed(false);
        ledManager.flush(sender);
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.RECORD && m[2] == Config.LED_OFF));
    }
}
