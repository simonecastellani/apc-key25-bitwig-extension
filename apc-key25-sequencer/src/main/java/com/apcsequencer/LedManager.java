package com.apcsequencer;

import java.util.Arrays;

public class LedManager {

    /** Abstracts MidiOut.sendMidi for testability. */
    public interface MidiSender {
        void sendMidi(int status, int data1, int data2);
    }

    // Shadow state: [row * NUM_STEPS + col]
    private final int[] padLeds     = new int[Config.NUM_TRACKS * Config.NUM_STEPS];
    private final int[] prevPadLeds = new int[Config.NUM_TRACKS * Config.NUM_STEPS];

    // Button LED indices
    private static final int BTN_COUNT = 7;
    private final int[] btnLeds     = new int[BTN_COUNT];
    private final int[] prevBtnLeds = new int[BTN_COUNT];

    // btnLeds indices
    private static final int BTN_SCENE_0 = 0;
    private static final int BTN_SCENE_1 = 1;
    private static final int BTN_SCENE_2 = 2;
    private static final int BTN_SCENE_3 = 3;
    private static final int BTN_SCENE_4 = 4;
    private static final int BTN_RECORD  = 5;
    // index 6 reserved

    private boolean dirty = false;

    public LedManager() {
        // Initialize prev arrays to -1 so first flush sends everything
        Arrays.fill(prevPadLeds, -1);
        Arrays.fill(prevBtnLeds, -1);
    }

    public void updateSequencerView(TrackState[] tracks) {
        for (int row = 0; row < Config.NUM_TRACKS; row++) {
            TrackState t = tracks[row];
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                int led;
                if (t.muted) {
                    led = Config.LED_GREEN_BLINK;
                } else if (col == t.currentStep) {
                    led = t.steps[col] ? Config.LED_ORANGE : Config.LED_RED;
                } else {
                    led = t.steps[col] ? Config.LED_GREEN : Config.LED_OFF;
                }
                setPadLed(row, col, led);
            }
            // Scene Launch LED: green=active, off=muted
            setBtnLed(row, t.muted ? Config.LED_OFF : Config.LED_GREEN);
        }
    }

    public void updateScaleView(int scaleIndex, int activeTrack) {
        // Row 0: scale pads lit; rows 1–4: off
        for (int col = 0; col < Config.NUM_STEPS; col++) {
            int led = (col == scaleIndex) ? Config.LED_GREEN_BLINK : Config.LED_GREEN;
            setPadLed(0, col, led);
        }
        for (int row = 1; row < Config.NUM_TRACKS; row++) {
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                setPadLed(row, col, Config.LED_OFF);
            }
        }
    }

    public void updateRecordLed(boolean melodicMode) {
        int led = melodicMode ? Config.LED_RED : Config.LED_OFF;
        if (btnLeds[BTN_RECORD] != led) {
            btnLeds[BTN_RECORD] = led;
            dirty = true;
        }
    }

    public boolean isDirty() { return dirty; }

    public void flush(MidiSender sender) {
        // Flush pad LEDs
        for (int row = 0; row < Config.NUM_TRACKS; row++) {
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                int idx = row * Config.NUM_STEPS + col;
                if (padLeds[idx] != prevPadLeds[idx]) {
                    sender.sendMidi(Config.NOTE_ON, Config.PADS[row][col], padLeds[idx]);
                    prevPadLeds[idx] = padLeds[idx];
                }
            }
        }
        // Flush Scene Launch button LEDs (indices 0–4)
        for (int i = 0; i < 5; i++) {
            if (btnLeds[i] != prevBtnLeds[i]) {
                sender.sendMidi(Config.NOTE_ON, Config.SCENE_LAUNCH[i], btnLeds[i]);
                prevBtnLeds[i] = btnLeds[i];
            }
        }
        // Flush Record LED
        if (btnLeds[BTN_RECORD] != prevBtnLeds[BTN_RECORD]) {
            sender.sendMidi(Config.NOTE_ON, Config.RECORD, btnLeds[BTN_RECORD]);
            prevBtnLeds[BTN_RECORD] = btnLeds[BTN_RECORD];
        }
        dirty = false;
    }

    private void setPadLed(int row, int col, int color) {
        int idx = row * Config.NUM_STEPS + col;
        if (padLeds[idx] != color) {
            padLeds[idx] = color;
            dirty = true;
        }
    }

    private void setBtnLed(int btnIdx, int color) {
        if (btnLeds[btnIdx] != color) {
            btnLeds[btnIdx] = color;
            dirty = true;
        }
    }
}
