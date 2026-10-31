# Instructions for Coding Agents

## Before every implementation step

1. Read `README.md` and all documents in `docs/`.
2. Check `docs/OPEN_QUESTIONS.md`. If an open point affects the next change, stop and ask instead of inventing the rule.
3. Check the current official Minecraft/Fabric compatibility information. Choose the Minecraft version that is the current stable release at project start and pin compatible versions of Fabric Loader, Fabric API, Java and Gradle. Record the decision in `docs/ARCHITECTURE.md` and the build files.
4. Implement small, compilable steps and run the relevant tests after each step.

## Non-negotiable rules

- Server-side logic; no client dependency for the core features.
- No custom permission management for administrative commands: use Minecraft OP levels. The concrete minimum level must be documented per command and verified before release.
- Stable internal IDs for plots, groups and registered original books. Players are identified permanently by UUID, not by mutable names.
- Every group belongs to exactly one plot and every plot to exactly one group (1:1).
- A plot only becomes active when exactly four valid Lodestone markers form the four horizontal corners, all in the same dimension key, and all markers are correctly registered.
- Coordinates alone must never create a marker registration. Markers must be registered by interacting with the valid original book on a Lodestone. Admin tools that accept coordinates must verify the existing marker registration.
- Book copies have no administrative authority whatsoever. Only the registered original can register markers and change the plot record via book editing.
- The first book page is system-generated. Only its setting lines (plot name, group name, protection mode, door setting, minY, maxY) are parsed back; invalid values are ignored and the page is regenerated from the data model. Display-only lines (owner, status, dimension, markers) are never accepted as input. From page 2 onwards, the member list is editable.
- Book edits are not blocked based on the editor's identity: every valid edit of the editable member pages is accepted, even if it does not come from the owner. This is a deliberate product decision.
- The owner is stored separately and must not be overwritten by editing the member list.
- Validate every data change completely first. Invalid changes must not corrupt the last valid state.
- Perform saves atomically, provide schema versioning/migrations, and create a backup before replacing a valid data file.
- If the data file is corrupt, attempt backup recovery. If no valid backup exists: fail safely, log clearly, and never silently start with empty data.
- Persist running regeneration jobs including block state and relevant block entity/inventory data. Recovery must be idempotent and must neither duplicate nor lose items.
- PvP remains fully vanilla in all protection modes.
- No protection bypass through indirect actions: explosions, fire, liquids, pistons, vehicles, leads, boats, minecarts, redstone and actors outside a plot must be checked.
- No silent product decisions. Record unresolved details in `docs/OPEN_QUESTIONS.md`.

## Quality

- Use clear modules with as little global state as possible.
- Write unit tests for geometry, overlap, mode priority, UUID/member resolution, book validation and data migration.
- Write integration tests for protection events, inventory safeguarding and restart during regeneration, as far as the test setup allows.
- Every protection decision should be central and traceable; avoid scattered, easily contradictory event handlers.
- Document known limits of the server-side Fabric hooks, especially for book editing and for Minecraft mechanics that cannot be reliably attributed to a player.
- No test may unnoticeably access an external server instance or a real production world.

## Completion criteria per work package

- Code compiles.
- Relevant tests pass.
- No regression in protection priorities or persistence.
- Documentation and `docs/OPEN_QUESTIONS.md` are updated.
- If the data format changes, migration tests and a backup/recovery test exist.
