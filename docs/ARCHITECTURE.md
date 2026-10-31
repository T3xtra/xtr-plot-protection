# Architecture and Data Model Proposal

This document proposes a structure. The concrete implementation must fit the pinned Minecraft/Fabric version and verify the official API hooks.

## 1. Modules

- `PlotProtectionMod`: initialization, registrations and lifecycle.
- `domain`: immutable value objects and rules (plot, group, dimension key, bounds, protection mode).
- `service`: plot/group management, owner transfer, book lifecycle, marker registration, mode resolution.
- `protection`: central decision layer for block, inventory, entity, explosion, liquid, fire, piston and transport events.
- `regeneration`: snapshot creation, job scheduling, restoration, conflict resolution.
- `persistence`: versioned data model, serializer, atomic saving, backups, recovery and migrations.
- `commands`: player and admin commands.
- `book`: book detection, original book ID, page rendering, edit validation and synchronization.
- `config`: global configuration and validation.
- `test`: unit and integration tests.

Protection logic should not be duplicated in individual event callbacks. Callbacks collect context and delegate to central services.

## 2. Central value objects

### Plot
- `plotId`: stable internal ID
- `name`: display name
- `groupId`: unique group ID
- `dimensionKey`: registry key of the dimension
- `minX`, `maxX`, `minZ`, `maxZ`: inclusive bounds
- `minY`, `maxY`: inclusive bounds
- `protectionMode`: `UNPROTECTED | REGENERATIVE | PROTECTED`
- `allowDirectDoorInteraction`: Boolean
- `status`: `ACTIVE | REPAIR_PENDING | DISABLED`
- `markers`: mapping of the four corners to registered marker IDs/positions
- `repairDeadline`: optional point in time
- `createdAt`, `updatedAt`

### Group
- `groupId`: stable ID
- `name`
- `plotId`: unique back reference
- `ownerUuid`
- `memberUuids`: set of UUIDs; owner stored separately, membership must not overwrite the owner
- `bookId`: ID of the current valid original book

### MarkerRegistration
- `markerId`
- `plotId`
- `corner`: `NE | NW | SE | SW`
- `dimensionKey`
- `position`: BlockPos
- `status`: `VALID | MISSING | REPAIR_PENDING`
- `registeredAt`

### OriginalBookRegistration
- `bookId`: random, stable, unguessable ID
- `groupId`
- `valid`: bool or derived from the current registration
- `issuedAt`
- optional issuing context; no sensitive data

### RegenerationJob
- `jobId`
- `plotId` or affected region
- `createdAt`, `restoreNotBefore`, `restoreDeadline` or an explicit time model
- snapshot/block change entries
- inventory/block entity snapshots
- status and retry information
- idempotency key for every entry to be restored

The exact time model and the handling of overlapping jobs are still to be confirmed; see `OPEN_QUESTIONS.md`.

## 3. Central services

### `PlotService`
- Create and validate plot/group pairs
- Register/replace markers
- Status transitions
- Calculation of bounds
- Overlap check
- Deletion after explicit confirmation

### `PermissionResolver`
- Returns the effective permission for player actions.
- Takes into account dimension, block position, all overlapping plots and mode priority.
- With multiple plots: the strictest mode wins.
- Handles special rules for doors, inventories, markers, entities and PvP separately from generic block interaction.

### `BookService`
- Checks server-side whether an item is the registered original.
- Generates the system page from the canonical state.
- Parses only editable pages.
- Validates names and members against known player profiles.
- Synchronizes valid changes in both directions.
- Invalidates the previous original book ID on replacement.

### `RegenerationService`
- Captures reliable prior states before or during a protected change.
- Prevents drops/inventory gains for unauthorized actions.
- Schedules delayed restoration.
- Saves jobs before a server shutdown.
- Applies snapshots idempotently.
- Handles conflicts and locked areas deterministically.

### `PersistenceService`
- Loads data with schema version.
- Validates invariants before publishing the state.
- Saves via temporary file and atomic replacement.
- Creates backups.
- Attempts recovery on errors and logs the result.
- Prevents empty replacement for irreparable data.

## 4. Data invariants

Validate on load and before every commit:

1. Every plot has exactly one group; every group references exactly one existing plot.
2. Exactly one owner per group.
3. A marker registration belongs to at most one plot.
4. Active plots have four distinct valid markers, the same dimension and a consistent rectangle.
5. `minX <= maxX`, `minY <= maxY`, `minZ <= maxZ`.
6. A book registration refers to an existing group; at most one valid original book per group.
7. Player permissions are resolved via UUID.
8. Overlapping plots have the same owner, or the commit is rejected.
9. No data change is committed before validation is fully complete.
10. Persisted regeneration jobs have valid, uniquely identifiable entries.

## 5. Event processing

For every relevant action:
1. Determine context: dimension, position(s), actor, cause, block/entity and direct vs. indirect action.
2. Check marker special rules.
3. Find all affected plots.
4. Determine the effective protection mode with priority.
5. Apply the action-type-specific rule.
6. For regeneration, trigger snapshot/job logic and prevent drops/inventory transfer.
7. Log the result traceably without flooding the logs with every normal block event.

