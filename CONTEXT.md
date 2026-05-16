# APC Key 25 Polyrhythmic Sequencer

A Bitwig Studio 6 extension that maps the Akai APC Key 25 MK1 pad grid onto a 5-track polyrhythmic/polymetric step sequencer, driving Bitwig clip launcher clips via MIDI.

## Language

**Track**:
One horizontal row of pads on the APC Key 25 (5 tracks total), corresponding to one clip in Bitwig's clip launcher.
_Avoid_: Channel, lane, row

**Step**:
One pad within a track's row; represents a single note event in the clip when active.
_Avoid_: Beat, cell, button

**Pad Grid**:
The 5×8 matrix of pads on the APC Key 25. Rows map to Tracks; columns map to Steps.
_Avoid_: Button matrix, pad matrix

**Step Duration**:
The time interval between consecutive steps in a track. One of 7 discrete subdivisions: 1/32, 1/16, 1/16T, 1/8, 1/8T, 3/16 (dotted 8th), 1/4. Independent per track.
_Avoid_: Tempo, speed, rate, clock division

**Step Count**:
The number of steps in a track's loop, equal to the Loop End Point column index (1–8). Together with Step Duration determines clip length.
_Avoid_: Length, sequence length, loop length

**Loop End Point**:
The column index (1–8) that defines where a track's sequence restarts. Set explicitly via hold Scene Launch + tap pad in that track's row. Independent from which steps are active or inactive.
_Avoid_: Last step, end step

**Parameter Knob**:
Any of the 8 knobs, each controlling a specific parameter determined by context: the modifier held (pad or Scene Launch) and the knob's index (1–8). See Knob Mapping.
_Avoid_: Config knob, encoder

**Velocity**:
Per-step MIDI velocity (0–127). Default: 100.
_Avoid_: Volume, accent, intensity

**Pitch Assignment**:
The gesture of holding a pad and pressing a keyboard key to assign that step's MIDI pitch. Default pitch for all steps is C3.
_Avoid_: Note assignment, key mapping

**Global Scale**:
A shared root note (C–B) and mode (Major, Minor, Dorian, Phrygian, Lydian, Mixolydian, Locrian, Pentatonic Major, Pentatonic Minor, Chromatic) applied to all tracks. Used by Scale Degree Offset.
_Avoid_: Key, tonality, tuning

**Scale Selection Mode**:
An overlay activated by Shift + Volume button. While active, the pad grid shows root notes on one row and modes on another. Tapping a pad sets the corresponding value. Deactivated by pressing Shift + Volume again.
_Avoid_: Scale edit mode, key select

**Chaos**:
_Removed — replaced by Velocity Spread (native `NoteStep.setVelocitySpread()`) and static pan (`NoteStep.setPan()`). Pan Chaos and Pitch Chaos had no native API support._

**Chord Voicing**:
A per-step parameter that adds scale-degree intervals above the step's assigned pitch, creating a chord. Intervals are computed from the Global Scale (not fixed semitones). Options: root only, power, major triad, minor triad, dominant 7th, major 7th, minor 7th, sus4, octave. Implemented as multiple simultaneous notes at the same step position in the clip.
_Avoid_: Chord type, harmony, chord mode

**Velocity Spread**:
Per-track humanisation amount applied to all notes' velocity via `NoteStep.setVelocitySpread()`. At 0% all notes play at their assigned velocity; at 100% velocity is randomised within the spread range. Replaces the dropped Pan Chaos and Pitch Chaos concepts.
_Avoid_: Velocity chaos, velocity randomisation, humanise

**Live Record Mode**:
A toggle activated by Shift + Stop All Clips. While active, incoming keyboard notes are captured into the Focused Track's current Sequence Slot: the nearest step is activated, and the note's pitch and velocity are assigned to it. Notes merge with the existing pattern (no overwrite). Quantized to the track's step grid.
_Avoid_: Record mode, input capture, step record
The track most recently launched via its Scene Launch button. Device macro and Send level controls always apply to the Focused Track.
_Avoid_: Selected track, active track, current track
One of 8 pattern variants available per track. Selecting a slot launches the corresponding Bitwig clip (creating a copy of the current slot if the destination is empty). The previously active slot's clip is preserved and re-selectable.
_Avoid_: Bank, variation, pattern

**Sequence Bank**:
The set of 8 Sequence Slots for a given track, corresponding to 8 clip launcher scene rows in Bitwig. Tracks can be on different Sequence Slots simultaneously.
_Avoid_: Pattern bank, preset bank

## Knob Mapping

**Per-step** (hold pad + knob N):

| Knob | Parameter | Range |
|------|-----------|-------|
| 1 | Velocity | 0–127 |
| 2 | Gate Length | 1–100% of Step Duration |
| 3 | Probability | 0–100% |
| 4 | Scale Degree Offset | –7 to +7 scale degrees within Global Scale |
| 5 | Chord Voicing | root only, power, maj triad, min triad, dom7, maj7, min7, sus4, octave |
| 6 | Ratchet Count | 1–8 repeats within Step Duration |
| 7 | Ratchet Decay | velocity drop per ratchet hit |
| 8 | Step Condition | fire every 1st / 2nd / 4th / 8th loop pass |

