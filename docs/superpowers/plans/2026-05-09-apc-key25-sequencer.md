# APC Key 25 Polyrhythmic Sequencer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Bitwig 6 Java Extension that turns the Akai APC Key 25 mk1 into a 5-track polyrhythmic step sequencer with drum and melodic modes.

**Architecture:** Java 17 Maven project compiled to a fat JAR renamed `.bwextension`; the extension creates 5 NoteInputs (one per track) and manually injects all MIDI events via `NoteInput.sendRawMidiEvent()`. Core logic is in pure-Java classes with testable interfaces so unit tests run without Bitwig.

**Tech Stack:** Java 17, Maven 3.8, Gson 2.10.1, JUnit Jupiter 5.10, Bitwig Extension API v19 (from local `bitwig.jar`)

---

## File Structure

```
apc-key25-sequencer/
├── lib/
│   └── bitwig-extension-api.jar          # copied from Bitwig installation
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/apcsequencer/
    │   │   ├── Config.java                          # all MIDI constants
    │   │   ├── TrackState.java                      # per-track data (steps, params)
    │   │   ├── InputState.java                      # input gesture state
    │   │   ├── ScaleManager.java                    # 8 scales, root note, pitch lookup
    │   │   ├── PersistenceManager.java              # JSON serialize/deserialize
    │   │   ├── Sequencer.java                       # tick engine, note injection
    │   │   ├── ModeManager.java                     # drum/melodic toggle (thin wrapper)
    │   │   ├── LedManager.java                      # shadow LED state, flush
    │   │   ├── InputHandler.java                    # raw MIDI → logical commands
    │   │   ├── ApcKey25SequencerExtensionDefinition.java
    │   │   └── ApcKey25SequencerExtension.java      # init/flush/exit wiring
    │   └── resources/META-INF/services/
    │       └── com.bitwig.extension.controller.ControllerExtensionDefinition
    └── test/java/com/apcsequencer/
        ├── TrackStateTest.java
        ├── ScaleManagerTest.java
        ├── PersistenceManagerTest.java
        ├── SequencerTest.java
        ├── LedManagerTest.java
        └── InputHandlerTest.java
```

---

## Task 1: Dev Environment + Maven Project Scaffold

**Files:**
- Create: `apc-key25-sequencer/` (project root — inside the repo root)
- Create: `apc-key25-sequencer/lib/bitwig-extension-api.jar`
- Create: `apc-key25-sequencer/pom.xml`

- [ ] **Step 1.1: Install Java 17 and Maven**

```bash
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk maven
java -version
mvn -version
```

Expected output: `java version "17.x.x"` and `Apache Maven 3.8.x`

- [ ] **Step 1.2: Create project directory structure**

```bash
mkdir -p apc-key25-sequencer/lib
mkdir -p apc-key25-sequencer/src/main/java/com/apcsequencer
mkdir -p apc-key25-sequencer/src/main/resources/META-INF/services
mkdir -p apc-key25-sequencer/src/test/java/com/apcsequencer
```

