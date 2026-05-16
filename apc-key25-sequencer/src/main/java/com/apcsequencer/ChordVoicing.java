package com.apcsequencer;

/**
 * Per-step chord voicing applied on top of a step's assigned pitch.
 *
 * <p><b>All intervals are diatonic.</b> Each voicing selects a set of scale-degree indices
 * (e.g. root + 3rd + 5th for triads) and resolves them through the active {@link GlobalScale}.
 * The labels (MAJ_TRIAD, MIN_TRIAD, etc.) are UX hints for the performer — the actual chord
 * quality (major vs minor) is determined by the scale, not by the voicing name. For example,
 * both MAJ_TRIAD and MIN_TRIAD resolve degrees {0, 2, 4}; in C Major that yields {C, E, G}
 * (major third); in C Minor it yields {C, Eb, G} (minor third).</p>
 *
 * <p>See {@link ScaleEngine#chordPitches} for the full resolution logic.</p>
 */
public enum ChordVoicing {
    ROOT_ONLY, POWER, MAJ_TRIAD, MIN_TRIAD, DOM7, MAJ7, MIN7, SUS4, OCTAVE
}
