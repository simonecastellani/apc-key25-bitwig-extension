# AGENTS.md

## What this is

A **Bitwig Studio 6 Java Extension** that turns the Akai APC Key 25 mk1 into a 5-track polyrhythmic/polymetric step sequencer. The extension is loaded by Bitwig at runtime — there is no standalone entry point.

## Technology Stack

- **Java 17** (JDK 17.0.2 available at `/tmp/opencode/jdk-17.0.2`), Maven 3.8+
- **Bitwig API 25**

## Build & Install

```bash
# Set up environment (paths below are for the local Java/Maven installs)
export JAVA_HOME=/tmp/opencode/jdk-17.0.2
export M2_HOME=/tmp/opencode/apache-maven-3.8.8
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH

cd apc-key25-sequencer
mvn package
```

`mvn package` compiles, runs all tests, creates a fat JAR (Gson bundled, Bitwig API excluded), and copies `ApcKey25Sequencer.bwextension` to Bitwig's extensions folder:

```
Windows/WSL: %USERPROFILE%\Documents\Bitwig Studio\Extensions
macOS: ~/Documents/Bitwig Studio/Extensions
Linux: ~/Bitwig Studio/Extensions
```

## Documentation

All the documentations should be stored in the `docs` folder.

| File | Scope | When to use |
|------|-------|-------------|
| `docs/apc-key-25-midi-implementation.md` | APC Key MK2 MIDI Implementation | Read as a reference to understand how to implement the MIDI I/O protocol between the APC Key 25 and Bitwig. **NOTE** This document if for MK2 but the project uses MK1 device, some differences may not be documented. |
| `docs/apc-key-25-midi-sniff.md` | APC Key MK1 MIDI code dump | Read as a reference of exact MIDI output code for each UI element |
| `docs/bitwig-docs.md` | A HTML-to-Markdown convertion of the Bitwig's API Specification | Read as a reference to implement the Bitwig Extension's code |
| `docs/bitwig-api-v25.txt` | A TXT version of the Bitwig's API v25 Specification from Maven repository | Read as a reference to implement the Bitwig Extension's code |
| `docs/adr/0001-notestep-clip-writing-over-internal-clock.md` | Architecture Decision Record | Rationale for using the NoteStep clip-writing API instead of an internal MIDI clock |

**NOTES**
- If a device's MIDI specification is not clear print debug messages to the `Controller Script Console` to check for the correct information and ask for feedbacks before continue with the actual implementation.
- Keep AGENTS.md's documentation index updated as new document are created.
- Always write documents in English

## MCP

- use `cavemem` MCP to manage cross-agent persistent memory

## Agent skills

### Issue tracker

Issues are tracked on GitHub Issues for this repository. See `docs/agents/issue-tracker.md` for conventions and `gh` CLI examples.

### Triage labels

This repository uses the canonical triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This repository is configured as single-context: `CONTEXT.md` at the repo root and ADRs under `docs/adr/`. See `docs/agents/domain.md`.