- [ ] **Step 1.3: Copy Bitwig Extension API JAR to lib/**

```bash
cp "/mnt/c/Program Files/Bitwig Studio/bin/bitwig.jar" \
   apc-key25-sequencer/lib/bitwig-extension-api.jar
```

Expected: no error; `ls apc-key25-sequencer/lib/` shows `bitwig-extension-api.jar`

- [ ] **Step 1.4: Write pom.xml**

Create `apc-key25-sequencer/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.apcsequencer</groupId>
  <artifactId>apc-key25-sequencer</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <bitwig.extensions.dir>/mnt/c/Users/sfrullo/Documents/Bitwig Studio/Extensions</bitwig.extensions.dir>
  </properties>

  <dependencies>
    <!-- Bitwig Extension API: provided at runtime by Bitwig -->
    <dependency>
      <groupId>com.bitwig</groupId>
      <artifactId>extension-api</artifactId>
      <version>19</version>
      <scope>system</scope>
      <systemPath>${project.basedir}/lib/bitwig-extension-api.jar</systemPath>
    </dependency>

    <!-- JSON serialization for persistence -->
    <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.10.1</version>
    </dependency>

    <!-- Unit testing -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- Run JUnit 5 tests -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>

      <!-- Bundle Gson into fat JAR; exclude Bitwig API (provided by Bitwig at runtime) -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <shadedArtifactAttached>false</shadedArtifactAttached>
              <artifactSet>
                <excludes>
                  <exclude>com.bitwig:extension-api</exclude>
                </excludes>
              </artifactSet>
              <filters>
                <filter>
                  <artifact>*:*</artifact>
                  <excludes>
                    <exclude>META-INF/*.SF</exclude>
                    <exclude>META-INF/*.DSA</exclude>
                    <exclude>META-INF/*.RSA</exclude>
                  </excludes>
                </filter>
              </filters>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <!-- Copy fat JAR as .bwextension into Bitwig Extensions folder -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-antrun-plugin</artifactId>
        <version>3.1.0</version>
        <executions>
          <execution>
            <id>copy-bwextension</id>
            <phase>package</phase>
            <goals><goal>run</goal></goals>
            <configuration>
              <target>
                <copy
                  file="${project.build.directory}/${project.artifactId}-${project.version}.jar"
                  tofile="${bitwig.extensions.dir}/ApcKey25Sequencer.bwextension"
                  overwrite="true"/>
              </target>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 1.5: Verify Maven resolves dependencies**

```bash
cd apc-key25-sequencer && mvn validate
```

Expected: `BUILD SUCCESS`

- [ ] **Step 1.6: Commit scaffold**

```bash
git add apc-key25-sequencer/
git commit -m "feat: maven project scaffold with pom.xml and lib/"
```

---

## Task 2: Config, TrackState, InputState

**Files:**
- Create: `src/main/java/com/apcsequencer/Config.java`
- Create: `src/main/java/com/apcsequencer/TrackState.java`
- Create: `src/main/java/com/apcsequencer/InputState.java`
- Create: `src/test/java/com/apcsequencer/TrackStateTest.java`

- [ ] **Step 2.1: Write failing TrackStateTest.java**

```java
package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackStateTest {

    @Test
    void defaultTrackState_hasCorrectArrayLengths() {
        TrackState t = new TrackState(1);
        assertEquals(8, t.steps.length);
        assertEquals(8, t.notes.length);
        assertEquals(8, t.velocities.length);
        assertEquals(8, t.gateLengths.length);
        assertEquals(8, t.probabilities.length);
        assertEquals(8, t.nudges.length);
        assertEquals(8, t.ratchets.length);
        assertEquals(8, t.chordIntervals.length);
        assertEquals(8, t.ccValues.length);
    }

    @Test
    void defaultTrackState_hasCorrectDefaults() {
        TrackState t = new TrackState(3);
        assertEquals(3, t.midiChannel);
        assertEquals(8, t.patternLength);
        assertFalse(t.muted);
        assertFalse(t.melodicMode);
        assertEquals(36, t.baseNote);
        assertEquals(0, t.currentStep);
        for (int i = 0; i < 8; i++) {
            assertFalse(t.steps[i]);
            assertEquals(-1, t.notes[i]);      // sentinel: use baseNote
            assertEquals(100, t.velocities[i]);
            assertEquals(0.5, t.gateLengths[i], 0.001);
            assertEquals(1.0, t.probabilities[i], 0.001);
            assertEquals(0, t.nudges[i]);
            assertEquals(1, t.ratchets[i]);
            assertEquals(0, t.chordIntervals[i]);
            assertEquals(64, t.ccValues[i]);
        }
    }

    @Test
    void reset_restoresDefaults() {
        TrackState t = new TrackState(1);
        t.steps[0] = true;
        t.velocities[0] = 50;
        t.patternLength = 4;
        t.muted = true;
        t.reset();
        assertFalse(t.steps[0]);
        assertEquals(100, t.velocities[0]);
        assertEquals(8, t.patternLength);
        assertFalse(t.muted);
    }

    @Test
    void deepCopy_isIndependent() {
        TrackState src = new TrackState(1);
        src.steps[0] = true;
        src.patternLength = 4;
        TrackState copy = src.deepCopy();
        // Modify original - copy should not change
        src.steps[0] = false;
        src.patternLength = 8;
        assertTrue(copy.steps[0]);
        assertEquals(4, copy.patternLength);
    }
}
```

- [ ] **Step 2.2: Run test to verify it fails**

```bash
cd apc-key25-sequencer && mvn test -pl . 2>&1 | tail -20
```

Expected: FAIL — `Config`, `TrackState` classes do not exist yet

- [ ] **Step 2.3: Write Config.java**

```java
package com.apcsequencer;

public final class Config {
    private Config() {}

    // MIDI port indices
    public static final int PORT_KEYBOARD = 0;  // Port 0: keyboard in
    public static final int PORT_PADS     = 1;  // Port 1: pads/buttons/knobs in
    public static final int PORT_OUT      = 0;  // Port 0: LEDs out

    // Pad note numbers: pads[row][col]
    // Row 0 (top) = Track 0, Row 4 (bottom) = Track 4
    // Col 0 (left) = Step 0, Col 7 (right) = Step 7
    public static final int[][] PADS = {
        {0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27}, // Row 0
        {0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F}, // Row 1
        {0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17}, // Row 2
        {0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F}, // Row 3
        {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}, // Row 4
    };

    // Scene Launch buttons (one per track row)
    public static final int[] SCENE_LAUNCH = {0x52, 0x53, 0x54, 0x55, 0x56};

    // Special buttons
    public static final int STOP_ALL_CLIPS = 0x51;
    public static final int PLAY           = 0x5B;
    public static final int RECORD         = 0x5D;
    public static final int SHIFT          = 0x62;

    // Knob CC numbers (absolute 0–127, channel 0, port 1)
    public static final int KNOB_1 = 0x30;
    public static final int KNOB_2 = 0x31;
    public static final int KNOB_3 = 0x32;
    public static final int KNOB_4 = 0x33;
    public static final int KNOB_5 = 0x34;
    public static final int KNOB_6 = 0x35;
    public static final int KNOB_7 = 0x36;
    public static final int KNOB_8 = 0x37;

    // LED velocities (mk1 three-color system)
    public static final int LED_OFF         = 0;
    public static final int LED_GREEN       = 1;
    public static final int LED_GREEN_BLINK = 2;
    public static final int LED_RED         = 3;
    public static final int LED_RED_BLINK   = 4;
    public static final int LED_ORANGE      = 5;

    // MIDI status byte high nibbles
    public static final int NOTE_ON  = 0x90;
    public static final int NOTE_OFF = 0x80;
    public static final int CC       = 0xB0;

    // CC 123 = All Notes Off
    public static final int CC_ALL_NOTES_OFF = 123;

    // Sequencer dimensions
    public static final int NUM_TRACKS = 5;
    public static final int NUM_STEPS  = 8;
    public static final int NUM_SCALES = 8;

    // Default step parameters
    public static final int    DEFAULT_VELOCITY    = 100;
    public static final double DEFAULT_GATE        = 0.5;
    public static final double DEFAULT_PROBABILITY = 1.0;
    public static final int    DEFAULT_NUDGE       = 0;
    public static final int    DEFAULT_RATCHET     = 1;
    public static final int    DEFAULT_CHORD       = 0;
    public static final int    DEFAULT_CC_VALUE    = 64;
    public static final int    DEFAULT_BASE_NOTE   = 36;

    // notes[i] = -1 means "use baseNote" (drum sentinel)
    public static final int NOTE_SENTINEL = -1;

    // Gesture timing
    public static final long DOUBLE_TAP_MS  = 400;
    public static final long HOLD_THRESH_MS = 200;
}
```

- [ ] **Step 2.4: Write TrackState.java**

```java
package com.apcsequencer;

import java.util.Arrays;

public class TrackState {
    public boolean[] steps;
    public int[]     notes;          // -1 = use baseNote (drum sentinel)
    public int[]     velocities;
    public double[]  gateLengths;
    public double[]  probabilities;
    public int[]     nudges;
    public int[]     ratchets;
    public int[]     chordIntervals;
    public int[]     ccValues;
    public int       patternLength;
    public boolean   muted;
    public boolean   melodicMode;
    public int       baseNote;
    public int       currentStep;
    public int       midiChannel;    // 1-indexed (1–5)

    public TrackState(int midiChannel) {
        this.midiChannel    = midiChannel;
        this.steps          = new boolean[Config.NUM_STEPS];
        this.notes          = new int[Config.NUM_STEPS];
        this.velocities     = new int[Config.NUM_STEPS];
        this.gateLengths    = new double[Config.NUM_STEPS];
        this.probabilities  = new double[Config.NUM_STEPS];
        this.nudges         = new int[Config.NUM_STEPS];
        this.ratchets       = new int[Config.NUM_STEPS];
        this.chordIntervals = new int[Config.NUM_STEPS];
        this.ccValues       = new int[Config.NUM_STEPS];
        reset();
    }

    public void reset() {
        Arrays.fill(steps,         false);
        Arrays.fill(notes,         Config.NOTE_SENTINEL);
        Arrays.fill(velocities,    Config.DEFAULT_VELOCITY);
        Arrays.fill(gateLengths,   Config.DEFAULT_GATE);
        Arrays.fill(probabilities, Config.DEFAULT_PROBABILITY);
        Arrays.fill(nudges,        Config.DEFAULT_NUDGE);
        Arrays.fill(ratchets,      Config.DEFAULT_RATCHET);
        Arrays.fill(chordIntervals,Config.DEFAULT_CHORD);
        Arrays.fill(ccValues,      Config.DEFAULT_CC_VALUE);
        patternLength = Config.NUM_STEPS;
        muted         = false;
        melodicMode   = false;
        baseNote      = Config.DEFAULT_BASE_NOTE;
        currentStep   = 0;
    }

    public TrackState deepCopy() {
        TrackState c = new TrackState(this.midiChannel);
        c.steps          = Arrays.copyOf(this.steps,          this.steps.length);
        c.notes          = Arrays.copyOf(this.notes,          this.notes.length);
        c.velocities     = Arrays.copyOf(this.velocities,     this.velocities.length);
        c.gateLengths    = Arrays.copyOf(this.gateLengths,    this.gateLengths.length);
        c.probabilities  = Arrays.copyOf(this.probabilities,  this.probabilities.length);
        c.nudges         = Arrays.copyOf(this.nudges,         this.nudges.length);
        c.ratchets       = Arrays.copyOf(this.ratchets,       this.ratchets.length);
        c.chordIntervals = Arrays.copyOf(this.chordIntervals, this.chordIntervals.length);
        c.ccValues       = Arrays.copyOf(this.ccValues,       this.ccValues.length);
        c.patternLength  = this.patternLength;
        c.muted          = this.muted;
        c.melodicMode    = this.melodicMode;
        c.baseNote       = this.baseNote;
        c.currentStep    = this.currentStep;
        return c;
    }
}
```

- [ ] **Step 2.5: Write InputState.java**

```java
package com.apcsequencer;

public class InputState {
    public boolean shiftHeld          = false;
    public boolean stopAllClipsHeld   = false;
    public int     heldStepNote       = -1;   // note# of held pad, -1 = none
    public int     heldStepTrack      = -1;   // track index of held pad
    public int     heldStepCol        = -1;   // step column of held pad
    public int     heldSceneLaunch    = -1;   // track index of Shift+Scene held, -1 = none
    public long    sceneLaunchPressTime = 0L;
    public long    sceneLaunchLastTap   = 0L;
    public int     activeTrack        = 0;    // 0–4
}
```

- [ ] **Step 2.6: Run test to verify it passes**

```bash
cd apc-key25-sequencer && mvn test -Dtest=TrackStateTest
```

Expected: `BUILD SUCCESS`, 4 tests pass

- [ ] **Step 2.7: Commit**

```bash
git add apc-key25-sequencer/src/
git commit -m "feat: Config, TrackState, InputState data classes"
```

---

## Task 3: ScaleManager

**Files:**
- Create: `src/main/java/com/apcsequencer/ScaleManager.java`
- Create: `src/test/java/com/apcsequencer/ScaleManagerTest.java`

- [ ] **Step 3.1: Write failing ScaleManagerTest.java**

```java
package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScaleManagerTest {

    @Test
    void getPitch_chromatic_C3_atKnob0() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(0); // Chromatic (12 notes)
        sm.setRootNote(0);   // C root
        // knob=0: degree=0, oct=0, semitone=0 → C3 = 3*12+0 = 36
        assertEquals(36, sm.getPitch(0));
    }

    @Test
    void getPitch_chromatic_spans3Octaves() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(0); // Chromatic: 12 notes/octave
        sm.setRootNote(0);
        // knob=127: totalDegrees = 12*3=36, degree = (127*36)/128 = 35
        // oct=35/12=2, semitone=35%12=11 → B5 = (3+2)*12 + 0 + 11 = 71
        int pitch = sm.getPitch(127);
        assertTrue(pitch >= 36 && pitch <= 84, "Pitch " + pitch + " not in C3–B5 range");
    }

    @Test
    void getPitch_major_rootC() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(1); // Major: {0,2,4,5,7,9,11} = 7 notes
        sm.setRootNote(0);   // C
        // knob=0: totalDegrees=7*3=21, degree=(0*21)/128=0, oct=0, semi=0 → C3=36
        assertEquals(36, sm.getPitch(0));
    }

    @Test
    void getPitch_major_rootA() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(1); // Major: {0,2,4,5,7,9,11}
        sm.setRootNote(69);  // A (69%12=9)
        // knob=0: degree=0, oct=0, semi=0 → (3+0)*12 + 9 + 0 = 45 = A3
        assertEquals(45, sm.getPitch(0));
    }

    @Test
    void setRootNote_extractsPitchClass() {
        ScaleManager sm = new ScaleManager();
        sm.setRootNote(69); // A4 = MIDI 69
        assertEquals(9, sm.getRootNote()); // 69 % 12 = 9 = A
    }

    @Test
    void applyChordInterval_none_returnsNegative() {
        assertEquals(-1, ScaleManager.applyChordInterval(60, 0));
    }

    @Test
    void applyChordInterval_third() {
        assertEquals(64, ScaleManager.applyChordInterval(60, 1)); // C + 4 semitones = E
    }

    @Test
    void applyChordInterval_fifth() {
        assertEquals(67, ScaleManager.applyChordInterval(60, 2)); // C + 7 = G
    }

    @Test
    void applyChordInterval_octave() {
        assertEquals(72, ScaleManager.applyChordInterval(60, 3)); // C + 12 = C+1
    }

    @Test
    void getScaleName_returnsCorrectNames() {
        ScaleManager sm = new ScaleManager();
        sm.setScaleIndex(0);
        assertEquals("Cromatica", sm.getScaleName());
        sm.setScaleIndex(5);
        assertEquals("Pentatonica Maggiore", sm.getScaleName());
    }
}
```

- [ ] **Step 3.2: Run to verify it fails**

```bash
cd apc-key25-sequencer && mvn test -Dtest=ScaleManagerTest 2>&1 | tail -10
```

Expected: FAIL — `ScaleManager` does not exist

- [ ] **Step 3.3: Write ScaleManager.java**

```java
package com.apcsequencer;

public class ScaleManager {
    public static final int BASE_OCTAVE = 3;  // C3 = lowest reachable note
    public static final int NUM_OCTAVES = 3;  // knob spans C3–B5

    public static final String[] SCALE_NAMES = {
        "Cromatica",
        "Maggiore",
        "Minore Naturale",
        "Dorian",
        "Mixolydian",
        "Pentatonica Maggiore",
        "Pentatonica Minore",
        "Blues"
    };

    public static final int[][] SCALES = {
        {0,1,2,3,4,5,6,7,8,9,10,11},  // Cromatica
        {0,2,4,5,7,9,11},              // Maggiore
        {0,2,3,5,7,8,10},             // Minore Naturale
        {0,2,3,5,7,9,10},             // Dorian
        {0,2,4,5,7,9,10},             // Mixolydian
        {0,2,4,7,9},                  // Pentatonica Maggiore
        {0,3,5,7,10},                 // Pentatonica Minore
        {0,3,5,6,7,10},              // Blues
    };

    private int scaleIndex = 0;
    private int rootNote   = 0;  // pitch class 0–11 (C=0)

    public void setScaleIndex(int idx) {
        if (idx >= 0 && idx < Config.NUM_SCALES) scaleIndex = idx;
    }

    public int getScaleIndex() { return scaleIndex; }

    /** Sets root note from a MIDI note number (extracts pitch class). */
    public void setRootNote(int midiNote) {
        rootNote = midiNote % 12;
    }

    public int getRootNote() { return rootNote; }

    public String getScaleName() { return SCALE_NAMES[scaleIndex]; }

    /**
     * Maps an absolute knob value (0–127) to a MIDI note.
     * Covers BASE_OCTAVE to (BASE_OCTAVE + NUM_OCTAVES - 1) inclusive.
     */
    public int getPitch(int knobValue) {
        int[] scale = SCALES[scaleIndex];
        int totalDegrees = scale.length * NUM_OCTAVES;
        int degree = (knobValue * totalDegrees) / 128;
        int oct    = degree / scale.length;
        int semi   = scale[degree % scale.length];
        return (BASE_OCTAVE + oct) * 12 + rootNote + semi;
    }

    /**
     * Returns the MIDI note of a chord tone above basePitch.
     * interval: 0=none (-1), 1=major 3rd (+4), 2=5th (+7), 3=octave (+12)
     */
    public static int applyChordInterval(int basePitch, int interval) {
        return switch (interval) {
            case 1  -> basePitch + 4;
            case 2  -> basePitch + 7;
            case 3  -> basePitch + 12;
            default -> -1;
        };
    }
}
```

- [ ] **Step 3.4: Run to verify it passes**

```bash
cd apc-key25-sequencer && mvn test -Dtest=ScaleManagerTest
```

Expected: `BUILD SUCCESS`, 10 tests pass

- [ ] **Step 3.5: Commit**

```bash
git add apc-key25-sequencer/src/
git commit -m "feat: ScaleManager with 8 scales and pitch mapping"
```

---

## Task 4: PersistenceManager

**Files:**
- Create: `src/main/java/com/apcsequencer/PersistenceManager.java`
- Create: `src/test/java/com/apcsequencer/PersistenceManagerTest.java`

- [ ] **Step 4.1: Write failing PersistenceManagerTest.java**

```java
package com.apcsequencer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceManagerTest {

    private TrackState[] freshTracks() {
        TrackState[] t = new TrackState[5];
        for (int i = 0; i < 5; i++) t[i] = new TrackState(i + 1);
        return t;
    }

    @Test
    void serialize_producesNonEmptyJson() {
        String json = PersistenceManager.serialize(freshTracks(), 2, 5, 1);
        assertNotNull(json);
        assertFalse(json.isBlank());
        assertTrue(json.contains("scaleIndex"));
        assertTrue(json.contains("tracks"));
    }

    @Test
    void roundtrip_scaleIndexAndRootNote() {
        TrackState[] src = freshTracks();
        String json = PersistenceManager.serialize(src, 3, 7, 2);

        TrackState[] dst = freshTracks();
        int[] scaleOut = {0}, rootOut = {0}, activeOut = {0};
        PersistenceManager.deserialize(json, dst, scaleOut, rootOut, activeOut);

        assertEquals(3, scaleOut[0]);
        assertEquals(7, rootOut[0]);
        assertEquals(2, activeOut[0]);
    }

    @Test
    void roundtrip_stepData() {
        TrackState[] src = freshTracks();
        src[0].steps[3]         = true;
        src[0].notes[3]         = 64;
        src[0].velocities[3]    = 80;
        src[0].gateLengths[3]   = 0.75;
        src[0].probabilities[3] = 0.5;
        src[0].nudges[3]        = 2;
        src[0].ratchets[3]      = 3;
        src[0].chordIntervals[3]= 1;
        src[0].ccValues[3]      = 100;
        src[0].patternLength    = 6;
        src[0].melodicMode      = true;
        src[0].baseNote         = 48;
        src[0].muted            = true;

        String json = PersistenceManager.serialize(src, 0, 0, 0);

        TrackState[] dst = freshTracks();
        int[] si = {0}, ri = {0}, ai = {0};
        PersistenceManager.deserialize(json, dst, si, ri, ai);

        TrackState t = dst[0];
        assertTrue(t.steps[3]);
        assertEquals(64,   t.notes[3]);
        assertEquals(80,   t.velocities[3]);
        assertEquals(0.75, t.gateLengths[3],   0.001);
        assertEquals(0.5,  t.probabilities[3], 0.001);
        assertEquals(2,    t.nudges[3]);
        assertEquals(3,    t.ratchets[3]);
        assertEquals(1,    t.chordIntervals[3]);
        assertEquals(100,  t.ccValues[3]);
        assertEquals(6,    t.patternLength);
        assertTrue(t.melodicMode);
        assertEquals(48,   t.baseNote);
        assertTrue(t.muted);
    }

    @Test
    void deserialize_emptyString_leavesDefaults() {
        TrackState[] dst = freshTracks();
        int[] si = {0}, ri = {0}, ai = {0};
        PersistenceManager.deserialize("", dst, si, ri, ai);
        // Should leave everything at defaults
        assertFalse(dst[0].steps[0]);
        assertEquals(0, si[0]);
    }

    @Test
    void deserialize_corruptJson_leavesDefaults() {
        TrackState[] dst = freshTracks();
        int[] si = {0}, ri = {0}, ai = {0};
        PersistenceManager.deserialize("{not valid json!!!", dst, si, ri, ai);
        assertFalse(dst[0].steps[0]);
    }
}
```

- [ ] **Step 4.2: Run to verify it fails**

```bash
cd apc-key25-sequencer && mvn test -Dtest=PersistenceManagerTest 2>&1 | tail -10
```

Expected: FAIL — `PersistenceManager` does not exist

- [ ] **Step 4.3: Write PersistenceManager.java**

```java
package com.apcsequencer;

