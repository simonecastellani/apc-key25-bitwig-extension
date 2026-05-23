package com.apcsequencer;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiOut;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GestureDispatcherTest {

    @Test
    void undo_gesture_calls_bitwig_application_undo() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        when(host.createApplication()).thenReturn(application);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

        dispatcher.dispatch(new UndoGesture());

        verify(application).undo();
    }

    @Test
    void redo_gesture_calls_bitwig_application_redo() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        when(host.createApplication()).thenReturn(application);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

        dispatcher.dispatch(new RedoGesture());

        verify(application).redo();
    }

    @Test
    void playhead_wrapped_to_loop_end_uses_solid_red_not_blink() {
        SequencerState state = new SequencerState();
        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        when(host.createApplication()).thenReturn(application);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

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
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        when(host.createApplication()).thenReturn(application);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

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
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        when(host.createApplication()).thenReturn(application);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

        dispatcher.dispatch(new PitchAssignGesture(1, 3, 64, 70));

        verify(clipWriter, times(1)).writeStep(eq(1), eq(3), eq(false), any(StepState.class));
    }

    @Test
    void pitch_assign_to_same_pitch_is_idempotent() {
        SequencerState state = new SequencerState();

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        when(host.createApplication()).thenReturn(application);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

        dispatcher.dispatch(new PitchAssignGesture(0, 0, 60, 100));

        verify(clipWriter, never()).writeStep(anyInt(), anyInt(), anyBoolean(), any(StepState.class));
    }

    @Test
    void pitch_assign_same_pitch_with_new_velocity_updates_active_note_velocity() {
        SequencerState state = new SequencerState();
        state.toggleStep(2, 4);

        ClipWriter clipWriter = mock(ClipWriter.class);
        MidiOut midiOut = mock(MidiOut.class);
        ControllerHost host = mock(ControllerHost.class);
        Application application = mock(Application.class);
        when(host.createApplication()).thenReturn(application);

        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, midiOut, host);

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
}
