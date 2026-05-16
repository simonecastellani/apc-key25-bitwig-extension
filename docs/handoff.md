# Handoff — APC Key 25 Sequencer (Issue #4 — Tracer Bullet)

**Date**: 2026-05-16  
**Repo**: `simonecastellani/apc-key25-bitwig-extension`  
**Branch**: `main`  
**Build**: `BUILD SUCCESS`, 61 tests pass  
**Installed to**: `~/Bitwig Studio/Extensions/ApcKey25Sequencer.bwextension`

---

## What is done

All domain-model and MIDI-decoding layers are complete and fully tested:

| Slice | Files | Tests |
|-------|-------|-------|
| ScaleEngine | `Mode`, `ChordVoicing`, `GlobalScale`, `ScaleEngine` | 10 |
| SequencerState | `StepState`, `TrackState`, `SequencerState`, `StateDiff`, `StepCondition`, `StepDuration`, `LoopMultiplier`, `EuclideanBitmask` | 26 |
| MidiDecoder | `MidiDecoder`, `ButtonId`, `PadEvent`, `ButtonEvent`, `KnobEvent`, `KeyboardNoteEvent` | 14 |
| InputModifierTracker | `Gesture` (sealed), `StepToggleGesture`, `UndoGesture`, `RedoGesture`, `InputModifierTracker` | 9 |
| LedRenderer | `LedRenderer` (static `render(SequencerState, int[]) → int[5][8]`) | 12 |

Integration glue (untested):
- `ClipWriter` / `BitwigClipWriter` — wraps `PinnableCursorClip[]`; calls `setStep`/`clearStep`
- `GestureDispatcher` — dispatches `Gesture` → `ClipWriter` + `LedRenderer` + `MidiOut`
- `MidiRouter` — MIDI callback → `MidiDecoder` → `InputModifierTracker` → `GestureDispatcher`
- `OverlayController` / `OverlayMode` — placeholder for future overlays

Extension discovery: `META-INF/services/com.bitwig.extension.ExtensionDefinition` present.

The pad LED toggling and gesture dispatch chain work: the console shows  
`[APC] stepToggle track=0 step=0 changes=1` and  
`[APC] writeStep track=0 step=0 active=true pitch=60`  
on every pad tap.

---

## Current bug — clip writes are silently dropped

**Symptom**: Tapping a pad produces the correct console output and LED update, but no note ever appears in Bitwig's piano roll.

**Root cause (unresolved)**: `BitwigClipWriter.writeStep()` calls `clip.setStep(0, step, pitch, velocity, duration)` successfully (no exception), but the note is not persisted in the clip. The most likely hypotheses, in priority order:

### Hypothesis 1 — Multiple cursors all land on track 0 (most likely)
We create 5 independent `CursorTrack` instances and navigate each to a different position with `selectFirst()` + `selectNext()×t`. These navigation calls are **asynchronous** — by the time `createLauncherCursorClip()` executes, all 5 cursors may still be pointing at track 0. All 5 `PinnableCursorClip` instances would then write to the same clip on track 0, and tracks 1–4 would never be touched.

**Suggested fix**: Use a single `CursorTrack` + `TrackBank` approach as done in `~/bitwig-beatstep-console` (the working reference extension). Create one `CursorTrack` with `shouldFollowSelection=true` and a `TrackBank` following it. To address multiple tracks, use a `TrackBank` of 5 items and get one `CursorClip` per item using each track's own clip launcher.

Alternatively: confirm which track each cursor resolved to by checking the console for `[APC] Track N cursor → "<name>"` messages. If all 5 print the same track name, this hypothesis is confirmed.

### Hypothesis 2 — `clip.exists()` observer never fires
If `createNewLauncherClip(0, 4)` is called but the `PinnableCursorClip.exists()` observer never fires `true`, then `setStepSize()` is never called. Bitwig may silently reject `setStep()` calls on an unconfigured clip.

**Debug**: Add a log in `BitwigClipWriter.writeStep()` to also print `clips[track].exists().get()` at write time. If it logs `false`, the cursor clip is still phantom.

### Hypothesis 3 — `selectSlot(0)` is called before the clip is created
`cursor.selectSlot(0)` is called in `init()`, but at that point slot 0 may be empty. When `createNewLauncherClip` runs later (asynchronously from the `hasContent` observer), the cursor slot is not automatically refreshed. The clip exists in Bitwig but the cursor may be desynced.

**Suggested fix**: Call `cursor.selectSlot(0)` inside the `hasContent` observer, after `createNewLauncherClip` returns (or in the `exists()` observer after it fires `true`).

---

## Key API facts (hard-won)

- `createLauncherCursorClip()` is on `CursorTrack`, NOT `Track`
- `selectChannel(trackBank.getItemAt(t))` does NOT work — all cursors end up with `name = ""`
- `selectFirst()` + `selectNext()×t` was the last attempted approach; also unconfirmed to work
- `clip.setStep(channel=0, x=step, y=pitch, velocity, duration)` — y is absolute MIDI note 0–127
- `gridHeight=128` means y=60 (middle C) is valid
- `clip.exists()` fires `true` only when the cursor points to a real clip (not a phantom proxy)
- `setStepSize()` is a no-op on a phantom proxy — must be called from inside `exists()` observer
- Commit `b6f926064a4b98806c49cd7c736fef7317deadf6` was the last known working multi-track version; it used `clip.exists()` → `syncPatternToClip()`, `gridHeight=1`, `y=0`, `scrollToKey(60)`, `STEP_SIZE_BEATS=0.5`

---

## Suggested next-session workflow

1. **diagnose** — reproduce the exact failure, instrument `BitwigClipWriter` with `exists().get()` logging, confirm hypothesis 1 or 2
2. **tdd** — once root cause is confirmed, fix the cursor navigation / clip wiring and add integration-style tests around `BitwigClipWriter`
3. Close issue #4 on GitHub once a pad tap reliably places a note in the correct track's piano roll

**Reference files**:
- `apc-key25-sequencer/src/main/java/com/apcsequencer/ApcKey25SequencerExtension.java` — cursor init loop is the hot spot (lines 62–113)
- `apc-key25-sequencer/src/main/java/com/apcsequencer/BitwigClipWriter.java` — clip write path
- `~/bitwig-beatstep-console/` — working reference extension (single CursorTrack + TrackBank pattern)
- `docs/bitwig-api-v25.txt` — full API; key lines: CursorTrack ~9577, createLauncherCursorClip ~9664, selectSlot ~3883, Track.createNewLauncherClip ~3855, PinnableCursorClip ~8832
- GitHub issues #4 (tracer bullet), #5–#19 (remaining slices)

---

## Build instructions

```bash
export JAVA_HOME=/tmp/opencode/jdk-17.0.2
export M2_HOME=/tmp/opencode/apache-maven-3.8.8
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH

cd apc-key25-sequencer
mvn package
```

61 tests, `BUILD SUCCESS`, installs to `~/Bitwig Studio/Extensions/ApcKey25Sequencer.bwextension`.
