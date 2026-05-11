package com.apcsequencer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.PinnableCursorClip;

import java.util.Arrays;

public class ApcKey25SequencerExtension extends ControllerExtension {

    // APC Key 25 mk1: all input on PORT 1; PORT 0 is LED output only.
    private static final int PORT_ALL_IN  = 1;
    private static final int PORT_LED_OUT = 0;

    // All pad rows: notes 0x00–0x27 (rows 4–0, bottom to top)
    private static final int PAD_MIN = 0x00;
    private static final int PAD_MAX = 0x27;

    // Scene Launch buttons 1–5: channel 0, notes 0x52–0x56
    private static final int SCENE_LAUNCH_MIN = 0x52;
    private static final int SCENE_LAUNCH_MAX = 0x56;

    private static final int NUM_TRACKS = 5;

    private final ControllerHost host;
    private TrackRouter          router;

    // Keep CursorTrack references alive to prevent garbage collection.
    @SuppressWarnings("FieldCanBeLocal")
    private CursorTrack[] cursors;

    protected ApcKey25SequencerExtension(
            ApcKey25SequencerExtensionDefinition definition,
            ControllerHost host) {
        super(definition, host);
        this.host = host;
    }

    @Override
    public void init() {
        MidiIn  allIn  = host.getMidiInPort(PORT_ALL_IN);
        MidiOut ledOut = host.getMidiOutPort(PORT_LED_OUT);

        // Block all hardware notes from passing through to Bitwig instruments (once, shared).
        NoteInput noteInput = allIn.createNoteInput("APC Key 25 Seq");
        noteInput.setKeyTranslationTable(blockAllTable());

        // Shared pad LED output (all sequencers write to the same port 0).
        Sequencer.LedOutput padLeds = (note, color) ->
                ledOut.sendMidi(0x90, note, color);

        // Scene Launch LED output (notes 0x52–0x56 on port 0).
        TrackRouter.SceneLedOutput sceneLeds = (row, color) ->
                ledOut.sendMidi(0x90, SCENE_LAUNCH_MIN + row, color);

        // Create 5 independent cursor tracks, navigated to positions 0–4.
        // shouldSelectHierarchy=false means each cursor ignores the Bitwig UI selection
        // and only moves via our API calls — giving fixed track mapping.
        cursors = new CursorTrack[NUM_TRACKS];
        Sequencer[] sequencers = new Sequencer[NUM_TRACKS];
        for (int i = 0; i < NUM_TRACKS; i++) {
            CursorTrack cursor = host.createCursorTrack(
                    "apc-seq-track-" + i, "APC Seq Track " + (i + 1), 0, 0, false);
            cursor.selectFirst();
            for (int j = 0; j < i; j++) {
                cursor.selectNext();
            }
            cursors[i] = cursor;
            PinnableCursorClip clip = cursor.createLauncherCursorClip(
                    Sequencer.PATTERN_LENGTH, 1);
            sequencers[i] = new Sequencer(clip, padLeds);
        }

        router = new TrackRouter(sequencers, sceneLeds);
        router.initLeds();

        // Wire raw MIDI callback last so router is fully initialised before any input.
        allIn.setMidiCallback((status, data1, data2) -> {
            host.println(MidiUtils.formatMidiMessage(status, data1, data2));
            dispatchMidi(status, data1, data2);
        });

        host.println("APC Key 25 Sequencer init OK — 5 tracks");
    }

    private void dispatchMidi(int status, int data1, int data2) {
        int channel = status & 0x0F;
        int msgType = status & 0xF0;

        // Only process note-on (velocity > 0) on channel 0
        if (msgType != 0x90 || channel != 0 || data2 == 0) return;

        if (data1 >= SCENE_LAUNCH_MIN && data1 <= SCENE_LAUNCH_MAX) {
            router.sceneLaunchPressed(data1 - SCENE_LAUNCH_MIN);
        } else if (data1 >= PAD_MIN && data1 <= PAD_MAX) {
            router.padTapped(data1);
        }
    }

    private static Integer[] blockAllTable() {
        Integer[] table = new Integer[128];
        Arrays.fill(table, -1);
        return table;
    }

    @Override
    public void flush() {}

    @Override
    public void exit() {
        host.println("APC Key 25 Sequencer exit");
    }
}
