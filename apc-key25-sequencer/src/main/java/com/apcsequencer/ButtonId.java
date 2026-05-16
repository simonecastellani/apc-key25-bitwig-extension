package com.apcsequencer;

/**
 * Canonical identifier for every non-pad button on the APC Key 25 MK1.
 */
public enum ButtonId {
    // Navigation
    UP, DOWN, LEFT, RIGHT,

    // Mode buttons
    VOLUME, PAN, SEND, DEVICE,

    // Transport
    PLAY_PAUSE, REC, STOP_ALL_CLIPS,

    // Modifiers
    SHIFT,

    // Scene Launch — one per track (top-down, track 0 first)
    SCENE_LAUNCH_0, SCENE_LAUNCH_1, SCENE_LAUNCH_2, SCENE_LAUNCH_3, SCENE_LAUNCH_4,

    // Sustain pedal
    SUSTAIN,
}