**Per-track** (hold Scene Launch + knob N):

| Knob | Parameter | Range |
|------|-----------|-------|
| 1 | Step Duration | 1/32, 1/16, 1/16T, 1/8, 1/8T, 3/16, 1/4 |
| 2 | Pattern Rotation | 0 to Step Count – 1 positions |
| 3 | Swing | 50–75% |
| 4 | Transpose | –12 to +12 semitones |
| 5 | Track Probability | 0–100% |
| 6 | Loop Multiplier | 0.5×, 1×, 2×, 4× |
| 7 | Euclidean Distribution | distributes N active steps evenly across the loop |
| 8 | Phase Offset | 0–100% of loop length |

## LED Mapping

**Normal step-edit view:**

| State | Color |
|-------|-------|
| Step inactive | Off |
| Step active | Green |
| Playhead on inactive step | Red |
| Playhead on active step | Yellow |
| Loop End Point marker (step inactive) | Red blink |
| Loop End Point marker (step active) | Yellow blink |

**Overlay views:**

| Overlay | Off | Green | Yellow |
|---------|-----|-------|--------|
| Sequence Bank | empty slot | populated slot | currently active slot |
| Scale Selection | unavailable | available option | currently selected |
| Copy mode (source selected) | — | — | Yellow blink |

## Interactions

**Pad hold gestures** (hold a pad, then):
- Press a keyboard key → Pitch Assignment for that step
- Rotate knob N → set the corresponding per-step parameter (see Knob Mapping)

**Scene Launch button** (one per track, 5 total):
- Tap → launch or stop that track's clip
- Hold + rotate knob N → set the corresponding per-track parameter (see Knob Mapping)
- Hold + tap pad in same row → set Loop End Point for that track

**Mode buttons:**
- Hold **Device** → knobs expose the 8 macro parameters of the instrument on the focused track; rotating writes automation into the clip
- Hold **Send** → knobs expose FX send levels for the focused track
- Hold **Volume** + rotate knob N → set clip volume for track N (knobs 1–5)
- Hold **Volume** + tap Scene Launch N → toggle mute for track N
- Hold **Pan** + rotate knob N → set static pan for all notes in track N (knobs 1–5)
- **Shift + Pan** + rotate knob N → set velocity spread (humanisation) for track N (knobs 1–5)
- **Shift + Volume** → activate Scale Selection Mode overlay

**Transport:**
- **Play/Pause** → Bitwig transport start/stop
- **Rec** → activate Sequence Bank overlay; tap a pad = select that Sequence Slot for that track (copies current slot if destination is empty)
- **Shift + Rec** → activate Sequence Bank overlay; tap a pad = clear that Sequence Slot

**Navigation:**
- **Left** → Undo last step edit
- **Right** → Redo
- **Up** → move all 5 tracks to the next Sequence Slot simultaneously
- **Down** → move all 5 tracks to the previous Sequence Slot simultaneously

**Stop All Clips button:**
- Tap → stop all 5 track clips simultaneously
- **Shift + Stop All Clips** → toggle Live Record mode (incoming keyboard notes captured and quantized to each track's step grid)

**Sustain pedal:**
- Tap → enter Copy mode; tap source pad then destination pad = copy all step parameters (step-level); tap source Scene Launch then destination Scene Launch = copy entire track's current sequence (track-level)
- **Shift + Sustain** → enter Clear mode; tap a pad = clear that step to defaults; tap a Scene Launch = clear all steps in that track

**Button LED states:**

| Control | Off | Green | Yellow | Red |
|---------|-----|-------|--------|-----|
| Scene Launch N | clip stopped | clip playing | clip playing + track muted | — |
| Volume / Pan / Send / Device | — | — | hold active (mode engaged) | — |
| Shift | not held | — | held | — |
| Play/Pause | transport stopped | transport playing | — | — |
| Rec | idle | — | Sequence Bank overlay active | — |
| Stop All Clips | — | — | — | Live Record active |

Convention: **yellow = you are currently in a hold/mode state** across all non-pad controls.

## Relationships

- A **Track** has 8 **Sequence Slots**, each corresponding to one Bitwig clip launcher scene row
- A **Track** contains exactly 8 **Steps** (one per column of the Pad Grid)
- A **Track** drives exactly one active Bitwig clip launcher **Clip** at a time (its current Sequence Slot)
- An active **Step** triggers one note event inside its **Clip**
- A **Track**'s clip length = **Step Count** × **Step Duration**
- A **Track**'s **Loop End Point** determines its **Step Count**

## Constraints

- The extension does **not** use any stock Bitwig APC Key 25 integration. Every hardware control (pads, buttons, knobs, keyboard) is exclusively owned by the sequencer.

## Flagged ambiguities

_(none)_