import com.google.gson.*;

public class PersistenceManager {

    public static String serialize(TrackState[] tracks, int scaleIndex,
                                   int rootNote, int activeTrack) {
        JsonObject root = new JsonObject();
        root.addProperty("scaleIndex",  scaleIndex);
        root.addProperty("rootNote",    rootNote);
        root.addProperty("activeTrack", activeTrack);

        JsonArray tracksArr = new JsonArray();
        for (TrackState t : tracks) {
            JsonObject obj = new JsonObject();
            obj.addProperty("patternLength", t.patternLength);
            obj.addProperty("melodicMode",   t.melodicMode);
            obj.addProperty("baseNote",      t.baseNote);
            obj.addProperty("muted",         t.muted);
            obj.add("steps",          boolArray(t.steps));
            obj.add("notes",          intArray(t.notes));
            obj.add("velocities",     intArray(t.velocities));
            obj.add("gateLengths",    doubleArray(t.gateLengths));
            obj.add("probabilities",  doubleArray(t.probabilities));
            obj.add("nudges",         intArray(t.nudges));
            obj.add("ratchets",       intArray(t.ratchets));
            obj.add("chordIntervals", intArray(t.chordIntervals));
            obj.add("ccValues",       intArray(t.ccValues));
            tracksArr.add(obj);
        }
        root.add("tracks", tracksArr);
        return new Gson().toJson(root);
    }

    public static void deserialize(String json, TrackState[] tracks,
                                   int[] scaleIndexOut, int[] rootNoteOut,
                                   int[] activeTrackOut) {
        if (json == null || json.isBlank()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            scaleIndexOut[0]  = root.get("scaleIndex").getAsInt();
            rootNoteOut[0]    = root.get("rootNote").getAsInt();
            activeTrackOut[0] = root.get("activeTrack").getAsInt();

            JsonArray arr = root.getAsJsonArray("tracks");
            for (int i = 0; i < Math.min(tracks.length, arr.size()); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                TrackState t = tracks[i];
                t.patternLength = obj.get("patternLength").getAsInt();
                t.melodicMode   = obj.get("melodicMode").getAsBoolean();
                t.baseNote      = obj.get("baseNote").getAsInt();
                t.muted         = obj.get("muted").getAsBoolean();
                readBoolArr(obj.getAsJsonArray("steps"),          t.steps);
                readIntArr( obj.getAsJsonArray("notes"),          t.notes);
                readIntArr( obj.getAsJsonArray("velocities"),     t.velocities);
                readDblArr( obj.getAsJsonArray("gateLengths"),    t.gateLengths);
                readDblArr( obj.getAsJsonArray("probabilities"),  t.probabilities);
                readIntArr( obj.getAsJsonArray("nudges"),         t.nudges);
                readIntArr( obj.getAsJsonArray("ratchets"),       t.ratchets);
                readIntArr( obj.getAsJsonArray("chordIntervals"), t.chordIntervals);
                readIntArr( obj.getAsJsonArray("ccValues"),       t.ccValues);
            }
        } catch (Exception ignored) {
            // Corrupt JSON — leave tracks at default state
        }
    }

