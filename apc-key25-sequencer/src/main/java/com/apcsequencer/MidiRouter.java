package com.apcsequencer;

import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.Transport;

/**
 * Connects the APC Key 25 MIDI input port to the sequencer pipeline.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li>Register a MIDI callback on the Bitwig {@link MidiIn} port.</li>
 *   <li>Decode each raw MIDI triplet with {@link MidiDecoder}.</li>
 *   <li>Feed decoded events to {@link InputModifierTracker} to produce
 *       {@link Gesture} objects.</li>
 *   <li>Forward gestures to {@link GestureDispatcher}.</li>
 * </ol>
 *
 * <p>Knob events and keyboard events are decoded here but not yet routed
 * (stubs for future slices).</p>
 */
public final class MidiRouter {

    private final InputModifierTracker tracker;
    private final GestureDispatcher    dispatcher;
    private final Transport            transport;

    public MidiRouter(MidiIn midiIn,
                      InputModifierTracker tracker,
                      GestureDispatcher dispatcher,
                      Transport transport) {
        this.tracker    = tracker;
        this.dispatcher = dispatcher;
        this.transport = transport;

        this.transport.playPosition().markInterested();

        // Register the MIDI message callback
        midiIn.setMidiCallback(this::onMidi);
    }

    // -----------------------------------------------------------------------
    // MIDI callback
    // -----------------------------------------------------------------------

    /**
     * Called by Bitwig for every incoming MIDI message on port 0.
     *
     * @param status status byte (e.g. 0x90 note-on ch1, 0xb0 CC ch1)
     * @param data1  first data byte (note or CC number)
     * @param data2  second data byte (velocity or CC value)
     */
    private void onMidi(int status, int data1, int data2) {
        // 1. Try pad
        PadEvent pad = MidiDecoder.decodePad(status, data1, data2);
        if (pad != null) {
            dispatcher.dispatch(tracker.handlePad(pad));
            return;
        }

        // 2. Try button
        ButtonEvent btn = MidiDecoder.decodeButton(status, data1, data2);
        if (btn != null) {
            dispatcher.dispatch(tracker.handleButton(btn));
            return;
        }

        // 3. Try knob
        KnobEvent knob = MidiDecoder.decodeKnob(status, data1, data2);
        if (knob != null) {
            dispatcher.dispatch(tracker.handleKnob(knob));
            return;
        }

        // 4. Try keyboard (stub — routed in a later slice)
        KeyboardNoteEvent key = MidiDecoder.decodeKeyboard(status, data1, data2, transport.playPosition().get());
        if (key != null) {
            dispatcher.dispatch(tracker.handleKeyboard(key));
        }
    }
}
