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
}
