package com.apcsequencer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackRouterTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    static List<int[]>   ledLog()       { return new ArrayList<>(); }
    static List<Integer> clipLog()      { return new ArrayList<>(); }
    static List<int[]>   sceneLedLog()  { return new ArrayList<>(); }

    static Sequencer.LedOutput asLed(List<int[]> log) {
        return (note, color) -> log.add(new int[]{note, color});
    }

    static Sequencer.ClipOutput asClip(List<Integer> log) {
        return new Sequencer.ClipOutput() {
            public void setStep(int step)   { log.add(step);  }
            public void clearStep(int step) { log.add(~step); }
        };
    }

    static TrackRouter.SceneLedOutput asScene(List<int[]> log) {
        return (row, color) -> log.add(new int[]{row, color});
    }

    /**
     * Test fixture: 5 sequencers each with isolated clip/LED spies + a shared scene spy.
     * Sequencers use the package-private 3-arg test constructor (NoteOutput, LedOutput, ClipOutput).
     */
    static class RouterFixture {
        final List<List<Integer>> clips = new ArrayList<>();
        final List<List<int[]>>   leds  = new ArrayList<>();
        final List<int[]>         scene = sceneLedLog();
        final Sequencer[]         seqs  = new Sequencer[5];
        final TrackRouter         router;

        RouterFixture() {
            for (int i = 0; i < 5; i++) {
                List<Integer> c = clipLog();
                List<int[]>   l = ledLog();
                clips.add(c);
                leds.add(l);
                seqs[i] = new Sequencer((s, d1, d2) -> {}, asLed(l), asClip(c));
            }
            router = new TrackRouter(seqs, asScene(scene));
        }
    }

    // ── Default state ─────────────────────────────────────────────────────

    @Test
    void defaultSelectedTrackIsZero() {
        RouterFixture f = new RouterFixture();
        assertEquals(0, f.router.selectedTrack());
    }

    // ── Pad routing ───────────────────────────────────────────────────────

    @Test
    void padTappedRoutesToTrack0ByDefault() {
        RouterFixture f = new RouterFixture();

        f.router.padTapped(0x00); // enable step 0 on track 0

        assertEquals(1, f.clips.get(0).size(), "track 0 received clip event");
        assertTrue(f.clips.get(1).isEmpty(),   "track 1 received no clip event");
        assertTrue(f.clips.get(2).isEmpty(),   "track 2 received no clip event");
    }

    @Test
    void sceneLaunchSwitchesRouting() {
        RouterFixture f = new RouterFixture();

        f.router.sceneLaunchPressed(2);
        f.router.padTapped(0x00); // should go to track 2 now

        assertTrue(f.clips.get(0).isEmpty(),   "track 0 received no clip event");
        assertEquals(1, f.clips.get(2).size(), "track 2 received clip event");
    }

    @Test
    void subsequentPadTapsGoToNewTrackAfterSwitch() {
        RouterFixture f = new RouterFixture();

        f.router.padTapped(0x00);        // → track 0
        f.router.sceneLaunchPressed(4);
        f.router.padTapped(0x01);        // → track 4

        assertEquals(1, f.clips.get(0).size(), "track 0 has 1 event");
        assertEquals(1, f.clips.get(4).size(), "track 4 has 1 event");
    }

    // ── Scene Launch LEDs ─────────────────────────────────────────────────

    @Test
    void initLeds_track0GreenRestOff() {
        RouterFixture f = new RouterFixture();

        f.router.initLeds();

        assertEquals(5, f.scene.size(), "5 scene LED events");
        assertEquals(0,                  f.scene.get(0)[0], "row 0");
        assertEquals(Sequencer.LED_GREEN, f.scene.get(0)[1], "row 0 = green");
        for (int i = 1; i < 5; i++) {
            assertEquals(i,               f.scene.get(i)[0], "row " + i);
            assertEquals(Sequencer.LED_OFF, f.scene.get(i)[1], "row " + i + " = off");
        }
    }

    @Test
    void sceneLaunchPressed_selectedRowGreenRestOff() {
        RouterFixture f = new RouterFixture();

        f.router.sceneLaunchPressed(3);

        assertEquals(5, f.scene.size(), "5 scene LED events");
        for (int i = 0; i < 5; i++) {
            int expected = (i == 3) ? Sequencer.LED_GREEN : Sequencer.LED_OFF;
            assertEquals(expected, f.scene.get(i)[1], "row " + i + " color");
        }
    }

    @Test
    void sceneLaunchPressed_callsRefreshLedsOnNewTrack() {
        RouterFixture f = new RouterFixture();
        f.seqs[2].padTapped(0x01); // enable step 1 on track 2
        f.leds.get(2).clear();

        f.router.sceneLaunchPressed(2);

        // refreshLeds emits 8 LED events for all 8 steps
        assertEquals(8, f.leds.get(2).size(), "track 2 refreshLeds emitted 8 events");
        assertEquals(Sequencer.LED_GREEN, f.leds.get(2).get(1)[1], "step 1 is green");
        assertEquals(Sequencer.LED_OFF,   f.leds.get(2).get(0)[1], "step 0 is off");
    }

    @Test
    void sceneLaunchPressed_idempotentOnCurrentTrack() {
        RouterFixture f = new RouterFixture();

        f.router.sceneLaunchPressed(0); // already 0
        f.router.sceneLaunchPressed(0); // again — must not throw

        // last 5 scene events: row 0 = green, rest = off
        int from = f.scene.size() - 5;
        assertEquals(Sequencer.LED_GREEN, f.scene.get(from)[1],     "row 0 green");
        for (int i = 1; i < 5; i++) {
            assertEquals(Sequencer.LED_OFF, f.scene.get(from + i)[1], "row " + i + " off");
        }
    }

    @Test
    void sceneLaunchPressed_outOfRangeIsIgnored() {
        RouterFixture f = new RouterFixture();

        assertDoesNotThrow(() -> f.router.sceneLaunchPressed(-1));
        assertDoesNotThrow(() -> f.router.sceneLaunchPressed(5));
        assertEquals(0, f.router.selectedTrack(), "track unchanged after out-of-range");
    }
}
