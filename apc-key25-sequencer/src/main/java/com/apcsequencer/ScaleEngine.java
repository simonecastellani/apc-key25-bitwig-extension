package com.apcsequencer;

/**
 * Pure music-theory engine. No Bitwig dependency.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>{@link #semitoneOffset} — resolves a scale-degree offset (–7..+7) to a semitone
 *       transposition within a {@link GlobalScale}.</li>
 *   <li>{@link #chordPitches} — expands a {@link ChordVoicing} at an absolute MIDI root pitch
 *       into the full set of MIDI pitches using diatonic intervals from the scale.</li>
 * </ol>
 */
public final class ScaleEngine {

    private ScaleEngine() {}

    // Semitone intervals above the root for each degree (0-based) of each mode.
    // Index 0 = unison (always 0), index 6 = 7th degree.
    private static final int[][] MODE_INTERVALS = {
        // MAJOR          W W H W W W H
        { 0, 2, 4, 5, 7, 9, 11 },
        // MINOR (natural) W H W W H W W
        { 0, 2, 3, 5, 7, 8, 10 },
        // DORIAN          W H W W W H W
        { 0, 2, 3, 5, 7, 9, 10 },
        // PHRYGIAN        H W W W H W W
        { 0, 1, 3, 5, 7, 8, 10 },
        // LYDIAN          W W W H W W H
        { 0, 2, 4, 6, 7, 9, 11 },
        // MIXOLYDIAN      W W H W W H W
        { 0, 2, 4, 5, 7, 9, 10 },
        // LOCRIAN         H W W H W W W
        { 0, 1, 3, 5, 6, 8, 10 },
        // PENTATONIC_MAJOR  W W m3 W m3
        { 0, 2, 4, 7, 9 },
        // PENTATONIC_MINOR  m3 W W m3 W
        { 0, 3, 5, 7, 10 },
        // CHROMATIC         all semitones
        { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 },
    };

    /**
     * Resolves {@code degreeOffset} (–7..+7) to a semitone transposition within {@code scale}.
     *
     * <p>Degree 0 always returns 0. Positive degrees walk up the scale; negative degrees walk
     * down. Going beyond the octave wraps: degree +7 in a 7-note scale = +1 octave (12 semitones)
     * above the root; degree –7 = –1 octave below the root.</p>
     *
     * <p>Note: {@code scale.root()} is not used here — the offset is relative to whatever the
     * step's absolute pitch is. The root only matters when converting an absolute pitch to a
     * scale degree (not done by this method).</p>
     */
    public static int semitoneOffset(GlobalScale scale, int degreeOffset) {
        int[] intervals = MODE_INTERVALS[scale.mode().ordinal()];
        int size = intervals.length;

        if (degreeOffset == 0) return 0;

        // floorDiv / floorMod give correct wrapping for negative offsets.
        // e.g. in a 7-note scale: degree –1 → floorDiv(−1,7)=−1, floorMod(−1,7)=6
        //   → −1*12 + intervals[6] = −12 + 11 = −1 (the leading tone one octave down, then up)
        int octaves = Math.floorDiv(degreeOffset, size);
        int remainder = Math.floorMod(degreeOffset, size);

        return octaves * 12 + intervals[remainder];
    }

    /**
     * Returns the MIDI pitches for {@code voicing} at {@code rootPitch} within {@code scale}.
     *
     * <p><b>All intervals are diatonic.</b> Each voicing maps to a fixed set of scale-degree
     * indices resolved through the mode's interval table. The chord quality (major vs minor) is
     * emergent from the scale, not hardcoded. Consequently:</p>
     * <ul>
     *   <li>{@code MAJ_TRIAD} and {@code MIN_TRIAD} both use degrees {0, 2, 4} — their quality
     *       differs only when the active scale changes (e.g. C Major → major 3rd; C Minor →
     *       minor 3rd). The names are UX labels for the performer.</li>
     *   <li>{@code DOM7}, {@code MAJ7}, and {@code MIN7} similarly all use degrees {0, 2, 4, 6};
     *       the 7th quality adapts to the active scale.</li>
     * </ul>
     *
     * @param rootPitch absolute MIDI pitch of the step's root note (0–127)
     */
    public static int[] chordPitches(GlobalScale scale, int rootPitch, ChordVoicing voicing) {
        return switch (voicing) {
            case ROOT_ONLY ->
                new int[]{ rootPitch };
            case POWER ->
                // Degrees: root (0) + 5th (4)
                new int[]{ rootPitch, rootPitch + diatonic(scale, 4) };
            case MAJ_TRIAD, MIN_TRIAD ->
                // Degrees: root (0) + 3rd (2) + 5th (4); quality adapts to the active scale
                new int[]{ rootPitch, rootPitch + diatonic(scale, 2), rootPitch + diatonic(scale, 4) };
            case DOM7, MAJ7, MIN7 ->
                // Degrees: root (0) + 3rd (2) + 5th (4) + 7th (6); quality adapts to the active scale
                new int[]{ rootPitch, rootPitch + diatonic(scale, 2), rootPitch + diatonic(scale, 4), rootPitch + diatonic(scale, 6) };
            case SUS4 ->
                // Degrees: root (0) + 4th (3) + 5th (4)
                new int[]{ rootPitch, rootPitch + diatonic(scale, 3), rootPitch + diatonic(scale, 4) };
            case OCTAVE ->
                // Fixed octave — not diatonic, always +12 semitones
                new int[]{ rootPitch, rootPitch + 12 };
        };
    }

    /**
     * Returns the semitone interval for scale degree {@code degreeIndex} (0-based, one octave).
     * Wraps via modulo if {@code degreeIndex} exceeds the scale size.
     */
    private static int diatonic(GlobalScale scale, int degreeIndex) {
        int[] intervals = MODE_INTERVALS[scale.mode().ordinal()];
        return intervals[degreeIndex % intervals.length];
    }
}
