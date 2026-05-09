# APC Key 25 Sequencer — Design Spec
**Date:** 2026-05-09  
**Status:** Approved  
**Hardware:** Akai APC Key 25 mk1  
**Target:** Bitwig Studio 6  

---

## 1. Overview

A Bitwig 6 Java Extension that turns the Akai APC Key 25 mk1 into a standalone
polyrhythmic step sequencer + beat machine. The controller stops acting as a DAW
controller and becomes a self-contained sequencer: 5 independent tracks, each with
up to 8 steps, sending MIDI notes to Bitwig instrument tracks via dedicated NoteInputs.

---

## 2. Architecture

**Type:** Bitwig 6 Extension (Java 17, Maven), packaged as `.bwextension`.  
**Install path:** `~/Documents/Bitwig Studio/Extensions/`  
**Build:** `mvn package` → copies `.bwextension` to install path.

### File structure

```
apc-key25-sequencer/
├── pom.xml
└── src/main/java/com/apcsequencer/
    ├── ApcKey25SequencerExtensionDefinition.java
    ├── ApcKey25SequencerExtension.java   # init(), flush(), exit()
    ├── Sequencer.java                    # engine: tick, polyrhythm, note fire
    ├── ModeManager.java                  # Drum ↔ Melodic per track, Record button
    ├── ScaleManager.java                 # 8 scales, root note, knob→pitch mapping
    ├── LedManager.java                   # LED state, flush to MidiOut
    ├── InputHandler.java                 # raw MIDI → logical commands
    └── Config.java                       # all MIDI constants
```

### Data flow

```
APC Key 25 (MIDI In port 1)
  → InputHandler        (raw MIDI → logical commands)
    → Sequencer         (step state, tick, polyrhythm)
    → ModeManager       (drum/melodic toggle)
    → ScaleManager      (scale, root, pitch lookup)
      → NoteInput × 5  (sendRawMidiEvent → Bitwig instrument tracks)
      → LedManager      (LED state update)
        → MidiOut       (sendMidi → APC Key 25 LEDs)
```

### Bitwig API used

| API | Purpose |
|-----|---------|
| `getMidiInPort(0).setMidiCallback()` | Raw hardware input |
| `getMidiInPort(0).createNoteInput(name, masks)` × 5 | One NoteInput per track (ch 1–5) |
| `NoteInput.sendRawMidiEvent()` | Inject sequencer notes into Bitwig engine |
| `getMidiOutPort(0).sendMidi()` | LED control |
| `getTransport().isPlaying().addValueObserver()` | Start/stop sync |
| `getTransport().tempo()` | BPM for step timing |
| `host.scheduleTask(callback, delayMs)` | Recursive 1/16 tick |
| `host.getDocumentState().getStringSetting()` | Pattern persistence per project |
| `host.getPreferences().getNumberSetting()` | Global CC config (knob 8) |

---

## 3. Hardware Mapping

### mk1 vs mk2 differences

| Aspect | mk2 (protocol doc) | mk1 (actual hardware) |
|--------|-------------------|----------------------|
| Knobs | Relative | **Absolute (0–127)** |
| Pad LEDs | RGB (128-color palette, MIDI ch = behavior) | **3 colors only** (green/red/orange) via velocity |
| Button note# | Same | Same |

### Pad matrix — Note numbers

Pads are numbered bottom-left to upper-right (per APC Key 25 mk2 protocol, same on mk1):

|  | Col 0 | Col 1 | Col 2 | Col 3 | Col 4 | Col 5 | Col 6 | Col 7 |
|--|-------|-------|-------|-------|-------|-------|-------|-------|
| **Row 0 (top)** | 0x20 | 0x21 | 0x22 | 0x23 | 0x24 | 0x25 | 0x26 | 0x27 |
| **Row 1** | 0x18 | 0x19 | 0x1A | 0x1B | 0x1C | 0x1D | 0x1E | 0x1F |
| **Row 2** | 0x10 | 0x11 | 0x12 | 0x13 | 0x14 | 0x15 | 0x16 | 0x17 |
| **Row 3** | 0x08 | 0x09 | 0x0A | 0x0B | 0x0C | 0x0D | 0x0E | 0x0F |
| **Row 4 (bottom)** | 0x00 | 0x01 | 0x02 | 0x03 | 0x04 | 0x05 | 0x06 | 0x07 |

