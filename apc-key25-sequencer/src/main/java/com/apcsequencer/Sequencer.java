package com.apcsequencer;

import java.util.Random;

public class Sequencer {

    /** Abstracts Bitwig NoteInput for testability. */
    public interface NoteInputPort {
        void sendRawMidiEvent(int status, int data1, int data2);
    }

    /** Abstracts host.scheduleTask for testability. */
    public interface TaskScheduler {
        void schedule(Runnable callback, long delayMs);
    }

    private final TrackState[]    tracks;
    private final NoteInputPort[] noteInputs;
    private final ScaleManager    scaleManager;
    private final TaskScheduler   scheduler;
    private final Random          random = new Random();

    private volatile boolean running  = false;
    private          double  stepMs   = 125.0; // 120 BPM default
    private          int     knob8Cc  = 74;

    public Sequencer(TrackState[] tracks, NoteInputPort[] noteInputs,
                     ScaleManager scaleManager, TaskScheduler scheduler) {
        this.tracks       = tracks;
        this.noteInputs   = noteInputs;
        this.scaleManager = scaleManager;
        this.scheduler    = scheduler;
    }

    public void setBpm(double bpm) {
        stepMs = (60000.0 / bpm) / 4.0;
    }

    public void setKnob8Cc(int cc) {
        this.knob8Cc = cc;
    }

    public boolean isRunning() { return running; }

    public void start() {
        resetAllStepCounters();
        running = true;
        scheduleTick();
    }

    public void stop() {
        running = false;
        sendAllNotesOff();
    }

    public void resetAllStepCounters() {
        for (TrackState t : tracks) t.currentStep = 0;
    }

    // Package-private for testing
    void tick() {
        if (!running) return;
        for (int i = 0; i < Config.NUM_TRACKS; i++) {
            TrackState t = tracks[i];
            int step = t.currentStep;
            if (!t.muted && t.steps[step]) {
                fireStep(i, step);
            }
            t.currentStep = (t.currentStep + 1) % t.patternLength;
        }
        scheduleTick();
    }

    private void scheduleTick() {
        scheduler.schedule(this::tick, Math.round(stepMs));
    }

    private void fireStep(int trackIdx, int step) {
        TrackState t = tracks[trackIdx];
        if (random.nextDouble() > t.probabilities[step]) return;

        int note     = resolveNote(trackIdx, step);
        int velocity = t.velocities[step];
        int ratchet  = t.ratchets[step];
        long nudgeMs  = (long)(t.nudges[step] * stepMs / 6.0);
        long gateMs   = (long)(t.gateLengths[step] * stepMs);

        if (ratchet <= 1) {
            scheduleNote(trackIdx, note, velocity, nudgeMs, gateMs);
        } else {
            long rStep = Math.round(stepMs / ratchet);
            for (int r = 0; r < ratchet; r++) {
                long delay = nudgeMs + r * rStep;
                long gate  = Math.max(1, Math.min(gateMs, rStep - 5));
                scheduleNote(trackIdx, note, velocity, delay, gate);
            }
        }

        // Chord interval (melodic mode only)
        if (t.melodicMode && t.chordIntervals[step] > 0) {
            int chord = ScaleManager.applyChordInterval(note, t.chordIntervals[step]);
            if (chord >= 0 && chord <= 127) {
                scheduleNote(trackIdx, chord, velocity, nudgeMs, gateMs);
            }
        }

        // MIDI CC (knob 8 per step)
        int ccStatus = Config.CC | (t.midiChannel - 1);
        noteInputs[trackIdx].sendRawMidiEvent(ccStatus, knob8Cc, t.ccValues[step]);
    }

    private int resolveNote(int trackIdx, int step) {
        TrackState t = tracks[trackIdx];
        int n = t.notes[step];
        if (n == Config.NOTE_SENTINEL) return t.baseNote;
        return n;
    }

    private void scheduleNote(int trackIdx, int note, int velocity,
                              long delayMs, long gateMs) {
        int onStatus  = Config.NOTE_ON  | (tracks[trackIdx].midiChannel - 1);
        int offStatus = Config.NOTE_OFF | (tracks[trackIdx].midiChannel - 1);
        Runnable noteOn = () -> {
            noteInputs[trackIdx].sendRawMidiEvent(onStatus, note, velocity);
            scheduler.schedule(
                () -> noteInputs[trackIdx].sendRawMidiEvent(offStatus, note, 0),
                Math.max(1, gateMs)
            );
        };
        if (delayMs <= 0) {
            noteOn.run(); // fire immediately when no nudge delay
        } else {
            scheduler.schedule(noteOn, delayMs);
        }
    }

    void sendAllNotesOff() {
        for (int i = 0; i < Config.NUM_TRACKS; i++) {
            int status = Config.CC | (tracks[i].midiChannel - 1);
            noteInputs[i].sendRawMidiEvent(status, Config.CC_ALL_NOTES_OFF, 0);
        }
    }
}
