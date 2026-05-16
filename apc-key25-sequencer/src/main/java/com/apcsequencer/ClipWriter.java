package com.apcsequencer;

/**
 * Abstraction over the Bitwig {@code CursorClip} API for writing step data.
 *
 * <p>One implementation ({@code BitwigClipWriter}) holds the real
 * {@code PinnableCursorClip[]} references and forwards calls to Bitwig.
 * In tests a no-op or spy implementation can be substituted.</p>
 */
public interface ClipWriter {

    /**
     * Write the current state of a single step to its Bitwig clip.
     *
     * <p>If {@code active} is {@code false} all notes at column {@code step}
     * are cleared; if {@code true} the step's notes are (re-)written from
     * {@code stepState}.</p>
     *
     * @param track     0-based track index (0–4)
     * @param step      0-based step column index (0–7)
     * @param active    whether the step should be on or off
     * @param stepState full per-step parameters (pitch, velocity, gate length …)
     */
    void writeStep(int track, int step, boolean active, StepState stepState);
}