Row 0 = Track 0 (MIDI ch 1), Row 4 = Track 4 (MIDI ch 5).  
Column 0 = Step 0, Column 7 = Step 7.

### Buttons

| Button | Note# | Assigned function |
|--------|-------|-------------------|
| Track Button 1–8 | 0x40–0x47 | **Reserved** (future use) |
| Scene Launch 1–5 | 0x52–0x56 | Mute/unmute track (per row) |
| Stop All Clips | 0x51 | **Scale Selector** (hold) |
| Play | 0x5B | **Reserved** (future use) |
| Record | 0x5D | Toggle Drum ↔ Melodic (active track) |
| Shift | 0x62 | Modifier |
| Oct Down / Oct Up | — | No MIDI — hardware keybed transpose only |

### Knobs (absolute, mk1)

| Knob | CC# | Function when (hold step + turn knob) |
|------|-----|---------------------------------------|
| 1 | 0x30 | Note in scale (melodic) / Velocity (drum) |
| 2 | 0x31 | Velocity |
| 3 | 0x32 | Gate length |
| 4 | 0x33 | Probability |
| 5 | 0x34 | Micro-timing nudge |
| 6 | 0x35 | Ratchet / Repeat |
| 7 | 0x36 | Chord interval (melodic only; inactive in drum mode) |
| 8 | 0x37 | MIDI CC (configurable via Bitwig Preferences, default CC 74) |

### LED colors (mk1)

| Velocity | Color | Used for |
|----------|-------|----------|
| 0 | Off | Step inactive |
| 1 | Green | Step active |
| 2 | Green blink | Track muted |
| 3 | Red | Playhead position |
| 4 | Red blink | Reserved |
| 5 | Orange | Active step + playhead (firing now) |

Single-color UI buttons (Scene Launch, Record, etc.) use `0x90 <note> 0x01` (on) / `0x90 <note> 0x00` (off).

---

## 4. Sequencer Engine

### Per-track state

```java
class TrackState {
    boolean[]  steps;          // 8 — step on/off
    int[]      notes;          // 8 — MIDI note per step (0–127)
    int[]      velocities;     // 8 — velocity per step (0–127)
    double[]   gateLengths;    // 8 — gate (0.0–1.0, fraction of 1/16)
    double[]   probabilities;  // 8 — fire probability (0.0–1.0)
    int[]      nudges;         // 8 — micro-timing (-3 to +3 ticks)
    int[]      ratchets;       // 8 — repeat count (1–4)
    int[]      chordIntervals; // 8 — 0=none, 1=3rd, 2=5th, 3=oct
    int[]      ccValues;       // 8 — CC value for knob 8 (0–127)
    int        patternLength;  // 1–8
    boolean    muted;
    boolean    melodicMode;    // false=drum, true=melodic
    int        baseNote;       // drum mode fixed note (0–127)
    int        currentStep;    // playhead position for this track
    int        midiChannel;    // 1–5
}
```

### Tick scheduling

```
stepMs = (60000.0 / BPM) / 4          // duration of 1/16 note in ms
host.scheduleTask(this::tick, stepMs)  // recursive self-scheduling
```

Per tick:
1. For each track: advance `currentStep = (currentStep + 1) % patternLength` — polyrhythm is natural.
2. If step is `on` and `random() < probability` → fire note.
3. If `ratchet > 1` → subdivide: fire N evenly-spaced note-on/off within the step window.
4. Apply nudge → additional `scheduleTask` delay before note-on.
5. `noteInput[track].sendRawMidiEvent(0x90, note, velocity)` → note-on.
6. After `gateLength * stepMs` ms → `sendRawMidiEvent(0x80, note, 0)` → note-off.

### Transport sync

```java
transport.isPlaying().addValueObserver(playing -> {
    if (playing) {
        resetAllStepCounters(); // all tracks start from step 0
        startTick();
    } else {
        stopTick();
        sendAllNotesOff(); // CC 123 on all 5 channels
    }
});
```

### Polyrhythm

Each track advances its own `currentStep` independently. Pattern lengths from 1 to 8
can differ freely between tracks (e.g., Track 0 = 3 steps, Track 1 = 8 steps, Track 2 = 5 steps).
Reset to step 0 only on transport stop+play.

---

## 5. Mode System

### Per-track mode

