package com.apcsequencer;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stateful tracker that converts raw {@link PadEvent} and {@link ButtonEvent}
 * messages into high-level {@link Gesture} objects, taking the currently-held
 * modifier buttons into account.
 *
 * <h3>Modifier buttons</h3>
 * <p>SHIFT, SCENE_LAUNCH_0–4, VOLUME, PAN, SEND, DEVICE, REC, SUSTAIN are all
 * "hold" buttons: they do not produce a gesture on press/release; instead they
 * update the internal held-set so that subsequent pad or knob events can be
 * interpreted in context.</p>
 *
 * <h3>Rules implemented in this slice (no modifier)</h3>
 * <ul>
 *   <li>Pad tap + no modifier held → {@link StepToggleGesture}(track, step)</li>
 *   <li>Pad hold + keyboard note press → {@link PitchAssignGesture}(track, step, pitch, velocity)</li>
 *   <li>Pad release with no keyboard note during hold → emits tap-toggle gesture</li>
 *   <li>Pad release after keyboard pitch-assign during hold → {@code null}</li>
 *   <li>LEFT press → {@link UndoGesture}</li>
 *   <li>RIGHT press → {@link RedoGesture}</li>
 *   <li>All other button press/release → update held-set, return {@code null}</li>
 * </ul>
 */
public final class InputModifierTracker {

    /** Buttons whose hold state modifies the meaning of subsequent events. */
    private static final Set<ButtonId> MODIFIER_BUTTONS = EnumSet.of(
            ButtonId.SHIFT,
            ButtonId.SCENE_LAUNCH_0, ButtonId.SCENE_LAUNCH_1,
            ButtonId.SCENE_LAUNCH_2, ButtonId.SCENE_LAUNCH_3, ButtonId.SCENE_LAUNCH_4,
            ButtonId.VOLUME, ButtonId.PAN, ButtonId.SEND, ButtonId.DEVICE,
            ButtonId.REC, ButtonId.SUSTAIN
    );

    /** Currently held modifier buttons. */
    private final Set<ButtonId> heldModifiers = EnumSet.noneOf(ButtonId.class);

    /** Currently held pad (track, step), or (-1, -1) when no pad is held. */
    private int heldPadTrack = -1;
    private int heldPadStep  = -1;

    /** True when current held-pad interaction should emit a tap-toggle on release. */
    private boolean pendingTapToggle = false;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Process a pad event and return the appropriate gesture, or {@code null}.
     */
    public Gesture handlePad(PadEvent event) {
        if (!event.pressed()) {
            Gesture releaseGesture = null;

            // Release — emit pending tap-toggle (if any), then clear held-pad state
            if (event.track() == heldPadTrack && event.step() == heldPadStep) {
                if (pendingTapToggle) {
                    releaseGesture = new StepToggleGesture(event.track(), event.step());
                }
                heldPadTrack = -1;
                heldPadStep  = -1;
                pendingTapToggle = false;
            }

            return releaseGesture;
        }

        int heldSceneTrack = heldSceneLaunchTrack();
        if (heldSceneTrack >= 0) {
            if (heldSceneTrack == event.track()) {
                pendingTapToggle = false;
                return new TrackLoopEndPointGesture(event.track(), event.step() + 1);
            }
            return null;
        }

        // Press
        heldPadTrack = event.track();
        heldPadStep  = event.step();
        pendingTapToggle = heldModifiers.isEmpty();

        // Tap-vs-hold is decided on release/keyboard; no immediate gesture on press.
        return null;
    }

    /**
     * Process a button event and return the appropriate gesture, or {@code null}.
     */
    public Gesture handleButton(ButtonEvent event) {
        ButtonId id = event.id();

        if (event.pressed() && !isAnyModifierHeld()) {
            if (id == ButtonId.PLAY_PAUSE) {
                return new ToggleTransportGesture();
            }
            if (id == ButtonId.STOP_ALL_CLIPS) {
                return new StopAllGesture();
            }
            int sceneTrack = sceneLaunchTrack(id);
            if (sceneTrack >= 0) {
                heldModifiers.add(id);
                return new LaunchClipGesture(sceneTrack);
            }
        }

        // Update modifier held-set
        if (MODIFIER_BUTTONS.contains(id)) {
            if (event.pressed()) {
                heldModifiers.add(id);

                // If a modifier is pressed during a held-pad interaction,
                // do not treat that interaction as a simple tap-toggle.
                if (heldPadTrack >= 0 && heldPadStep >= 0) {
                    pendingTapToggle = false;
                }
            } else {
                heldModifiers.remove(id);
            }
            return null;
        }

        // Navigation buttons — emit gestures only on press
        if (!event.pressed()) return null;

        return switch (id) {
            case LEFT  -> new UndoGesture();
            case RIGHT -> new RedoGesture();
            default    -> null;
        };
    }

