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

    private static final int PORT_ALL_IN  = 0;
    private static final int PORT_LED_OUT = 0;

    // All pad rows: notes 0x00–0x27 (rows 4–0, bottom to top)
    private static final int PAD_MIN = 0x00;
    private static final int PAD_MAX = 0x27;

    // Scene Launch buttons 1–5: channel 0, notes 0x52–0x56
    private static final int SCENE_LAUNCH_MIN = 0x52;
    private static final int SCENE_LAUNCH_MAX = 0x56;

    // Keep CursorTrack references alive to prevent garbage collection.
    @SuppressWarnings("FieldCanBeLocal")
    private CursorTrack[] cursors;

    protected ApcKey25SequencerExtension(
            ApcKey25SequencerExtensionDefinition definition,
            ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {
        final ControllerHost host = getHost();

        MidiIn  allIn  = host.getMidiInPort(PORT_ALL_IN);
        MidiOut ledOut = host.getMidiOutPort(PORT_LED_OUT);

        // Block all hardware notes from passing through to Bitwig instruments.
        NoteInput noteInput = allIn.createNoteInput("APC Key 25 Seq");
        noteInput.setKeyTranslationTable(blockAllTable());

        // ----------------------------------------------------------------
        // Bitwig cursor clips — one independent CursorTrack per sequencer track.
        //
        // Using 5 separate CursorTrack objects (not one shared cursor) ensures
        // each PinnableCursorClip follows its own, independently-navigated
        // cursor and therefore targets a different track. A single shared cursor
        // would make all 5 clips aliases of the same position.
        //
        // selectFirst() + selectNext()×t on a CursorTrack navigates only regular
        // (instrument/MIDI) tracks — it does NOT navigate FX/send or master
        // tracks — so clips are never accidentally created on those.
        // ----------------------------------------------------------------
        PinnableCursorClip[] clips = new PinnableCursorClip[SequencerState.TRACK_COUNT];
        cursors = new CursorTrack[SequencerState.TRACK_COUNT];

        SequencerState state = new SequencerState();

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            final int trackIndex = t;

            CursorTrack cursor = host.createCursorTrack(
                    "seq-track-" + t, "Sequencer Track " + t, 0, 1, false);
            cursors[t] = cursor;

            // Navigate this cursor to its target track.
            cursor.selectFirst();
            for (int i = 0; i < t; i++) {
                cursor.selectNext();
            }

            // createLauncherCursorClip must be called during init().
            clips[t] = cursor.createLauncherCursorClip(TrackState.STEP_COUNT, 128);

            // Navigate the launcher cursor to slot 0 so setStep() has a target.
            cursor.selectSlot(0);

            final PinnableCursorClip clip = clips[t];
            final CursorTrack finalCursor = cursor;

            // Configure clip dimensions once it exists.
            clip.exists().addValueObserver(exists -> {
                if (exists) {
                    double beatTime = state.getTrack(trackIndex).getStepDuration().beatTime();
                    clip.setStepSize(beatTime);
                    clip.scrollToKey(60);
                }
            });

            // Auto-create an empty clip at slot 0 if none exists yet.
            // setStep() is a silent no-op when the cursor points at an empty slot.
            cursor.getClipLauncherSlots().getItemAt(0).hasContent().addValueObserver(has -> {
                if (!has) {
                    finalCursor.createNewLauncherClip(0, 4);
                }
            });

            // Log which Bitwig track each cursor resolved to.
            cursor.name().markInterested();
            cursor.name().addValueObserver(name ->
                    host.println("ApcKey25Sequencer: track " + trackIndex + " -> \"" + name + "\""));
        }

        // ----------------------------------------------------------------
        // Wire up the domain model and dispatcher.
        // ----------------------------------------------------------------
        BitwigClipWriter clipWriter = new BitwigClipWriter(clips, state, host);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, ledOut, host);

        InputModifierTracker tracker = new InputModifierTracker();
        new MidiRouter(allIn, tracker, dispatcher);

        dispatcher.flushLeds();

        host.println("APC Key 25 Sequencer init OK - " + SequencerState.TRACK_COUNT + " tracks");
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
        getHost().println("APC Key 25 Sequencer exit");
    }
}
