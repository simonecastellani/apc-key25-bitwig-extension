# S7: Multi-Track Selection — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 5-track selection via Scene Launch buttons so all 5 clips play simultaneously (polyrhythm) while pads show/edit the pattern of the currently selected track.

**Architecture:** `TrackRouter` (new testable POJO) holds 5 `Sequencer` instances and routes `padTapped` / `sceneLaunchPressed` to the right one. `ApcKey25SequencerExtension` creates 5 independent `CursorTrack` objects (navigated to fixed positions 0–4 with `shouldSelectHierarchy=false`), builds one `Sequencer` per track by injecting a `PinnableCursorClip`, wires a `TrackRouter`, and delegates all MIDI dispatch through it. `Sequencer` gains one new method (`refreshLeds`) and one new runtime constructor; its 43 existing tests are untouched.

**Tech Stack:** Java 17, Bitwig Extension API v19 (CursorTrack, PinnableCursorClip), JUnit 5, Maven 3.8+

---

## File Map

| File | Change |
|---|---|
| `src/main/java/com/apcsequencer/Sequencer.java` | Add `refreshLeds()`, add `Sequencer(PinnableCursorClip, LedOutput)` runtime constructor, guard `host.println()` in `syncPatternToClip()` |
| `src/main/java/com/apcsequencer/TrackRouter.java` | **Create** — routing logic, `SceneLedOutput` interface, scene LED management |
| `src/main/java/com/apcsequencer/ApcKey25SequencerExtension.java` | Replace single `Sequencer` with 5 cursor tracks + 5 sequencers + `TrackRouter`; move `NoteInput` setup here; add scene launch routing |
| `src/test/java/com/apcsequencer/SequencerTest.java` | Add 3 `refreshLeds()` tests |
| `src/test/java/com/apcsequencer/TrackRouterTest.java` | **Create** — 9 routing + LED tests |

---

## Build command (run from `apc-key25-sequencer/`)

```bash
export JAVA_HOME=/tmp/opencode/jdk-17.0.2
export M2_HOME=/tmp/opencode/apache-maven-3.8.8
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH
```

- Run all tests: `mvn test`
- Build + deploy: `mvn package`
- Single class: `mvn test -Dtest=SequencerTest`

---

## Task 1: Add `Sequencer.refreshLeds()` (TDD)

**Files:**
- Modify: `src/test/java/com/apcsequencer/SequencerTest.java`
- Modify: `src/main/java/com/apcsequencer/Sequencer.java`

- [ ] **Step 1.1: Write 3 failing tests**

Append this block to `SequencerTest.java` before the final `}`:

```java
    // ── refreshLeds ───────────────────────────────────────────────────────

    @Test
    void refreshLeds_allOffWhenPatternEmpty() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);
        leds.clear();

        seq.refreshLeds();

        assertEquals(8, leds.size(), "one LED event per step");
        for (int i = 0; i < 8; i++) {
            assertEquals(i,               leds.get(i)[0], "step " + i + " note");
            assertEquals(Sequencer.LED_OFF, leds.get(i)[1], "step " + i + " is off");
        }
    }

    @Test
    void refreshLeds_greenForEnabledSteps() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);
        seq.padTapped(0x02); // enable step 2
        seq.padTapped(0x05); // enable step 5
        leds.clear();

        seq.refreshLeds();

        assertEquals(8, leds.size());
        assertEquals(Sequencer.LED_OFF,   leds.get(0)[1], "step 0 off");
        assertEquals(Sequencer.LED_GREEN, leds.get(2)[1], "step 2 green");
        assertEquals(Sequencer.LED_GREEN, leds.get(5)[1], "step 5 green");
        assertEquals(Sequencer.LED_OFF,   leds.get(7)[1], "step 7 off");
    }

    @Test
    void refreshLeds_redForPlayheadGreenForOtherEnabled() {
        List<int[]> leds = ledLog();
        Sequencer seq = seqWith(noteLog(), leds);
        seq.padTapped(0x03); // enable step 3
        seq.padTapped(0x05); // enable step 5
        seq.tick(true, 1.5); // 1.5 beats × 2 steps/beat = step 3 → playhead
        leds.clear();

        seq.refreshLeds();

        assertEquals(8, leds.size());
        assertEquals(Sequencer.LED_RED,   leds.get(3)[1], "step 3 is playhead → red");
        assertEquals(Sequencer.LED_GREEN, leds.get(5)[1], "step 5 enabled, not playhead → green");
        assertEquals(Sequencer.LED_OFF,   leds.get(0)[1], "step 0 disabled → off");
    }
```

