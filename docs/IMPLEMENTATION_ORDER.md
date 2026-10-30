# Recommended Implementation Order

Each package should compile separately, contain tests and land as a small, reviewable commit.

## Progress and handoff

Status: ✅ done · 🟡 partly done (missing part noted) · ⬜ open. Keep these markers up to date after every work package.

- Domain code is pure Java without Minecraft types (`xtr.plotprotection.domain`); Minecraft wiring lives in `PlotProtectionMod`, `command`, `i18n`.
- The dedicated dev server starts with the mod (`./gradlew runServer`). Server-side integration tests run as Fabric GameTests (`src/gametest`, `./gradlew runGameTest`, fresh world in `build/`); they use mock players, a real client was never connected.
- The project machine has no JDK 25 and none may be installed there. Build with a JDK downloaded to a temporary directory (`JAVA_HOME=<jdk> ./gradlew build runGameTest`) or via the GitHub workflow (`.github/workflows/release.yml`, runs on push to `main`).
- A2, A3, A4, A6, A8, A9 were decided on 2026-10-30 (see `OPEN_QUESTIONS.md`).

## Phase 0 – Toolchain and technical spike tests
1. ✅ Select and pin the current stable Minecraft version and compatible Fabric/Java/Gradle versions.
2. 🟡 Build a minimal Fabric server mod, compile it and start it on a dedicated server as well as an integrated server. — dedicated dev server and GameTest server start; integrated server (singleplayer) not tested
3. ✅ Check how Book and Quill editing is intercepted server-side. — mixin on `updateBookContents`/`signBook`, GameTest `bookEditAppliesValidSettingsAndResetsInvalidOnes`
4. ✅ Spike tests for explosion/fire causes, pistons/liquids, inventory snapshots and entity transport. — replaced by the GameTests of phases 5–7
5. ✅ Document results and API limitations. Resolve product decisions from `OPEN_QUESTIONS.md`. — hooks and limits in ARCHITECTURE; all product decisions resolved

## Phase 1 – Domain and persistence
1. ✅ Implement value objects and data invariants. — `domain/*`, `Invariants`
2. ✅ Geometry and dimension logic with unit tests. — `Bounds`, `BoundsTest`
3. ✅ Versioned storage format, atomic writes, backups and recovery. — `WorldDataStore`
4. ✅ Migrations and tests for corrupt data. — migration list is empty (schema v1), `WorldDataStoreTest`
5. ✅ Do not activate protection hooks before loading/saving is reliable.

## Phase 2 – Groups and book
1. ✅ Create group/plot 1:1. — `/plot create`, book on Lodestone (`MarkerHandler`)
2. ✅ UUID resolution and member management. — `PlotRuntime.resolve` uses only online players and the server's name cache, never a remote lookup
3. ✅ Original book registration, copy detection and replacement book. — `OriginalBooks`; bit-identical duplicates are a known limit (ARCHITECTURE §6)
4. ✅ Generate system page; validate member pages; editable setting lines on page 1 (A9).
5. ✅ Synchronize command and book changes. — every commit re-renders originals in online inventories; join re-renders; edits only apply lines changed since the last render
6. 🟡 Tests for full inventories and invalidation of old originals. — invalidation tested (unit + GameTest); full inventory check implemented (`requireFreeSlot`) but not tested

## Phase 3 – Markers and plot status
1. ✅ Registration only via original book interaction on a Lodestone. — `MarkerHandler.onUse`, GameTests
2. ✅ Four corners, dimension, bounds and automatic heights.
3. ✅ Marker uniqueness and overlap validation.
4. 🟡 Repair countdown, status changes and replacement markers. — player break hook done (only with original book, GameTest); indirect destruction (explosions, pistons, …) is phase 5
5. ✅ Explicit delete confirmation when four markers are missing. — clickable offer to the owner, `/plot delete <group> confirm`

## Phase 4 – Commands
1. ✅ `/plot list`
2. ✅ `/plot groups`
3. ✅ `/plot book`
4. ✅ `/plot book <group-name-or-id>`
5. ✅ Owner transfer and necessary admin support. — `/plot transfer`, `/plot set`, `/plot delete`, `/plot create`
6. ✅ Update `COMMANDS.md` with concrete OP levels.