For events without a reliable player as the cause, the safe default rule according to the specification must be chosen: regenerate explosions/fire without an authorized initiator; always regenerate creeper damage.

## 6. Versioning and dependencies

Decided on 2026-10-29 (sources: `meta.fabricmc.net`, Mojang version manifest, official `fabric-example-mod`):

| Component | Version | Location |
|---|---|---|
| Minecraft | 26.3 (latest stable version, released 2026-09-15) | `gradle.properties` |
| Fabric Loader | 0.20.1 | `gradle.properties` |
| Fabric API | 0.165.0+26.3 | `gradle.properties` |
| Fabric Loom | 1.19.3 (exact, no `-SNAPSHOT`) | `gradle.properties` |
| Java | 25 (required by Minecraft 26.3; Gradle toolchain) | `build.gradle` |
| Gradle | 9.8.2 | `gradle/wrapper/gradle-wrapper.properties` |
| JUnit | 6.1.3 | `gradle.properties` |

Minecraft is no longer obfuscated since 26.1: there are no Yarn mappings anymore, the code uses the official Mojang names directly.

### Config and data format (B5)
- No additional library: JSON via the Gson (2.14) shipped with Minecraft.
- Global configuration: `config/xtr_plot_protection.json`. If the file is missing, defaults are written; missing keys (e.g. after an update) get their defaults and are written back; unparseable files or invalid values abort startup.
- World data: `<world>/xtr_plot_protection/plots.json` with `schemaVersion`, backups under `backups/plots-<epochMillis>.json`. Migrations are an ordered list in `WorldDataStore.MIGRATIONS` (entry i migrates version i+1 → i+2). Schema v2 added `regeneration` (pending block snapshots). Schema v3 changed `restoreAt` from epoch millis to overworld game ticks; the migration sets pending entries to 0, so they restore right after the upgrade.

### Regeneration (phase 6)
- One entry per dimension + block position with the block state and full block entity NBT as SNBT, the restore time and a `restored` flag (`domain/RegenerationEntry`).
- Capture happens before the change; a block already pending keeps its first snapshot and clock. Unauthorized breaking is performed by the mod itself without drops and without neighbour updates (attached blocks such as torches stay in place until restoration); inventories are emptied first.
- Restore times are overworld game time in ticks (`PlotRuntime.gameTime()`, persisted with the world), so `/tick rate|sprint|freeze` apply and regeneration pauses while the server is offline. The repair countdown stays wall-clock time.
- Blocks captured together (one explosion, one break) get random restore times within `regenerationSpreadSeconds`, assigned bottom-up so supporting blocks return before the blocks resting on them (sand, torches, plants); both halves of doors/beds/tall plants share one time. Once per second up to 20 × `regenerationBlocksPerTick` due entries are restored (oldest first), so the spread is visible in one-second steps (restoring every tick would mean 20 data file writes per second). Restoring overwrites block and block entity, so applying an entry twice is harmless. Failing entries are kept, logged and retried after the delay.
- Restored entries stay in the data file until `ServerLifecycleEvents.AFTER_SAVE`; on start, restored-but-unpruned entries are applied again. This covers a crash between restoration and world save.

### Hook ADR (current state of investigation)
- **Book editing (B1):** Fabric API offers no event. Server-side, every Book and Quill edit ends up in `ServerGamePacketListenerImpl.updateBookContents(List<FilteredText>, int slot)` (verified in 26.3 via `javap`). Implementation via a mixin on this method; validation via `BookPages.applyMemberPages`, then re-rendering of all pages from the canonical state.
- **Lifecycle/commands:** `ServerLifecycleEvents.SERVER_STARTING` (loading; an error aborts startup), `ServerTickEvents.END_SERVER_TICK` (expiry of repair countdowns), `CommandRegistrationCallback`, `ServerPlayConnectionEvents.JOIN` (book sync).
- **Markers:** `UseBlockCallback` (book on Lodestone), `PlayerBlockBreakEvents.BEFORE/AFTER` (marker removal only with the original book).
- **Protection (direct):** `UseBlockCallback`, `UseItemCallback` (buckets, lily pads), `AttackBlockCallback`, `PlayerBlockBreakEvents.BEFORE`, mixin on `AbstractContainerMenu.clicked` (taking items). All delegate to `PermissionResolver`.
- **Protection (indirect):** mixins on `ServerExplosion.calculateExplodedPositions`, `FireBlock.checkBurnOut`/`getIgniteOdds`, `LavaFluid.isFlammable`, `FlowingFluid.spreadTo`, `PistonStructureResolver.resolve`, `DispenserBlock.dispenseFrom`, `HopperBlockEntity.getSourceContainer`, `Level.destroyBlock` (entities), `FarmlandBlock.turnToBaseBlock`, enderman take/leave goals, `BreakDoorGoal.canUse`. All delegate to `PermissionResolver.decideIndirect`.
- **Entities:** `AttackEntityCallback`, `UseEntityCallback`, `ServerLivingEntityEvents.ALLOW_DAMAGE`, mixins on `hurtServer` of `BlockAttachedEntity`/`ItemFrame`/`VehicleEntity`/`ArmorStand`, `Entity.startRiding`, `FishingHook.pullEntity`. Player targets are always passed through, so PvP stays vanilla.
- **Bounds visualization:** every `boundsViewIntervalTicks` server ticks, `visual/BoundsView` sends dust particles to each player holding a Book and Quill via `ServerLevel.sendParticles(ServerPlayer, …)`, so only that player sees them.
- **Integration tests:** Fabric GameTests via Loom `fabricApi.configureTests` in `src/gametest`; they run as part of `./gradlew build`. `MixinTargetsTest` loads every mixin target so a broken injection fails the build.
- **Still to check (spikes B2–B4, B6):** explosion/fire causes, pistons, liquids, entity transport, order of marker removal vs. regeneration. Only together with implementing the protection phase.