- [ ] **Step 1.2: Run tests — expect FAIL**

```bash
mvn test -Dtest=SequencerTest -pl apc-key25-sequencer
```

Expected output contains: `cannot find symbol` or similar compile error for `refreshLeds()`.

- [ ] **Step 1.3: Implement `refreshLeds()` in `Sequencer.java`**

Add this method after `setPlayhead()` (around line 258):

```java
    /**
     * Re-emit the current LED state for all 8 pad steps without changing sequencer
     * state. Called by {@link TrackRouter} when the user switches to this track.
     */
    public void refreshLeds() {
        for (int s = 0; s < PATTERN_LENGTH; s++) {
            int color = (s == currentStep) ? LED_RED
                      : (enabled[s]        ? LED_GREEN : LED_OFF);
            ledOutput.setLed(bottomRowNote(s), color);
        }
    }
```

- [ ] **Step 1.4: Run tests — expect PASS**

```bash
mvn test -Dtest=SequencerTest -pl apc-key25-sequencer
```

Expected: **BUILD SUCCESS**, 46 tests pass (43 existing + 3 new).

- [ ] **Step 1.5: Commit**

```bash
cd apc-key25-sequencer && git add src/test/java/com/apcsequencer/SequencerTest.java \
  src/main/java/com/apcsequencer/Sequencer.java
git commit -m "feat(sequencer): add refreshLeds() for track switching"
```

---

## Task 2: Add multi-track runtime constructor to `Sequencer`

**Files:**
- Modify: `src/main/java/com/apcsequencer/Sequencer.java`

- [ ] **Step 2.1: Add the new constructor**

Insert this constructor after the existing `public Sequencer(ControllerHost host)` block (after line 154, before the `syncPatternToClip` comment):

```java
    /**
     * Multi-track runtime constructor. The caller (Extension) is responsible for
     * blocking NoteInput passthrough on Port 1 before creating sequencers.
     *
     * @param clip      a {@link PinnableCursorClip} from a CursorTrack at the desired position
     * @param ledOutput LED sink for pad row LEDs (Port 0 note-on)
     */
    public Sequencer(PinnableCursorClip clip, LedOutput ledOutput) {
        this.host       = null;
        this.noteOutput = null;
        this.ledOutput  = ledOutput;
        this.clip       = clip;
        clip.playingStep().addValueObserver(
                (IntegerValueChangedCallback) this::setPlayhead, -1);
        clip.exists().addValueObserver(
                (BooleanValueChangedCallback) exists -> {
                    if (exists) syncPatternToClip();
                });
        this.clipOutput = new ClipOutput() {
            public void setStep(int step) {
                clip.setStep(0, step, 0, FIXED_VELOCITY, GATE_DURATION);
            }
            public void clearStep(int step) {
                clip.clearStep(0, step, 0);
            }
        };
    }
```

- [ ] **Step 2.2: Guard `host.println()` in `syncPatternToClip()`**

In `syncPatternToClip()` (around line 175), replace:

```java
        host.println("Pattern synced to clip (" + PATTERN_LENGTH + " steps)");
```

with:

```java
        if (host != null) host.println("Pattern synced to clip (" + PATTERN_LENGTH + " steps)");
```

- [ ] **Step 2.3: Run all tests — expect PASS**

```bash
mvn test -pl apc-key25-sequencer
```

Expected: **BUILD SUCCESS**, 46 tests pass. (The new constructor uses Bitwig API types and is tested indirectly at runtime; no new unit tests needed here.)

- [ ] **Step 2.4: Commit**

```bash
git add src/main/java/com/apcsequencer/Sequencer.java
git commit -m "feat(sequencer): add multi-track runtime constructor (PinnableCursorClip + LedOutput)"
```

---

## Task 3: Create `TrackRouter` with tests (TDD)

**Files:**
- Create: `src/test/java/com/apcsequencer/TrackRouterTest.java`
- Create: `src/main/java/com/apcsequencer/TrackRouter.java`

