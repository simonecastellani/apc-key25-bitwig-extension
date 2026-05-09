package com.apcsequencer;

public class InputHandler {

    private final TrackState[]              tracks;
    private final InputState                state;
    private final ScaleManager              scaleManager;
    private final Sequencer.NoteInputPort[] noteInputs;
    private final Runnable                  saveCallback;
    private final Runnable                  flushCallback;

    /** True if a hold-gesture (knob/key) occurred while a step was held. */
    private boolean holdGestureHappened = false;

    public InputHandler(TrackState[] tracks, InputState state,
                        ScaleManager scaleManager,
                        Sequencer.NoteInputPort[] noteInputs,
                        Runnable saveCallback, Runnable flushCallback) {
        this.tracks        = tracks;
        this.state         = state;
        this.scaleManager  = scaleManager;
        this.noteInputs    = noteInputs;
        this.saveCallback  = saveCallback;
        this.flushCallback = flushCallback;
    }

    // ── Port 0: keyboard ────────────────────────────────────────────────────

    public void onKeyboardMidi(int status, int data1, int data2) {
        int type = status & 0xF0;
        if (type == 0x90 && data2 > 0) {
            onKeyboardNoteOn(data1, data2);
        } else if (type == 0x80 || (type == 0x90 && data2 == 0)) {
            onKeyboardNoteOff(data1);
        }
    }

    private void onKeyboardNoteOn(int note, int velocity) {
        // Priority 1: Shift + Scene Launch held → set drum base note
        if (state.heldSceneLaunch >= 0) {
            tracks[state.heldSceneLaunch].baseNote = note;
            saveCallback.run();
            return;
        }
        // Priority 2: Shift held alone → set global root note
        if (state.shiftHeld) {
            scaleManager.setRootNote(note);
            saveCallback.run();
            flushCallback.run();
            return;
        }
        // Priority 3: Step pad held → set step note (drum or melodic)
        if (state.heldStepNote >= 0) {
            tracks[state.heldStepTrack].notes[state.heldStepCol] = note;
            holdGestureHappened = true;
            saveCallback.run();
            return;
        }
        // Default: live play → inject to active track NoteInput
        int ch = tracks[state.activeTrack].midiChannel - 1;
        noteInputs[state.activeTrack].sendRawMidiEvent(0x90 | ch, note, velocity);
    }

    private void onKeyboardNoteOff(int note) {
        // Live play note-off (only relevant when no modifiers active)
        if (state.heldStepNote < 0 && !state.shiftHeld && state.heldSceneLaunch < 0) {
            int ch = tracks[state.activeTrack].midiChannel - 1;
            noteInputs[state.activeTrack].sendRawMidiEvent(0x80 | ch, note, 0);
        }
    }

    // ── Port 1: pads / buttons / knobs ──────────────────────────────────────

    public void onPadMidi(int status, int data1, int data2) {
        int type = status & 0xF0;
        if (type == 0x90 && data2 > 0) {
            onPadNoteOn(data1);
        } else if (type == 0x80 || (type == 0x90 && data2 == 0)) {
            onPadNoteOff(data1);
        } else if (type == 0xB0) {
            onKnob(data1, data2);
        }
    }

    private void onPadNoteOn(int note) {
        // Decode pad row/col
        int row = padRow(note);
        int col = padCol(note);

        if (row >= 0) {
            onStepPadPress(row, col, note);
            return;
        }

        // Special buttons
        switch (note) {
            case Config.SHIFT          -> state.shiftHeld = true;
            case Config.STOP_ALL_CLIPS -> { state.stopAllClipsHeld = true; flushCallback.run(); }
            case Config.RECORD         -> {
                tracks[state.activeTrack].melodicMode = !tracks[state.activeTrack].melodicMode;
                saveCallback.run();
                flushCallback.run();
            }
            default -> {
                // Scene Launch buttons
                for (int i = 0; i < Config.NUM_TRACKS; i++) {
                    if (note == Config.SCENE_LAUNCH[i]) {
                        onSceneLaunchPress(i);
                        return;
                    }
                }
            }
        }
    }

    private void onPadNoteOff(int note) {
        int row = padRow(note);
        int col = padCol(note);

        if (row >= 0) {
            onStepPadRelease(row, col, note);
            return;
        }

        switch (note) {
            case Config.SHIFT          -> { state.shiftHeld = false; state.heldSceneLaunch = -1; }
            case Config.STOP_ALL_CLIPS -> { state.stopAllClipsHeld = false; flushCallback.run(); }
            default -> {
                for (int i = 0; i < Config.NUM_TRACKS; i++) {
                    if (note == Config.SCENE_LAUNCH[i]) {
                        onSceneLaunchRelease(i);
                        return;
                    }
                }
            }
        }
    }

