package com.apcsequencer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

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

    @SuppressWarnings("FieldCanBeLocal")
    private TrackBank mainTrackBank;

    private MidiOut ledOutPort;

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
        ledOutPort = ledOut;

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
        Track[] tracks = new Track[SequencerState.TRACK_COUNT];
        cursors = new CursorTrack[SequencerState.TRACK_COUNT];
        mainTrackBank = host.createMainTrackBank(SequencerState.TRACK_COUNT, 0, 1);
        boolean[] clipCreateRequested = new boolean[SequencerState.TRACK_COUNT];

        SequencerState state = new SequencerState();

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            final int trackIndex = t;
            final Track bankTrack = mainTrackBank.getItemAt(trackIndex);
            tracks[trackIndex] = bankTrack;
            bankTrack.exists().markInterested();

            CursorTrack cursor = host.createCursorTrack(
                    "seq-track-" + t, "Sequencer Track " + t, 0, 1, false);
            cursors[t] = cursor;
            cursor.exists().markInterested();

            // createLauncherCursorClip must be called during init().
            clips[t] = cursor.createLauncherCursorClip(TrackState.STEP_COUNT, 128);
            clips[t].playingStep().markInterested();

            final PinnableCursorClip clip = clips[t];
            final CursorTrack finalCursor = cursor;
            clip.exists().markInterested();

            // Configure clip dimensions once it exists.
            clip.exists().addValueObserver(exists -> {
                if (exists) {
                    double beatTime = state.getTrack(trackIndex).getStepDuration().beatTime();
                    clip.setStepSize(beatTime);
                }
            });

            // Auto-create an empty clip at slot 0 if none exists yet.
            // setStep() is a silent no-op when the cursor points at an empty slot.
            final var slot0 = cursor.getClipLauncherSlots().getItemAt(0);
            slot0.hasContent().markInterested();
            slot0.hasContent().addValueObserver(has -> {
                if (has) {
                    clipCreateRequested[trackIndex] = false;
                }
            });

            Runnable ensureClipReady = () -> {
                boolean cursorExists = finalCursor.exists().get();
                boolean slotHasContent = slot0.hasContent().get();

                if (!cursorExists) {
                    clipCreateRequested[trackIndex] = false;
                    return;
                }

                finalCursor.selectSlot(0);

                if (!slotHasContent && !clipCreateRequested[trackIndex]) {
                    clipCreateRequested[trackIndex] = true;
                    finalCursor.createNewLauncherClip(0, 4);
                }
            };

            cursor.exists().addValueObserver(exists -> {
                if (exists) {
                    ensureClipReady.run();
                }
            });

            slot0.hasContent().addValueObserver(has -> {
                if (!has) {
                    ensureClipReady.run();
                }
            });

            final int[] bindAttempts = {0};
            final Runnable[] bindCursorToTrack = new Runnable[1];
            bindCursorToTrack[0] = () -> {
                bindAttempts[0]++;
                boolean bankExists = bankTrack.exists().get();

                if (bankExists) {
                    finalCursor.selectChannel(bankTrack);
                    finalCursor.selectSlot(0);
                }

                ensureClipReady.run();

                if (!finalCursor.exists().get() && bindAttempts[0] < 20) {
                    host.scheduleTask(bindCursorToTrack[0], 250);
                }
            };

            bankTrack.exists().addValueObserver(exists -> {
                if (exists) {
                    bindCursorToTrack[0].run();
                }
            });

            host.scheduleTask(bindCursorToTrack[0], 50);

            // Log which Bitwig track each cursor resolved to.
            cursor.name().markInterested();
        }

        // ----------------------------------------------------------------
        // Wire up the domain model and dispatcher.
        // ----------------------------------------------------------------
        BitwigClipWriter clipWriter = new BitwigClipWriter(clips, tracks, state);
        GestureDispatcher dispatcher = new GestureDispatcher(state, clipWriter, ledOut, host);

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            final int trackIndex = t;
            clips[t].playingStep().addValueObserver(step -> dispatcher.setPlayhead(trackIndex, step));
        }

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
        sendAllLedsOff(ledOutPort);
        getHost().println("APC Key 25 Sequencer exit");
    }

    static void sendAllLedsOff(MidiOut ledOut) {
        if (ledOut == null) return;

        for (int note = PAD_MIN; note <= PAD_MAX; note++) {
            ledOut.sendMidi(0x90, note, 0);
        }

        for (int note = 0x40; note <= 0x62; note++) {
            ledOut.sendMidi(0x90, note, 0);
        }
    }
}
