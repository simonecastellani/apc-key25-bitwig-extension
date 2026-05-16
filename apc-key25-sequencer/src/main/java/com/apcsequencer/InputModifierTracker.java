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
 *   <li>Pad press + no modifier held → {@link StepToggleGesture}(track, step)</li>
 *   <li>Pad release → {@code null} (toggle fires on press only)</li>
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

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Process a pad event and return the appropriate gesture, or {@code null}.
     */
    public Gesture handlePad(PadEvent event) {
        if (!event.pressed()) {
            // Release — clear any held-pad state
            if (event.track() == heldPadTrack && event.step() == heldPadStep) {
                heldPadTrack = -1;
                heldPadStep  = -1;
            }
            return null; // toggle fires on press only
        }

        // Press
        heldPadTrack = event.track();
        heldPadStep  = event.step();

        if (heldModifiers.isEmpty()) {
            // No modifier — simple step toggle
            return new StepToggleGesture(event.track(), event.step());
        }

        // Modifier is held — future slices will handle pad+modifier combos here
        return null;
    }

    /**
     * Process a button event and return the appropriate gesture, or {@code null}.
     */
    public Gesture handleButton(ButtonEvent event) {
        ButtonId id = event.id();

        // Update modifier held-set
        if (MODIFIER_BUTTONS.contains(id)) {
            if (event.pressed()) heldModifiers.add(id);
            else                 heldModifiers.remove(id);
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