    private static JsonArray boolArray(boolean[] a) {
        JsonArray r = new JsonArray();
        for (boolean v : a) r.add(v);
        return r;
    }
    private static JsonArray intArray(int[] a) {
        JsonArray r = new JsonArray();
        for (int v : a) r.add(v);
        return r;
    }
    private static JsonArray doubleArray(double[] a) {
        JsonArray r = new JsonArray();
        for (double v : a) r.add(v);
        return r;
    }
    private static void readBoolArr(JsonArray a, boolean[] out) {
        for (int i = 0; i < Math.min(a.size(), out.length); i++)
            out[i] = a.get(i).getAsBoolean();
    }
    private static void readIntArr(JsonArray a, int[] out) {
        for (int i = 0; i < Math.min(a.size(), out.length); i++)
            out[i] = a.get(i).getAsInt();
    }
    private static void readDblArr(JsonArray a, double[] out) {
        for (int i = 0; i < Math.min(a.size(), out.length); i++)
            out[i] = a.get(i).getAsDouble();
    }
}
```

- [ ] **Step 4.4: Run to verify it passes**

```bash
cd apc-key25-sequencer && mvn test -Dtest=PersistenceManagerTest
```

Expected: `BUILD SUCCESS`, 5 tests pass

- [ ] **Step 4.5: Commit**

```bash
git add apc-key25-sequencer/src/
git commit -m "feat: PersistenceManager JSON roundtrip"
```

---

## Task 5: Sequencer

**Files:**
- Create: `src/main/java/com/apcsequencer/Sequencer.java`
- Create: `src/test/java/com/apcsequencer/SequencerTest.java`

- [ ] **Step 5.1: Write failing SequencerTest.java**

```java
package com.apcsequencer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SequencerTest {

    private TrackState[] tracks;
    private List<int[]>  firedNotes;
    private List<Long>   scheduledDelays;
    private List<Runnable> pendingTasks;
    private Sequencer    sequencer;

    @BeforeEach
    void setup() {
        tracks = new TrackState[5];
        for (int i = 0; i < 5; i++) tracks[i] = new TrackState(i + 1);

        firedNotes      = new ArrayList<>();
        scheduledDelays = new ArrayList<>();
        pendingTasks    = new ArrayList<>();

        Sequencer.NoteInputPort[] ports = new Sequencer.NoteInputPort[5];
        for (int i = 0; i < 5; i++) {
            final int ti = i;
            ports[i] = (s, d1, d2) -> firedNotes.add(new int[]{ti, s, d1, d2});
        }

        // Test scheduler: records task + delay, does NOT run immediately
        Sequencer.TaskScheduler scheduler = (cb, delay) -> {
            scheduledDelays.add(delay);
            pendingTasks.add(cb);
        };

        ScaleManager sm = new ScaleManager();
        sequencer = new Sequencer(tracks, ports, sm, scheduler);
        sequencer.setBpm(120.0); // stepMs = (60000/120)/4 = 125 ms
    }

    /** Fires one tick: runs the oldest scheduled task. */
    private void tick() {
        assertFalse(pendingTasks.isEmpty(), "No pending task to tick");
        pendingTasks.remove(0).run();
    }

    @Test
    void start_schedulesFirstTick() {
        sequencer.start();
        assertEquals(1, pendingTasks.size());
        assertEquals(125L, scheduledDelays.get(0)); // 120 BPM → 125 ms
    }

    @Test
    void tick_advancesCurrentStep() {
        tracks[0].patternLength = 4;
        sequencer.start();
        tick(); // step 0 → 1
        assertEquals(1, tracks[0].currentStep);
    }

    @Test
    void tick_firesActiveStep() {
        tracks[0].steps[0] = true;
        tracks[0].velocities[0] = 80;
        tracks[0].notes[0] = Config.NOTE_SENTINEL; // use baseNote (36)
        tracks[0].probabilities[0] = 1.0;

        sequencer.start();
        tick(); // processes step 0

        // Should fire NoteOn on track 0 (channel 1 = status 0x90)
        assertTrue(firedNotes.stream()
            .anyMatch(n -> n[0] == 0 && (n[1] & 0xF0) == 0x90 && n[2] == 36));
    }

    @Test
    void tick_mutedTrack_doesNotFire() {
        tracks[1].steps[0] = true;
        tracks[1].muted = true;

        sequencer.start();
        tick();

        assertTrue(firedNotes.stream().noneMatch(n -> n[0] == 1));
    }

    @Test
    void tick_polyrhythm_independentLengths() {
        tracks[0].patternLength = 3;
        tracks[1].patternLength = 5;
        sequencer.start();

        // After 3 ticks: track 0 steps: 0→1→2→0 (wraps), track 1: 0→1→2→3
        tick(); tick(); tick();

        assertEquals(0, tracks[0].currentStep); // 3 % 3 = 0 (wrapped)
        assertEquals(3, tracks[1].currentStep); // 3 % 5 = 3 (no wrap)
    }

    @Test
    void probability_zero_neverFires() {
        tracks[0].steps[0] = true;
        tracks[0].probabilities[0] = 0.0;

        sequencer.start();
        tick();

        assertTrue(firedNotes.stream()
            .noneMatch(n -> n[0] == 0 && (n[1] & 0xF0) == 0x90));
    }

    @Test
    void stop_sendsAllNotesOff() {
        sequencer.start();
        sequencer.stop();

        // 5 tracks × CC 123 on channels 0–4
        long ccCount = firedNotes.stream()
            .filter(n -> (n[1] & 0xF0) == 0xB0 && n[2] == Config.CC_ALL_NOTES_OFF)
            .count();
        assertEquals(5, ccCount);
    }

    @Test
    void setBpm_changesScheduledDelay() {
        sequencer.setBpm(60.0); // stepMs = (60000/60)/4 = 250 ms
        sequencer.start();
        assertEquals(250L, scheduledDelays.get(0));
    }

    @Test
    void resetAllStepCounters_setsAllToZero() {
        tracks[0].currentStep = 5;
        tracks[2].currentStep = 3;
        sequencer.resetAllStepCounters();
        for (TrackState t : tracks) assertEquals(0, t.currentStep);
    }
}
```

- [ ] **Step 5.2: Run to verify it fails**

```bash
cd apc-key25-sequencer && mvn test -Dtest=SequencerTest 2>&1 | tail -10
```

Expected: FAIL — `Sequencer` does not exist

- [ ] **Step 5.3: Write Sequencer.java**

```java
package com.apcsequencer;

import java.util.Random;

public class Sequencer {

    /** Abstracts Bitwig NoteInput for testability. */
    public interface NoteInputPort {
        void sendRawMidiEvent(int status, int data1, int data2);
    }

    /** Abstracts host.scheduleTask for testability. */
    public interface TaskScheduler {
        void schedule(Runnable callback, long delayMs);
    }

    private final TrackState[]    tracks;
    private final NoteInputPort[] noteInputs;
    private final ScaleManager    scaleManager;
    private final TaskScheduler   scheduler;
    private final Random          random = new Random();

    private volatile boolean running  = false;
    private          double  stepMs   = 125.0; // 120 BPM default
    private          int     knob8Cc  = 74;

    public Sequencer(TrackState[] tracks, NoteInputPort[] noteInputs,
                     ScaleManager scaleManager, TaskScheduler scheduler) {
        this.tracks       = tracks;
        this.noteInputs   = noteInputs;
        this.scaleManager = scaleManager;
        this.scheduler    = scheduler;
    }

    public void setBpm(double bpm) {
        stepMs = (60000.0 / bpm) / 4.0;
    }

    public void setKnob8Cc(int cc) {
        this.knob8Cc = cc;
    }

    public boolean isRunning() { return running; }

    public void start() {
        resetAllStepCounters();
        running = true;
        scheduleTick();
    }

    public void stop() {
        running = false;
        sendAllNotesOff();
    }

    public void resetAllStepCounters() {
        for (TrackState t : tracks) t.currentStep = 0;
    }

    // Package-private for testing
    void tick() {
        if (!running) return;
        for (int i = 0; i < Config.NUM_TRACKS; i++) {
            TrackState t = tracks[i];
            int step = t.currentStep;
            if (!t.muted && t.steps[step]) {
                fireStep(i, step);
            }
            t.currentStep = (t.currentStep + 1) % t.patternLength;
        }
        scheduleTick();
    }

    private void scheduleTick() {
        scheduler.schedule(this::tick, Math.round(stepMs));
    }

    private void fireStep(int trackIdx, int step) {
        TrackState t = tracks[trackIdx];
        if (random.nextDouble() > t.probabilities[step]) return;

        int note     = resolveNote(trackIdx, step);
        int velocity = t.velocities[step];
        int ratchet  = t.ratchets[step];
        long nudgeMs  = (long)(t.nudges[step] * stepMs / 6.0);
        long gateMs   = (long)(t.gateLengths[step] * stepMs);

        if (ratchet <= 1) {
            scheduleNote(trackIdx, note, velocity, nudgeMs, gateMs);
        } else {
            long rStep = Math.round(stepMs / ratchet);
            for (int r = 0; r < ratchet; r++) {
                long delay = nudgeMs + r * rStep;
                long gate  = Math.max(1, Math.min(gateMs, rStep - 5));
                scheduleNote(trackIdx, note, velocity, delay, gate);
            }
        }

        // Chord interval (melodic mode only)
        if (t.melodicMode && t.chordIntervals[step] > 0) {
            int chord = ScaleManager.applyChordInterval(note, t.chordIntervals[step]);
            if (chord >= 0 && chord <= 127) {
                scheduleNote(trackIdx, chord, velocity, nudgeMs, gateMs);
            }
        }

        // MIDI CC (knob 8 per step)
        int ccStatus = Config.CC | (t.midiChannel - 1);
        noteInputs[trackIdx].sendRawMidiEvent(ccStatus, knob8Cc, t.ccValues[step]);
    }

    private int resolveNote(int trackIdx, int step) {
        TrackState t = tracks[trackIdx];
        int n = t.notes[step];
        if (n == Config.NOTE_SENTINEL) return t.baseNote;
        return n;
    }

    private void scheduleNote(int trackIdx, int note, int velocity,
                              long delayMs, long gateMs) {
        int onStatus  = Config.NOTE_ON  | (tracks[trackIdx].midiChannel - 1);
        int offStatus = Config.NOTE_OFF | (tracks[trackIdx].midiChannel - 1);
        scheduler.schedule(() -> {
            noteInputs[trackIdx].sendRawMidiEvent(onStatus, note, velocity);
            scheduler.schedule(
                () -> noteInputs[trackIdx].sendRawMidiEvent(offStatus, note, 0),
                Math.max(1, gateMs)
            );
        }, Math.max(0, delayMs));
    }

    void sendAllNotesOff() {
        for (int i = 0; i < Config.NUM_TRACKS; i++) {
            int status = Config.CC | (tracks[i].midiChannel - 1);
            noteInputs[i].sendRawMidiEvent(status, Config.CC_ALL_NOTES_OFF, 0);
        }
    }
}
```

- [ ] **Step 5.4: Run to verify it passes**

```bash
cd apc-key25-sequencer && mvn test -Dtest=SequencerTest
```

Expected: `BUILD SUCCESS`, 9 tests pass

- [ ] **Step 5.5: Commit**

```bash
git add apc-key25-sequencer/src/
git commit -m "feat: Sequencer engine with polyrhythm, ratchet, nudge"
```

---

## Task 6: LedManager

**Files:**
- Create: `src/main/java/com/apcsequencer/LedManager.java`
- Create: `src/test/java/com/apcsequencer/LedManagerTest.java`

- [ ] **Step 6.1: Write failing LedManagerTest.java**

```java
package com.apcsequencer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LedManagerTest {

    private LedManager ledManager;
    private List<int[]> sentMidi;
    private LedManager.MidiSender sender;

    @BeforeEach
    void setup() {
        ledManager = new LedManager();
        sentMidi   = new ArrayList<>();
        sender     = (status, d1, d2) -> sentMidi.add(new int[]{status, d1, d2});
    }

    private TrackState[] freshTracks() {
        TrackState[] t = new TrackState[5];
        for (int i = 0; i < 5; i++) t[i] = new TrackState(i + 1);
        return t;
    }

    @Test
    void updateSequencerView_activeStep_showsGreen() {
        TrackState[] tracks = freshTracks();
        tracks[0].steps[2] = true;  // step active, not playhead
        tracks[0].currentStep = 5;  // playhead elsewhere

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[0][2] && m[2] == Config.LED_GREEN));
    }

    @Test
    void updateSequencerView_playheadOnEmpty_showsRed() {
        TrackState[] tracks = freshTracks();
        tracks[1].currentStep = 3;
        tracks[1].steps[3] = false;

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[1][3] && m[2] == Config.LED_RED));
    }

    @Test
    void updateSequencerView_playheadOnActive_showsOrange() {
        TrackState[] tracks = freshTracks();
        tracks[2].currentStep = 0;
        tracks[2].steps[0] = true;

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[2][0] && m[2] == Config.LED_ORANGE));
    }

    @Test
    void updateSequencerView_mutedRow_showsGreenBlink() {
        TrackState[] tracks = freshTracks();
        tracks[3].muted = true;
        tracks[3].steps[4] = true;  // active step but muted overrides

        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);

        // All pads in row 3 should be GREEN_BLINK
        for (int col = 0; col < Config.NUM_STEPS; col++) {
            final int c = col;
            assertTrue(sentMidi.stream()
                .anyMatch(m -> m[1] == Config.PADS[3][c] && m[2] == Config.LED_GREEN_BLINK),
                "Col " + col + " should be GREEN_BLINK");
        }
    }

    @Test
    void flush_dirtyAfterUpdate_notDirtyAfterFlush() {
        TrackState[] tracks = freshTracks();
        ledManager.updateSequencerView(tracks);
        assertTrue(ledManager.isDirty());
        ledManager.flush(sender);
        assertFalse(ledManager.isDirty());
    }

    @Test
    void flush_doesNotResendUnchangedLeds() {
        TrackState[] tracks = freshTracks();
        ledManager.updateSequencerView(tracks);
        ledManager.flush(sender);
        int firstCount = sentMidi.size();

        ledManager.flush(sender);  // nothing changed
        assertEquals(firstCount, sentMidi.size());
    }

    @Test
    void updateScaleView_activeScale_showsGreenBlink() {
        ledManager.updateScaleView(4, 0);
        ledManager.flush(sender);

        // Pad at row 0, col 4 = active scale = GREEN_BLINK
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[0][4] && m[2] == Config.LED_GREEN_BLINK));
        // Other scale pads should be GREEN
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.PADS[0][0] && m[2] == Config.LED_GREEN));
    }

    @Test
    void updateScaleView_nonRow0Pads_areOff() {
        ledManager.updateScaleView(0, 0);
        ledManager.flush(sender);

        // All row 1–4 pads should be off
        for (int row = 1; row < Config.NUM_TRACKS; row++) {
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                final int r = row, c = col;
                assertTrue(sentMidi.stream()
                    .anyMatch(m -> m[1] == Config.PADS[r][c] && m[2] == Config.LED_OFF),
                    "Row " + row + " col " + col + " should be OFF");
            }
        }
    }

    @Test
    void updateRecordLed_melodic_sendsRed() {
        ledManager.updateRecordLed(true);
        ledManager.flush(sender);
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.RECORD && m[2] == Config.LED_RED));
    }

    @Test
    void updateRecordLed_drum_sendsOff() {
        // Set RED first, then switch to drum (OFF)
        ledManager.updateRecordLed(true);
        ledManager.flush(sender);
        sentMidi.clear();

        ledManager.updateRecordLed(false);
        ledManager.flush(sender);
        assertTrue(sentMidi.stream()
            .anyMatch(m -> m[1] == Config.RECORD && m[2] == Config.LED_OFF));
    }
}
```

- [ ] **Step 6.2: Run to verify it fails**

```bash
cd apc-key25-sequencer && mvn test -Dtest=LedManagerTest 2>&1 | tail -10
```

Expected: FAIL — `LedManager` does not exist

- [ ] **Step 6.3: Write LedManager.java**

```java
package com.apcsequencer;

