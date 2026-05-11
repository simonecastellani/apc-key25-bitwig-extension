# S7: Multi-Track Selection — Design Spec

**Date:** 2026-05-12  
**Status:** Approved

---

## Context

Stories S1–S6 deliver a single-track, clip-based step sequencer. S7 extends this to 5
independent tracks so the APC Key 25's 5 Scene Launch buttons each map to one Bitwig
track. All 5 clips play simultaneously (polyrhythm). The Scene Launch buttons only
control which track's pattern is shown and edited on the 5×8 pad grid.

---

## Decisions

| Question | Decision |
|---|---|
| Do all 5 tracks play simultaneously? | Yes — full polyrhythm; selection is edit-only |
| Track-to-Bitwig-track mapping | Fixed: slot 0 → Bitwig track 1, …, slot 4 → track 5 |
| Selected track LED | GREEN (vel 1); others OFF (vel 0) |
| Architecture | Option A: 5 `Sequencer` instances, routing in `Extension` |

---

## Architecture

### `ApcKey25SequencerExtension` changes

**`init()`:**
1. Create `TrackBank(5, 0, 0)` — surfaces Bitwig tracks 1–5.
2. Create `Sequencer[5]`; each entry receives:
   - `track.createLauncherCursorClip(8, 1)` (its own `PinnableCursorClip`)
   - Its own `LedOutput` instance (wraps Port 0 note-on for pads)
3. `int selectedTrack = 0`
4. Send initial Scene Launch LEDs: note 0x52 vel 1 (green), notes 0x53–0x56 vel 0 (off).

**`dispatchMidi()` routing (Port 1, ch 0):**

| Note range | Action |
|---|---|
| 0x52–0x56 (Scene Launch 1–5) | `sceneLaunchPressed(note - 0x52)` |
| 0x00–0x27 (pads) | `sequencers[selectedTrack].padTapped(note)` |

### `sceneLaunchPressed(int row)`

```
selectedTrack = row
for i in 0..4:
    send note-on(Port 0, note=0x52+i, vel=(i==row ? 1 : 0))
sequencers[selectedTrack].refreshLeds()
```

`refreshLeds()` is a new public method on `Sequencer` that re-emits pad LED state
(current step pattern + playhead) without changing any sequencer state.

### `Sequencer` — no structural changes

All 43 existing tests remain valid. `refreshLeds()` is the only addition: it iterates
`enabled[]` and `currentStep`, calling `ledOutput.send()` for each pad exactly as
`updateLeds()` does today (may reuse or extract a shared helper).

---

## LED State Summary

| Element | Condition | Color / Velocity |
|---|---|---|
| Scene Launch button `selectedTrack` | always | GREEN (vel 1) |
| Scene Launch buttons (others) | always | OFF (vel 0) |
| Pad at `currentStep` | playhead active | RED (vel 3) |
| Pad enabled, not playhead | step on | GREEN (vel 1) |
| Pad disabled, not playhead | step off | OFF (vel 0) |

---

## Test Plan

New tests cover Extension-level routing behaviour with mock clips and LED sinks:

1. **Default routing**: `padTapped` with `selectedTrack=0` calls `sequencers[0]`, not others.
2. **Track switch routing**: `sceneLaunchPressed(2)` → subsequent `padTapped` calls `sequencers[2]`.
3. **Scene Launch LEDs on switch**: after `sceneLaunchPressed(2)`, note 0x54 = vel 1; notes 0x52, 0x53, 0x55, 0x56 = vel 0.
4. **LED refresh on switch**: `sceneLaunchPressed(row)` calls `sequencers[row].refreshLeds()`.
5. **Idempotent**: `sceneLaunchPressed` with the already-selected track is safe (no crash, LEDs correct).
6. **Init LEDs**: on extension init, Scene Launch 0 (0x52) = green, rest = off.

`Sequencer` unit tests:
7. `refreshLeds()` emits correct LED state for the current `enabled[]` + `currentStep` without modifying state.

---

## Out of Scope (this story)

- Per-track step length / polyrhythm parameters (S8+)
- Track-specific note pitch or velocity (S9+)
- Persistence of multi-track state (S10)
- Any UI for re-mapping tracks to non-fixed Bitwig positions
