package com.apcsequencer;

/**
 * Abstraction over the Bitwig {@code CursorClip} API for writing step data.
 *
 * <p>One implementation ({@code BitwigClipWriter}) holds the real
 * {@code PinnableCursorClip[]} references and forwards calls to Bitwig.
 * In tests a no-op or spy implementation can be substituted.</p>
 */
public interface ClipWriter {

    interface PlaybackStateListener {
        void onTrackPlayingChanged(int track, boolean playing);

        void onTrackMutedChanged(int track, boolean muted);
    }

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

    /**
     * Toggles play/stop for a track's active Sequence Slot.
     */
    void toggleTrackClipPlayback(int track, int slot);

    /**
     * Stops clip launcher playback for all sequencer tracks.
     */
    void stopAllTrackClips();

    /**
     * Returns true when any clip is currently playing on the given track.
     */
    boolean isTrackPlaying(int track);

    /**
     * Returns true when the given track is muted.
     */
    boolean isTrackMuted(int track);

    /**
     * Registers a listener for track playback/mute state changes.
     */
    void setPlaybackStateListener(PlaybackStateListener listener);
}