import java.util.Arrays;

public class LedManager {

    /** Abstracts MidiOut.sendMidi for testability. */
    public interface MidiSender {
        void sendMidi(int status, int data1, int data2);
    }

    // Shadow state: [row * NUM_STEPS + col]
    private final int[] padLeds     = new int[Config.NUM_TRACKS * Config.NUM_STEPS];
    private final int[] prevPadLeds = new int[Config.NUM_TRACKS * Config.NUM_STEPS];

    // Button LED indices
    private static final int BTN_COUNT = 7;
    private final int[] btnLeds     = new int[BTN_COUNT];
    private final int[] prevBtnLeds = new int[BTN_COUNT];

    // btnLeds indices
    private static final int BTN_SCENE_0 = 0;
    private static final int BTN_SCENE_1 = 1;
    private static final int BTN_SCENE_2 = 2;
    private static final int BTN_SCENE_3 = 3;
    private static final int BTN_SCENE_4 = 4;
    private static final int BTN_RECORD  = 5;
    // index 6 reserved

    private boolean dirty = false;

    public LedManager() {
        // Initialize prev arrays to -1 so first flush sends everything
        Arrays.fill(prevPadLeds, -1);
        Arrays.fill(prevBtnLeds, -1);
    }

    public void updateSequencerView(TrackState[] tracks) {
        for (int row = 0; row < Config.NUM_TRACKS; row++) {
            TrackState t = tracks[row];
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                int led;
                if (t.muted) {
                    led = Config.LED_GREEN_BLINK;
                } else if (col == t.currentStep) {
                    led = t.steps[col] ? Config.LED_ORANGE : Config.LED_RED;
                } else {
                    led = t.steps[col] ? Config.LED_GREEN : Config.LED_OFF;
                }
                setPadLed(row, col, led);
            }
            // Scene Launch LED: green=active, off=muted
            setBtnLed(row, t.muted ? Config.LED_OFF : Config.LED_GREEN);
        }
    }

    public void updateScaleView(int scaleIndex, int activeTrack) {
        // Row 0: scale pads lit; rows 1–4: off
        for (int col = 0; col < Config.NUM_STEPS; col++) {
            int led = (col == scaleIndex) ? Config.LED_GREEN_BLINK : Config.LED_GREEN;
            setPadLed(0, col, led);
        }
        for (int row = 1; row < Config.NUM_TRACKS; row++) {
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                setPadLed(row, col, Config.LED_OFF);
            }
        }
    }

    public void updateRecordLed(boolean melodicMode) {
        int led = melodicMode ? Config.LED_RED : Config.LED_OFF;
        if (btnLeds[BTN_RECORD] != led) {
            btnLeds[BTN_RECORD] = led;
            dirty = true;
        }
    }

    public boolean isDirty() { return dirty; }

    public void flush(MidiSender sender) {
        // Flush pad LEDs
        for (int row = 0; row < Config.NUM_TRACKS; row++) {
            for (int col = 0; col < Config.NUM_STEPS; col++) {
                int idx = row * Config.NUM_STEPS + col;
                if (padLeds[idx] != prevPadLeds[idx]) {
                    sender.sendMidi(Config.NOTE_ON, Config.PADS[row][col], padLeds[idx]);
                    prevPadLeds[idx] = padLeds[idx];
                }
            }
        }
        // Flush Scene Launch button LEDs (indices 0–4)
        for (int i = 0; i < 5; i++) {
            if (btnLeds[i] != prevBtnLeds[i]) {
                sender.sendMidi(Config.NOTE_ON, Config.SCENE_LAUNCH[i], btnLeds[i]);
                prevBtnLeds[i] = btnLeds[i];
            }
        }
        // Flush Record LED
        if (btnLeds[BTN_RECORD] != prevBtnLeds[BTN_RECORD]) {
            sender.sendMidi(Config.NOTE_ON, Config.RECORD, btnLeds[BTN_RECORD]);
            prevBtnLeds[BTN_RECORD] = btnLeds[BTN_RECORD];
        }
        dirty = false;
    }

    private void setPadLed(int row, int col, int color) {
        int idx = row * Config.NUM_STEPS + col;
        if (padLeds[idx] != color) {
            padLeds[idx] = color;
            dirty = true;
        }
    }

    private void setBtnLed(int btnIdx, int color) {
        if (btnLeds[btnIdx] != color) {
            btnLeds[btnIdx] = color;
            dirty = true;
        }
    }
}
```

- [ ] **Step 6.4: Run to verify it passes**

```bash
cd apc-key25-sequencer && mvn test -Dtest=LedManagerTest
```

Expected: `BUILD SUCCESS`, 10 tests pass

- [ ] **Step 6.5: Commit**

```bash
git add apc-key25-sequencer/src/
git commit -m "feat: LedManager with shadow state and dirty-only flush"
```

---

## Task 7: InputHandler

**Files:**
- Create: `src/main/java/com/apcsequencer/InputHandler.java`
- Create: `src/test/java/com/apcsequencer/InputHandlerTest.java`

- [ ] **Step 7.1: Write failing InputHandlerTest.java**

```java
package com.apcsequencer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InputHandlerTest {

    private TrackState[]              tracks;
    private InputState                state;
    private ScaleManager              scaleManager;
    private List<int[]>               injected;   // {trackIdx, status, d1, d2}
    private Sequencer.NoteInputPort[] ports;
    private boolean                   saved;
    private boolean                   flushed;
    private InputHandler              handler;

    @BeforeEach
    void setup() {
        tracks      = new TrackState[5];
        for (int i = 0; i < 5; i++) tracks[i] = new TrackState(i + 1);
        state        = new InputState();
        scaleManager = new ScaleManager();
        injected     = new ArrayList<>();
        saved        = false;
        flushed      = false;

        ports = new Sequencer.NoteInputPort[5];
        for (int i = 0; i < 5; i++) {
            final int ti = i;
            ports[i] = (s, d1, d2) -> injected.add(new int[]{ti, s, d1, d2});
        }

        handler = new InputHandler(
            tracks, state, scaleManager, ports,
            () -> saved  = true,
            () -> flushed = true
        );
    }

    // ── Pad gestures ────────────────────────────────────────────────────────

    @Test
    void padQuickTap_togglesStepOn() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertTrue(tracks[0].steps[0]);
        assertTrue(saved);
        assertTrue(flushed);
    }

    @Test
    void padQuickTap_togglesStepOff() {
        tracks[0].steps[1] = true;
        handler.onPadMidi(0x90, Config.PADS[0][1], 100);
        handler.onPadMidi(0x80, Config.PADS[0][1], 0);
        assertFalse(tracks[0].steps[1]);
    }

    @Test
    void padHoldWithKnob_setsVelocityAndDoesNotToggle() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100); // press step
        handler.onPadMidi(0xB0, Config.KNOB_2, 80);      // velocity knob
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);   // release step
        assertFalse(tracks[0].steps[0]);                  // NOT toggled
        assertEquals(80, tracks[0].velocities[0]);
    }

    @Test
    void padHoldWithKnob3_setsGateLength() {
        handler.onPadMidi(0x90, Config.PADS[1][3], 100);
        handler.onPadMidi(0xB0, Config.KNOB_3, 64);      // gate = 64/127 ≈ 0.504
        handler.onPadMidi(0x80, Config.PADS[1][3], 0);
        assertEquals(64.0 / 127.0, tracks[1].gateLengths[3], 0.001);
    }

    @Test
    void padHoldWithKnob4_setsProbability() {
        handler.onPadMidi(0x90, Config.PADS[2][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_4, 63);      // prob = 63/127 ≈ 0.496
        handler.onPadMidi(0x80, Config.PADS[2][0], 0);
        assertEquals(63.0 / 127.0, tracks[2].probabilities[0], 0.001);
    }

    @Test
    void padHoldWithKnob5_setsNudge() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_5, 85);  // (85-64)/21 = 1
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(1, tracks[0].nudges[0]);
    }

    @Test
    void padHoldWithKnob6_setsRatchet() {
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_6, 96);  // (96/32)+1 = 4
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(4, tracks[0].ratchets[0]);
    }

    @Test
    void padHoldWithKnob7_melodicMode_setsChord() {
        tracks[0].melodicMode = true;
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_7, 64);  // 64/32 = 2
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(2, tracks[0].chordIntervals[0]);
    }

    @Test
    void padHoldWithKnob7_drumMode_doesNothing() {
        tracks[0].melodicMode = false;
        int originalChord = tracks[0].chordIntervals[0];
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_7, 64);
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(originalChord, tracks[0].chordIntervals[0]); // unchanged
    }

    @Test
    void padHoldWithKnob1_melodic_setsPitch() {
        tracks[0].melodicMode = true;
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_1, 64);
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(scaleManager.getPitch(64), tracks[0].notes[0]);
    }

    @Test
    void padHoldWithKnob1_drum_setsVelocity() {
        tracks[0].melodicMode = false;
        handler.onPadMidi(0x90, Config.PADS[0][0], 100);
        handler.onPadMidi(0xB0, Config.KNOB_1, 90);  // direct velocity
        handler.onPadMidi(0x80, Config.PADS[0][0], 0);
        assertEquals(90, tracks[0].velocities[0]);
    }

    // ── Pattern length ───────────────────────────────────────────────────────

    @Test
    void shiftPad_setsPatternLength() {
        state.shiftHeld = true;
        handler.onPadMidi(0x90, Config.PADS[2][4], 100);  // row 2, col 4 → length 5
        assertEquals(5, tracks[2].patternLength);
        assertTrue(saved);
    }

    // ── Scale selection ──────────────────────────────────────────────────────

    @Test
    void stopAllClipsHeld_row0Pad_selectsScale() {
        state.stopAllClipsHeld = true;
        handler.onPadMidi(0x90, Config.PADS[0][3], 100);
        assertEquals(3, scaleManager.getScaleIndex());
        assertTrue(saved);
    }

    @Test
    void stopAllClipsHeld_nonRow0Pad_ignored() {
        state.stopAllClipsHeld = true;
        handler.onPadMidi(0x90, Config.PADS[1][3], 100);
        assertEquals(0, scaleManager.getScaleIndex()); // unchanged
    }

    // ── Mode toggle ──────────────────────────────────────────────────────────

    @Test
    void recordButton_togglesMelodicOnActiveTrack() {
        state.activeTrack = 2;
        assertFalse(tracks[2].melodicMode);
        handler.onPadMidi(0x90, Config.RECORD, 100);
        assertTrue(tracks[2].melodicMode);
        handler.onPadMidi(0x90, Config.RECORD, 100);
        assertFalse(tracks[2].melodicMode);
    }

    // ── Mute / track selection ───────────────────────────────────────────────

    @Test
    void sceneLaunchTap_mutesAndUnmutes() {
        handler.onPadMidi(0x90, Config.SCENE_LAUNCH[1], 100);
        handler.onPadMidi(0x80, Config.SCENE_LAUNCH[1], 0);
        assertTrue(tracks[1].muted);
        handler.onPadMidi(0x90, Config.SCENE_LAUNCH[1], 100);
        handler.onPadMidi(0x80, Config.SCENE_LAUNCH[1], 0);
        assertFalse(tracks[1].muted);
    }

    // ── Keyboard gestures ────────────────────────────────────────────────────

    @Test
    void shiftKeyboard_setsRootNote() {
        state.shiftHeld = true;
        handler.onKeyboardMidi(0x90, 65, 100); // F (65 % 12 = 5)
        assertEquals(5, scaleManager.getRootNote());
        assertTrue(saved);
    }

    @Test
    void keyboard_livePlay_injectsToActiveTrack() {
        state.activeTrack = 3;
        handler.onKeyboardMidi(0x90, 60, 100);
        assertEquals(1, injected.size());
        int[] ev = injected.get(0);
        assertEquals(3, ev[0]);                    // track 3
        assertEquals(0x90 | 3, ev[1]);             // NoteOn ch 4 (0-indexed ch 3)
        assertEquals(60, ev[2]);
        assertEquals(100, ev[3]);
    }

    @Test
    void keyboard_noteOff_injectsNoteOff() {
        state.activeTrack = 0;
        handler.onKeyboardMidi(0x80, 60, 0);
        assertEquals(1, injected.size());
        assertEquals(0x80 | 0, injected.get(0)[1]); // NoteOff ch 1
    }

    @Test
    void padHoldKeyboard_setsStepNote_noToggle() {
        handler.onPadMidi(0x90, Config.PADS[1][2], 100); // hold step
        handler.onKeyboardMidi(0x90, 64, 100);            // press E
        handler.onPadMidi(0x80, Config.PADS[1][2], 0);   // release step
        assertFalse(tracks[1].steps[2]);                  // not toggled
        assertEquals(64, tracks[1].notes[2]);
    }

    @Test
    void shiftSceneLaunchPlusKeyboard_setsBaseNote() {
        state.shiftHeld = true;
        handler.onPadMidi(0x90, Config.SCENE_LAUNCH[2], 100); // hold scene 2
        handler.onKeyboardMidi(0x90, 48, 100);                 // press C3
        assertEquals(48, tracks[2].baseNote);
    }
}
```

- [ ] **Step 7.2: Run to verify it fails**

```bash
cd apc-key25-sequencer && mvn test -Dtest=InputHandlerTest 2>&1 | tail -10
```

Expected: FAIL — `InputHandler` does not exist

- [ ] **Step 7.3: Write InputHandler.java**

```java
package com.apcsequencer;

