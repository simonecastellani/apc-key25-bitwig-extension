# AGENTS.md

## What this is

A **Bitwig Studio 6 Java Extension** that turns the Akai APC Key 25 mk1 into a 5-track polyrhythmic/polymetric step sequencer. The extension is loaded by Bitwig at runtime — there is no standalone entry point.

## Technology Stack

- **Java 25**, Maven 3.8+
- **Bitwig API 25**

## Build & Install

```bash
# Set up environment (paths below are for the local Java/Maven installs)
export JAVA_HOME=/tmp/opencode/jdk-25
export M2_HOME=/tmp/opencode/apache-maven-3.8.8
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH

cd apc-key25-sequencer
mvn package
```

`mvn package` compiles, runs all tests, creates a fat JAR (Gson bundled, Bitwig API excluded), and copies `ApcKey25Sequencer.bwextension` to Bitwig's extensions folder:

```
Windows: %USERPROFILE%\Documents\Bitwig Studio\Extensions
macOS: ~/Documents/Bitwig Studio/Extensions
Linux: ~/Bitwig Studio/Extensions
```

## Documentation

All the documentations should be stored in the `docs` folder.

| File | Scope | When to use |
|------|-------|-------------|
| `docs/apc-key-25-midi-implementation.md` | APC Key MK2 MIDI Implementation | Read as a reference to understand how to implement the MIDI I/O protocol between the APC Key 25 and Bitwig. **NOTE** This document if for MK2 but the project uses MK1 device, some differences may not be documented. |
| `docs/apc-key-25-midi-sniff.md` | APC Key MK1 MIDI code dump | Read as a reference of exact MIDI output code for each UI element |
| `docs/bitwig-api-v25.txt` | A TXT version of the Bitwig's API v25 Specification from Maven repository | Read as a reference to implement the Bitwig Extension's code |

**NOTE** If a device's MIDI specification is not clear print debug messages to the `Controller Script Console` to check for the correct information and ask for feedbacks before continue with the actual implementation.

**NOTE** Keep AGENTS.md's documentation index updated as new document are created.

## MCP

- use `cavemem` MCP to manage cross-agent persistent memory

## Agent skills

### Skill → Scenario mapping

- caveman: produce extremely concise responses or summaries.
- commit-work: create high-quality commits, stage and commit changes (use only when explicitly allowed).
- diagnose: reproduce bugs, propose fixes, add instrumentation guidance.
- grill-with-docs: question or stress-test a plan; use grill-with-docs when you want checks against existing docs/ADRs.
- handoff: produce a concise handoff for a human or another agent.
- improve-codebase-architecture: propose larger architectural improvements.
- tdd: write failing tests first and implement code to pass them.
- to-issues: turn a problem/idea into a well-formed GitHub issue.
- to-prd: produce a product/feature spec for larger work.
- triage: triage a list of incoming issues/bug reports.
- zoom-out: provide a high-level summary of a code area or system.

### High-level workflow

1. Prepare context: grill-with-docs, reproduction steps, failing commands, affected files, constraints, and desired deliverable.
2. Classify the task: bug / feature / docs / triage / release.
3. Select the skill (see mapping below).
4. Invoke the skill with a structured prompt (template below).
5. Review the agent output, run tests locally, iterate if needed.
6. If changes are correct and user approved, use the commit-work skill.

### Example flows
- New feature: 
    1. grill-with-docs 
    2. to-prd 
    3. to-issues 
    4. tdd loop
    5. adversarial review 
    6. commit-work

- Small bug fix: 
    1. diagnose 
    2. tdd loop
    3. adversarial review 
    4. commit-work

- Big bug fix:
    1. diagnose 
    2. to-issues 
    3. tdd loop
    4. adversarial review 
    5. commit-work

### Prompt template
- Goal: <one short sentence>
- Background: <why / expected behavior>
- Reproduce: <commands to reproduce + failing output> # if in bugs workflow
- Files: <list of files or directories to inspect>
- Tests: <command(s) to run>
- Constraints: <max LOC, do-not-touch paths, performance, etc.>
- Deliverable: <diff | commit | tests | issue body | PR body>
- Commit permission: <yes/no> (default no)
- Branch name (if commit permission yes): <suggested branch>

### Issue tracker

Issues are tracked in GitHub Issues for this repository and should be managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the canonical triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

This repo is configured as single-context (root `CONTEXT.md` and `docs/adr/`; proceed silently if missing). See `docs/agents/domain.md`.