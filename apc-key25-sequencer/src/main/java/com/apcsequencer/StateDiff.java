package com.apcsequencer;

/**
 * Represents an observed change produced by a single mutation on {@link SequencerState}.
 *
 * <p>An empty diff (see {@link #isEmpty()}) means the mutation produced no actual state change
 * (idempotent write). Consumers such as {@code ClipWriter} use the diff to know exactly which
 * {@code NoteStep} handles need re-writing, avoiding full clip rebuilds on every event.</p>
 *
 * <p>Build via {@link Builder}; instances are immutable once built.</p>
 */
public final class StateDiff {

    /** A single step whose state changed. */
    public record StepChange(int trackIndex, int stepIndex) {}

    private final java.util.List<StepChange> stepChanges;

    private StateDiff(Builder b) {
        this.stepChanges = java.util.List.copyOf(b.stepChanges);
    }

    /** Returns {@code true} if no state was changed by the mutation. */
    public boolean isEmpty() {
        return stepChanges.isEmpty();
    }

    /** Returns all step-level changes in this diff (unmodifiable). */
    public java.util.List<StepChange> stepChanges() {
        return stepChanges;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final java.util.List<StepChange> stepChanges = new java.util.ArrayList<>();

        public Builder addStepChange(int trackIndex, int stepIndex) {
            stepChanges.add(new StepChange(trackIndex, stepIndex));
            return this;
        }

        public StateDiff build() { return new StateDiff(this); }
    }
}