    /**
     * Process a keyboard-note event and return the appropriate gesture, or {@code null}.
     *
     * <p>Rule for issue #5: if a pad is currently held and a keyboard key is pressed,
     * emit {@link PitchAssignGesture} for that held step. Keyboard note-off produces
     * no gesture.</p>
     */
    public Gesture handleKeyboard(KeyboardNoteEvent event) {
        if (!event.pressed()) return null;
        if (heldPadTrack < 0 || heldPadStep < 0) return null;

        // Holding pad + keyboard means pitch assignment intent, not tap-toggle.
        pendingTapToggle = false;

        return new PitchAssignGesture(heldPadTrack, heldPadStep, event.pitch(), event.velocity());
    }

    /**
     * Process a knob event and return the appropriate gesture, or {@code null}.
     *
     * <p>For issue #6, when a pad is held, knob indices 1,2,3,6,7,8 (0-based 0,1,2,5,6,7)
     * map to per-step Parameter Knob gestures.</p>
     */
    public Gesture handleKnob(KnobEvent event) {
        int heldSceneTrack = heldSceneLaunchTrack();
        if (heldSceneTrack >= 0) {
            if (event.knob() == 0 && event.delta() != 0) {
                pendingTapToggle = false;
                return new TrackStepDurationTurnGesture(heldSceneTrack, event.delta());
            }
            PerTrackParameter parameter = switch (event.knob()) {
                case 1 -> PerTrackParameter.PATTERN_ROTATION;
                case 2 -> PerTrackParameter.SWING;
                case 3 -> PerTrackParameter.TRANSPOSE;
                case 4 -> PerTrackParameter.TRACK_PROBABILITY;
                case 5 -> PerTrackParameter.LOOP_MULTIPLIER;
                case 6 -> PerTrackParameter.EUCLIDEAN_DISTRIBUTION;
                case 7 -> PerTrackParameter.PHASE_OFFSET;
                default -> null;
            };
            if (parameter != null && event.delta() != 0) {
                pendingTapToggle = false;
                return new PerTrackKnobTurnGesture(heldSceneTrack, parameter, event.delta());
            }
            return null;
        }

        if (heldPadTrack < 0 || heldPadStep < 0) return null;

        PerStepParameter parameter = switch (event.knob()) {
            case 0 -> PerStepParameter.VELOCITY;
            case 1 -> PerStepParameter.GATE_LENGTH;
            case 2 -> PerStepParameter.PROBABILITY;
            case 3 -> PerStepParameter.SCALE_DEGREE_OFFSET;
            case 5 -> PerStepParameter.RATCHET_COUNT;
            case 6 -> PerStepParameter.RATCHET_DECAY;
            case 7 -> PerStepParameter.STEP_CONDITION;
            default -> null;
        };
        if (parameter == null || event.delta() == 0) return null;

        pendingTapToggle = false;
        return new PerStepKnobTurnGesture(heldPadTrack, heldPadStep, parameter, event.delta());
    }

    // -----------------------------------------------------------------------
    // Package-private inspection (for GestureDispatcher / tests)
    // -----------------------------------------------------------------------

    /** Returns {@code true} if the given modifier button is currently held. */
    boolean isHeld(ButtonId id) {
        return heldModifiers.contains(id);
    }

    /** Returns {@code true} if any modifier button is currently held. */
    boolean isAnyModifierHeld() {
        return !heldModifiers.isEmpty();
    }

    private static int sceneLaunchTrack(ButtonId id) {
        return switch (id) {
            case SCENE_LAUNCH_0 -> 0;
            case SCENE_LAUNCH_1 -> 1;
            case SCENE_LAUNCH_2 -> 2;
            case SCENE_LAUNCH_3 -> 3;
            case SCENE_LAUNCH_4 -> 4;
            default -> -1;
        };
    }

    private int heldSceneLaunchTrack() {
        for (int track = 0; track < SequencerState.TRACK_COUNT; track++) {
            if (heldModifiers.contains(sceneLaunchButton(track))) {
                return track;
            }
        }
        return -1;
    }

    private static ButtonId sceneLaunchButton(int track) {
        return switch (track) {
            case 0 -> ButtonId.SCENE_LAUNCH_0;
            case 1 -> ButtonId.SCENE_LAUNCH_1;
            case 2 -> ButtonId.SCENE_LAUNCH_2;
            case 3 -> ButtonId.SCENE_LAUNCH_3;
            case 4 -> ButtonId.SCENE_LAUNCH_4;
            default -> throw new IllegalArgumentException("invalid track: " + track);
        };
    }
}
