package com.apcsequencer;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiOut;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
}