    private void onStepPadPress(int row, int col, int note) {
        // Scale view: Stop All Clips held
        if (state.stopAllClipsHeld) {
            if (row == 0 && col < Config.NUM_SCALES) {
                scaleManager.setScaleIndex(col);
                saveCallback.run();
                flushCallback.run();
            }
            return;
        }
        // Shift + pad: set pattern length for that row
        if (state.shiftHeld) {
            tracks[row].patternLength = col + 1;
            saveCallback.run();
            flushCallback.run();
            return;
        }
        // Normal: record step hold
        state.heldStepNote  = note;
        state.heldStepTrack = row;
        state.heldStepCol   = col;
        holdGestureHappened = false;
    }

    private void onStepPadRelease(int row, int col, int note) {
        if (state.heldStepNote == note) {
            if (!holdGestureHappened) {
                // Quick tap: toggle step
                tracks[row].steps[col] = !tracks[row].steps[col];
                saveCallback.run();
            }
            state.heldStepNote  = -1;
            state.heldStepTrack = -1;
            state.heldStepCol   = -1;
            holdGestureHappened = false;
            flushCallback.run();
        }
    }

    private void onSceneLaunchPress(int track) {
        if (state.shiftHeld) {
            if (state.heldSceneLaunch < 0) {
                // Start shift-scene hold
                state.heldSceneLaunch     = track;
                state.sceneLaunchPressTime = System.currentTimeMillis();
            } else if (state.heldSceneLaunch != track) {
                // Copy pattern A → B
                copyPattern(state.heldSceneLaunch, track);
                saveCallback.run();
                flushCallback.run();
            }
        } else {
            // Non-shift tap: mute/unmute
            tracks[track].muted = !tracks[track].muted;
            saveCallback.run();
            flushCallback.run();
        }
    }

    private void onSceneLaunchRelease(int track) {
        if (state.shiftHeld && state.heldSceneLaunch == track) {
            long now     = System.currentTimeMillis();
            long elapsed = now - state.sceneLaunchPressTime;
            if (elapsed < Config.HOLD_THRESH_MS) {
                // Quick tap
                if (now - state.sceneLaunchLastTap < Config.DOUBLE_TAP_MS) {
                    // Double-tap: clear pattern
                    tracks[track].reset();
                    saveCallback.run();
                    flushCallback.run();
                } else {
                    // Single tap: select active track
                    state.activeTrack = track;
                    flushCallback.run();
                }
                state.sceneLaunchLastTap = now;
            }
            state.heldSceneLaunch = -1;
        }
    }

    private void onKnob(int cc, int value) {
        if (state.heldStepNote < 0) return;  // only process when step held

        int knobIdx = cc - Config.KNOB_1;    // 0–7
        if (knobIdx < 0 || knobIdx > 7) return;

        TrackState t = tracks[state.heldStepTrack];
        int step     = state.heldStepCol;
        holdGestureHappened = true;

        switch (knobIdx) {
            case 0 -> {  // Knob 1: pitch (melodic) or velocity (drum)
                if (t.melodicMode) t.notes[step]      = scaleManager.getPitch(value);
                else               t.velocities[step] = value;
            }
            case 1 -> t.velocities[step]     = value;
            case 2 -> t.gateLengths[step]    = value / 127.0;
            case 3 -> t.probabilities[step]  = value / 127.0;
            case 4 -> t.nudges[step]         = (value - 64) / 21;
            case 5 -> t.ratchets[step]       = (value / 32) + 1;
            case 6 -> { if (t.melodicMode) t.chordIntervals[step] = value / 32; }
            case 7 -> t.ccValues[step]       = value;
        }

        saveCallback.run();
    }

    private void copyPattern(int from, int to) {
        TrackState src  = tracks[from];
        TrackState copy = src.deepCopy();
        TrackState dst  = tracks[to];
        // Copy all sequencer data, preserve midiChannel/currentStep
        dst.steps          = copy.steps;
        dst.notes          = copy.notes;
        dst.velocities     = copy.velocities;
        dst.gateLengths    = copy.gateLengths;
        dst.probabilities  = copy.probabilities;
        dst.nudges         = copy.nudges;
        dst.ratchets       = copy.ratchets;
        dst.chordIntervals = copy.chordIntervals;
        dst.ccValues       = copy.ccValues;
        dst.patternLength  = copy.patternLength;
        dst.melodicMode    = copy.melodicMode;
        dst.baseNote       = copy.baseNote;
    }

    // ── Pad decoding ────────────────────────────────────────────────────────

    private int padRow(int note) {
        for (int r = 0; r < Config.NUM_TRACKS; r++)
            for (int c = 0; c < Config.NUM_STEPS; c++)
                if (Config.PADS[r][c] == note) return r;
        return -1;
    }

    private int padCol(int note) {
        for (int r = 0; r < Config.NUM_TRACKS; r++)
            for (int c = 0; c < Config.NUM_STEPS; c++)
                if (Config.PADS[r][c] == note) return c;
        return -1;
    }
}
