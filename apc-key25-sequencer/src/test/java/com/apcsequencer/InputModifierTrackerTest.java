package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for {@link InputModifierTracker}.
 *
 * Slice C — no-modifier gestures:
 *   pad press (no modifier held) → StepToggleGesture(track, step)
 *   pad release                  → null (toggle fires on press only)
 *   LEFT press                   → UndoGesture
 *   RIGHT press                  → RedoGesture
 *   LEFT/RIGHT release           → null
 */
class InputModifierTrackerTest {

    // -----------------------------------------------------------------------
    // Slice C — no modifier + pad → StepToggleGesture
    // -----------------------------------------------------------------------

    @Test
    void no_modifier_pad_press_produces_step_toggle_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handlePad(new PadEvent(0, 0, true));
        assertInstanceOf(StepToggleGesture.class, g, "pad press with no modifier → StepToggleGesture");
        StepToggleGesture toggle = (StepToggleGesture) g;
        assertEquals(0, toggle.track());
        assertEquals(0, toggle.step());
    }

    @Test
    void no_modifier_pad_press_encodes_correct_track_and_step() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handlePad(new PadEvent(3, 5, true));
        assertInstanceOf(StepToggleGesture.class, g);
        StepToggleGesture toggle = (StepToggleGesture) g;
        assertEquals(3, toggle.track(), "track must be preserved");
        assertEquals(5, toggle.step(),  "step must be preserved");
    }

    @Test
    void no_modifier_pad_release_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handlePad(new PadEvent(0, 0, false));
        assertNull(g, "pad release → no gesture (toggle fires on press only)");
    }

    // -----------------------------------------------------------------------
    // Navigation: LEFT → UndoGesture, RIGHT → RedoGesture
    // -----------------------------------------------------------------------

    @Test
    void left_button_press_produces_undo_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.LEFT, true));
        assertInstanceOf(UndoGesture.class, g, "LEFT press → UndoGesture");
    }

    @Test
    void right_button_press_produces_redo_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.RIGHT, true));
        assertInstanceOf(RedoGesture.class, g, "RIGHT press → RedoGesture");
    }

    @Test
    void left_button_release_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.LEFT, false));
        assertNull(g, "LEFT release → no gesture");
    }

    @Test
    void right_button_release_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.RIGHT, false));
        assertNull(g, "RIGHT release → no gesture");
    }

    // -----------------------------------------------------------------------
    // Modifier buttons do NOT produce gestures (they update held-state only)
    // -----------------------------------------------------------------------

    @Test
    void shift_button_press_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SHIFT, true));
        assertNull(g, "SHIFT press → no gesture (modifier state update only)");
    }

    @Test
    void scene_launch_press_produces_no_gesture() {
        InputModifierTracker tracker = new InputModifierTracker();
        Gesture g = tracker.handleButton(new ButtonEvent(ButtonId.SCENE_LAUNCH_0, true));
        assertNull(g, "SCENE_LAUNCH press → no gesture (modifier state update only)");
    }
}