- [ ] **Step 3.1: Write the test file**

Create `src/test/java/com/apcsequencer/TrackRouterTest.java`:

```java
package com.apcsequencer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackRouterTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    static List<int[]>   ledLog()       { return new ArrayList<>(); }
    static List<Integer> clipLog()      { return new ArrayList<>(); }
    static List<int[]>   sceneLedLog()  { return new ArrayList<>(); }

    static Sequencer.LedOutput asLed(List<int[]> log) {
        return (note, color) -> log.add(new int[]{note, color});
    }

    static Sequencer.ClipOutput asClip(List<Integer> log) {
        return new Sequencer.ClipOutput() {
            public void setStep(int step)   { log.add(step);  }
            public void clearStep(int step) { log.add(~step); }
        };
    }

    static TrackRouter.SceneLedOutput asScene(List<int[]> log) {
        return (row, color) -> log.add(new int[]{row, color});
    }

    /**
     * Test fixture: 5 sequencers each with isolated clip/LED spies + a shared scene spy.
     * Sequencers use the package-private 3-arg test constructor (NoteOutput, LedOutput, ClipOutput).
     */
    static class RouterFixture {
        final List<List<Integer>> clips = new ArrayList<>();
        final List<List<int[]>>   leds  = new ArrayList<>();
        final List<int[]>         scene = sceneLedLog();
        final Sequencer[]         seqs  = new Sequencer[5];
        final TrackRouter         router;

        RouterFixture() {
            for (int i = 0; i < 5; i++) {
                List<Integer> c = clipLog();
                List<int[]>   l = ledLog();
                clips.add(c);
                leds.add(l);
                seqs[i] = new Sequencer((s, d1, d2) -> {}, asLed(l), asClip(c));
            }
            router = new TrackRouter(seqs, asScene(scene));
        }
    }

    // ── Default state ─────────────────────────────────────────────────────

    @Test
    void defaultSelectedTrackIsZero() {
        RouterFixture f = new RouterFixture();
        assertEquals(0, f.router.selectedTrack());
    }

    // ── Pad routing ───────────────────────────────────────────────────────

    @Test
    void padTappedRoutesToTrack0ByDefault() {
        RouterFixture f = new RouterFixture();

        f.router.padTapped(0x00); // enable step 0 on track 0

        assertEquals(1, f.clips.get(0).size(), "track 0 received clip event");
        assertTrue(f.clips.get(1).isEmpty(),   "track 1 received no clip event");
        assertTrue(f.clips.get(2).isEmpty(),   "track 2 received no clip event");
    }

    @Test
    void sceneLaunchSwitchesRouting() {
        RouterFixture f = new RouterFixture();

        f.router.sceneLaunchPressed(2);
        f.router.padTapped(0x00); // should go to track 2 now

        assertTrue(f.clips.get(0).isEmpty(),   "track 0 received no clip event");
        assertEquals(1, f.clips.get(2).size(), "track 2 received clip event");
    }

    @Test
    void subsequentPadTapsGoToNewTrackAfterSwitch() {
        RouterFixture f = new RouterFixture();

        f.router.padTapped(0x00);        // → track 0
        f.router.sceneLaunchPressed(4);
        f.router.padTapped(0x01);        // → track 4

        assertEquals(1, f.clips.get(0).size(), "track 0 has 1 event");
        assertEquals(1, f.clips.get(4).size(), "track 4 has 1 event");
    }

    // ── Scene Launch LEDs ─────────────────────────────────────────────────

    @Test
    void initLeds_track0GreenRestOff() {
        RouterFixture f = new RouterFixture();

        f.router.initLeds();

        assertEquals(5, f.scene.size(), "5 scene LED events");
        assertEquals(0,                  f.scene.get(0)[0], "row 0");
        assertEquals(Sequencer.LED_GREEN, f.scene.get(0)[1], "row 0 = green");
        for (int i = 1; i < 5; i++) {
            assertEquals(i,               f.scene.get(i)[0], "row " + i);
            assertEquals(Sequencer.LED_OFF, f.scene.get(i)[1], "row " + i + " = off");
        }
    }

    @Test
    void sceneLaunchPressed_selectedRowGreenRestOff() {
        RouterFixture f = new RouterFixture();

        f.router.sceneLaunchPressed(3);

        assertEquals(5, f.scene.size(), "5 scene LED events");
        for (int i = 0; i < 5; i++) {
            int expected = (i == 3) ? Sequencer.LED_GREEN : Sequencer.LED_OFF;
            assertEquals(expected, f.scene.get(i)[1], "row " + i + " color");
        }
    }

    @Test
    void sceneLaunchPressed_callsRefreshLedsOnNewTrack() {
        RouterFixture f = new RouterFixture();
        f.seqs[2].padTapped(0x01); // enable step 1 on track 2
        f.leds.get(2).clear();

        f.router.sceneLaunchPressed(2);

        // refreshLeds emits 8 LED events for all 8 steps
        assertEquals(8, f.leds.get(2).size(), "track 2 refreshLeds emitted 8 events");
        assertEquals(Sequencer.LED_GREEN, f.leds.get(2).get(1)[1], "step 1 is green");
        assertEquals(Sequencer.LED_OFF,   f.leds.get(2).get(0)[1], "step 0 is off");
    }

    @Test
    void sceneLaunchPressed_idempotentOnCurrentTrack() {
        RouterFixture f = new RouterFixture();

        f.router.sceneLaunchPressed(0); // already 0
        f.router.sceneLaunchPressed(0); // again — must not throw

        // last 5 scene events: row 0 = green, rest = off
        int from = f.scene.size() - 5;
        assertEquals(Sequencer.LED_GREEN, f.scene.get(from)[1],     "row 0 green");
        for (int i = 1; i < 5; i++) {
            assertEquals(Sequencer.LED_OFF, f.scene.get(from + i)[1], "row " + i + " off");
        }
    }

    @Test
    void sceneLaunchPressed_outOfRangeIsIgnored() {
        RouterFixture f = new RouterFixture();

        assertDoesNotThrow(() -> f.router.sceneLaunchPressed(-1));
        assertDoesNotThrow(() -> f.router.sceneLaunchPressed(5));
        assertEquals(0, f.router.selectedTrack(), "track unchanged after out-of-range");
    }
}
```

