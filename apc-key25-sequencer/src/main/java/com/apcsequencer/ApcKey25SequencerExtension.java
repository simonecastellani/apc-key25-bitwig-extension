package com.apcsequencer;

import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.StringValueChangedCallback;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;

public class ApcKey25SequencerExtension extends ControllerExtension {

    private ControllerHost controllerHost;

    private TrackState[]  tracks;
    private InputState    inputState;
    private ScaleManager  scaleManager;
    private Sequencer     sequencer;
    private LedManager    ledManager;
    private InputHandler  inputHandler;

    // Persistence setting (DocumentState)
    private SettableStringValue persistenceSetting;

    // Out-parameter arrays for deserialization
    private final int[] scaleIdxHolder  = {0};
    private final int[] rootNoteHolder  = {0};
    private final int[] activeTrackHolder = {0};

    protected ApcKey25SequencerExtension(
            ApcKey25SequencerExtensionDefinition definition, ControllerHost host) {
        super(definition, host);
        this.controllerHost = host;
    }

    @Override
    public void init() {
        final ControllerHost host = controllerHost;

        // ── Tracks ──────────────────────────────────────────────────────────
        tracks = new TrackState[Config.NUM_TRACKS];
        for (int i = 0; i < Config.NUM_TRACKS; i++) tracks[i] = new TrackState(i + 1);

        // ── State objects ────────────────────────────────────────────────────
        inputState   = new InputState();
        scaleManager = new ScaleManager();
        ledManager   = new LedManager();

        // ── MIDI ports ───────────────────────────────────────────────────────
        final MidiIn  keyboard = getMidiInPort(Config.PORT_KEYBOARD);
        final MidiIn  pads     = getMidiInPort(Config.PORT_PADS);
        final MidiOut ledOut   = getMidiOutPort(Config.PORT_OUT);

        // ── NoteInputs (one per track; no masks → we inject everything manually) ──
        final Sequencer.NoteInputPort[] noteInputPorts =
            new Sequencer.NoteInputPort[Config.NUM_TRACKS];
        for (int i = 0; i < Config.NUM_TRACKS; i++) {
            // createNoteInput with no masks: Bitwig shows it as a virtual MIDI input
            // but no raw hardware MIDI auto-routes to it.
            final NoteInput ni = keyboard.createNoteInput("APC Seq Track " + (i + 1));
            noteInputPorts[i]  = ni::sendRawMidiEvent;
        }

        // ── Sequencer ────────────────────────────────────────────────────────
        sequencer = new Sequencer(
            tracks, noteInputPorts, scaleManager,
            (cb, delayMs) -> host.scheduleTask(cb, delayMs)
        );

        // ── Transport sync ───────────────────────────────────────────────────
        final Transport transport = host.createTransport();

        transport.isPlaying().addValueObserver((BooleanValueChangedCallback) playing -> {
            if (playing) sequencer.start();
            else         sequencer.stop();
        });

        // addRawValueObserver gives the actual BPM as a double
        transport.tempo().addRawValueObserver(bpm -> sequencer.setBpm(bpm));

        // ── Input handler ────────────────────────────────────────────────────
        inputHandler = new InputHandler(
            tracks, inputState, scaleManager, noteInputPorts,
            this::save,
            host::requestFlush
        );

        keyboard.setMidiCallback((s, d1, d2) -> inputHandler.onKeyboardMidi(s, d1, d2));
        pads    .setMidiCallback((s, d1, d2) -> inputHandler.onPadMidi(s, d1, d2));

        // ── Preferences: configurable CC for knob 8 ─────────────────────────
        final SettableRangedValue knob8Setting = host.getPreferences().getNumberSetting(
            "CC Number (Knob 8)", "Sequencer", 0, 127, 1, "", 74
        );
        knob8Setting.addRawValueObserver(
            cc -> sequencer.setKnob8Cc((int) Math.round(cc))
        );

        // ── Persistence: restore on project load ─────────────────────────────
        persistenceSetting = host.getDocumentState().getStringSetting(
            "Sequencer State", "Sequencer", 65535, ""
        );
        persistenceSetting.addValueObserver(new StringValueChangedCallback() {
            @Override public void valueChanged(Object newVal) {
                final String json = (String) newVal;
                if (json != null && !json.isEmpty()) {
                    PersistenceManager.deserialize(
                        json, tracks, scaleIdxHolder, rootNoteHolder, activeTrackHolder
                    );
                    scaleManager.setScaleIndex(scaleIdxHolder[0]);
                    scaleManager.setRootNote(rootNoteHolder[0]);
                    inputState.activeTrack = activeTrackHolder[0];
                    controllerHost.requestFlush();
                }
            }
        });

        host.showPopupNotification("APC Key 25 Sequencer initialized");
    }

    @Override
    public void flush() {
        final MidiOut ledOut = getMidiOutPort(Config.PORT_OUT);
        final LedManager.MidiSender sender =
            (status, d1, d2) -> ledOut.sendMidi(status, d1, d2);

        if (inputState.stopAllClipsHeld) {
            ledManager.updateScaleView(
                scaleManager.getScaleIndex(), inputState.activeTrack
            );
        } else {
            ledManager.updateSequencerView(tracks);
        }
        ledManager.updateRecordLed(tracks[inputState.activeTrack].melodicMode);
        ledManager.flush(sender);
    }

    @Override
    public void exit() {
        sequencer.stop();
    }

    private void save() {
        persistenceSetting.set(PersistenceManager.serialize(
            tracks,
            scaleManager.getScaleIndex(),
            scaleManager.getRootNote(),
            inputState.activeTrack
        ));
    }
}
