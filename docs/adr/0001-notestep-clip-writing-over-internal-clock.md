# ADR 0001: Use NoteStep clip-writing instead of an internal clock

**Date:** 2026-05-15  
**Status:** Accepted

---

## Context

A Bitwig controller extension can drive notes in two ways:

1. **Internal clock** — the extension subscribes to a transport beat callback, maintains its own step state, and fires `NoteInput.sendRawMidiEvent()` (or equivalent) in real time.
2. **NoteStep clip-writing** — the extension uses `CursorClip.getStep()` to obtain `NoteStep` handles and calls setter methods (`setVelocity`, `setChance`, `setRepeatCount`, …) that write note parameters directly into a Bitwig clip. Bitwig's clip engine handles all playback timing.

The APC Key 25 sequencer is a *parameter editor* for a 5-track polyrhythmic step sequencer. Users edit steps; Bitwig plays them back.

---

## Decision

Use **NoteStep clip-writing exclusively**. The extension never fires MIDI note events directly and never subscribes to a real-time beat clock.

---

## Consequences

### Positive

- **Zero scheduling latency** — all playback timing is handled by Bitwig's audio engine, not JVM code.
- **Free undo/redo** — every `NoteStep` setter is automatically undoable via Bitwig's edit history.
- **Native parameter support** — velocity, gate length, probability (`setChance`), scale degree offset (`setTranspose`), ratchet count (`setRepeatCount`), ratchet decay (`setRepeatVelocityEnd` / `setRepeatVelocityCurve`), step condition (`setOccurrence` + `setRecurrence`), pan, velocity spread, chord voicing (multiple simultaneous notes at same x-position) are all first-class `NoteStep` properties.
- **Clip portability** — sequences live as real Bitwig clips; users can open them in the piano roll, export MIDI, or use them without the controller attached.
- **Simpler extension lifecycle** — no timer threads, no beat-sync state machine, no MIDI buffer management.

### Negative / Trade-offs

- **Bounded by NoteStep API** — any parameter without a `NoteStep` setter cannot be implemented. Onset offset (micro-timing) was dropped for this reason; it was replaced by Chord Voicing (knob 5).
- **Chord Voicing complexity** — chords require writing multiple `NoteStep` handles at the same x-position with different y (pitch) values. Removing a voicing requires explicitly clearing the extra steps, which demands careful bookkeeping in extension state.
- **Clip length must be managed explicitly** — the extension must set `CursorClip.setStepSize()` and `CursorClip.getLoopLength()` whenever Step Duration or Step Count changes, because Bitwig does not auto-resize clips.
- **No live generative variation** — parameters like Euclidean Distribution, Pattern Rotation, and Phase Offset are computed once and written statically; they do not vary dynamically during playback unless the user rotates a knob.

---

## Alternatives considered

| Alternative | Reason rejected |
|-------------|-----------------|
| Internal clock (beat callback + raw MIDI) | Introduces scheduling jitter, bypasses Bitwig undo, loses clip portability, and duplicates playback logic that Bitwig's engine already handles correctly. |
| Hybrid (internal clock for live-record capture, NoteStep for stored steps) | Hybrid boundaries are hard to maintain; the Live Record feature can be implemented purely via NoteStep writes on note-off from the keyboard, avoiding any need for a beat callback. |
