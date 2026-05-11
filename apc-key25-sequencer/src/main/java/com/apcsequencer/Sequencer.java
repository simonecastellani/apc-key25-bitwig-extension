package com.apcsequencer;

import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.IntegerValueChangedCallback;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.PinnableCursorClip;

import java.util.Arrays;

public class Sequencer {

    public static final int STEPS_PER_BEAT  = 2;    // 8th-note steps
    public static final int PATTERN_LENGTH  = 8;
    private static final int FIXED_NOTE     = 60;   // Middle C
    private static final int FIXED_VELOCITY = 100;

    /**
     * Note gate as a fraction of step duration (used by clip scheduling).
     * e.g. 0.5-beat step × 0.85 = 0.425 beats gate.
     */
    private static final double GATE_RATIO      = 0.85;
    private static final double STEP_SIZE_BEATS = 1.0 / STEPS_PER_BEAT;  // 0.5
    static final double         GATE_DURATION   = STEP_SIZE_BEATS * GATE_RATIO; // 0.425

    // LED color velocities (APC Key 25 mk1)
    public static final int LED_OFF    = 0;
    public static final int LED_GREEN  = 1;
    public static final int LED_RED    = 3;
    public static final int LED_ORANGE = 5;

    // ── Testable interfaces ───────────────────────────────────────────────

    public interface NoteOutput {
        void sendRawMidiEvent(int status, int data1, int data2);
    }

    public interface LedOutput {
        void setLed(int noteNumber, int color);
    }

    public interface ClipOutput {
        /** Write an enabled step into the clip (uses fixed velocity + gate duration). */
        void setStep(int step);
        /** Remove a step from the clip. */
        void clearStep(int step);
    }

    private static final ClipOutput NOOP_CLIP_OUTPUT = new ClipOutput() {
        public void setStep(int step)   {}
        public void clearStep(int step) {}
    };

    // ── Pure address helpers ──────────────────────────────────────────────

    /** Bottom pad row (row 4): notes 0x00–0x07, one-to-one with step index. */
    public static int bottomRowNote(int step) {
        return step;
    }

    /** Map a beat position to a pattern step index [0, patternLength). */
    public static int calculateStep(double beatPosition, int stepsPerBeat, int patternLength) {
        if (beatPosition < 0) return 0;
        return (int)(beatPosition * stepsPerBeat) % patternLength;
    }

    /**
     * Duration of one step in milliseconds at the given BPM and steps-per-beat.
     * e.g. 120 BPM, 2 steps/beat → 250 ms/step.
     */
    public static double calculateStepDurationMs(double bpm, int stepsPerBeat) {
        return 60_000.0 / bpm / stepsPerBeat;
    }

    // ── Instance state ────────────────────────────────────────────────────

    private final ControllerHost     host;       // null in tests
    private final NoteOutput         noteOutput; // null in runtime (clips handle notes)
    private final LedOutput          ledOutput;
    private final ClipOutput         clipOutput;
    private       PinnableCursorClip clip;       // null in tests; set in runtime constructor

    private int currentStep = -1;

    /** Step enabled/disabled state. All off by default. */
    private final boolean[] enabled = new boolean[PATTERN_LENGTH];

    // ── Test constructors ─────────────────────────────────────────────────

    /** Backward-compatible test constructor — uses no-op ClipOutput. */
    Sequencer(NoteOutput noteOutput, LedOutput ledOutput) {
        this(noteOutput, ledOutput, NOOP_CLIP_OUTPUT);
    }

    /** Full test constructor — inject all outputs. */
    Sequencer(NoteOutput noteOutput, LedOutput ledOutput, ClipOutput clipOutput) {
        this.host       = null;
        this.noteOutput = noteOutput;
        this.ledOutput  = ledOutput;
        this.clipOutput = clipOutput;
    }

    // ── Runtime constructor ───────────────────────────────────────────────

    public Sequencer(ControllerHost host) {
        this.host = host;

        // Block hardware notes from passing through to Bitwig instruments
        MidiIn midiIn = host.getMidiInPort(1);
        NoteInput noteInput = midiIn.createNoteInput("Track 1 — APC Seq");
        Integer[] blockAll = new Integer[128];
        Arrays.fill(blockAll, -1);
        noteInput.setKeyTranslationTable(blockAll);
        // Clips handle note scheduling; no direct MIDI note output needed
        this.noteOutput = null;

        // LED output: MidiOut on port 0
        MidiOut midiOut = host.getMidiOutPort(0);
        this.ledOutput = (note, color) -> midiOut.sendMidi(0x90, note, color);

        // ── Plan C: clip-based sequencer ─────────────────────────────────
        // Follow the user's selected track via a CursorTrack; create an 8-step,
        // 1-key launcher clip.  Bitwig's audio engine handles all note timing at
        // sample accuracy — no scheduleTask or interpolation needed.

        CursorTrack cursorTrack = host.createCursorTrack(1, 0);
        this.clip = cursorTrack.createLauncherCursorClip(PATTERN_LENGTH, 1);

        // playingStep() fires with the current step index [0, PATTERN_LENGTH-1]
        // while the clip is running, or -1 when stopped/not playing.
        // This drives the LED playhead and replaces all Plan B timing code.
        clip.playingStep().addValueObserver(
                (IntegerValueChangedCallback) this::setPlayhead, -1);

        // When a clip appears on the cursor track (user creates or selects one),
        // configure it and push the current enabled[] state into the clip grid.
        clip.exists().addValueObserver(
                (BooleanValueChangedCallback) exists -> {
                    if (exists) syncPatternToClip();
                });

        // Clip output: delegates to clip.setStep / clip.clearStep
        this.clipOutput = new ClipOutput() {
            public void setStep(int step) {
                clip.setStep(0, step, 0, FIXED_VELOCITY, GATE_DURATION);
            }
            public void clearStep(int step) {
                clip.clearStep(0, step, 0);
            }
        };
    }

