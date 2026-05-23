package com.apcsequencer;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GestureDispatcherTest {

    private static final class ClipWrite {
        final int track;
        final int step;
        final boolean active;

        ClipWrite(int track, int step, boolean active) {
            this.track = track;
            this.step = step;
            this.active = active;
        }
    }

    private static final class CapturingClipWriter implements ClipWriter {
        private final java.util.List<ClipWrite> writes = new java.util.ArrayList<>();

        @Override
        public void writeStep(int track, int step, boolean active, StepState stepState) {
            writes.add(new ClipWrite(track, step, active));
        }

        @Override
        public void writeStepParameters(int track,
                                        int step,
                                        int pitch,
                                        double velocity,
                                        double duration,
                                        double chance,
                                        int repeatCount,
                                        double repeatVelocityEnd,
                                        com.bitwig.extension.controller.api.NoteOccurrence occurrence,
                                        int recurrenceLength,
                                        int recurrenceMask,
                                        int transposeSemitones) {
        }

        @Override
        public void applyTrackTiming(int track) {
        }

        @Override
        public void adjustTrackClipVolume(int track, int delta) {
        }

        @Override
        public void toggleTrackMute(int track) {
        }

        @Override
        public void applyTrackStaticPan(int track) {
        }

        @Override
        public void applyTrackVelocitySpread(int track) {
        }

        @Override
        public void adjustFocusedTrackDeviceMacro(int track, int macro, int delta) {
        }

        @Override
        public void adjustFocusedTrackSendLevel(int track, int send, int delta) {
        }

        @Override
        public void toggleTrackClipPlayback(int track, int slot) {
        }

        @Override
        public void copySlotIfEmpty(int track, int sourceSlot, int destinationSlot) {
        }

        @Override
        public void launchSlot(int track, int slot) {
        }

        @Override
        public void clearSlot(int track, int slot) {
        }

        @Override
        public boolean isSlotPopulated(int track, int slot) {
            return false;
        }

        @Override
        public void stopAllTrackClips() {
        }

        @Override
        public boolean isTrackPlaying(int track) {
            return false;
        }

        @Override
        public boolean isTrackMuted(int track) {
            return false;
        }

        @Override
        public void setPlaybackStateListener(PlaybackStateListener listener) {
        }

        java.util.List<ClipWrite> writes() {
            return writes;
        }
    }

    private static final class HostContext {
        final ControllerHost host;
        final Application application;
        final Transport transport;

        HostContext(ControllerHost host, Application application, Transport transport) {
            this.host = host;
            this.application = application;
            this.transport = transport;
        }
    }

    private HostContext hostContext;

    @BeforeEach
    void setUp() {
        hostContext = hostContext();
    }

    private HostContext hostContext() {
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        Transport transport = mock(Transport.class);
        SettableBooleanValue isPlaying = mock(SettableBooleanValue.class);
        when(host.createApplication()).thenReturn(application);
        when(host.createTransport()).thenReturn(transport);
        when(transport.isPlaying()).thenReturn(isPlaying);
        when(isPlaying.get()).thenReturn(false);
        return new HostContext(host, application, transport);
    }

    @Test
    void undo_gesture_calls_bitwig_application_undo() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new UndoGesture());

        verify(hostContext.application).undo();
    }

    @Test
    void redo_gesture_calls_bitwig_application_redo() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new RedoGesture());

        verify(hostContext.application).redo();
    }

    @Test
    void playhead_wrapped_to_loop_end_uses_solid_red_not_blink() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.setPlayhead(0, 47);

        // Track 0 / step 7 pad note = (4 - 0) * 8 + 7 = 39
        verify(midiOut).sendMidi(0x90, 39, LedRenderer.RED);
        verify(midiOut, never()).sendMidi(0x90, 39, LedRenderer.RED_BLINK);
    }

    @Test
    void pitch_assign_updates_step_pitch_and_rewrites_active_step() {
        SequencerState state = new SequencerState();
        state.toggleStep(1, 3);

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PitchAssignGesture(1, 3, 64, 87));

        ArgumentCaptor<StepState> stepCaptor = ArgumentCaptor.forClass(StepState.class);

        verify(clipWriter, times(2)).writeStep(eq(1), eq(3), anyBoolean(), stepCaptor.capture());

        StepState firstWrite = stepCaptor.getAllValues().get(0);
        StepState secondWrite = stepCaptor.getAllValues().get(1);

        assertEquals(60, firstWrite.getPitch(), "first write clears old pitch C3");
        assertEquals(100, firstWrite.getVelocity(), "first write clears old default velocity");
        assertEquals(64, secondWrite.getPitch(), "second write writes newly assigned pitch");
        assertEquals(87, secondWrite.getVelocity(), "second write uses keyboard velocity");
    }

    @Test
    void pitch_assign_on_inactive_step_emits_only_single_rewrite() {
        SequencerState state = new SequencerState();

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PitchAssignGesture(1, 3, 64, 70));

        verify(clipWriter, times(1)).writeStep(eq(1), eq(3), eq(false), any(StepState.class));
    }

    @Test
    void pitch_assign_to_same_pitch_is_idempotent() {
        SequencerState state = new SequencerState();

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PitchAssignGesture(0, 0, 60, 100));

        verify(clipWriter, never()).writeStep(anyInt(), anyInt(), anyBoolean(), any(StepState.class));
    }

    @Test
    void pitch_assign_same_pitch_with_new_velocity_updates_active_note_velocity() {
        SequencerState state = new SequencerState();
        state.toggleStep(2, 4);

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PitchAssignGesture(2, 4, 60, 115));

        ArgumentCaptor<StepState> stepCaptor = ArgumentCaptor.forClass(StepState.class);
        verify(clipWriter, times(2)).writeStep(eq(2), eq(4), anyBoolean(), stepCaptor.capture());

        StepState firstWrite = stepCaptor.getAllValues().get(0);
        StepState secondWrite = stepCaptor.getAllValues().get(1);

        assertEquals(60, firstWrite.getPitch(), "clear write keeps old pitch");
        assertEquals(100, firstWrite.getVelocity(), "clear write keeps old velocity");
        assertEquals(60, secondWrite.getPitch(), "rewrite keeps same pitch");
        assertEquals(115, secondWrite.getVelocity(), "rewrite applies new keyboard velocity");

        reset(clipWriter);
        dispatcher.dispatch(new PitchAssignGesture(2, 4, 60, 115));
        verify(clipWriter, never()).writeStep(anyInt(), anyInt(), anyBoolean(), any(StepState.class));
    }

    @Test
    void scene_launch_dispatch_updates_focused_track_and_toggles_track_clip() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new LaunchClipGesture(3));

        assertEquals(3, state.getFocusedTrack());
        verify(clipWriter).toggleTrackClipPlayback(3, 0);
    }

    @Test
    void stop_all_gesture_calls_stop_all_track_clips() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new StopAllGesture());

        verify(clipWriter).stopAllTrackClips();
    }

    @Test
    void play_pause_gesture_toggles_transport() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        Transport transport = mock(Transport.class);
        SettableBooleanValue isPlaying = mock(SettableBooleanValue.class);
        when(host.createApplication()).thenReturn(application);
        when(host.createTransport()).thenReturn(transport);
        when(transport.isPlaying()).thenReturn(isPlaying);
        when(isPlaying.get()).thenReturn(false);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

        dispatcher.dispatch(new ToggleTransportGesture());

        verify(transport).togglePlay();
    }

    @Test
    void scene_launch_led_is_yellow_when_track_is_playing_and_muted() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();

        doAnswer(invocation -> {
            ClipWriter.PlaybackStateListener listener = invocation.getArgument(0);
            listener.onTrackPlayingChanged(2, true);
            listener.onTrackMutedChanged(2, true);
            return null;
        }).when(clipWriter).setPlaybackStateListener(any());

        new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        verify(midiOut).sendMidi(0x90, 0x54, LedRenderer.YELLOW);
    }

    @Test
    void per_step_knob_velocity_updates_state_and_rewrites_step() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 0);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerStepKnobTurnGesture(0, 0, PerStepParameter.VELOCITY, 3));

        assertEquals(103, state.getStep(0, 0).getVelocity());
        verify(clipWriter).writeStep(eq(0), eq(0), eq(true), any(StepState.class));
    }

    @Test
    void per_step_knob_velocity_is_clamped_to_valid_range() {
        SequencerState state = new SequencerState();
        state.setStepVelocity(1, 2, 0);
        state.toggleStep(1, 2);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerStepKnobTurnGesture(1, 2, PerStepParameter.VELOCITY, -1));

        assertEquals(0, state.getStep(1, 2).getVelocity());
        verify(clipWriter, never()).writeStep(anyInt(), anyInt(), anyBoolean(), any(StepState.class));
    }

    @Test
    void per_step_knob_step_condition_cycles_through_four_values() {
        SequencerState state = new SequencerState();
        state.toggleStep(2, 3);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerStepKnobTurnGesture(2, 3, PerStepParameter.STEP_CONDITION, 1));
        assertEquals(StepCondition.EVERY_2ND, state.getStep(2, 3).getStepCondition());

        dispatcher.dispatch(new PerStepKnobTurnGesture(2, 3, PerStepParameter.STEP_CONDITION, 1));
        assertEquals(StepCondition.EVERY_4TH, state.getStep(2, 3).getStepCondition());

        dispatcher.dispatch(new PerStepKnobTurnGesture(2, 3, PerStepParameter.STEP_CONDITION, 1));
        assertEquals(StepCondition.EVERY_8TH, state.getStep(2, 3).getStepCondition());

        dispatcher.dispatch(new PerStepKnobTurnGesture(2, 3, PerStepParameter.STEP_CONDITION, 1));
        assertEquals(StepCondition.ALWAYS, state.getStep(2, 3).getStepCondition());
    }

    @Test
    void per_step_knob_scale_degree_offset_updates_state_and_rewrites_step() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 1);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerStepKnobTurnGesture(0, 1, PerStepParameter.SCALE_DEGREE_OFFSET, 1));

        assertEquals(1, state.getStep(0, 1).getScaleDegreeOffset());
        verify(clipWriter).writeStep(eq(0), eq(1), eq(true), any(StepState.class));
    }

    @Test
    void per_step_knob_scale_degree_offset_is_clamped_to_minus7_plus7() {
        SequencerState state = new SequencerState();
        state.setStepScaleDegreeOffset(1, 2, 7);
        state.toggleStep(1, 2);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerStepKnobTurnGesture(1, 2, PerStepParameter.SCALE_DEGREE_OFFSET, 1));

        assertEquals(7, state.getStep(1, 2).getScaleDegreeOffset());
        verify(clipWriter, never()).writeStep(anyInt(), anyInt(), anyBoolean(), any(StepState.class));
    }

    @Test
    void per_step_knob_chord_voicing_cycles_and_rewrites_step() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 2);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerStepKnobTurnGesture(0, 2, PerStepParameter.CHORD_VOICING, 1));
        assertEquals(ChordVoicing.POWER, state.getStep(0, 2).getChordVoicing());

        dispatcher.dispatch(new PerStepKnobTurnGesture(0, 2, PerStepParameter.CHORD_VOICING, -1));
        assertEquals(ChordVoicing.ROOT_ONLY, state.getStep(0, 2).getChordVoicing());

        verify(clipWriter, times(2)).writeStep(eq(0), eq(2), eq(true), any(StepState.class));
    }

    @Test
    void update_global_scale_rewrites_all_active_steps() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 0);
        state.toggleStep(2, 5);
        state.toggleStep(4, 7);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.updateGlobalScale(new GlobalScale(0, Mode.MINOR));

        assertEquals(new GlobalScale(0, Mode.MINOR), state.getGlobalScale());
        verify(clipWriter).writeStep(eq(0), eq(0), eq(true), any(StepState.class));
        verify(clipWriter).writeStep(eq(2), eq(5), eq(true), any(StepState.class));
        verify(clipWriter).writeStep(eq(4), eq(7), eq(true), any(StepState.class));
        verify(clipWriter, times(3)).writeStep(anyInt(), anyInt(), eq(true), any(StepState.class));
    }

    @Test
    void track_step_duration_turn_cycles_step_duration_and_applies_track_timing() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new TrackStepDurationTurnGesture(1, 1));

        assertEquals(StepDuration.S16T, state.getTrack(1).getStepDuration());
        verify(clipWriter).applyTrackTiming(1);
    }

    @Test
    void track_loop_end_point_updates_state_and_applies_track_timing() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new TrackLoopEndPointGesture(2, 5));

        assertEquals(5, state.getTrack(2).getLoopEndPoint());
        verify(clipWriter).applyTrackTiming(2);
    }

    @Test
    void per_track_knob_pattern_rotation_updates_state_and_applies_track_timing() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(0, PerTrackParameter.PATTERN_ROTATION, 1));

        assertEquals(1, state.getTrack(0).getPatternRotation());
        verify(clipWriter).applyTrackTiming(0);
    }

    @Test
    void per_track_knob_swing_updates_state_and_applies_track_timing() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(1, PerTrackParameter.SWING, 1));

        assertEquals(51, state.getTrack(1).getSwing());
        verify(clipWriter).applyTrackTiming(1);
    }

    @Test
    void per_track_knob_transpose_updates_state_and_rewrites_track_timing() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(2, PerTrackParameter.TRANSPOSE, 1));

        assertEquals(1, state.getTrack(2).getTranspose());
        verify(clipWriter).applyTrackTiming(2);
    }

    @Test
    void per_track_knob_probability_updates_state_and_applies_track_timing() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(3, PerTrackParameter.TRACK_PROBABILITY, -10));

        assertEquals(0.9, state.getTrack(3).getTrackProbability(), 1e-9);
        verify(clipWriter).applyTrackTiming(3);
    }

    @Test
    void per_track_knob_clip_volume_delegates_to_clip_writer() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(1, PerTrackParameter.CLIP_VOLUME, 2));

        verify(clipWriter).adjustTrackClipVolume(1, 2);
        verify(clipWriter, never()).applyTrackTiming(anyInt());
    }

    @Test
    void per_track_knob_static_pan_updates_state_and_applies_notestep_pan() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(2, PerTrackParameter.STATIC_PAN, 5));

        assertEquals(0.05, state.getTrack(2).getStaticPan(), 1e-9);
        verify(clipWriter).applyTrackStaticPan(2);
        verify(clipWriter, never()).applyTrackTiming(anyInt());
    }

    @Test
    void per_track_knob_velocity_spread_updates_state_and_applies_notestep_spread() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(0, PerTrackParameter.VELOCITY_SPREAD, 4));

        assertEquals(0.04, state.getTrack(0).getVelocitySpread(), 1e-9);
        verify(clipWriter).applyTrackVelocitySpread(0);
        verify(clipWriter, never()).applyTrackTiming(anyInt());
    }

    @Test
    void toggle_track_mute_gesture_calls_clip_writer_toggle() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new ToggleTrackMuteGesture(3));

        verify(clipWriter).toggleTrackMute(3);
    }

    @Test
    void per_track_knob_loop_multiplier_cycles_values() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        HostContext hostContext = hostContext();
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(4, PerTrackParameter.LOOP_MULTIPLIER, -1));

        assertEquals(LoopMultiplier.HALF, state.getTrack(4).getLoopMultiplier());
        verify(clipWriter).applyTrackTiming(4);
    }

    @Test
    void per_track_knob_phase_offset_updates_state_and_applies_track_timing() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(0, PerTrackParameter.PHASE_OFFSET, 1));

        assertEquals(0.01, state.getTrack(0).getPhaseOffset(), 1e-9);
        verify(clipWriter).applyTrackTiming(0);
    }

    @Test
    void per_track_knob_euclidean_distribution_updates_active_steps_and_writes_changed_steps_only() {
        SequencerState state = new SequencerState();
        CapturingClipWriter clipWriter = new CapturingClipWriter();
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new PerTrackKnobTurnGesture(0, PerTrackParameter.EUCLIDEAN_DISTRIBUTION, 3));

        assertEquals(3, state.getTrack(0).getEuclideanDistribution());
        assertTrue(state.getStep(0, 0).isActive());
        assertTrue(state.getStep(0, 3).isActive());
        assertTrue(state.getStep(0, 5).isActive());
        assertFalse(state.getStep(0, 1).isActive());
        assertFalse(state.getStep(0, 2).isActive());
        assertFalse(state.getStep(0, 4).isActive());
        assertFalse(state.getStep(0, 6).isActive());
        assertFalse(state.getStep(0, 7).isActive());

        assertEquals(3, clipWriter.writes().size());
        assertEquals(0, clipWriter.writes().get(0).track);
        assertEquals(0, clipWriter.writes().get(0).step);
        assertTrue(clipWriter.writes().get(0).active);
        assertEquals(0, clipWriter.writes().get(1).track);
        assertEquals(3, clipWriter.writes().get(1).step);
        assertTrue(clipWriter.writes().get(1).active);
        assertEquals(0, clipWriter.writes().get(2).track);
        assertEquals(5, clipWriter.writes().get(2).step);
        assertTrue(clipWriter.writes().get(2).active);
    }

    @Test
    void toggle_scale_selection_overlay_switches_volume_led_on_and_off() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new ToggleScaleSelectionOverlayGesture());

        verify(midiOut).sendMidi(0x90, 0x44, LedRenderer.YELLOW);

        reset(midiOut);
        dispatcher.dispatch(new ToggleScaleSelectionOverlayGesture());
        verify(midiOut).sendMidi(0x90, 0x44, LedRenderer.OFF);
    }

    @Test
    void scale_selection_pad_updates_global_root_and_rewrites_active_steps() {
        SequencerState state = new SequencerState();
        state.toggleStep(0, 1);
        state.setStepScaleDegreeOffset(0, 1, 2);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);
        dispatcher.dispatch(new ToggleScaleSelectionOverlayGesture());

        dispatcher.dispatch(new ScaleSelectionPadGesture(0, 4));

        assertEquals(4, state.getGlobalScale().root());
        verify(clipWriter).writeStep(eq(0), eq(1), eq(true), any(StepState.class));
    }

    @Test
    void scale_selection_pad_updates_mode_and_rewrites_active_steps() {
        SequencerState state = new SequencerState();
        state.toggleStep(3, 2);
        state.setStepScaleDegreeOffset(3, 2, -2);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);
        dispatcher.dispatch(new ToggleScaleSelectionOverlayGesture());

        dispatcher.dispatch(new ScaleSelectionPadGesture(1, 2));

        assertEquals(Mode.DORIAN, state.getGlobalScale().mode());
        verify(clipWriter).writeStep(eq(3), eq(2), eq(true), any(StepState.class));
    }

    @Test
    void dismiss_scale_selection_overlay_returns_to_normal_and_turns_off_volume_led() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);
        dispatcher.dispatch(new ToggleScaleSelectionOverlayGesture());

        reset(midiOut);
        dispatcher.dispatch(new DismissScaleSelectionOverlayGesture());

        verify(midiOut).sendMidi(0x90, 0x44, LedRenderer.OFF);
    }

    @Test
    void set_volume_held_gesture_lights_and_clears_volume_led() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetVolumeHeldGesture(true));
        verify(midiOut).sendMidi(0x90, 0x44, LedRenderer.YELLOW);

        reset(midiOut);
        dispatcher.dispatch(new SetVolumeHeldGesture(false));
        verify(midiOut).sendMidi(0x90, 0x44, LedRenderer.OFF);
    }

    @Test
    void set_pan_held_gesture_lights_and_clears_pan_led() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetPanHeldGesture(true));
        verify(midiOut).sendMidi(0x90, 0x45, LedRenderer.YELLOW);

        reset(midiOut);
        dispatcher.dispatch(new SetPanHeldGesture(false));
        verify(midiOut).sendMidi(0x90, 0x45, LedRenderer.OFF);
    }

    @Test
    void set_send_held_gesture_lights_and_clears_send_led() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetSendHeldGesture(true));
        verify(midiOut).sendMidi(0x90, 0x46, LedRenderer.YELLOW);

        reset(midiOut);
        dispatcher.dispatch(new SetSendHeldGesture(false));
        verify(midiOut).sendMidi(0x90, 0x46, LedRenderer.OFF);
    }

    @Test
    void set_device_held_gesture_lights_and_clears_device_led() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetDeviceHeldGesture(true));
        verify(midiOut).sendMidi(0x90, 0x47, LedRenderer.YELLOW);

        reset(midiOut);
        dispatcher.dispatch(new SetDeviceHeldGesture(false));
        verify(midiOut).sendMidi(0x90, 0x47, LedRenderer.OFF);
    }

    @Test
    void device_macro_turn_targets_current_focused_track() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new LaunchClipGesture(2));
        dispatcher.dispatch(new DeviceMacroTurnGesture(0, 1));

        verify(clipWriter).adjustFocusedTrackDeviceMacro(2, 0, 1);

        dispatcher.dispatch(new LaunchClipGesture(3));
        dispatcher.dispatch(new DeviceMacroTurnGesture(0, -1));
        verify(clipWriter).adjustFocusedTrackDeviceMacro(3, 0, -1);
    }

    @Test
    void send_level_turn_targets_current_focused_track() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new LaunchClipGesture(1));
        dispatcher.dispatch(new SendLevelTurnGesture(4, 1));

        verify(clipWriter).adjustFocusedTrackSendLevel(1, 4, 1);

        dispatcher.dispatch(new LaunchClipGesture(0));
        dispatcher.dispatch(new SendLevelTurnGesture(4, -1));
        verify(clipWriter).adjustFocusedTrackSendLevel(0, 4, -1);
    }

    @Test
    void set_sequence_bank_overlay_gesture_toggles_rec_led() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetSequenceBankOverlayGesture(true, false));

        verify(midiOut).sendMidi(0x90, 0x5D, LedRenderer.YELLOW);

        reset(midiOut);
        dispatcher.dispatch(new SetSequenceBankOverlayGesture(false, false));
        verify(midiOut).sendMidi(0x90, 0x5D, LedRenderer.OFF);
    }

    @Test
    void sequence_bank_pad_on_empty_slot_copies_switches_and_launches() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        when(clipWriter.isSlotPopulated(0, 3)).thenReturn(false);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetSequenceBankOverlayGesture(true, false));
        dispatcher.dispatch(new SequenceBankPadGesture(0, 3));

        verify(clipWriter).copySlotIfEmpty(0, 0, 3);
        verify(clipWriter).launchSlot(0, 3);
        assertEquals(3, state.getTrack(0).getActiveSlot());
    }

    @Test
    void sequence_bank_pad_on_populated_slot_switches_without_copy() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        when(clipWriter.isSlotPopulated(1, 4)).thenReturn(true);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetSequenceBankOverlayGesture(true, false));
        dispatcher.dispatch(new SequenceBankPadGesture(1, 4));

        verify(clipWriter, never()).copySlotIfEmpty(anyInt(), anyInt(), anyInt());
        verify(clipWriter).launchSlot(1, 4);
        assertEquals(4, state.getTrack(1).getActiveSlot());
    }

    @Test
    void sequence_bank_clear_mode_clears_slot_without_launching() {
        SequencerState state = new SequencerState();
        state.switchSlot(2, 5);
        state.toggleStep(2, 1);
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetSequenceBankOverlayGesture(true, true));
        dispatcher.dispatch(new SequenceBankPadGesture(2, 5));

        verify(clipWriter).clearSlot(2, 5);
        verify(clipWriter, never()).launchSlot(anyInt(), anyInt());
    }

    @Test
    void move_all_tracks_sequence_slot_wraps_and_launches_each_track() {
        SequencerState state = new SequencerState();
        state.switchSlot(0, 7);
        ClipWriter clipWriter = mock(ClipWriter.class);
        when(clipWriter.isSlotPopulated(anyInt(), anyInt())).thenReturn(true);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new MoveAllTracksSequenceSlotGesture(1));

        assertEquals(0, state.getTrack(0).getActiveSlot());
        assertEquals(1, state.getTrack(1).getActiveSlot());
        verify(clipWriter).launchSlot(0, 0);
        verify(clipWriter).launchSlot(1, 1);
        verify(clipWriter).launchSlot(2, 1);
        verify(clipWriter).launchSlot(3, 1);
        verify(clipWriter).launchSlot(4, 1);
    }

    @Test
    void copy_step_gestures_clone_step_parameters_and_rewrite_destination_step() {
        SequencerState state = new SequencerState();
        state.setStepActive(1, 2, true);
        state.setStepPitch(1, 2, 72);
        state.setStepVelocity(1, 2, 81);
        state.setStepGateLength(1, 2, 0.64);
        state.setStepProbability(1, 2, 0.37);
        state.setStepChordVoicing(1, 2, ChordVoicing.MAJ7);
        state.setStepScaleDegreeOffset(1, 2, -2);
        state.setStepRatchetCount(1, 2, 5);
        state.setStepRatchetDecay(1, 2, 0.2);
        state.setStepCondition(1, 2, StepCondition.EVERY_4TH);

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetCopyOverlayGesture(true));
        dispatcher.dispatch(new CopyPadGesture(1, 2));
        dispatcher.dispatch(new CopyPadGesture(3, 6));

        StepState destination = state.getStep(3, 6);
        assertTrue(destination.isActive());
        assertEquals(72, destination.getPitch());
        assertEquals(81, destination.getVelocity());
        assertEquals(0.64, destination.getGateLength(), 1e-9);
        assertEquals(0.37, destination.getProbability(), 1e-9);
        assertEquals(ChordVoicing.MAJ7, destination.getChordVoicing());
        assertEquals(-2, destination.getScaleDegreeOffset());
        assertEquals(5, destination.getRatchetCount());
        assertEquals(0.2, destination.getRatchetDecay(), 1e-9);
        assertEquals(StepCondition.EVERY_4TH, destination.getStepCondition());
        verify(clipWriter).writeStep(eq(3), eq(6), eq(true), any(StepState.class));
    }

    @Test
    void copy_track_gestures_clone_track_sequence_and_apply_track_timing() {
        SequencerState state = new SequencerState();
        state.setLoopEndPoint(0, 5);
        state.setStepDuration(0, StepDuration.S8);
        state.setStepActive(0, 0, true);
        state.setStepPitch(0, 0, 65);
        state.setStepVelocity(0, 0, 111);

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetCopyOverlayGesture(true));
        dispatcher.dispatch(new CopyTrackGesture(0));
        dispatcher.dispatch(new CopyTrackGesture(4));

        assertEquals(5, state.getTrack(4).getLoopEndPoint());
        assertEquals(StepDuration.S8, state.getTrack(4).getStepDuration());
        assertTrue(state.getStep(4, 0).isActive());
        assertEquals(65, state.getStep(4, 0).getPitch());
        assertEquals(111, state.getStep(4, 0).getVelocity());
        verify(clipWriter).applyTrackTiming(4);
    }

    @Test
    void copy_source_step_blinks_yellow_in_copy_target_state() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        reset(midiOut);
        dispatcher.dispatch(new SetCopyOverlayGesture(true));
        dispatcher.dispatch(new CopyPadGesture(2, 3));

        int sourcePadNote = (4 - 2) * 8 + 3;
        verify(midiOut).sendMidi(0x90, sourcePadNote, LedRenderer.YELLOW_BLINK);
    }

    @Test
    void copy_source_track_scene_launch_blinks_yellow_in_copy_target_state() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        reset(midiOut);
        dispatcher.dispatch(new SetCopyOverlayGesture(true));
        dispatcher.dispatch(new CopyTrackGesture(3));

        verify(midiOut).sendMidi(0x90, 0x52 + 3, LedRenderer.YELLOW_BLINK);
    }

    @Test
    void clear_pad_gesture_resets_step_to_clear_defaults_and_rewrites_inactive() {
        SequencerState state = new SequencerState();
        state.setStepActive(1, 2, true);
        state.setStepPitch(1, 2, 72);
        state.setStepVelocity(1, 2, 81);
        state.setStepGateLength(1, 2, 0.64);
        state.setStepProbability(1, 2, 0.37);
        state.setStepChordVoicing(1, 2, ChordVoicing.MAJ7);
        state.setStepScaleDegreeOffset(1, 2, -2);
        state.setStepRatchetCount(1, 2, 5);
        state.setStepRatchetDecay(1, 2, 0.2);
        state.setStepCondition(1, 2, StepCondition.EVERY_4TH);

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetClearOverlayGesture(true));
        dispatcher.dispatch(new ClearPadGesture(1, 2));

        StepState cleared = state.getStep(1, 2);
        assertFalse(cleared.isActive());
        assertEquals(60, cleared.getPitch());
        assertEquals(100, cleared.getVelocity());
        assertEquals(1.0, cleared.getGateLength(), 1e-9);
        assertEquals(1.0, cleared.getProbability(), 1e-9);
        assertEquals(ChordVoicing.ROOT_ONLY, cleared.getChordVoicing());
        assertEquals(0, cleared.getScaleDegreeOffset());
        assertEquals(1, cleared.getRatchetCount());
        assertEquals(0.0, cleared.getRatchetDecay(), 1e-9);
        assertEquals(StepCondition.ALWAYS, cleared.getStepCondition());

        verify(clipWriter).writeStep(eq(1), eq(2), eq(false), any(StepState.class));
    }

    @Test
    void clear_track_gesture_resets_all_track_steps_to_defaults_and_rewrites_all_inactive() {
        SequencerState state = new SequencerState();
        for (int step = 0; step < TrackState.STEP_COUNT; step++) {
            state.setStepActive(4, step, true);
            state.setStepPitch(4, step, 65 + step);
            state.setStepVelocity(4, step, 70 + step);
            state.setStepGateLength(4, step, 0.2 + (step * 0.05));
            state.setStepChordVoicing(4, step, ChordVoicing.MAJ7);
        }

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetClearOverlayGesture(true));
        dispatcher.dispatch(new ClearTrackGesture(4));

        for (int step = 0; step < TrackState.STEP_COUNT; step++) {
            StepState cleared = state.getStep(4, step);
            assertFalse(cleared.isActive());
            assertEquals(60, cleared.getPitch());
            assertEquals(100, cleared.getVelocity());
            assertEquals(1.0, cleared.getGateLength(), 1e-9);
            assertEquals(ChordVoicing.ROOT_ONLY, cleared.getChordVoicing());
        }
        verify(clipWriter, times(TrackState.STEP_COUNT)).writeStep(eq(4), anyInt(), eq(false), any(StepState.class));
    }

    @Test
    void clear_overlay_blocks_step_toggle_until_clear_overlay_is_exited() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, hostContext.host);

        dispatcher.dispatch(new SetClearOverlayGesture(true));
        dispatcher.dispatch(new StepToggleGesture(0, 0));

        assertFalse(state.getStep(0, 0).isActive());
        verify(clipWriter, never()).writeStep(anyInt(), anyInt(), anyBoolean(), any(StepState.class));

        dispatcher.dispatch(new SetClearOverlayGesture(false));
        dispatcher.dispatch(new StepToggleGesture(0, 0));

        assertTrue(state.getStep(0, 0).isActive());
        verify(clipWriter).writeStep(eq(0), eq(0), eq(true), any(StepState.class));
    }
}
