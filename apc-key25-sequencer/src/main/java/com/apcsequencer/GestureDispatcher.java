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
    private final OverlayController  overlayController = new OverlayController();

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
        if (gesture instanceof ToggleScaleSelectionOverlayGesture) {
            if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
                overlayController.returnToNormal();
            } else {
                overlayController.enterScaleSelection();
            }
            flushLeds();
            return;
        }
        if (gesture instanceof DismissScaleSelectionOverlayGesture) {
            if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
                overlayController.returnToNormal();
                flushLeds();
            }
            return;
        }
        if (gesture instanceof ScaleSelectionPadGesture g) {
            handleScaleSelectionPad(g);
            return;
        }
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
        } else if (gesture instanceof PerStepKnobTurnGesture g) {
            handlePerStepKnobTurn(g);
        } else if (gesture instanceof TrackStepDurationTurnGesture g) {
            handleTrackStepDurationTurn(g);
        } else if (gesture instanceof TrackLoopEndPointGesture g) {
            handleTrackLoopEndPoint(g);
        } else if (gesture instanceof PerTrackKnobTurnGesture g) {
            handlePerTrackKnobTurn(g);
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
     * Updates Global Scale and rewrites all active steps so per-step scale-degree
     * transpositions are re-resolved against the new scale.
     */
    public void updateGlobalScale(GlobalScale scale) {
        if (scale.equals(state.getGlobalScale())) {
            return;
        }
        state.setGlobalScale(scale);
        for (int track = 0; track < SequencerState.TRACK_COUNT; track++) {
            for (int step = 0; step < TrackState.STEP_COUNT; step++) {
                StepState stepState = state.getStep(track, step);
                if (stepState.isActive()) {
                    clipWriter.writeStep(track, step, true, stepState);
                }
            }
        }
        flushLeds();
    }

    /**
     * Forces a full LED refresh.  Useful on init and after slot switches.
     */
    public void flushLeds() {
        int[][] leds = overlayController.getMode() == OverlayMode.SCALE_SELECTION
                ? LedRenderer.renderScaleSelection(state)
                : LedRenderer.render(state, playheads);
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
        midiOut.sendMidi(0x90, 0x44,
                overlayController.getMode() == OverlayMode.SCALE_SELECTION ? LedRenderer.YELLOW : LedRenderer.OFF);
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
        if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
            return;
        }
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
        if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
            return;
        }
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
        if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
            return;
        }
        int track = g.track();
        state.setFocusedTrack(track);
        clipWriter.toggleTrackClipPlayback(track, state.getTrack(track).getActiveSlot());
    }

    private void handlePerStepKnobTurn(PerStepKnobTurnGesture g) {
        if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
            return;
        }
        StepState step = state.getStep(g.track(), g.step());
        StateDiff diff = switch (g.parameter()) {
            case VELOCITY -> {
                int next = clampInt(step.getVelocity() + g.delta(), 0, 127);
                yield state.setStepVelocity(g.track(), g.step(), next);
            }
            case GATE_LENGTH -> {
                double next = clampDouble(step.getGateLength() + (g.delta() * 0.01), 0.01, 1.0);
                yield state.setStepGateLength(g.track(), g.step(), next);
            }
            case PROBABILITY -> {
                double next = clampDouble(step.getProbability() + (g.delta() * 0.01), 0.0, 1.0);
                yield state.setStepProbability(g.track(), g.step(), next);
            }
            case SCALE_DEGREE_OFFSET -> {
                int next = clampInt(step.getScaleDegreeOffset() + g.delta(), -7, 7);
                yield state.setStepScaleDegreeOffset(g.track(), g.step(), next);
            }
            case CHORD_VOICING -> {
                int direction = Integer.compare(g.delta(), 0);
                if (direction == 0) {
                    yield StateDiff.builder().build();
                }
                ChordVoicing[] voicings = ChordVoicing.values();
                int next = Math.floorMod(step.getChordVoicing().ordinal() + direction, voicings.length);
                yield state.setStepChordVoicing(g.track(), g.step(), voicings[next]);
            }
            case RATCHET_COUNT -> {
                int next = clampInt(step.getRatchetCount() + g.delta(), 1, 8);
                yield state.setStepRatchetCount(g.track(), g.step(), next);
            }
            case RATCHET_DECAY -> {
                double next = clampDouble(step.getRatchetDecay() + (g.delta() * 0.05), 0.0, 1.0);
                yield state.setStepRatchetDecay(g.track(), g.step(), next);
            }
            case STEP_CONDITION -> {
                StepCondition[] conditions = StepCondition.values();
                int current = step.getStepCondition().ordinal();
                int direction = Integer.compare(g.delta(), 0);
                int next = Math.floorMod(current + direction, conditions.length);
                yield state.setStepCondition(g.track(), g.step(), conditions[next]);
            }
        };

        if (diff.isEmpty()) {
            return;
        }

        StepState updated = state.getStep(g.track(), g.step());
        clipWriter.writeStep(g.track(), g.step(), updated.isActive(), updated);
        flushLeds();
    }

    private void handleTrackStepDurationTurn(TrackStepDurationTurnGesture g) {
        if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
            return;
        }
        TrackState track = state.getTrack(g.track());
        StepDuration[] values = StepDuration.values();
        int direction = Integer.compare(g.delta(), 0);
        if (direction == 0) {
            return;
        }
        int current = track.getStepDuration().ordinal();
        int next = Math.floorMod(current + direction, values.length);
        StateDiff diff = state.setStepDuration(g.track(), values[next]);
        if (diff.isEmpty()) {
            return;
        }
        clipWriter.applyTrackTiming(g.track());
        flushLeds();
    }

    private void handleTrackLoopEndPoint(TrackLoopEndPointGesture g) {
        if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
            return;
        }
        StateDiff diff = state.setLoopEndPoint(g.track(), g.loopEndPoint());
        if (diff.isEmpty()) {
            return;
        }
        clipWriter.applyTrackTiming(g.track());
        flushLeds();
    }

    private void handlePerTrackKnobTurn(PerTrackKnobTurnGesture g) {
        if (overlayController.getMode() == OverlayMode.SCALE_SELECTION) {
            return;
        }
        TrackState track = state.getTrack(g.track());
        StateDiff diff = switch (g.parameter()) {
            case PATTERN_ROTATION -> state.setPatternRotation(g.track(), track.getPatternRotation() + g.delta());
            case SWING -> state.setSwing(g.track(), track.getSwing() + g.delta());
            case TRANSPOSE -> state.setTranspose(g.track(), track.getTranspose() + g.delta());
            case TRACK_PROBABILITY ->
                    state.setTrackProbability(g.track(), track.getTrackProbability() + (g.delta() * 0.01));
            case LOOP_MULTIPLIER -> {
                LoopMultiplier[] values = LoopMultiplier.values();
                int current = track.getLoopMultiplier().ordinal();
                int direction = Integer.compare(g.delta(), 0);
                if (direction == 0) {
                    yield StateDiff.builder().build();
                }
                int next = Math.floorMod(current + direction, values.length);
                yield state.setLoopMultiplier(g.track(), values[next]);
            }
            case EUCLIDEAN_DISTRIBUTION ->
                    state.setEuclideanDistribution(g.track(), track.getEuclideanDistribution() + g.delta());
            case PHASE_OFFSET -> state.setPhaseOffset(g.track(), track.getPhaseOffset() + (g.delta() * 0.01));
        };
        if (diff.isEmpty()) {
            return;
        }

        if (g.parameter() == PerTrackParameter.EUCLIDEAN_DISTRIBUTION) {
            for (StateDiff.StepChange change : diff.stepChanges()) {
                StepState step = state.getStep(change.trackIndex(), change.stepIndex());
                clipWriter.writeStep(change.trackIndex(), change.stepIndex(), step.isActive(), step);
            }
        } else {
            clipWriter.applyTrackTiming(g.track());
        }
        flushLeds();
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void handleScaleSelectionPad(ScaleSelectionPadGesture g) {
        int root = state.getGlobalScale().root();
        Mode mode = state.getGlobalScale().mode();

        if (g.track() == 0 && g.step() <= 7) {
            root = g.step();
        } else if (g.track() == 1 && g.step() <= 7) {
            mode = switch (g.step()) {
                case 0 -> Mode.MAJOR;
                case 1 -> Mode.MINOR;
                case 2 -> Mode.DORIAN;
                case 3 -> Mode.PHRYGIAN;
                case 4 -> Mode.LYDIAN;
                case 5 -> Mode.MIXOLYDIAN;
                case 6 -> Mode.LOCRIAN;
                case 7 -> Mode.PENTATONIC_MAJOR;
                default -> mode;
            };
        } else if (g.track() == 2 && g.step() <= 1) {
            mode = g.step() == 0 ? Mode.PENTATONIC_MINOR : Mode.CHROMATIC;
        } else {
            return;
        }

        updateGlobalScale(new GlobalScale(root, mode));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}
