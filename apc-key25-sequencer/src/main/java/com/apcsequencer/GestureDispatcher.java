package com.apcsequencer;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.Transport;

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
public final class GestureDispatcher implements ClipWriter.PlaybackStateListener {

    private static final int SCENE_LAUNCH_NOTE_BASE = 0x52;
    private static final int PLAY_PAUSE_NOTE = 0x5B;

    private final SequencerState     state;
    private final ClipWriter         clipWriter;
    private final Application        application;
    private final Transport          transport;
    private final MidiOut            midiOut;

    /**
     * Playhead positions, one per track (0-based step index; -1 = not playing).
     * Updated by {@link MidiRouter} via {@link #setPlayhead(int, int)}.
     */
    private final int[] playheads = {-1, -1, -1, -1, -1};
    private final boolean[] trackPlaying = new boolean[SequencerState.TRACK_COUNT];
    private final boolean[] trackMuted = new boolean[SequencerState.TRACK_COUNT];
    private boolean transportPlaying;

    public GestureDispatcher(SequencerState state,
                             ClipWriter clipWriter,
                             MidiOut midiOut,
                             ControllerHost host) {
        this.state      = state;
        this.clipWriter = clipWriter;
        this.application = host.createApplication();
        this.transport  = host.createTransport();
        this.midiOut    = midiOut;

        this.clipWriter.setPlaybackStateListener(this);

        transport.isPlaying().markInterested();
        transport.isPlaying().addValueObserver(isPlaying -> {
            transportPlaying = isPlaying;
            flushLeds();
        });
        transportPlaying = transport.isPlaying().get();

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            trackPlaying[t] = clipWriter.isTrackPlaying(t);
            trackMuted[t] = clipWriter.isTrackMuted(t);
        }
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
        } else if (gesture instanceof PitchAssignGesture g) {
            handlePitchAssign(g);
        } else if (gesture instanceof UndoGesture) {
            application.undo();
        } else if (gesture instanceof RedoGesture) {
            application.redo();
        } else if (gesture instanceof LaunchClipGesture g) {
            handleLaunchClip(g);
        } else if (gesture instanceof ToggleTransportGesture) {
            transport.togglePlay();
        } else if (gesture instanceof StopAllGesture) {
            clipWriter.stopAllTrackClips();
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

        for (int track = 0; track < SequencerState.TRACK_COUNT; track++) {
            int note = SCENE_LAUNCH_NOTE_BASE + track;
            int velocity = trackPlaying[track]
                    ? (trackMuted[track] ? LedRenderer.YELLOW : LedRenderer.GREEN)
                    : LedRenderer.OFF;
            midiOut.sendMidi(0x90, note, velocity);
        }

        midiOut.sendMidi(0x90, PLAY_PAUSE_NOTE, transportPlaying ? LedRenderer.GREEN : LedRenderer.OFF);
    }

    @Override
    public void onTrackPlayingChanged(int track, boolean playing) {
        trackPlaying[track] = playing;
        flushLeds();
    }

    @Override
    public void onTrackMutedChanged(int track, boolean muted) {
        trackMuted[track] = muted;
        flushLeds();
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

    private void handlePitchAssign(PitchAssignGesture g) {
        StepState currentStep = state.getStep(g.track(), g.step());
        int oldPitch = currentStep.getPitch();
        int oldVelocity = currentStep.getVelocity();
        boolean wasActive = currentStep.isActive();

        StateDiff pitchDiff = state.setStepPitch(g.track(), g.step(), g.pitch());
        StateDiff velocityDiff = state.setStepVelocity(g.track(), g.step(), g.velocity());
        boolean changed = !pitchDiff.isEmpty() || !velocityDiff.isEmpty();

        if (wasActive && changed) {
            StepState oldPitchSnapshot = currentStep.copy();
            oldPitchSnapshot.setPitch(oldPitch);
            oldPitchSnapshot.setVelocity(oldVelocity);
            clipWriter.writeStep(g.track(), g.step(), false, oldPitchSnapshot);
        }

        if (changed) {
            TrackState track = state.getTrack(g.track());
            StepState step = track.getStep(g.step());
            clipWriter.writeStep(g.track(), g.step(), step.isActive(), step);
        }

        flushLeds();
    }

    private void handleLaunchClip(LaunchClipGesture g) {
        int track = g.track();
        state.setFocusedTrack(track);
        clipWriter.toggleTrackClipPlayback(track, state.getTrack(track).getActiveSlot());
    }
}