public class InputHandler {

    private final TrackState[]              tracks;
    private final InputState                state;
    private final ScaleManager              scaleManager;
    private final Sequencer.NoteInputPort[] noteInputs;
    private final Runnable                  saveCallback;
    private final Runnable                  flushCallback;

    /** True if a hold-gesture (knob/key) occurred while a step was held. */
    private boolean holdGestureHappened = false;

    public InputHandler(TrackState[] tracks, InputState state,
                        ScaleManager scaleManager,
                        Sequencer.NoteInputPort[] noteInputs,
                        Runnable saveCallback, Runnable flushCallback) {
        this.tracks        = tracks;
        this.state         = state;
        this.scaleManager  = scaleManager;
        this.noteInputs    = noteInputs;
        this.saveCallback  = saveCallback;
        this.flushCallback = flushCallback;
    }

    // ── Port 0: keyboard ────────────────────────────────────────────────────

    public void onKeyboardMidi(int status, int data1, int data2) {
        int type = status & 0xF0;
        if (type == 0x90 && data2 > 0) {
            onKeyboardNoteOn(data1, data2);
        } else if (type == 0x80 || (type == 0x90 && data2 == 0)) {
            onKeyboardNoteOff(data1);
        }
    }

    private void onKeyboardNoteOn(int note, int velocity) {
        // Priority 1: Shift + Scene Launch held → set drum base note
        if (state.heldSceneLaunch >= 0) {
            tracks[state.heldSceneLaunch].baseNote = note;
            saveCallback.run();
            return;
        }
        // Priority 2: Shift held alone → set global root note
        if (state.shiftHeld) {
            scaleManager.setRootNote(note);
            saveCallback.run();
            flushCallback.run();
            return;
        }
        // Priority 3: Step pad held → set step note (drum or melodic)
        if (state.heldStepNote >= 0) {
            tracks[state.heldStepTrack].notes[state.heldStepCol] = note;
            holdGestureHappened = true;
            saveCallback.run();
            return;
        }
        // Default: live play → inject to active track NoteInput
        int ch = tracks[state.activeTrack].midiChannel - 1;
        noteInputs[state.activeTrack].sendRawMidiEvent(0x90 | ch, note, velocity);
    }

    private void onKeyboardNoteOff(int note) {
        // Live play note-off (only relevant when no modifiers active)
        if (state.heldStepNote < 0 && !state.shiftHeld && state.heldSceneLaunch < 0) {
            int ch = tracks[state.activeTrack].midiChannel - 1;
            noteInputs[state.activeTrack].sendRawMidiEvent(0x80 | ch, note, 0);
        }
    }

    // ── Port 1: pads / buttons / knobs ──────────────────────────────────────

    public void onPadMidi(int status, int data1, int data2) {
        int type = status & 0xF0;
        if (type == 0x90 && data2 > 0) {
            onPadNoteOn(data1);
        } else if (type == 0x80 || (type == 0x90 && data2 == 0)) {
            onPadNoteOff(data1);
        } else if (type == 0xB0) {
            onKnob(data1, data2);
        }
    }

    private void onPadNoteOn(int note) {
        // Decode pad row/col
        int row = padRow(note);
        int col = padCol(note);

        if (row >= 0) {
            onStepPadPress(row, col, note);
            return;
        }

        // Special buttons
        switch (note) {
            case Config.SHIFT          -> state.shiftHeld = true;
            case Config.STOP_ALL_CLIPS -> { state.stopAllClipsHeld = true; flushCallback.run(); }
            case Config.RECORD         -> {
                tracks[state.activeTrack].melodicMode = !tracks[state.activeTrack].melodicMode;
                saveCallback.run();
                flushCallback.run();
            }
            default -> {
                // Scene Launch buttons
                for (int i = 0; i < Config.NUM_TRACKS; i++) {
                    if (note == Config.SCENE_LAUNCH[i]) {
                        onSceneLaunchPress(i);
                        return;
                    }
                }
            }
        }
    }

    private void onPadNoteOff(int note) {
        int row = padRow(note);
        int col = padCol(note);

        if (row >= 0) {
            onStepPadRelease(row, col, note);
            return;
        }

        switch (note) {
            case Config.SHIFT          -> { state.shiftHeld = false; state.heldSceneLaunch = -1; }
            case Config.STOP_ALL_CLIPS -> { state.stopAllClipsHeld = false; flushCallback.run(); }
            default -> {
                for (int i = 0; i < Config.NUM_TRACKS; i++) {
                    if (note == Config.SCENE_LAUNCH[i]) {
                        onSceneLaunchRelease(i);
                        return;
                    }
                }
            }
        }
    }

    private void onStepPadPress(int row, int col, int note) {
        // Scale view: Stop All Clips held
        if (state.stopAllClipsHeld) {
            if (row == 0 && col < Config.NUM_SCALES) {
                scaleManager.setScaleIndex(col);
                saveCallback.run();
                flushCallback.run();
            }
            return;
        }
        // Shift + pad: set pattern length for that row
        if (state.shiftHeld) {
            tracks[row].patternLength = col + 1;
            saveCallback.run();
            flushCallback.run();
            return;
        }
        // Normal: record step hold
        state.heldStepNote  = note;
        state.heldStepTrack = row;
        state.heldStepCol   = col;
        holdGestureHappened = false;
    }

    private void onStepPadRelease(int row, int col, int note) {
        if (state.heldStepNote == note) {
            if (!holdGestureHappened) {
                // Quick tap: toggle step
                tracks[row].steps[col] = !tracks[row].steps[col];
                saveCallback.run();
            }
            state.heldStepNote  = -1;
            state.heldStepTrack = -1;
            state.heldStepCol   = -1;
            holdGestureHappened = false;
            flushCallback.run();
        }
    }

