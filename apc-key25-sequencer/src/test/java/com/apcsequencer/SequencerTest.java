package com.apcsequencer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SequencerTest {

    private TrackState[] tracks;
    private List<int[]>  firedNotes;
    private List<Long>   scheduledDelays;
    private List<Runnable> pendingTasks;
    private Sequencer    sequencer;

    @BeforeEach
    void setup() {
        tracks = new TrackState[5];
        for (int i = 0; i < 5; i++) tracks[i] = new TrackState(i + 1);

        firedNotes      = new ArrayList<>();
        scheduledDelays = new ArrayList<>();
        pendingTasks    = new ArrayList<>();

        Sequencer.NoteInputPort[] ports = new Sequencer.NoteInputPort[5];
        for (int i = 0; i < 5; i++) {
            final int ti = i;
            ports[i] = (s, d1, d2) -> firedNotes.add(new int[]{ti, s, d1, d2});
        }

        // Test scheduler: records task + delay, does NOT run immediately
        Sequencer.TaskScheduler scheduler = (cb, delay) -> {
            scheduledDelays.add(delay);
            pendingTasks.add(cb);
        };

        ScaleManager sm = new ScaleManager();
        sequencer = new Sequencer(tracks, ports, sm, scheduler);
        sequencer.setBpm(120.0); // stepMs = (60000/120)/4 = 125 ms
    }

    /** Fires one tick: runs the oldest scheduled task. */
    private void tick() {
        assertFalse(pendingTasks.isEmpty(), "No pending task to tick");
        pendingTasks.remove(0).run();
    }

    @Test
    void start_schedulesFirstTick() {
        sequencer.start();
        assertEquals(1, pendingTasks.size());
        assertEquals(125L, scheduledDelays.get(0)); // 120 BPM → 125 ms
    }

    @Test
    void tick_advancesCurrentStep() {
        tracks[0].patternLength = 4;
        sequencer.start();
        tick(); // step 0 → 1
        assertEquals(1, tracks[0].currentStep);
    }

    @Test
    void tick_firesActiveStep() {
        tracks[0].steps[0] = true;
        tracks[0].velocities[0] = 80;
        tracks[0].notes[0] = Config.NOTE_SENTINEL; // use baseNote (36)
        tracks[0].probabilities[0] = 1.0;

        sequencer.start();
        tick(); // processes step 0

        // Should fire NoteOn on track 0 (channel 1 = status 0x90)
        assertTrue(firedNotes.stream()
            .anyMatch(n -> n[0] == 0 && (n[1] & 0xF0) == 0x90 && n[2] == 36));
    }

    @Test
    void tick_mutedTrack_doesNotFire() {
        tracks[1].steps[0] = true;
        tracks[1].muted = true;

        sequencer.start();
        tick();

        assertTrue(firedNotes.stream().noneMatch(n -> n[0] == 1));
    }

    @Test
    void tick_polyrhythm_independentLengths() {
        tracks[0].patternLength = 3;
        tracks[1].patternLength = 5;
        sequencer.start();

        // After 3 ticks: track 0 steps: 0→1→2→0 (wraps), track 1: 0→1→2→3
        tick(); tick(); tick();

        assertEquals(0, tracks[0].currentStep); // 3 % 3 = 0 (wrapped)
        assertEquals(3, tracks[1].currentStep); // 3 % 5 = 3 (no wrap)
    }

    @Test
    void probability_zero_neverFires() {
        tracks[0].steps[0] = true;
        tracks[0].probabilities[0] = 0.0;

        sequencer.start();
        tick();

        assertTrue(firedNotes.stream()
            .noneMatch(n -> n[0] == 0 && (n[1] & 0xF0) == 0x90));
    }

    @Test
    void stop_sendsAllNotesOff() {
        sequencer.start();
        sequencer.stop();

        // 5 tracks × CC 123 on channels 0–4
        long ccCount = firedNotes.stream()
            .filter(n -> (n[1] & 0xF0) == 0xB0 && n[2] == Config.CC_ALL_NOTES_OFF)
            .count();
        assertEquals(5, ccCount);
    }

    @Test
    void setBpm_changesScheduledDelay() {
        sequencer.setBpm(60.0); // stepMs = (60000/60)/4 = 250 ms
        sequencer.start();
        assertEquals(250L, scheduledDelays.get(0));
    }

    @Test
    void resetAllStepCounters_setsAllToZero() {
        tracks[0].currentStep = 5;
        tracks[2].currentStep = 3;
        sequencer.resetAllStepCounters();
        for (TrackState t : tracks) assertEquals(0, t.currentStep);
    }
}