- [ ] **Step 3.2: Run tests — expect FAIL**

```bash
mvn test -Dtest=TrackRouterTest -pl apc-key25-sequencer
```

Expected: compile error — `TrackRouter` does not exist.

- [ ] **Step 3.3: Create `TrackRouter.java`**

Create `src/main/java/com/apcsequencer/TrackRouter.java`:

```java
package com.apcsequencer;

/**
 * Routes pad taps and scene launch presses to the appropriate {@link Sequencer}
 * and manages Scene Launch button LEDs.
 *
 * <p>All 5 sequencers run (and play) simultaneously; this class only controls
 * which one's pattern is shown and edited on the pad row.</p>
 */
public class TrackRouter {

    /** Sink for Scene Launch button LEDs (5 buttons, rows 0–4). */
    public interface SceneLedOutput {
        /** Set Scene Launch button {@code row} (0-based) to the given LED color. */
        void setSceneLed(int row, int color);
    }

    private final Sequencer[]    sequencers;
    private final SceneLedOutput sceneLeds;
    private       int            selectedTrack = 0;

    /**
     * @param sequencers  array of exactly 5 sequencers, one per Bitwig track
     * @param sceneLeds   LED sink for the 5 Scene Launch buttons
     */
    public TrackRouter(Sequencer[] sequencers, SceneLedOutput sceneLeds) {
        this.sequencers = sequencers;
        this.sceneLeds  = sceneLeds;
    }

    /**
     * Emit initial Scene Launch LEDs: track 0 green, all others off.
     * Call once from {@code Extension.init()} after all sequencers are ready.
     */
    public void initLeds() {
        for (int i = 0; i < sequencers.length; i++) {
            sceneLeds.setSceneLed(i, i == selectedTrack ? Sequencer.LED_GREEN : Sequencer.LED_OFF);
        }
    }

    /** Route a pad note-on to the currently selected sequencer. */
    public void padTapped(int noteNumber) {
        sequencers[selectedTrack].padTapped(noteNumber);
    }

    /**
     * Switch the active track to {@code row}, update Scene Launch LEDs,
     * and refresh the pad LEDs to show the new track's pattern.
     * Out-of-range values are silently ignored.
     */
    public void sceneLaunchPressed(int row) {
        if (row < 0 || row >= sequencers.length) return;
        selectedTrack = row;
        for (int i = 0; i < sequencers.length; i++) {
            sceneLeds.setSceneLed(i, i == selectedTrack ? Sequencer.LED_GREEN : Sequencer.LED_OFF);
        }
        sequencers[selectedTrack].refreshLeds();
    }

    /** Returns the 0-based index of the currently selected track. */
    public int selectedTrack() {
        return selectedTrack;
    }
}
```

