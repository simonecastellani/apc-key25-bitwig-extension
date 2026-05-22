package com.apcsequencer;

import com.bitwig.extension.controller.api.MidiOut;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApcKey25SequencerExtensionTest {

    @Test
    void sendAllLedsOff_clears_pad_and_button_note_ranges() {
        MidiOut midiOut = mock(MidiOut.class);

        ApcKey25SequencerExtension.sendAllLedsOff(midiOut);

        for (int note = 0x00; note <= 0x27; note++) {
            verify(midiOut).sendMidi(0x90, note, 0);
        }

        for (int note = 0x40; note <= 0x62; note++) {
            verify(midiOut).sendMidi(0x90, note, 0);
        }

        verify(midiOut, times(75)).sendMidi(eq(0x90), anyInt(), eq(0));
    }
}
