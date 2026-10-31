# Plot Protection (Fabric)

A server-side Minecraft Fabric mod for managing rectangular plots, groups, plot books, protection rules and regenerative restoration.

> **Rule for coding agents:** Read `AGENTS.md` first, then `docs/SPEC.md`, `docs/ARCHITECTURE.md`, `docs/OPEN_QUESTIONS.md` and `docs/IMPLEMENTATION_ORDER.md`. Never implement an unresolved product decision silently. Keep open points in `docs/OPEN_QUESTIONS.md` up to date.

## Status

In development. Implemented so far: toolchain, domain and persistence, groups and original books (incl. editing via Book and Quill), Lodestone markers and all `/plot` commands (`docs/COMMANDS.md`), protection modes with direct and indirect protection, regeneration and entity protection. Remaining release work (phase 8) is tracked in `docs/IMPLEMENTATION_ORDER.md`.

## Installation

Put the mod `.jar` (from the GitHub releases) together with Fabric API into the `mods/` folder of a Fabric server (Minecraft 26.3, Fabric Loader ≥ 0.20.1, Java 25). Players need no client mod.

## Usage

1. `/plot create <name>` (or use a Book and Quill on a Lodestone; its first line becomes the name). You get the plot's **original book**.
2. Use the original book on four Lodestones forming the corners of a rectangle. With the fourth marker the plot becomes active (height: lowest marker − 20 to highest marker + 40).
3. Page 1 of the book shows the plot; its lines for plot name, group, mode, doors, Min Y and Max Y can be edited. Pages 2+ list the members by player name. Whoever holds the original can edit it, so keep it safe.
4. Modes: `protected` (only members change anything), `regenerative` (strangers may change blocks, which are restored after the delay; no drops, no items from inventories), `unprotected`. Doors/trapdoors/fence gates can be opened for everyone with the door setting. PvP is never changed.
5. Markers can only be mined while carrying the original book; a removed marker starts the repair countdown.
6. While holding any Book and Quill, nearby plots are outlined with purple particles (the plot of the held original book in green); plots still being set up show columns at their registered markers. Only the holder sees them.

All commands: `/plot help` in game or `docs/COMMANDS.md`. Known limitations: `docs/ARCHITECTURE.md` (section "Known limits").

## Development

Toolchain: Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Java 25, Gradle 9.8.2 (details in `docs/ARCHITECTURE.md` §6).

```sh
./gradlew build        # compile + unit tests (requires JDK 25)
./gradlew runGameTest  # server-side integration tests in a fresh test world
./gradlew runServer    # dev server in run/
```

Every push to `main` builds the mod via GitHub Actions (`.github/workflows/release.yml`), runs the tests and publishes the `.jar` as GitHub release `v<version>-build.<n>`.

In-game texts follow each player's client language: English, German, French, Italian, Spanish, Simplified Chinese and Japanese (English fallback).

Configuration: `config/xtr_plot_protection.json` (created with defaults on first start; missing keys are added with defaults):

| Key | Default | Meaning |
|---|---|---|
| `heightBelowMarker` / `heightAboveMarker` | 20 / 40 | default plot height around the markers |
| `repairCountdownSeconds` | 600 | time to replace a removed marker |
| `backupCount` | 20 | number of rotating data backups |
| `regenerationDelaySeconds` | 120 | time from a block's first unauthorized change to its restoration |
| `regenerationSpreadSeconds` | 60 | blocks changed together (e.g. by one explosion) reappear one after another within this window, lowest first |
| `regenerationBlocksPerTick` | 20 | restoration batch limit |
| `boundsViewRange` | 32 | distance up to which plot outlines are shown |
| `boundsViewIntervalTicks` | 10 | how often plot outlines are redrawn |

OPs (level 3) can change the repair countdown and the regeneration values in game with `/plot config <key> <value>` (saved to the file, applies to changes from then on); `/plot config` shows the current values. Regeneration times count game ticks (20 per second), so they follow `/tick rate|sprint|freeze` and pause while the server is offline; the repair countdown is real time.

World data: `<world>/xtr_plot_protection/plots.json` plus `backups/`.

## Documents

- `AGENTS.md` – binding rules for Codex, Claude Code and other coding agents
- `docs/SPEC.md` – functional requirements and binding rules
- `docs/ARCHITECTURE.md` – proposed technical structure and data model
- `docs/COMMANDS.md` – commands, permissions and expected output
- `docs/TEST_PLAN.md` – test strategy and acceptance criteria
- `docs/OPEN_QUESTIONS.md` – decisions to be confirmed before or during implementation
- `docs/IMPLEMENTATION_ORDER.md` – recommended implementation in small, verifiable steps

## Principles

1. Server-side enforcement; no client mod required.
2. Server state is persistent and validated. Books are the editable management interface, but book copies are never authoritative.
3. Every protection decision is made server-side based on dimension, position, actor, action, group and protection mode.
4. Invalid data or a failed save must never replace the last valid state with empty or partial data.
5. PvP stays vanilla in all modes.