- [ ] **Step 3.4: Run all tests — expect PASS**

```bash
mvn test -pl apc-key25-sequencer
```

Expected: **BUILD SUCCESS**, 55 tests pass (46 + 9 new).

- [ ] **Step 3.5: Commit**

```bash
git add src/main/java/com/apcsequencer/TrackRouter.java \
        src/test/java/com/apcsequencer/TrackRouterTest.java
git commit -m "feat: add TrackRouter for 5-track pad routing and scene LED management"
```

---

## Task 4: Refactor `ApcKey25SequencerExtension` for multi-track

**Files:**
- Modify: `src/main/java/com/apcsequencer/ApcKey25SequencerExtension.java`

- [ ] **Step 4.1: Rewrite the Extension**

Replace the entire contents of `ApcKey25SequencerExtension.java` with:

```java
package com.apcsequencer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.PinnableCursorClip;

import java.util.Arrays;

public class ApcKey25SequencerExtension extends ControllerExtension {

    // APC Key 25 mk1: all input on PORT 1; PORT 0 is LED output only.
    private static final int PORT_ALL_IN  = 1;
    private static final int PORT_LED_OUT = 0;

    // All pad rows: notes 0x00–0x27 (rows 4–0, bottom to top)
    private static final int PAD_MIN = 0x00;
    private static final int PAD_MAX = 0x27;

    // Scene Launch buttons 1–5: channel 0, notes 0x52–0x56
    private static final int SCENE_LAUNCH_MIN = 0x52;
    private static final int SCENE_LAUNCH_MAX = 0x56;

    private static final int NUM_TRACKS = 5;

    private final ControllerHost host;
    private TrackRouter          router;

    // Keep CursorTrack references alive to prevent garbage collection.
    @SuppressWarnings("FieldCanBeLocal")
    private CursorTrack[] cursors;

    protected ApcKey25SequencerExtension(
            ApcKey25SequencerExtensionDefinition definition,
            ControllerHost host) {
        super(definition, host);
        this.host = host;
    }

    @Override
    public void init() {
        MidiIn  allIn  = host.getMidiInPort(PORT_ALL_IN);
        MidiOut ledOut = host.getMidiOutPort(PORT_LED_OUT);

        // Block all hardware notes from passing through to Bitwig instruments (once, shared).
        NoteInput noteInput = allIn.createNoteInput("APC Key 25 Seq");
        noteInput.setKeyTranslationTable(blockAllTable());

        // Shared pad LED output (all sequencers write to the same port 0).
        Sequencer.LedOutput padLeds = (note, color) ->
                ledOut.sendMidi(0x90, note, color);

        // Scene Launch LED output (notes 0x52–0x56 on port 0).
        TrackRouter.SceneLedOutput sceneLeds = (row, color) ->
                ledOut.sendMidi(0x90, SCENE_LAUNCH_MIN + row, color);

        // Create 5 independent cursor tracks, navigated to positions 0–4.
        // shouldSelectHierarchy=false means each cursor ignores the Bitwig UI selection
        // and only moves via our API calls — giving fixed track mapping.
        cursors = new CursorTrack[NUM_TRACKS];
        Sequencer[] sequencers = new Sequencer[NUM_TRACKS];
        for (int i = 0; i < NUM_TRACKS; i++) {
            CursorTrack cursor = host.createCursorTrack(
                    "apc-seq-track-" + i, "APC Seq Track " + (i + 1), 0, 0, false);
            cursor.selectFirst();
            for (int j = 0; j < i; j++) {
                cursor.selectNext();
            }
            cursors[i] = cursor;
            PinnableCursorClip clip = cursor.createLauncherCursorClip(
                    Sequencer.PATTERN_LENGTH, 1);
            sequencers[i] = new Sequencer(clip, padLeds);
        }

        router = new TrackRouter(sequencers, sceneLeds);
        router.initLeds();

        // Wire raw MIDI callback last so router is fully initialised before any input.
        allIn.setMidiCallback((status, data1, data2) -> {
            host.println(MidiUtils.formatMidiMessage(status, data1, data2));
            dispatchMidi(status, data1, data2);
        });

        host.println("APC Key 25 Sequencer init OK — 5 tracks");
    }

    private void dispatchMidi(int status, int data1, int data2) {
        int channel = status & 0x0F;
        int msgType = status & 0xF0;

        // Only process note-on (velocity > 0) on channel 0
        if (msgType != 0x90 || channel != 0 || data2 == 0) return;

        if (data1 >= SCENE_LAUNCH_MIN && data1 <= SCENE_LAUNCH_MAX) {
            router.sceneLaunchPressed(data1 - SCENE_LAUNCH_MIN);
        } else if (data1 >= PAD_MIN && data1 <= PAD_MAX) {
            router.padTapped(data1);
        }
    }

    private static Integer[] blockAllTable() {
        Integer[] table = new Integer[128];
        Arrays.fill(table, -1);
        return table;
    }

    @Override
    public void flush() {}

    @Override
    public void exit() {
        host.println("APC Key 25 Sequencer exit");
    }
}
```