Each track has its own `melodicMode` boolean (default: `false` = drum).  
The **Record button** (0x5D) toggles the mode of the **currently active track** only.

**Record LED:**
- Off → active track is in Drum mode
- Red (on) → active track is in Melodic mode

### Drum mode

- Each step fires the track's `baseNote` (unless overridden per step).
- Knob 1 = velocity for held step.
- Knob 7 (chord interval) = inactive.
- Set `baseNote`: hold Shift + Scene Launch of track + press keyboard key.

### Melodic mode

- Each step has its own note, selected from the active scale via knob 1.
- Knob 1 = note in scale (mapped from absolute knob value 0–127 → pitch via `ScaleManager`).
- Knob 7 = chord interval (adds a second note above at the selected interval).
- Steps show their individual notes; root note and scale are global.

---

## 6. Scale System

### Scales (8)

| Index | Pad (row 0) | Name | Intervals |
|-------|-------------|------|-----------|
| 0 | 0x20 | Cromatica | 0,1,2,3,4,5,6,7,8,9,10,11 |
| 1 | 0x21 | Maggiore | 0,2,4,5,7,9,11 |
| 2 | 0x22 | Minore Naturale | 0,2,3,5,7,8,10 |
| 3 | 0x23 | Dorian | 0,2,3,5,7,9,10 |
| 4 | 0x24 | Mixolydian | 0,2,4,5,7,9,10 |
| 5 | 0x25 | Pentatonica Maggiore | 0,2,4,7,9 |
| 6 | 0x26 | Pentatonica Minore | 0,3,5,7,10 |
| 7 | 0x27 | Blues | 0,3,5,6,7,10 |

### Root note

Set via **Shift + keyboard key** → root = MIDI note % 12.

### Knob → pitch mapping

```java
static final int BASE_OCTAVE  = 3;  // lowest octave in knob range (C3)
static final int NUM_OCTAVES  = 3;  // knob spans 3 octaves (C3–B5)

int getPitch(int knobValue /* 0-127 */) {
    int[] scale = SCALES[scaleIndex];
    int totalDegrees = scale.length * NUM_OCTAVES;
    int degree = (knobValue * totalDegrees) / 128;
    int oct = degree / scale.length;
    return (BASE_OCTAVE + oct) * 12 + rootNote + scale[degree % scale.length];
}
```

### Scale View (hold Stop All Clips)

While Stop All Clips is held:
- Row 0 pads light up: 8 pads = 8 scales.
- Active scale: green blink.
- Other scales: green solid.
- Press a pad → select that scale.
- Release Stop All Clips → return to sequencer view.
- All other inputs blocked while in Scale View.

---

## 7. Input Handling

### Input state

```java
class InputState {
    boolean shiftHeld;
    boolean stopAllClipsHeld;
    int     heldStepNote;          // -1 = none
    int     heldStepTrack;         // -1 = none
    int     heldSceneLaunch;       // -1 = none
    long    sceneLaunchPressTime;
    int     activeTrack;           // 0–4
}
```

### Full gesture map

| Gesture | Action |
|---------|--------|
| Pad press/release | Toggle step on/off |
| Pad hold + Knob N | Set parameter N for held step |
| Pad hold + keyboard key (melodic) | Set note for that specific step |
| Pad hold + keyboard key (drum) | Set note override for that specific step only (does not change track baseNote) |
| Shift + Scene Launch N hold + keyboard key | Set baseNote for drum track N (default note for all steps without override) |
| Shift + Pad | Set pattern length of row = column index + 1 |
| Scene Launch tap | Mute/unmute track |
| Shift + Scene Launch tap | Select active track |
| Shift + hold Scene Launch A + tap Scene Launch B | Copy pattern from track A to B (full copy: steps + all params) |
| Shift + Scene Launch double-tap (< 400ms) | Clear pattern (all steps off, params reset) |
| Stop All Clips hold | Enter Scale View |
| Stop All Clips hold + Row 0 pad tap | Select scale |
| Shift + keyboard key | Set global root note |
| Record press | Toggle Drum ↔ Melodic on active track |
| Keyboard (no hold) | Play live on active track MIDI channel |
| Play, Track Buttons 1–8 | Reserved — no action, documented |
| Oct Down / Oct Up | Hardware only — no MIDI |

### Gesture priority (conflict resolution)