### Translations
Vanilla clients do not have this mod's language files, so all player-facing texts are translated on the server (`i18n/Messages`) into the language the player selected in their client (`ServerPlayer.clientInformation().language()`). Files: `assets/xtr_plot_protection/lang/<locale>.json` for `en_us`, `de_de`, `fr_fr`, `it_it`, `es_es`, `zh_cn`, `ja_jp`. Fallback: exact locale → same language in another region (e.g. `de_at` → `de_de`) → `en_us`. A unit test checks that every file has the same keys and placeholders as `en_us`. Domain errors carry a message key (`PlotException`), not text.

### Known limits
- Book pages are stored in the item, not per viewer. Page 1 is rendered in the language of the player whose action last regenerated the book.
- The original book ID is stored in an item component and checked against the server-side registration. A bit-identical duplicate of the item (creative mode, duplication bugs) cannot be distinguished from the original. Vanilla does not allow copying Book and Quill via crafting.
- Originals are re-rendered when data changes only if they are in an online player's inventory (and on join). A book stored in a container shows stale content until it is picked up and edited or the player rejoins; an edit only applies the page-1 lines that differ from the book's previous content, so stale lines never revert newer changes.
- Using a book on a Lodestone also opens the book screen on the vanilla client (the client predicts the item use). The server closes it again with a `ClientboundContainerClosePacket` (the client closes any screen on it), so it flashes for one round trip. Closing it without changes sends nothing.
- Players are resolved only via online players and the server's name cache (`usercache.json`). An expired cache entry makes vanilla perform a blocking profile lookup for that known name.
- Owners who are offline when a marker is removed are not notified in chat; the book shows the status.
- Regeneration and crashes: if the server crashes after a world save but before the following prune is written (a window of milliseconds, longer with asynchronous chunk IO), an entry is applied again on start. That is harmless unless an authorized player changed the restored block in between, in which case the snapshot overwrites that change (an item taken from a restored chest could reappear).
- Snapshots are stored as SNBT of the running game version; pending entries are not run through Minecraft's data fixers when the game is updated. Let regeneration finish before upgrading Minecraft.
- Entities (item frames, armor stands, paintings) are not part of block snapshots; in `REGENERATIVE` plots they are protected like in `PROTECTED` instead of regenerated.
- Damage to mobs that cannot be attributed to a player (other mobs, creepers, fire, lava, fall damage) stays vanilla; only player-attributed damage is restricted. Decorations and vehicles are also protected against explosions and mobs.
- Inside a protected plot, empty boats/minecarts and those ridden by strangers do not pick up animals; this also applies to empty boats placed by members (a member has to ride along).
- Not covered: mobs leaving through water currents or portals built by members, villagers/animals following a stranger holding food (they walk out on their own), entities pushed by collisions.
- Block interactions are classified in `PlayerProtection.interaction`: doors/trapdoors/fence gates (door setting; iron ones count as a normal change because they ignore the click), harmless blocks that always consume the click (buttons, levers, ender chest, workstations), blocks that hand out items without a menu (lectern, flower pot, berries, beehive, composter, pumpkin carving, brushable blocks, cauldrons, vaults, chiseled bookshelf, decorated pot, jukebox, shelf, and any other container without a menu) count as taking items, containers with a menu as opening, everything else as a change. Item-yielding interactions not on that list (e.g. hoeing rooted dirt) are regenerated but may hand the stranger an item.
- Fire spread and burning cannot be attributed to a player; it always counts as unauthorized (deny in `PROTECTED`, regenerate in `REGENERATIVE`). Explosions are attributed via the explosion's indirect source entity (e.g. the player who lit TNT); beds/respawn anchors have none.
- Mechanics (liquids, pistons, dispensers, hoppers) are allowed only if their source position lies inside every protecting plot of the target. Droppers are not restricted (they only move items).
- Not yet covered indirect changes: silverfish, sheep eating grass, snow golems, falling blocks, projectiles hitting blocks (e.g. chorus flowers, pointed dripstone, target blocks), lightning, sculk spread, vines/plant growth into a plot, and commands such as `/setblock` (admin action).