- [ ] **Step 4.2: Run all tests — expect PASS**

```bash
mvn test -pl apc-key25-sequencer
```

Expected: **BUILD SUCCESS**, 55 tests pass. (Extension changes are runtime-only; all unit tests still pass.)

- [ ] **Step 4.3: Commit**

```bash
git add src/main/java/com/apcsequencer/ApcKey25SequencerExtension.java
git commit -m "feat: multi-track S7 — 5 cursor tracks + TrackRouter in Extension"
```

---

## Task 5: Build, deploy, and verify in Bitwig

- [ ] **Step 5.1: Build and deploy**

```bash
mvn package -pl apc-key25-sequencer
```

Expected: **BUILD SUCCESS**. The `.bwextension` file is copied to
`/mnt/c/Users/sfrullo/Documents/Bitwig Studio/Extensions/ApcKey25Sequencer.bwextension`.
(If that path does not exist, `mvn package` will fail at the antrun step — this is not a code error; the `.jar` in `target/` is still built correctly.)

- [ ] **Step 5.2: Verify cursor track navigation**

In Bitwig:
1. Open a project with at least 5 instrument tracks.
2. Activate the extension.
3. Check the Controller Script Console for `"APC Key 25 Sequencer init OK — 5 tracks"`.
4. Check that **no** log errors appear about tracks not found.
5. Press Scene Launch 1 (0x52) — its LED should go green, others off.
6. Press Scene Launch 2 (0x53) — LED switches to row 2 green, row 1 off.
7. Press a bottom-row pad while track 2 is selected — note should appear in track 2's clip, not track 1's.
8. Press Scene Launch 1 again — pad LEDs should show track 1's (empty) pattern.
9. Repeat for all 5 tracks.

**If cursor navigation is wrong** (e.g., all 5 cursors land on the same track):
- Add `host.println("Track " + i + ": " + cursor.name().get())` inside the init loop to identify actual cursor positions.
- Report the output before proceeding.

- [ ] **Step 5.3: Verify polyrhythm playback**

1. Enable a few steps on track 1 (e.g., steps 0 and 4).
2. Switch to track 2, enable different steps (e.g., steps 2 and 6).
3. Launch both clips manually in Bitwig launcher.
4. Both should play simultaneously with their respective patterns.
5. The LED playhead follows whichever track is currently selected via Scene Launch.

---

## Notes

- The old single-track `Sequencer(ControllerHost host)` constructor is still present but no longer called. It can be removed in a future cleanup story.
- The `CursorTrack` cursor navigation approach (`selectFirst()` + N×`selectNext()`) is position-based. If the user reorders tracks in Bitwig, the cursors will follow their associated tracks (Bitwig tracks by identity, not position). This is acceptable behavior for S7.
- The `shouldSelectHierarchy=false` parameter ensures the 5 cursors do not move when the user clicks tracks in the Bitwig GUI.