## Phase 5 – Central protection decision
1. ✅ PermissionResolver with mode priority. — `domain/PermissionResolver`, `PermissionResolverTest`
2. ✅ Protected: block and inventory interactions. — `protection/PlayerProtection`, container click mixin, GameTests
3. ✅ Door/trapdoor/fence gate setting. — GameTests
4. 🟡 Redstone and indirect actions. — explosions, fire, lava ignition, liquids, pistons, dispensers, hoppers, entity `destroyBlock`, farmland trampling, endermen, door-breaking mobs (`protection/IndirectProtection` + mixins); not covered: see ARCHITECTURE known limits
5. ✅ Marker protection and actions from outside. — markers are never changed indirectly, GameTests for explosions and pistons
6. ✅ PvP regression tests. — `EntityProtectionTest` (players are never protection targets)

## Phase 6 – Regeneration
1. ✅ Snapshot and drop prevention. — `regeneration/WorldRegeneration`; unauthorized breaking/explosions/mob breaking remove blocks without drops
2. ✅ Block entity/inventory safeguarding. — full block entity NBT (A4), inventories emptied before removal
3. ✅ Delay/duration scheduling according to the confirmed time model. — per block (A3), `regenerationDelaySeconds`, `regenerationBlocksPerTick`
4. ✅ Idempotent restoration and conflict resolution. — restoring overwrites; entries are pruned only after a world save
5. ✅ Persistence and resumption after restart. — entries in `plots.json` (schema v2, migration test); restored-but-unsaved entries are re-applied on start (unit tests + GameTest re-apply without duplication); no real server restart in tests
6. 🟡 Test explosion, fire, creeper, liquid, piston and transport. — GameTests for breaking, placing, fluids, explosions; creeper/fire rules unit-tested only; transport is phase 7

## Phase 7 – Entities
1. ✅ Protection against damage/death by unauthorized players. — `protection/EntityProtection`: living entities via `ServerLivingEntityEvents.ALLOW_DAMAGE` (player-attributed damage incl. projectiles, TNT, tamed animals), item frames/paintings/armor stands/boats/minecarts via `DecorationHurtMixin`
2. ✅ Permitted harmless interactions. — feeding/breeding (not taming), milking, shearing, trading
3. ✅ Boats, minecarts, leads and other transport mechanics. — leashing/riding denied, vehicles only pick up protected entities with an authorized rider (`EntityMixin.startRiding`), fishing rods cannot pull them
4. ✅ PvP remains unchanged. — GameTest

## Bounds visualization (A1)
1. ✅ Particle outlines for Book and Quill holders. — `domain/BoundsOutline` (unit tests), `visual/BoundsView` (smoke GameTest; particles not checked on a real client)

## Phase 8 – Hardening and release
1. 🟡 Complete test plan. — 62 unit tests, 25 GameTests (`./gradlew build`); manual acceptance (TEST_PLAN §5: real clients, singleplayer, restart with running timers) not done
2. ⬜ Load/performance tests and controlled regeneration batches. — batches are limited (`regenerationBlocksPerTick`); plot lookups are linear scans (`WorldData`), no load test yet
3. ✅ Backup/recovery test with corrupt files. — `WorldDataStoreTest` incl. v1→v2 migration with backup
4. ✅ README with installation, configuration, commands and known limitations.
5. 🟡 Meet the release gate from `TEST_PLAN.md`. — see below
6. 🟡 Leave no open security-critical points unchecked. — known limits are listed in `ARCHITECTURE.md`

### Release gate status (2026-10-30)
- Item duplication during regeneration: restoring overwrites (idempotent, tested). Remaining theoretical case: crash between a world save and the following prune after a member took items from a restored container (`ARCHITECTURE.md` known limits).
- Inadmissible overlap: rejected on every commit (invariants), strictest plot wins per position (unit tests).
- Copies as original: forged and replaced books are rejected (GameTest); bit-identical duplicates (creative mode, dupe bugs) cannot be told apart (known limit).
- Corrupt data: backup recovery or refusal to start, never empty data (unit tests).
- PvP: unchanged (GameTest).
- Open product decisions: none (A1 implemented as `visual/BoundsView`).
