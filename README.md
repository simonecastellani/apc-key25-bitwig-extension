# APC Key 25 Polyrhythmic Sequencer — Bitwig Extension

A Bitwig Studio 6 Java Extension that turns the **Akai APC Key 25 mk1** into a
standalone 5-track polyrhythmic step sequencer + beat machine.

---

## Features

- **5 independent tracks × 8 steps** — each track sends MIDI notes to its own
  Bitwig instrument track via dedicated NoteInputs (MIDI channels 1–5)
- **Polyrhythm** — each track has its own pattern length (1–8 steps), advancing
  independently on every 1/16 tick
- **Drum mode** (default) and **Melodic mode** per track — toggled by the Record button
- **8 per-step parameters** via hold-step + turn knob:
  - Knob 1: Note in scale (melodic) / Velocity (drum)
  - Knob 2: Velocity
  - Knob 3: Gate length
  - Knob 4: Probability
  - Knob 5: Micro-timing nudge
  - Knob 6: Ratchet / Repeat (1–4)
  - Knob 7: Chord interval (melodic only — none / maj 3rd / 5th / octave)
  - Knob 8: MIDI CC output (configurable in Bitwig Preferences, default CC 74)
- **8 scales**: Chromatic, Major, Natural Minor, Dorian, Mixolydian, Major Pentatonic,
  Minor Pentatonic, Blues
- **Pattern persistence** — state saved/restored per Bitwig project via DocumentState

---

## Hardware

APC Key 25 **mk1** only. Key differences from mk2:
- Knobs are **absolute** (0–127), not relative
- Pad LEDs are **3 colors** (green / red / orange), not RGB

---

## Controls

### Pads (5×8 grid)

| Row | Track | MIDI channel |
|-----|-------|-------------|
| 0 (top) | Track 1 | Ch 1 |
| 1 | Track 2 | Ch 2 |
| 2 | Track 3 | Ch 3 |
| 3 | Track 4 | Ch 4 |
| 4 (bottom) | Track 5 | Ch 5 |

- **Tap pad** → toggle step on/off
- **Hold pad + turn knob** → edit step parameter (no toggle on release)

### LED colors

| Color | Meaning |
|-------|---------|
| Off | Step inactive |
| Green solid | Step active |
| Green blink | Track muted |
| Red solid | Playhead position |
| Orange | Step active + playhead (firing now) |

### Buttons

| Button | Function |
|--------|----------|
| **Record** | Toggle Drum ↔ Melodic mode for the active track |
| **Scene Launch 1–5** | Mute/unmute track |
| **Shift + Scene Launch** | Select active track |
| **Stop All Clips (hold)** | Enter scale selector (row 0 pads = 8 scales) |
| **Shift + pad (row)** | Set pattern length for that track (col+1 steps) |
| **Shift + keyboard key** | Set global root note |
| **Shift + Scene A (hold) + Scene B** | Copy pattern from track A to track B |

---

## Build & Install

Requirements: Java 17, Maven 3.8+, Bitwig Studio 6.

```bash
# Set up environment (paths below are for the local Java/Maven installs)
export JAVA_HOME=/tmp/opencode/jdk-17.0.2
export M2_HOME=/tmp/opencode/apache-maven-3.8.8
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH

cd apc-key25-sequencer
mvn package
```

`mvn package` compiles, runs all 59 tests, creates a fat JAR (Gson bundled,
Bitwig API excluded), and copies `ApcKey25Sequencer.bwextension` to:

```
~/Documents/Bitwig Studio/Extensions/
```

Then in Bitwig: **Settings → Controllers → Add controller → APC Key 25 Sequencer**.

---

## Project Structure

```
apc-key25-sequencer/
├── pom.xml
└── src/
    ├── main/java/com/apcsequencer/
    │   ├── ApcKey25SequencerExtensionDefinition.java
    │   ├── ApcKey25SequencerExtension.java   # Bitwig lifecycle: init/flush/exit
    │   ├── Sequencer.java                    # Tick engine, polyrhythm, note scheduling
    │   ├── ScaleManager.java                 # 8 scales, root note, knob→pitch
    │   ├── LedManager.java                   # LED shadow state, dirty flush
    │   ├── InputHandler.java                 # Raw MIDI → logical commands
    │   ├── PersistenceManager.java           # Gson JSON ↔ DocumentState
    │   ├── TrackState.java                   # Per-track sequencer state
    │   ├── InputState.java                   # Transient gesture state
    │   └── Config.java                       # MIDI constants
    └── test/java/com/apcsequencer/
        ├── TrackStateTest.java        (4 tests)
        ├── ScaleManagerTest.java      (10 tests)
        ├── PersistenceManagerTest.java (5 tests)
        ├── SequencerTest.java         (9 tests)
        ├── LedManagerTest.java        (10 tests)
        └── InputHandlerTest.java      (21 tests)
```

---

## Technical Notes

- **Scheduling**: `host.scheduleTask()` recursive 1/16 tick;
  `stepMs = 60000 / BPM / 4`; BPM from `transport.tempo().addRawValueObserver()`
- **NoteInput strategy**: 5 NoteInputs with no channel masks; all MIDI injected
  programmatically via `sendRawMidiEvent()`; keyboard MIDI re-routed to active track
- **Bitwig API quirks**: `getHost()` returns `com.bitwig.extension.api.Host`
  (base type) — store `ControllerHost` from the constructor parameter instead.
  Value observer lambdas require explicit casts to typed callback sub-interfaces
  (`BooleanValueChangedCallback`, `StringValueChangedCallback`) because the base
  `ValueChangedCallback` has no abstract methods.
