package com.apcsequencer;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiOut;

/**
 * Dispatches high-level {@link Gesture} objects to the appropriate subsystems:
 * <ol>
 *   <li>Mutates {@link SequencerState} for step-level changes.</li>
 *   <li>Drives {@link ClipWriter} to reflect the new state in Bitwig's clip.</li>
 *   <li>Flushes a fresh LED render via the {@link MidiOut} port.</li>
 *   <li>Forwards Undo/Redo to Bitwig's {@link Application} API.</li>
 * </ol>
 *
 * <p>All gestures are processed synchronously on Bitwig's controller thread.</p>
 */
public final class GestureDispatcher {

    private final SequencerState     state;
    private final ClipWriter         clipWriter;
    private final Application        application;
    private final MidiOut            midiOut;

    /**
     * Playhead positions, one per track (0-based step index; -1 = not playing).
     * Updated by {@link MidiRouter} via {@link #setPlayhead(int, int)}.
     */
    private final int[] playheads = {-1, -1, -1, -1, -1};

    public GestureDispatcher(SequencerState state,
                             ClipWriter clipWriter,
                             MidiOut midiOut,
                             ControllerHost host) {
        this.state      = state;
        this.clipWriter = clipWriter;
        this.application = host.createApplication();
        this.midiOut    = midiOut;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Dispatch a gesture.  No-op if {@code gesture} is {@code null}.
     */
    public void dispatch(Gesture gesture) {
        if (gesture == null) return;
        if (gesture instanceof StepToggleGesture g) {
            handleStepToggle(g);
        } else if (gesture instanceof UndoGesture) {
            application.undo();
        } else if (gesture instanceof RedoGesture) {
            application.redo();
        }
    }

    /**
     * Called by {@link MidiRouter} when Bitwig reports a new playhead position
     * for a track's clip.
     *
     * @param track    0-based track index
     * @param stepIndex 0-based step index, or −1 if the clip is not playing
     */
    public void setPlayhead(int track, int stepIndex) {
        playheads[track] = stepIndex;
        flushLeds();
    }

    /**
     * Forces a full LED refresh.  Useful on init and after slot switches.
     */
    public void flushLeds() {
        int[][] leds = LedRenderer.render(state, playheads);
        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            for (int s = 0; s < TrackState.STEP_COUNT; s++) {
                int padNote  = (4 - t) * 8 + s;   // pad LED MIDI note
                int velocity = leds[t][s];          // LED colour code = velocity
                midiOut.sendMidi(0x90, padNote, velocity);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private handlers
    // -----------------------------------------------------------------------

    private void handleStepToggle(StepToggleGesture g) {
        StateDiff diff = state.toggleStep(g.track(), g.step());

        // Reflect every changed step to Bitwig
        for (StateDiff.StepChange change : diff.stepChanges()) {
            TrackState  track     = state.getTrack(change.trackIndex());
            StepState   step      = track.getStep(change.stepIndex());
            boolean     active    = step.isActive();
            clipWriter.writeStep(change.trackIndex(), change.stepIndex(), active, step);
        }

        flushLeds();
    }
}
