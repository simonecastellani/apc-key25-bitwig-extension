package com.apcsequencer;

public class InputState {
    public boolean shiftHeld          = false;
    public boolean stopAllClipsHeld   = false;
    public int     heldStepNote       = -1;   // note# of held pad, -1 = none
    public int     heldStepTrack      = -1;   // track index of held pad
    public int     heldStepCol        = -1;   // step column of held pad
    public int     heldSceneLaunch    = -1;   // track index of Shift+Scene held, -1 = none
    public long    sceneLaunchPressTime = 0L;
    public long    sceneLaunchLastTap   = 0L;
    public int     activeTrack        = 0;    // 0–4
}