1. Stop All Clips held → Scale View (blocks all other input)
2. Shift held + input → Shift gesture
3. Step pad held → step edit mode
4. Scene Launch hold context → copy pattern
5. Default → toggle step / mute / live keyboard

### Knob value mapping (absolute mk1)

| Knob | Parameter | Mapping from 0–127 |
|------|-----------|---------------------|
| 1 (melodic) | Note in scale | `ScaleManager.getPitch(value)` |
| 1 (drum) | Velocity | Direct (0–127) |
| 2 | Velocity | Direct (0–127) |
| 3 | Gate length | `value / 127.0` → 0.0–1.0 |
| 4 | Probability | `value / 127.0` → 0.0–1.0 |
| 5 | Nudge | `(value - 64) / 21` → -3 to +3 ticks |
| 6 | Ratchet | `(value / 32) + 1` → 1–4 |
| 7 | Chord interval | `value / 32` → 0–3 (none/3rd/5th/oct) |
| 8 | MIDI CC | Direct (0–127) |

---

## 8. LED Feedback

### Sequencer view (normal)

| Condition | LED color |
|-----------|-----------|
| Step off | Off (0) |
| Step on, not playhead | Green (1) |
| Playhead on empty step | Red (3) |
| Playhead on active step | Orange (5) — firing now |
| Track muted (all pads in row) | Green blink (2) |

### Button LEDs

| Button | LED state |
|--------|-----------|
| Record | Off = drum mode, Red on = melodic mode (active track) |
| Scene Launch N | Green on = track active; off = track muted |
| Active track Scene Launch | Green blink (selected) |

### Scale View (Stop All Clips held)

| Pad | LED state |
|-----|-----------|
| Active scale pad | Green blink |
| Other scale pads | Green solid |
| All other pads | Off |

### Flush strategy

`LedManager` maintains a shadow state of all 40 pad LEDs + button LEDs.  
On any state change, marks dirty cells and calls `host.requestFlush()`.  
In `flush()`, only sends `midiOut.sendMidi()` for changed LEDs (no full repaint every tick).

---

## 9. Persistence

### Per-project (DocumentState)

Entire sequencer state serialized as JSON into a single `StringSetting`:

```java
host.getDocumentState().getStringSetting(
    "Sequencer State", "Sequencer", 65535, ""
);
```

Saved on every user interaction that changes state.  
Restored in `init()` after all NoteInputs and Transport observers are registered.

JSON schema:
```json
{
  "scaleIndex": 0,
  "rootNote": 0,
  "activeTrack": 0,
  "tracks": [
    {
      "patternLength": 8,
      "melodicMode": false,
      "baseNote": 36,
      "muted": false,
      "steps":          [true, false, ...],
      "notes":          [60, 60, ...],
      "velocities":     [100, 80, ...],
      "gateLengths":    [0.5, ...],
      "probabilities":  [1.0, ...],
      "nudges":         [0, ...],
      "ratchets":       [1, ...],
      "chordIntervals": [0, ...],
      "ccValues":       [64, ...]
    }
    // × 5 tracks
  ]
}
```

### Global (Preferences)

```java
host.getPreferences().getNumberSetting(
    "CC Number (Knob 8)", "Sequencer", 0, 127, 1, "", 74
);
```

---

## 10. MIDI Routing Setup

The extension creates 5 named NoteInputs on `getMidiInPort(0)`:

```java
NoteInput track0 = midiIn.createNoteInput("APC Seq Track 1", "?0????");
NoteInput track1 = midiIn.createNoteInput("APC Seq Track 2", "?1????");
// ... up to track4 on channel 5
```

Each NoteInput filters its own MIDI channel. Notes are injected via `sendRawMidiEvent()`.

**User setup in Bitwig (documented in README.md):**
1. Create 5 Instrument tracks in Bitwig.
2. For each track, set MIDI Input to `APC Key 25 Sequencer → APC Seq Track N`.
3. Load an instrument on each track.
4. Press Play in Bitwig — the sequencer starts automatically.

---

## 11. Out of Scope (MVP)

The following are documented as reserved for future implementation:

- Play / Stop buttons on APC (transport control from hardware)
- Track Buttons 1–8 (0x40–0x47)
- Oct Down / Oct Up (no MIDI — hardware only)
- Pattern bank switching (patterns > 8 steps)
- Per-track CC assignment (knob 8 is global)
- Per-track scale (scale is global)
- MIDI Clock output to sync external devices