    private void onSceneLaunchPress(int track) {
        if (state.shiftHeld) {
            if (state.heldSceneLaunch < 0) {
                // Start shift-scene hold
                state.heldSceneLaunch     = track;
                state.sceneLaunchPressTime = System.currentTimeMillis();
            } else if (state.heldSceneLaunch != track) {
                // Copy pattern A → B
                copyPattern(state.heldSceneLaunch, track);
                saveCallback.run();
                flushCallback.run();
            }
        } else {
            // Non-shift tap: mute/unmute
            tracks[track].muted = !tracks[track].muted;
            saveCallback.run();
            flushCallback.run();
        }
    }

    private void onSceneLaunchRelease(int track) {
        if (state.shiftHeld && state.heldSceneLaunch == track) {
            long now     = System.currentTimeMillis();
            long elapsed = now - state.sceneLaunchPressTime;
            if (elapsed < Config.HOLD_THRESH_MS) {
                // Quick tap
                if (now - state.sceneLaunchLastTap < Config.DOUBLE_TAP_MS) {
                    // Double-tap: clear pattern
                    tracks[track].reset();
                    saveCallback.run();
                    flushCallback.run();
                } else {
                    // Single tap: select active track
                    state.activeTrack = track;
                    flushCallback.run();
                }
                state.sceneLaunchLastTap = now;
            }
            state.heldSceneLaunch = -1;
        }
    }

    private void onKnob(int cc, int value) {
        if (state.heldStepNote < 0) return;  // only process when step held

        int knobIdx = cc - Config.KNOB_1;    // 0–7
        if (knobIdx < 0 || knobIdx > 7) return;

        TrackState t = tracks[state.heldStepTrack];
        int step     = state.heldStepCol;
        holdGestureHappened = true;

        switch (knobIdx) {
            case 0 -> {  // Knob 1: pitch (melodic) or velocity (drum)
                if (t.melodicMode) t.notes[step]      = scaleManager.getPitch(value);
                else               t.velocities[step] = value;
            }
            case 1 -> t.velocities[step]     = value;
            case 2 -> t.gateLengths[step]    = value / 127.0;
            case 3 -> t.probabilities[step]  = value / 127.0;
            case 4 -> t.nudges[step]         = (value - 64) / 21;
            case 5 -> t.ratchets[step]       = (value / 32) + 1;
            case 6 -> { if (t.melodicMode) t.chordIntervals[step] = value / 32; }
            case 7 -> t.ccValues[step]       = value;
        }

        saveCallback.run();
    }

    private void copyPattern(int from, int to) {
        TrackState src  = tracks[from];
        TrackState copy = src.deepCopy();
        TrackState dst  = tracks[to];
        // Copy all sequencer data, preserve midiChannel/currentStep
        dst.steps          = copy.steps;
        dst.notes          = copy.notes;
        dst.velocities     = copy.velocities;
        dst.gateLengths    = copy.gateLengths;
        dst.probabilities  = copy.probabilities;
        dst.nudges         = copy.nudges;
        dst.ratchets       = copy.ratchets;
        dst.chordIntervals = copy.chordIntervals;
        dst.ccValues       = copy.ccValues;
        dst.patternLength  = copy.patternLength;
        dst.melodicMode    = copy.melodicMode;
        dst.baseNote       = copy.baseNote;
    }

    // ── Pad decoding ────────────────────────────────────────────────────────

    private int padRow(int note) {
        for (int r = 0; r < Config.NUM_TRACKS; r++)
            for (int c = 0; c < Config.NUM_STEPS; c++)
                if (Config.PADS[r][c] == note) return r;
        return -1;
    }

