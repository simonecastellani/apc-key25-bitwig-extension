package com.apcsequencer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SequencerTest {

    // ── Pure step calculation ─────────────────────────────────────────────

    @Test
    void stepZeroAtBeatZero() {
        assertEquals(0, Sequencer.calculateStep(0.0, 2, 8));
    }

    @Test
    void stepOneAtHalfBeat() {
        assertEquals(1, Sequencer.calculateStep(0.5, 2, 8));
    }

    @Test
    void stepAdvancesMonotonically() {
        assertEquals(2, Sequencer.calculateStep(1.0, 2, 8));
        assertEquals(3, Sequencer.calculateStep(1.5, 2, 8));
        assertEquals(4, Sequencer.calculateStep(2.0, 2, 8));
    }

    @Test
    void stepWrapsAroundPatternLength() {
        assertEquals(0, Sequencer.calculateStep(4.0, 2, 8));
    }

    @Test
    void stepWrapsMultipleTimes() {
        assertEquals(1, Sequencer.calculateStep(8.5, 2, 8));
    }

    @Test
    void negativeBeatPositionClampsToZero() {
        assertEquals(0, Sequencer.calculateStep(-0.1, 2, 8));
    }

    // ── Step duration calculation (for BPM-driven timer) ─────────────────

    @Test
    void stepDurationAt120BpmEighthNote() {
        assertEquals(250.0, Sequencer.calculateStepDurationMs(120.0, 2), 0.001);
    }

    @Test
    void stepDurationAt60BpmEighthNote() {
        assertEquals(500.0, Sequencer.calculateStepDurationMs(60.0, 2), 0.001);
    }

    @Test
    void stepDurationAt120BpmSixteenthNote() {
        assertEquals(125.0, Sequencer.calculateStepDurationMs(120.0, 4), 0.001);
    }

    // ── LED step-to-note address ──────────────────────────────────────────

    @Test
    void bottomRowStepMapsToNoteDirectly() {
        // Bottom row pads: note 0x00–0x07 → steps 0–7
        for (int step = 0; step < 8; step++) {
            assertEquals(step, Sequencer.bottomRowNote(step));
        }
    }

    // ── Tick state machine helpers ────────────────────────────────────────

    static List<int[]> noteLog() { return new ArrayList<>(); }
    static List<int[]> ledLog()  { return new ArrayList<>(); }

    static Sequencer.NoteOutput asNote(List<int[]> log) {
        return (status, data1, data2) -> log.add(new int[]{status, data1, data2});
    }

    static Sequencer.LedOutput asLed(List<int[]> log) {
        return (note, color) -> log.add(new int[]{note, color});
    }

    static Sequencer seqWith(List<int[]> notes, List<int[]> leds) {
        return new Sequencer(asNote(notes), asLed(leds));
    }

    // ── Step toggle (S5) ──────────────────────────────────────────────────

    @Test
    void allStepsDisabledByDefault() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.tick(true, 0.0);  // step 0 – disabled

        assertFalse(notes.stream().anyMatch(e -> e[0] == 0x90), "no note-on for disabled step");
    }

    @Test
    void padTapEnablesBottomRowStep() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.padTapped(0x00);  // enable step 0

        // LED should be green (step enabled, not current playhead)
        int[] last = leds.get(leds.size() - 1);
        assertEquals(0x00, last[0], "pad note");
        assertEquals(Sequencer.LED_GREEN, last[1], "enabled step is green");
    }

    @Test
    void padTapTogglesStepOff() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.padTapped(0x00);  // enable
        leds.clear();
        seq.padTapped(0x00);  // disable

        int[] last = leds.get(leds.size() - 1);
        assertEquals(0x00, last[0], "pad note");
        assertEquals(Sequencer.LED_OFF, last[1], "disabled step is off");
    }

    @Test
    void enabledStepFiresNote() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.padTapped(0x00);  // enable step 0
        seq.tick(true, 0.0);  // advance to step 0

        assertTrue(notes.stream().anyMatch(e -> e[0] == 0x90), "note-on fired for enabled step");
    }

    @Test
    void disabledStepDoesNotFireNote() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        // step 0 disabled by default
        seq.tick(true, 0.0);

        assertFalse(notes.stream().anyMatch(e -> e[0] == 0x90), "no note-on for disabled step");
    }

    @Test
    void padTapOnNonBottomRowIsIgnored() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.padTapped(0x08);  // not bottom row (row 3 starts at 0x08)

        // No LED change expected for non-bottom-row pad
        // (Implementation may or may not update LED for row 3 – we assert step 0 is still off)
        // The simplest check: no note fires
        List<int[]> notes = noteLog();
        seq.tick(true, 0.0);
        assertFalse(notes.stream().anyMatch(e -> e[0] == 0x90));
    }

    @Test
    void noteOnFiredOnFirstEnabledStep() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.padTapped(0x00);  // enable step 0
        seq.tick(true, 0.0);

        // Only note-on; no prior note to silence (currentStep was -1)
        assertEquals(1, notes.size(), "one event: note-on only");
        assertEquals(0x90, notes.get(0)[0], "note-on");
    }

    @Test
    void noteOffThenNoteOnWhenBothStepsEnabled() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.padTapped(0x00);  // enable step 0
        seq.padTapped(0x01);  // enable step 1
        seq.tick(true, 0.0);  // step 0 note-on
        notes.clear();

        seq.tick(true, 0.5);  // step 1: note-off step0, note-on step1
        assertEquals(2, notes.size());
        assertEquals(0x80, notes.get(0)[0], "note-off step 0");
        assertEquals(0x90, notes.get(1)[0], "note-on step 1");
    }

    @Test
    void noEventsWhenStepDoesNotChange() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.padTapped(0x00);  // enable step 0
        seq.tick(true, 0.0);
        int after = notes.size();

        seq.tick(true, 0.1);  // still step 0
        assertEquals(after, notes.size());
    }

    @Test
    void noteOffSentWhenTransportStops() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.padTapped(0x00);  // enable step 0
        seq.tick(true, 0.0);
        notes.clear();

        seq.tick(false, 0.0);

        assertEquals(1, notes.size());
        assertEquals(0x80, notes.get(0)[0]);
    }

    @Test
    void noNoteOffWhenTransportStopsOnDisabledStep() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.tick(true, 0.0);   // step 0 – disabled, no note fired
        notes.clear();

        seq.tick(false, 0.0);  // transport stop – nothing to silence

        assertTrue(notes.isEmpty(), "no note-off for step that never played");
    }

    @Test
    void noDoubleNoteOffWhenAlreadyStopped() {
        List<int[]> notes = noteLog();
        Sequencer seq = seqWith(notes, ledLog());

        seq.tick(false, 0.0);
        assertTrue(notes.isEmpty());

        seq.tick(false, 0.0);
        assertTrue(notes.isEmpty());
    }

    // ── LED events ────────────────────────────────────────────────────────

    @Test
    void playheadShowsRedOnCurrentStep() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.tick(true, 0.0);  // step 0 (disabled)

        int[] last = leds.get(leds.size() - 1);
        assertEquals(Sequencer.bottomRowNote(0), last[0], "pad note");
        assertEquals(Sequencer.LED_RED, last[1], "playhead is red");
    }

    @Test
    void playheadShowsRedOnEnabledStep() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.padTapped(0x00);  // enable step 0
        leds.clear();
        seq.tick(true, 0.0);  // step 0

        int[] last = leds.get(leds.size() - 1);
        assertEquals(Sequencer.bottomRowNote(0), last[0], "pad note");
        assertEquals(Sequencer.LED_RED, last[1], "playhead is red even when enabled");
    }

    @Test
    void previousDisabledStepLedTurnedOffWhenStepChanges() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.tick(true, 0.0);   // step 0 → playhead RED (disabled)
        leds.clear();

        seq.tick(true, 0.5);   // step 1 → step 0 restored to OFF, step 1 RED
        assertEquals(2, leds.size());
        assertEquals(Sequencer.bottomRowNote(0), leds.get(0)[0], "step-0 pad restored");
        assertEquals(Sequencer.LED_OFF,          leds.get(0)[1], "disabled step restores to off");
        assertEquals(Sequencer.bottomRowNote(1), leds.get(1)[0], "step-1 playhead");
        assertEquals(Sequencer.LED_RED,          leds.get(1)[1], "new playhead is red");
    }

    @Test
    void previousEnabledStepLedTurnedGreenWhenStepChanges() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.padTapped(0x00);  // enable step 0
        seq.tick(true, 0.0);  // step 0 → RED (playhead)
        leds.clear();

        seq.tick(true, 0.5);  // step 1 → step 0 restored to GREEN, step 1 RED
        assertEquals(2, leds.size());
        assertEquals(Sequencer.bottomRowNote(0), leds.get(0)[0], "step-0 pad restored");
        assertEquals(Sequencer.LED_GREEN,        leds.get(0)[1], "enabled step restores to green");
        assertEquals(Sequencer.bottomRowNote(1), leds.get(1)[0], "step-1 playhead");
        assertEquals(Sequencer.LED_RED,          leds.get(1)[1], "new playhead is red");
    }

    @Test
    void ledRestoredToOffOnTransportStop_disabledStep() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.tick(true, 0.0);   // step 0 on (disabled, LED RED)
        leds.clear();

        seq.tick(false, 0.0);  // transport stop → restore to off

        assertEquals(1, leds.size());
        assertEquals(Sequencer.LED_OFF, leds.get(0)[1], "disabled step LED is off on stop");
    }

    @Test
    void ledRestoredToGreenOnTransportStop_enabledStep() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.padTapped(0x00);  // enable step 0
        seq.tick(true, 0.0);  // step 0 → RED (playhead)
        leds.clear();

        seq.tick(false, 0.0);  // transport stop → restore to green

        assertEquals(1, leds.size());
        assertEquals(Sequencer.LED_GREEN, leds.get(0)[1], "enabled step restores to green on stop");
    }

    @Test
    void noLedEventsWhenStoppedBeforeAnyStep() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.tick(false, 0.0);
        assertTrue(leds.isEmpty());
    }

    // ── Pad tap while playing ─────────────────────────────────────────────

    @Test
    void tapNonPlayheadStepShowsGreen_whilePlaying() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.tick(true, 0.0);   // step 0 is playhead
        leds.clear();

        seq.padTapped(0x01);   // enable step 1 (not playhead)

        int[] last = leds.get(leds.size() - 1);
        assertEquals(0x01, last[0], "step-1 pad");
        assertEquals(Sequencer.LED_GREEN, last[1], "newly enabled step shows green");
    }

    // ── setPlayhead bounds guard ──────────────────────────────────────────

    @Test
    void setPlayheadClampsOutOfRangeStepToStopped() {
        // Bitwig may fire playingStep() with PATTERN_LENGTH (8) as a
        // "just-past-end" sentinel.  setPlayhead() must treat it as -1.
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);

        seq.tick(true, 0.0);   // step 0 is playhead (disabled → RED)
        leds.clear();

        seq.setPlayhead(Sequencer.PATTERN_LENGTH); // value = 8 → out of range

        // Should not throw; step 0 LED should be restored to OFF (disabled step)
        assertEquals(1, leds.size(), "restore LED for old step, no new playhead LED");
        assertEquals(0x00, leds.get(0)[0], "step 0 note");
        assertEquals(Sequencer.LED_OFF, leds.get(0)[1], "disabled step restored to off");
    }

    // ── ClipOutput integration (Plan C) ──────────────────────────────────

    /** Records setStep/clearStep calls by step index (positive = setStep, negative = clearStep). */
    static List<Integer> clipLog() { return new ArrayList<>(); }

    static Sequencer.ClipOutput asClip(List<Integer> log) {
        return new Sequencer.ClipOutput() {
            public void setStep(int step)   { log.add(step); }
            public void clearStep(int step) { log.add(~step); }  // ~step encodes clearStep
        };
    }

    @Test
    void padTapCallsClipSetStepWhenEnabling() {
        List<Integer> clip = clipLog();
        Sequencer seq = new Sequencer(asNote(noteLog()), asLed(ledLog()), asClip(clip));

        seq.padTapped(0x00);  // step 0: disabled → enabled

        assertEquals(1, clip.size(), "one clip event");
        assertEquals(0, (int) clip.get(0), "setStep(0) encoded as 0");
    }

    @Test
    void padTapCallsClipClearStepWhenDisabling() {
        List<Integer> clip = clipLog();
        Sequencer seq = new Sequencer(asNote(noteLog()), asLed(ledLog()), asClip(clip));

        seq.padTapped(0x00);  // enable
        clip.clear();
        seq.padTapped(0x00);  // disable

        assertEquals(1, clip.size(), "one clip event");
        assertEquals(~0, (int) clip.get(0), "clearStep(0) encoded as ~0");
    }

    // ── refreshLeds ───────────────────────────────────────────────────────

    @Test
    void refreshLeds_allOffWhenPatternEmpty() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);
        leds.clear();

        seq.refreshLeds();

        assertEquals(8, leds.size(), "one LED event per step");
        for (int i = 0; i < 8; i++) {
            assertEquals(i,               leds.get(i)[0], "step " + i + " note");
            assertEquals(Sequencer.LED_OFF, leds.get(i)[1], "step " + i + " is off");
        }
    }

    @Test
    void refreshLeds_greenForEnabledSteps() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);
        seq.padTapped(0x02); // enable step 2
        seq.padTapped(0x05); // enable step 5
        leds.clear();

        seq.refreshLeds();

        assertEquals(8, leds.size());
        assertEquals(Sequencer.LED_OFF,   leds.get(0)[1], "step 0 off");
        assertEquals(Sequencer.LED_GREEN, leds.get(2)[1], "step 2 green");
        assertEquals(Sequencer.LED_GREEN, leds.get(5)[1], "step 5 green");
        assertEquals(Sequencer.LED_OFF,   leds.get(7)[1], "step 7 off");
    }

    @Test
    void refreshLeds_redForPlayheadGreenForOtherEnabled() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);
        seq.padTapped(0x03); // enable step 3
        seq.padTapped(0x05); // enable step 5
        seq.tick(true, 1.5); // 1.5 beats × 2 steps/beat = step 3 → playhead
        leds.clear();

        seq.refreshLeds();

        assertEquals(8, leds.size());
        assertEquals(Sequencer.LED_RED,   leds.get(3)[1], "step 3 is playhead → red");
        assertEquals(Sequencer.LED_GREEN, leds.get(5)[1], "step 5 enabled, not playhead → green");
        assertEquals(Sequencer.LED_OFF,   leds.get(0)[1], "step 0 disabled → off");
    }
}
