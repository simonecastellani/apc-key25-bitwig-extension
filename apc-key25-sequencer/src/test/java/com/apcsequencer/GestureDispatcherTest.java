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
                                        int recurrenceMask) {
        }

        @Override
        public void applyTrackTiming(int track) {
        }

        @Override
        public void toggleTrackClipPlayback(int track, int slot) {
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
}
