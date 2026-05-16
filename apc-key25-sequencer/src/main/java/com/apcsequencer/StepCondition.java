package com.apcsequencer;

/**
 * Per-step condition: controls on which loop pass the step fires.
 *
 * <p>Maps to {@code NoteStep.setRecurrence(length, mask)} per ADR 0001:</p>
 * <ul>
 *   <li>{@code ALWAYS}     → fires every pass (no recurrence filter)</li>
 *   <li>{@code EVERY_2ND}  → {@code setRecurrence(2, 0b01)}</li>
 *   <li>{@code EVERY_4TH}  → {@code setRecurrence(4, 0b0001)}</li>
 *   <li>{@code EVERY_8TH}  → {@code setRecurrence(8, 0b00000001)}</li>
 * </ul>
 */
public enum StepCondition {
    ALWAYS, EVERY_2ND, EVERY_4TH, EVERY_8TH
}