    /**
     * Multi-track runtime constructor. The caller (Extension) is responsible for
     * blocking NoteInput passthrough on Port 1 before creating sequencers.
     *
     * @param clip      a {@link PinnableCursorClip} from a CursorTrack at the desired position
     * @param ledOutput LED sink for pad row LEDs (Port 0 note-on)
     */
    public Sequencer(PinnableCursorClip clip, LedOutput ledOutput) {
        this.host       = null;
        this.noteOutput = null;
        this.ledOutput  = ledOutput;
        this.clip       = clip;
        clip.playingStep().addValueObserver(
                (IntegerValueChangedCallback) this::setPlayhead, -1);
        clip.exists().addValueObserver(
                (BooleanValueChangedCallback) exists -> {
                    if (exists) syncPatternToClip();
                });
        this.clipOutput = new ClipOutput() {
            public void setStep(int step) {
                clip.setStep(0, step, 0, FIXED_VELOCITY, GATE_DURATION);
            }
            public void clearStep(int step) {
                clip.clearStep(0, step, 0);
            }
        };
    }

    // ── Sync pattern → clip ───────────────────────────────────────────────

    /**
     * Configure the cursor clip and write the current {@code enabled[]} state
     * into the Bitwig clip grid.  Called when a clip first appears on the
     * cursor track ({@code clip.exists()} transitions to {@code true}).
     */
    private void syncPatternToClip() {
        clip.setStepSize(STEP_SIZE_BEATS);
        clip.scrollToKey(FIXED_NOTE);
        clip.isLoopEnabled().set(true);
        clip.getLoopLength().set(STEP_SIZE_BEATS * PATTERN_LENGTH); // 4.0 beats
        for (int s = 0; s < PATTERN_LENGTH; s++) {
            if (enabled[s]) {
                clipOutput.setStep(s);
            } else {
                clipOutput.clearStep(s);
            }
        }
        if (host != null) host.println("Pattern synced to clip (" + PATTERN_LENGTH + " steps)");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Called by the extension after construction. Currently a no-op; setup happens in the constructor. */
    public void start() {}

    // ── Pad input ─────────────────────────────────────────────────────────

    public void padTapped(int noteNumber) {
        if (noteNumber < 0x00 || noteNumber > 0x07) return;
        int step = noteNumber;

        enabled[step] = !enabled[step];

        if (enabled[step]) {
            clipOutput.setStep(step);
        } else {
            clipOutput.clearStep(step);
        }

        if (step != currentStep) {
            ledOutput.setLed(noteNumber, enabled[step] ? LED_GREEN : LED_OFF);
        }
    }

    // ── Tick state machine (package-private for tests) ────────────────────
    //
    // Used directly only in tests. Runtime uses clip playingStep observer.
    // Kept beat-position-driven for test readability.

    void tick(boolean isPlaying, double beatPos) {
        if (isPlaying) {
            int newStep = calculateStep(beatPos, STEPS_PER_BEAT, PATTERN_LENGTH);
            if (newStep != currentStep) {
                if (currentStep >= 0 && noteOutput != null && enabled[currentStep]) {
                    noteOutput.sendRawMidiEvent(0x80, FIXED_NOTE, 0);
                }
                setPlayhead(newStep);
                if (noteOutput != null && enabled[currentStep]) {
                    noteOutput.sendRawMidiEvent(0x90, FIXED_NOTE, FIXED_VELOCITY);
                }
            }
        } else {
            stopPlayback();
        }
    }

    // ── Stop ──────────────────────────────────────────────────────────────

    private void stopPlayback() {
        if (currentStep >= 0) {
            if (noteOutput != null && enabled[currentStep]) {
                noteOutput.sendRawMidiEvent(0x80, FIXED_NOTE, 0);
            }
            setPlayhead(-1);
        }
    }

    // ── Playhead LED helper ───────────────────────────────────────────────

    /**
     * Advance the playhead to {@code newStep}, updating LEDs:
     * <ul>
     *   <li>Restores the previous step's LED (green if enabled, off if disabled).</li>
     *   <li>Sets the new step's LED to red (playhead). Pass -1 to only restore the old step.</li>
     * </ul>
     * Also called from the {@code clip.playingStep()} observer at runtime.
     */
    void setPlayhead(int newStep) {
        // Bitwig may fire playingStep() with PATTERN_LENGTH as a "just-past-end"
        // sentinel on the loop boundary.  Treat any out-of-range value as stopped.
        if (newStep >= PATTERN_LENGTH) newStep = -1;

        if (currentStep >= 0) {
            ledOutput.setLed(bottomRowNote(currentStep),
                    enabled[currentStep] ? LED_GREEN : LED_OFF);
        }
        currentStep = newStep;
        if (newStep >= 0) {
            ledOutput.setLed(bottomRowNote(newStep), LED_RED);
        }
    }

    /**
     * Re-emit the current LED state for all 8 pad steps without changing sequencer
     * state. Called by {@link TrackRouter} when the user switches to this track.
     */
    public void refreshLeds() {
        for (int s = 0; s < PATTERN_LENGTH; s++) {
            int color = (s == currentStep) ? LED_RED
                      : (enabled[s]        ? LED_GREEN : LED_OFF);
            ledOutput.setLed(bottomRowNote(s), color);
        }
    }
}
