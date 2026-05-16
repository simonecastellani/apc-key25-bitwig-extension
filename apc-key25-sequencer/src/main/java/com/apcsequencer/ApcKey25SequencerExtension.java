package com.apcsequencer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.PinnableCursorClip;

/**
 * Root extension class. Bitwig calls {@link #init()} on load and {@link #exit()} on unload.
 *
 * <h3>Architecture</h3>
 * <p>All clip writing goes through the {@code NoteStep} clip-writing API exclusively.
 * No raw MIDI note events are fired, and no real-time beat clock is subscribed to.
 * See docs/adr/0001-notestep-clip-writing-over-internal-clock.md.</p>
 *
 * <h3>Module wiring (tracer-bullet slice)</h3>
 * <pre>
 *   APC MIDI in ──► MidiRouter ──► InputModifierTracker ──► GestureDispatcher
 *                                                                │
 *                     SequencerState ◄──────────────────────────┤
 *                     BitwigClipWriter (→ CursorClip[]) ◄───────┤
 *                     LedRenderer → MidiOut ◄───────────────────┘
 * </pre>
 */
public class ApcKey25SequencerExtension extends ControllerExtension {

    private MidiIn              midiIn;
    private MidiOut             midiOut;
    private SequencerState      sequencerState;
    private GestureDispatcher   gestureDispatcher;

    protected ApcKey25SequencerExtension(
            ApcKey25SequencerExtensionDefinition definition, ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {
        final ControllerHost host = getHost();

        midiIn  = host.getMidiInPort(0);
        midiOut = host.getMidiOutPort(0);

        // ----------------------------------------------------------------
        // 1. Domain state
        // ----------------------------------------------------------------
        sequencerState = new SequencerState();

        // ----------------------------------------------------------------
        // 2. Bitwig cursor clips (5 tracks × 8 steps × 128 pitches)
        //
        // createLauncherCursorClip() is on CursorTrack, not Track.
        // We create one independent CursorTrack per sequencer track and
        // navigate it to position t using selectFirst() + selectNext()×t.
        // This is more reliable than selectChannel(trackBank.getItemAt(t))
        // which suffers from async resolution timing at init.
        // ----------------------------------------------------------------
        PinnableCursorClip[] clips = new PinnableCursorClip[SequencerState.TRACK_COUNT];
        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            CursorTrack cursor = host.createCursorTrack(
                    "seq-track-" + t, "Sequencer Track " + t, 0, 8, false);

            // Navigate to track t: first → then step forward t times
            cursor.selectFirst();
            for (int i = 0; i < t; i++) {
                cursor.selectNext();
            }

            clips[t] = cursor.createLauncherCursorClip(TrackState.STEP_COUNT, 128);

            // Navigate the launcher cursor to slot 0 so setStep() has a target clip.
            cursor.selectSlot(0);

            final int trackIndex = t;
            final CursorTrack finalCursor = cursor;
            final PinnableCursorClip finalClip = clips[t];

            // Configure clip (stepSize, loopEnabled, loopLength) only once a real clip exists.
            // setStepSize() is a no-op on a phantom cursor proxy — must fire inside this observer.
            finalClip.exists().markInterested();
            finalClip.exists().addValueObserver(exists -> {
                if (exists) {
                    host.println("[APC] Track " + trackIndex + " clip exists → configuring");
                    double beatTime = sequencerState.getTrack(trackIndex).getStepDuration().beatTime();
                    finalClip.setStepSize(beatTime);
                    finalClip.isLoopEnabled().set(true);
                    finalClip.getLoopLength().set(TrackState.STEP_COUNT * beatTime);
                }
            });

            // Auto-create an empty clip at slot 0 if none exists yet.
            // setStep() is a silent no-op when the cursor points to an empty slot.
            cursor.getClipLauncherSlots().getItemAt(0).hasContent().addValueObserver(has -> {
                if (!has) {
                    host.println("[APC] Track " + trackIndex + ": slot 0 empty → creating clip");
                    finalCursor.createNewLauncherClip(0, 4);
                } else {
                    host.println("[APC] Track " + trackIndex + ": slot 0 has content → ready");
                }
            });

            // Register playhead observer for this track
            finalClip.playingStep().addValueObserver(step ->
                    gestureDispatcher.setPlayhead(trackIndex, step));

            // Debug: log which Bitwig track each cursor resolved to
            cursor.name().markInterested();
            cursor.name().addValueObserver(name ->
                    host.println("[APC] Track " + trackIndex + " cursor → \"" + name + "\""));
        }

        // ----------------------------------------------------------------
        // 3. ClipWriter
        // ----------------------------------------------------------------
        ClipWriter clipWriter = new BitwigClipWriter(clips, sequencerState);

        // ----------------------------------------------------------------
        // 4. Application (for undo/redo)
        // ----------------------------------------------------------------
        Application application = host.createApplication();

        // ----------------------------------------------------------------
        // 5. GestureDispatcher
        // ----------------------------------------------------------------
        gestureDispatcher = new GestureDispatcher(
                sequencerState, clipWriter, application, midiOut, host);

        // ----------------------------------------------------------------
        // 6. InputModifierTracker + MidiRouter
        // ----------------------------------------------------------------
        InputModifierTracker tracker = new InputModifierTracker();
        new MidiRouter(midiIn, tracker, gestureDispatcher);

        // ----------------------------------------------------------------
        // 7. Initial LED flush
        // ----------------------------------------------------------------
        allLedsOff();           // reset any stale LEDs left by previous integrations
        gestureDispatcher.flushLeds();

        host.showPopupNotification("APC Key 25 Sequencer loaded");
        host.println("APC Key 25 Polyrhythmic Sequencer initialised");
    }

    @Override
    public void exit() {
        // Reset all LEDs on exit so the hardware is not left in a lit state
        allLedsOff();
        getHost().println("APC Key 25 Polyrhythmic Sequencer exited");
    }

    @Override
    public void flush() {
        // Called by Bitwig when it is safe to send MIDI output.
        // Incremental LED updates are sent immediately in GestureDispatcher.flushLeds();
        // nothing extra needed here for the tracer-bullet slice.
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Turn off all addressable LEDs: pads (0x00–0x27) and scene/button LEDs (0x40–0x62). */
    private void allLedsOff() {
        if (midiOut == null) return;
        // Pad matrix
        for (int note = 0x00; note <= 0x27; note++) {
            midiOut.sendMidi(0x90, note, 0x00);
        }
        // Buttons and scene launch buttons (0x40–0x62 covers UP/DOWN/LEFT/RIGHT,
        // VOLUME/PAN/SEND/DEVICE, STOP_ALL_CLIPS, SCENE_LAUNCH 0-4, PLAY/REC, SHIFT)
        for (int note = 0x40; note <= 0x62; note++) {
            midiOut.sendMidi(0x90, note, 0x00);
        }
    }
}