    private int padCol(int note) {
        for (int r = 0; r < Config.NUM_TRACKS; r++)
            for (int c = 0; c < Config.NUM_STEPS; c++)
                if (Config.PADS[r][c] == note) return c;
        return -1;
    }
}
```

- [ ] **Step 7.4: Run to verify it passes**

```bash
cd apc-key25-sequencer && mvn test -Dtest=InputHandlerTest
```

Expected: `BUILD SUCCESS`, all tests pass

- [ ] **Step 7.5: Run all tests to confirm nothing broke**

```bash
cd apc-key25-sequencer && mvn test
```

Expected: `BUILD SUCCESS`, all tests pass

- [ ] **Step 7.6: Commit**

```bash
git add apc-key25-sequencer/src/
git commit -m "feat: InputHandler with full gesture parsing"
```

---

## Task 8: Extension Definition and Wiring

**Files:**
- Create: `src/main/java/com/apcsequencer/ApcKey25SequencerExtensionDefinition.java`
- Create: `src/main/java/com/apcsequencer/ApcKey25SequencerExtension.java`
- Create: `src/main/resources/META-INF/services/com.bitwig.extension.controller.ControllerExtensionDefinition`

- [ ] **Step 8.1: Write ApcKey25SequencerExtensionDefinition.java**

```java
package com.apcsequencer;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class ApcKey25SequencerExtensionDefinition extends ControllerExtensionDefinition {

    private static final UUID EXTENSION_ID =
        UUID.fromString("a4c1b2d3-e5f6-7890-ab12-cd34ef567890");

    @Override public String getName()    { return "APC Key 25 Polyrhythmic Sequencer"; }
    @Override public String getAuthor()  { return "APC Sequencer"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public UUID   getId()      { return EXTENSION_ID; }
    @Override public int    getRequiredAPIVersion() { return 19; }

    @Override public String getHardwareVendor() { return "Akai"; }
    @Override public String getHardwareModel()  { return "APC Key 25"; }
    @Override public int    getNumMidiInPorts()  { return 2; }
    @Override public int    getNumMidiOutPorts() { return 1; }

    @Override
    public void listAutoDetectionMidiPortNames(
            AutoDetectionMidiPortNamesList list, PlatformType platformType) {
        // Each add() call specifies one candidate set of port names.
        // inputNames.length == getNumMidiInPorts(), outputNames.length == getNumMidiOutPorts()
        if (platformType == PlatformType.WINDOWS) {
            list.add(
                new String[]{"APC Key 25", "APC Key 25 MIDI 2"},
                new String[]{"APC Key 25"}
            );
            list.add(
                new String[]{"APC Key 25 MIDI", "APC Key 25 MIDI 2"},
                new String[]{"APC Key 25 MIDI"}
            );
        } else if (platformType == PlatformType.MAC) {
            list.add(
                new String[]{"APC Key 25", "APC Key 25 Port 2"},
                new String[]{"APC Key 25"}
            );
        } else { // Linux
            list.add(
                new String[]{"APC Key 25 MIDI 1", "APC Key 25 MIDI 2"},
                new String[]{"APC Key 25 MIDI 1"}
            );
        }
    }

    @Override
    public ControllerExtension createInstance(ControllerHost host) {
        return new ApcKey25SequencerExtension(this, host);
    }
}
```

- [ ] **Step 8.2: Write ApcKey25SequencerExtension.java**

```java
package com.apcsequencer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;

public class ApcKey25SequencerExtension extends ControllerExtension {

    private TrackState[]  tracks;
    private InputState    inputState;
    private ScaleManager  scaleManager;
    private Sequencer     sequencer;
    private LedManager    ledManager;
    private InputHandler  inputHandler;

    // Persistence setting (DocumentState)
    private SettableStringValue persistenceSetting;

    // Out-parameter arrays for deserialization
    private final int[] scaleIdxHolder  = {0};
    private final int[] rootNoteHolder  = {0};
    private final int[] activeTrackHolder = {0};

    protected ApcKey25SequencerExtension(
            ApcKey25SequencerExtensionDefinition definition, ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {
        final ControllerHost host = getHost();

        // ── Tracks ──────────────────────────────────────────────────────────
        tracks = new TrackState[Config.NUM_TRACKS];
        for (int i = 0; i < Config.NUM_TRACKS; i++) tracks[i] = new TrackState(i + 1);

        // ── State objects ────────────────────────────────────────────────────
        inputState   = new InputState();
        scaleManager = new ScaleManager();
        ledManager   = new LedManager();

        // ── MIDI ports ───────────────────────────────────────────────────────
        final MidiIn  keyboard = getMidiInPort(Config.PORT_KEYBOARD);
        final MidiIn  pads     = getMidiInPort(Config.PORT_PADS);
        final MidiOut ledOut   = getMidiOutPort(Config.PORT_OUT);

        // ── NoteInputs (one per track; no masks → we inject everything manually) ──
        final Sequencer.NoteInputPort[] noteInputPorts =
            new Sequencer.NoteInputPort[Config.NUM_TRACKS];
        for (int i = 0; i < Config.NUM_TRACKS; i++) {
            // createNoteInput with no masks: Bitwig shows it as a virtual MIDI input
            // but no raw hardware MIDI auto-routes to it.
            final NoteInput ni = keyboard.createNoteInput("APC Seq Track " + (i + 1));
            noteInputPorts[i]  = ni::sendRawMidiEvent;
        }

        // ── Sequencer ────────────────────────────────────────────────────────
        sequencer = new Sequencer(
            tracks, noteInputPorts, scaleManager,
            (cb, delayMs) -> host.scheduleTask(cb, delayMs)
        );

        // ── Transport sync ───────────────────────────────────────────────────
        final Transport transport = host.createTransport();

        transport.isPlaying().addValueObserver(playing -> {
            if (playing) sequencer.start();
            else         sequencer.stop();
        });

        // addRawValueObserver gives the actual BPM as a double
        transport.tempo().addRawValueObserver(bpm -> sequencer.setBpm(bpm));

        // ── Input handler ────────────────────────────────────────────────────
        inputHandler = new InputHandler(
            tracks, inputState, scaleManager, noteInputPorts,
            this::save,
            host::requestFlush
        );

        keyboard.setMidiCallback((s, d1, d2) -> inputHandler.onKeyboardMidi(s, d1, d2));
        pads    .setMidiCallback((s, d1, d2) -> inputHandler.onPadMidi(s, d1, d2));

        // ── Preferences: configurable CC for knob 8 ─────────────────────────
        final SettableRangedValue knob8Setting = host.getPreferences().getNumberSetting(
            "CC Number (Knob 8)", "Sequencer", 0, 127, 1, "", 74
        );
        knob8Setting.addRawValueObserver(
            cc -> sequencer.setKnob8Cc((int) Math.round(cc))
        );

        // ── Persistence: restore on project load ─────────────────────────────
        persistenceSetting = host.getDocumentState().getStringSetting(
            "Sequencer State", "Sequencer", 65535, ""
        );
        persistenceSetting.addValueObserver(json -> {
            if (json != null && !json.isEmpty()) {
                PersistenceManager.deserialize(
                    json, tracks, scaleIdxHolder, rootNoteHolder, activeTrackHolder
                );
                scaleManager.setScaleIndex(scaleIdxHolder[0]);
                scaleManager.setRootNote(rootNoteHolder[0]);
                inputState.activeTrack = activeTrackHolder[0];
                host.requestFlush();
            }
        });

        host.showPopupNotification("APC Key 25 Sequencer initialized");
    }

    @Override
    public void flush() {
        final MidiOut ledOut = getMidiOutPort(Config.PORT_OUT);
        final LedManager.MidiSender sender =
            (status, d1, d2) -> ledOut.sendMidi(status, d1, d2);

        if (inputState.stopAllClipsHeld) {
            ledManager.updateScaleView(
                scaleManager.getScaleIndex(), inputState.activeTrack
            );
        } else {
            ledManager.updateSequencerView(tracks);
        }
        ledManager.updateRecordLed(tracks[inputState.activeTrack].melodicMode);
        ledManager.flush(sender);
    }

    @Override
    public void exit() {
        sequencer.stop();
    }

    private void save() {
        persistenceSetting.set(PersistenceManager.serialize(
            tracks,
            scaleManager.getScaleIndex(),
            scaleManager.getRootNote(),
            inputState.activeTrack
        ));
    }
}
```

- [ ] **Step 8.3: Write ServiceLoader discovery file**

Create `src/main/resources/META-INF/services/com.bitwig.extension.controller.ControllerExtensionDefinition`:

```
com.apcsequencer.ApcKey25SequencerExtensionDefinition
```

(Single line, no trailing whitespace or newline beyond the class name.)

- [ ] **Step 8.4: Compile to verify no errors**

```bash
cd apc-key25-sequencer && mvn compile 2>&1 | tail -20
```

Expected: `BUILD SUCCESS` — all Java files compile

- [ ] **Step 8.5: Commit**

```bash
git add apc-key25-sequencer/src/
git commit -m "feat: Extension definition and wiring (init/flush/exit)"
```

---

## Task 9: Build, Package, and Deploy

- [ ] **Step 9.1: Run all tests**

```bash
cd apc-key25-sequencer && mvn test
```

Expected: `BUILD SUCCESS`, all tests pass

- [ ] **Step 9.2: Build the fat JAR and deploy as .bwextension**

```bash
cd apc-key25-sequencer && mvn package
```

Expected: `BUILD SUCCESS`  
Check output:
```
[INFO] Copying apc-key25-sequencer-1.0.0.jar to
       /mnt/c/Users/sfrullo/Documents/Bitwig Studio/Extensions/ApcKey25Sequencer.bwextension
```

- [ ] **Step 9.3: Verify the .bwextension was created**

```bash
ls -lh "/mnt/c/Users/sfrullo/Documents/Bitwig Studio/Extensions/ApcKey25Sequencer.bwextension"
```

Expected: file exists, size ~500KB (fat JAR with Gson included)

- [ ] **Step 9.4: Verify ServiceLoader entry is in the JAR**

```bash
unzip -p apc-key25-sequencer/target/apc-key25-sequencer-1.0.0.jar \
  META-INF/services/com.bitwig.extension.controller.ControllerExtensionDefinition
```

Expected: `com.apcsequencer.ApcKey25SequencerExtensionDefinition`

- [ ] **Step 9.5: Commit**

```bash
git add apc-key25-sequencer/
git commit -m "build: package and deploy ApcKey25Sequencer.bwextension"
```

---

## Task 10: README (User Setup Instructions)

- [ ] **Step 10.1: Write README.md**

Create `apc-key25-sequencer/README.md`:

```markdown
# APC Key 25 Polyrhythmic Sequencer

A Bitwig 6 extension that turns the Akai APC Key 25 mk1 into a
5-track polyrhythmic step sequencer with drum and melodic modes.

## Requirements

- Bitwig Studio 6 (API v19)
- Akai APC Key 25 mk1
- Java 17 + Maven (for building)

## Building

```bash
cp "/mnt/c/Program Files/Bitwig Studio/bin/bitwig.jar" lib/bitwig-extension-api.jar
mvn package
```

This builds `target/apc-key25-sequencer-1.0.0.jar` and copies it as
`ApcKey25Sequencer.bwextension` to
`/mnt/c/Users/<user>/Documents/Bitwig Studio/Extensions/`.
Update `bitwig.extensions.dir` in `pom.xml` if your path differs.

## Bitwig Setup

1. Open Bitwig Studio → Settings → Controllers → Add Controller.
2. Select **Akai / APC Key 25 Polyrhythmic Sequencer**.
3. Set MIDI In 1 to your APC Key 25 keyboard port.
4. Set MIDI In 2 to your APC Key 25 pads/transport port.
5. Set MIDI Out to your APC Key 25 output port.
6. Create 5 Instrument tracks in Bitwig.
7. For each track N (1–5), set its MIDI Input to:
   `APC Key 25 Polyrhythmic Sequencer → APC Seq Track N`
8. Load an instrument on each track.
9. Press **Play** in Bitwig — the sequencer starts automatically.

> **If auto-detection fails:** check your MIDI port names in the
> Controller settings and manually select them. Common Windows port
> names: `APC Key 25` (keyboard) and `APC Key 25 MIDI 2` (pads).

## Controls

### Pad Grid (5 rows × 8 columns)

| Row | Track | MIDI channel |
|-----|-------|--------------|
| 0 (top) | Track 1 | Ch 1 |
| 1 | Track 2 | Ch 2 |
| 2 | Track 3 | Ch 3 |
| 3 | Track 4 | Ch 4 |
| 4 (bottom) | Track 5 | Ch 5 |

- **Tap pad** → toggle step on/off
- **Hold pad + turn Knob 1–8** → edit step parameter (see table below)
- **Hold pad + press keyboard key** → set note for that step

### Knobs (while holding a step pad)

| Knob | Drum mode | Melodic mode |
|------|-----------|--------------|
| 1 | Velocity | Note in scale |
| 2 | Velocity | Velocity |
| 3 | Gate length | Gate length |
| 4 | Probability | Probability |
| 5 | Nudge (micro-timing) | Nudge |
| 6 | Ratchet (1–4) | Ratchet |
| 7 | — (inactive) | Chord interval |
| 8 | MIDI CC value | MIDI CC value |

### Buttons

| Button | Action |
|--------|--------|
| **Record** | Toggle Drum ↔ Melodic for active track |
| **Scene Launch 1–5** | Tap = mute/unmute track |
| **Shift + Scene Launch tap** | Select active track |
| **Shift + Scene Launch double-tap (<400ms)** | Clear pattern |
| **Shift + hold Scene Launch A, tap Scene Launch B** | Copy pattern A → B |
| **Shift + pad** | Set pattern length (col+1 steps) for that row |
| **Shift + keyboard key** | Set global root note |
| **Shift + hold Scene Launch + keyboard key** | Set drum base note for that track |
| **Stop All Clips (hold)** | Enter Scale View (row 0 pads = 8 scales) |

### Scale View (hold Stop All Clips)

Tap any pad in row 0 to select one of 8 scales:

| Col | Scale |
|-----|-------|
| 0 | Cromatica |
| 1 | Maggiore |
| 2 | Minore Naturale |
| 3 | Dorian |
| 4 | Mixolydian |
| 5 | Pentatonica Maggiore |
| 6 | Pentatonica Minore |
| 7 | Blues |

### Preferences

**Bitwig → Controllers → APC Key 25 Sequencer → CC Number (Knob 8)**
Sets the MIDI CC number emitted by Knob 8 (default: CC 74 / Brightness).

## LED Reference (mk1 three-color)

| Color | Meaning |
|-------|---------|
| Off | Step inactive |
| Green solid | Step active |
| Red solid | Playhead on empty step |
| Orange | Playhead on active step (firing) |
| Green blink | Track muted |
| Red (Record LED) | Track in Melodic mode |

## Persistence

All patterns are saved inside the Bitwig project file automatically.
Open the **Studio I/O** panel → Sequencer State to inspect the raw JSON.
```

- [ ] **Step 10.2: Commit**

```bash
git add apc-key25-sequencer/README.md
git commit -m "docs: add user setup and controls README"
```

---

## Self-Review Checklist

### Spec Coverage

| Spec requirement | Covered by task |
|-----------------|----------------|
| 5 tracks × 8 steps | Task 2 (Config), Task 5 (Sequencer) |
| Polyrhythm (1–8 pattern length per track) | Task 5 (Sequencer.tick), Task 7 (InputHandler shift+pad) |
| Drum mode (baseNote per track) | Task 2 (TrackState.baseNote), Task 5 (resolveNote) |
| Melodic mode (note per step from scale) | Task 3 (ScaleManager), Task 7 (knob 1) |
| 8 step parameters via hold+knob | Task 7 (onKnob) |
| Scale selector (Stop All Clips + row 0) | Task 7 (onStepPadPress with stopAllClipsHeld) |
| Root note (Shift + keyboard) | Task 7 (onKeyboardNoteOn) |
| Drum base note (Shift + Scene Launch + key) | Task 7 (onKeyboardNoteOn priority 1) |
| Pattern copy (Shift + hold A + tap B) | Task 7 (onSceneLaunchPress) |
| Clear pattern (Shift + double-tap) | Task 7 (onSceneLaunchRelease) |
| Mute/unmute (Scene Launch tap) | Task 7 (onSceneLaunchPress non-shift) |
| Active track select (Shift + Scene Launch tap) | Task 7 (onSceneLaunchRelease single tap) |
| LED feedback (sequencer view) | Task 6 (LedManager.updateSequencerView) |
| Scale view LED feedback | Task 6 (LedManager.updateScaleView) |
| Record LED (melodic mode indicator) | Task 6 (LedManager.updateRecordLed) |
| Persistence (JSON in DocumentState) | Task 4 (PersistenceManager), Task 8 (Extension) |
| Global preferences (CC knob 8) | Task 8 (Extension.init) |
| Transport sync (play/stop/BPM) | Task 8 (Extension.init) |
| NoteInput × 5 for routing | Task 8 (Extension.init) |
| Live keyboard play | Task 7 (onKeyboardNoteOn default case) |
| mk1 absolute knobs (0–127) | Task 7 (onKnob direct mapping) |
| mk1 3-color LEDs (velocity 0–5) | Task 2 (Config LED constants), Task 6 |

### Placeholder Check

No "TBD", "TODO", or "fill in later" in any code block above. ✓

### Type Consistency Check

- `TrackState.notes[]` initialized to `Config.NOTE_SENTINEL` (-1) throughout ✓
- `Sequencer.NoteInputPort` interface used consistently in Sequencer, InputHandler, Extension ✓
- `Sequencer.TaskScheduler` used in Sequencer and Extension (`host.scheduleTask`) ✓
- `LedManager.MidiSender` used in LedManager and Extension (`ledOut.sendMidi`) ✓
- `scaleManager.getPitch(value)` used in InputHandler knob 1 and consistent with ScaleManager ✓
- `Config.PADS[row][col]` used consistently in LedManager flush and InputHandler pad decode ✓
- `Config.SCENE_LAUNCH[i]` used in LedManager flush and InputHandler scene launch ✓

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-09-apc-key25-sequencer.md`.**
