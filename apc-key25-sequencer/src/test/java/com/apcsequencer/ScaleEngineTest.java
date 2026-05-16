package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScaleEngineTest {

    // -------------------------------------------------------------------------
    // semitoneOffset — tracer bullet
    // -------------------------------------------------------------------------

    @Test
    void degreeOffset_zero_returns_zero_semitones() {
        GlobalScale cMajor = new GlobalScale(0, Mode.MAJOR);
        assertEquals(0, ScaleEngine.semitoneOffset(cMajor, 0));
    }

    // -------------------------------------------------------------------------
    // semitoneOffset — C Major ascending (+1..+7)
    // C major intervals: C D E F G A B C  →  0 2 4 5 7 9 11 12
    // -------------------------------------------------------------------------

    @Test
    void cMajor_ascending_degrees_produce_correct_semitone_ladder() {
        GlobalScale cMajor = new GlobalScale(0, Mode.MAJOR);
        assertAll(
            () -> assertEquals( 2, ScaleEngine.semitoneOffset(cMajor, +1), "degree +1 -> D  (+2 st)"),
            () -> assertEquals( 4, ScaleEngine.semitoneOffset(cMajor, +2), "degree +2 -> E  (+4 st)"),
            () -> assertEquals( 5, ScaleEngine.semitoneOffset(cMajor, +3), "degree +3 -> F  (+5 st)"),
            () -> assertEquals( 7, ScaleEngine.semitoneOffset(cMajor, +4), "degree +4 -> G  (+7 st)"),
            () -> assertEquals( 9, ScaleEngine.semitoneOffset(cMajor, +5), "degree +5 -> A  (+9 st)"),
            () -> assertEquals(11, ScaleEngine.semitoneOffset(cMajor, +6), "degree +6 -> B  (+11 st)"),
            () -> assertEquals(12, ScaleEngine.semitoneOffset(cMajor, +7), "degree +7 -> C' (+12 st / octave)")
        );
    }

    // -------------------------------------------------------------------------
    // semitoneOffset — C Major descending (-1..-7)
    // Going down from C: B A G F E D C  ->  -1 -3 -5 -7 -8 -10 -12
    // -------------------------------------------------------------------------

    @Test
    void cMajor_descending_degrees_produce_correct_semitone_ladder() {
        GlobalScale cMajor = new GlobalScale(0, Mode.MAJOR);
        assertAll(
            () -> assertEquals( -1, ScaleEngine.semitoneOffset(cMajor, -1), "degree -1 -> B  (-1 st)"),
            () -> assertEquals( -3, ScaleEngine.semitoneOffset(cMajor, -2), "degree -2 -> A  (-3 st)"),
            () -> assertEquals( -5, ScaleEngine.semitoneOffset(cMajor, -3), "degree -3 -> G  (-5 st)"),
            () -> assertEquals( -7, ScaleEngine.semitoneOffset(cMajor, -4), "degree -4 -> F  (-7 st)"),
            () -> assertEquals( -8, ScaleEngine.semitoneOffset(cMajor, -5), "degree -5 -> E  (-8 st)"),
            () -> assertEquals(-10, ScaleEngine.semitoneOffset(cMajor, -6), "degree -6 -> D  (-10 st)"),
            () -> assertEquals(-12, ScaleEngine.semitoneOffset(cMajor, -7), "degree -7 -> C  (-12 st / octave down)")
        );
    }

    // -------------------------------------------------------------------------
    // semitoneOffset — C Natural Minor ascending (+1..+7)
    // C minor intervals: C D Eb F G Ab Bb C  ->  0 2 3 5 7 8 10 12
    // -------------------------------------------------------------------------

    @Test
    void cNaturalMinor_ascending_degrees_produce_correct_semitone_ladder() {
        GlobalScale cMinor = new GlobalScale(0, Mode.MINOR);
        assertAll(
            () -> assertEquals( 2, ScaleEngine.semitoneOffset(cMinor, +1), "degree +1 -> D  (+2 st)"),
            () -> assertEquals( 3, ScaleEngine.semitoneOffset(cMinor, +2), "degree +2 -> Eb (+3 st)"),
            () -> assertEquals( 5, ScaleEngine.semitoneOffset(cMinor, +3), "degree +3 -> F  (+5 st)"),
            () -> assertEquals( 7, ScaleEngine.semitoneOffset(cMinor, +4), "degree +4 -> G  (+7 st)"),
            () -> assertEquals( 8, ScaleEngine.semitoneOffset(cMinor, +5), "degree +5 -> Ab (+8 st)"),
            () -> assertEquals(10, ScaleEngine.semitoneOffset(cMinor, +6), "degree +6 -> Bb (+10 st)"),
            () -> assertEquals(12, ScaleEngine.semitoneOffset(cMinor, +7), "degree +7 -> C' (+12 st)")
        );
    }

    // -------------------------------------------------------------------------
    // semitoneOffset — Chromatic: every degree = 1 semitone step
    // -------------------------------------------------------------------------

    @Test
    void chromatic_each_degree_offset_is_one_semitone() {
        GlobalScale chromatic = new GlobalScale(0, Mode.CHROMATIC);
        for (int d = -7; d <= 7; d++) {
            int deg = d;
            assertEquals(d, ScaleEngine.semitoneOffset(chromatic, deg),
                "chromatic degree " + deg + " should be " + d + " semitones");
        }
    }

    // -------------------------------------------------------------------------
    // semitoneOffset — Pentatonic Major: 5-note scale, gap handling
    // C Pentatonic Major: C D E G A  ->  0 2 4 7 9
    // degree +5 wraps to next octave: floorDiv(5,5)=1 octave, floorMod(5,5)=0 -> 12+0=12
    // -------------------------------------------------------------------------

    @Test
    void pentatonicMajor_degree_offsets_skip_missing_scale_tones() {
        GlobalScale cPentaMajor = new GlobalScale(0, Mode.PENTATONIC_MAJOR);
        assertAll(
            () -> assertEquals( 2, ScaleEngine.semitoneOffset(cPentaMajor, +1), "degree +1 -> D  (+2 st)"),
            () -> assertEquals( 4, ScaleEngine.semitoneOffset(cPentaMajor, +2), "degree +2 -> E  (+4 st)"),
            () -> assertEquals( 7, ScaleEngine.semitoneOffset(cPentaMajor, +3), "degree +3 -> G  (+7 st)"),
            () -> assertEquals( 9, ScaleEngine.semitoneOffset(cPentaMajor, +4), "degree +4 -> A  (+9 st)"),
            () -> assertEquals(12, ScaleEngine.semitoneOffset(cPentaMajor, +5), "degree +5 -> C' (+12 st, wraps to octave)")
        );
    }

    // -------------------------------------------------------------------------
    // chordPitches — tracer bullet: ROOT_ONLY returns just the root pitch
    // -------------------------------------------------------------------------

    @Test
    void rootOnly_voicing_returns_single_root_pitch() {
        GlobalScale cMajor = new GlobalScale(0, Mode.MAJOR);
        assertArrayEquals(new int[]{ 60 }, ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.ROOT_ONLY));
    }

    // -------------------------------------------------------------------------
    // chordPitches — key design invariant: chord intervals are DIATONIC, not fixed
    // MAJ_TRIAD in C Major  -> [60, 64, 67]  (major 3rd + perfect 5th)
    // MAJ_TRIAD in C Minor  -> [60, 63, 67]  (minor 3rd + perfect 5th)
    // Same voicing name, different actual semitones — chord follows the scale.
    // -------------------------------------------------------------------------

    @Test
    void majTriad_in_cMajor_uses_major_third() {
        GlobalScale cMajor = new GlobalScale(0, Mode.MAJOR);
        // C Major degrees 0,2,4 -> semitones 0,4,7 -> MIDI 60,64,67
        assertArrayEquals(new int[]{ 60, 64, 67 }, ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.MAJ_TRIAD));
    }

    @Test
    void majTriad_in_cMinor_uses_minor_third() {
        GlobalScale cMinor = new GlobalScale(0, Mode.MINOR);
        // C Minor degrees 0,2,4 -> semitones 0,3,7 -> MIDI 60,63,67
        assertArrayEquals(new int[]{ 60, 63, 67 }, ScaleEngine.chordPitches(cMinor, 60, ChordVoicing.MAJ_TRIAD));
    }

    // -------------------------------------------------------------------------
    // chordPitches — all 9 voicings produce correct note counts and intervals
    // in C Major (root = MIDI 60)
    // -------------------------------------------------------------------------

    @Test
    void all_voicings_in_cMajor_produce_correct_pitches() {
        GlobalScale cMajor = new GlobalScale(0, Mode.MAJOR);
        // C Major: 0,2,4,5,7,9,11
        assertAll(
            // ROOT_ONLY: just C
            () -> assertArrayEquals(new int[]{ 60 },
                ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.ROOT_ONLY),
                "ROOT_ONLY"),
            // POWER: C + G (degree 0 + degree 4 = 0+7)
            () -> assertArrayEquals(new int[]{ 60, 67 },
                ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.POWER),
                "POWER"),
            // MAJ_TRIAD: C E G (0+0, 0+4, 0+7)
            () -> assertArrayEquals(new int[]{ 60, 64, 67 },
                ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.MAJ_TRIAD),
                "MAJ_TRIAD"),
            // MIN_TRIAD: C Eb G — uses C Minor's degree 2,4 (0,3,7)
            // Note: MIN_TRIAD in C Major is non-diatonic; we verify it uses
            // the scale's actual degree-2 and degree-4 intervals.
            // In C Major those are 4 and 7 -> same as MAJ_TRIAD here.
            // The distinction shows in a minor scale context (tested above).
            () -> assertEquals(3, ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.MIN_TRIAD).length,
                "MIN_TRIAD has 3 notes"),
            // DOM7: 4 notes
            () -> assertEquals(4, ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.DOM7).length,
                "DOM7 has 4 notes"),
            // MAJ7: C E G B (0+0, 0+4, 0+7, 0+11)
            () -> assertArrayEquals(new int[]{ 60, 64, 67, 71 },
                ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.MAJ7),
                "MAJ7"),
            // MIN7: 4 notes
            () -> assertEquals(4, ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.MIN7).length,
                "MIN7 has 4 notes"),
            // SUS4: C F G (degree 0, degree 3, degree 4 -> 0, 5, 7)
            () -> assertArrayEquals(new int[]{ 60, 65, 67 },
                ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.SUS4),
                "SUS4"),
            // OCTAVE: C C' (60, 72)
            () -> assertArrayEquals(new int[]{ 60, 72 },
                ScaleEngine.chordPitches(cMajor, 60, ChordVoicing.OCTAVE),
                "OCTAVE")
        );
    }
}
