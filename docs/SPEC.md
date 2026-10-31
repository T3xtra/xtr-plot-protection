# Functional Specification

## 1. Goal and platform

The mod manages plots on a Minecraft server. The goal is a server-side Fabric mod that works both on dedicated servers and on the integrated server in singleplayer. A client mod must not be required for core features. The management interface consists of vanilla books and chat commands.

At project start, the current stable Minecraft version is determined. Minecraft, Fabric Loader, Fabric API, Java and Gradle must be compatible and pinned exactly.

## 2. Terms

- **Plot:** Rectangular, axis-aligned area in one dimension with defined minY/maxY bounds.
- **Group:** Member and owner assignment of a plot. The relationship group ↔ plot is exactly 1:1.
- **Original book:** The server-side registered management book for a group/plot. Copies are not authoritative.
- **Marker:** One of the four registered Lodestones that define the horizontal corners of the plot.
- **Authorized member:** Player whose UUID is stored as a group member, including the owner.
- **Unauthorized player:** Player who is neither owner nor group member.
- **Protection mode:** `UNPROTECTED`, `REGENERATIVE` or `PROTECTED`.
- **Active plot:** A plot with four valid, correctly registered markers and validated data.
- **Disabled plot:** A registered plot whose protection is currently not active, for example after a marker repair countdown has expired.

## 3. Plot geometry

1. Exactly four Lodestones define the corners northeast (NE), northwest (NW), southeast (SE) and southwest (SW) in X/Z.
2. The area is horizontally axis-aligned. Markers may have different Y coordinates.
3. All four markers must be in the same dimension. The dimension is part of the plot key; identical coordinates in another dimension do not overlap.
4. A Lodestone can be assigned to at most one plot or original book.
5. A marker is registered only by using the valid original book on a Lodestone. Typed coordinates do not create a marker registration.
6. A plot becomes active automatically as soon as four distinct, valid corners are registered and all plot data has been validated. There is no activation command.
7. Plot bounds are inclusive.
8. Default height on the first complete registration: `minY = lowest marker Y − 20`; `maxY = highest marker Y + 40`. These values are stored as explicit plot values. Later changes to other book fields must not reset manually changed heights.
9. Plot name and group name can be changed. Internal IDs remain stable.

## 4. Overlaps

- Overlaps are only allowed if the owner UUID of all overlapping plots is identical.
- For overlaps, the strictest mode applies per affected position: `PROTECTED > REGENERATIVE > UNPROTECTED`.
- A more permissive setting (e.g. direct door opening) must not override a stricter protection decision from another overlapping plot.
- An owner transfer must be validated against the overlap rule. Recommended safe implementation: reject the transfer if it would create an inadmissible overlap.

## 5. Protection modes

### 5.1 Unprotected

All players may modify and interact with blocks inside the plot normally. Special rules for registered markers and management books still apply. PvP is vanilla.

### 5.2 Regenerative

Unauthorized players and unauthorized causes may trigger changes but must not receive drops or inventory gains from them. After a configurable delay and over a configurable duration, the area is reset to the saved initial state.

Binding rules:

- Unauthorized mining yields no block drops.
- Unauthorized players receive no items from block entity inventories. Ender chests are exempt from this restriction.
- If a chest or another inventory object is destroyed without authorization, the relevant inventory contents must be stored temporarily and safely and restored during restoration. The triggering player must not receive anything from it.
- Regeneration survives server restarts.
- Explosion or fire damage regenerates if no authorized player caused it.
- Creeper damage always regenerates, even if an authorized player triggered/lured the creeper.
- If an authorized player caused an explosion or a fire, vanilla behavior applies, except for the creeper special rule and for registered markers.
- Liquids, fire, pistons, explosions and other indirect changes must be checked for bypasses.
- Registered Lodestone markers are treated as if they had never been removed. They must not be permanently removed or moved by explosions, fire, liquids, pistons or other mechanics.
- Regeneration delay and duration are globally configurable; details on the time model, conflicts and block entity special cases are recorded in `OPEN_QUESTIONS.md`.

### 5.3 Protected

- Only authorized players may directly mine or modify protected blocks and directly open/manipulate protected inventories.
- Non-player entities inside the plot are protected from damage, death and unauthorized removal (see section 7).
- Indirect actions from outside the plot must not bypass protection.
- The option for direct door/trapdoor/fence gate opening can allow direct player interaction with these three block types. It does not allow any other inventory or block manipulation.
- PvP remains vanilla.

## 6. General interactions and inventories

- Unauthorized players may still operate buttons, levers, pressure plates and redstone circuits, as long as no protected blocks are directly manipulated by doing so. Redstone may operate doors/mechanisms normally.
- Dispensers and droppers may be triggered by buttons/redstone and output items; the mod should not block all redstone outputs across the board.
- Direct inventory manipulation in protected block inventories remains blocked.
- Ender chests are the explicit exception to the ban on taking items from chests. The desired vanilla access depth for ender chests should be preserved as fully as technically possible.
- The door/trapdoor/fence gate setting only affects direct player interaction with doors, trapdoors and fence gates. It applies in `REGENERATIVE` and `PROTECTED`; in `UNPROTECTED` it is irrelevant as there are no restrictions.

## 7. Entities

- Animals, villagers and other non-player entities are protected.
- Unauthorized players must not hurt, kill, abduct or move such entities out of the plot.
- Harmless interactions remain allowed: feeding, breeding, milking, shearing and trading with villagers.
- Transport via boats, minecarts, leads and comparable mechanics must not be usable to remove entities from a plot without authorization.
- PvP between players remains vanilla in all modes. Non-player entity protection must not accidentally change player-versus-player damage.

## 8. Groups and members

1. Every plot has exactly one group, every group exactly one plot.
2. When a plot is created, a group is created automatically. The creator becomes the owner and initially the only member. Every player can create a plot via `/plot create <name>` or by using an unregistered Book and Quill on an unregistered Lodestone (see `OPEN_QUESTIONS.md` A8).
3. Each group has exactly one owner.
4. The owner can transfer the group. An OP can transfer it in an emergency.
5. Players can be members of multiple groups.
6. The member list is edited in the book from page 2 onwards.
7. A known player name is added to the group immediately; no additional confirmation.
8. Only players whose identity the server already knows and whose name can be resolved to a UUID are accepted. Unknown or misspelled names are not stored as active members.
9. The UUID is the permanent identity. On a name change, the permission is kept; the display should update to the current name as soon as the server knows it.
10. The owner is stored separately and must not be replaced or removed by editing the member list.
11. **Deliberate security decision:** Editing the editable book pages is not bound to the editor's identity. Every valid edit is accepted, including from non-owners. Anyone with access to the original book can therefore change the member list.
12. Commands change the canonical server state and synchronize the original book. Book editing updates the canonical server state. The system page is always regenerated.
13. Only the registered original book can change configuration. Copies have no effect.
14. Issuing a replacement book creates a new original and invalidates the previous original.

## 9. Original book and pages

- Use an editable vanilla book format (presumably "Book and Quill"), since signed books cannot be edited. The feasibility of server-side interception and validation must be verified for the pinned Fabric version.
- Page 1 is generated by the system. Its setting lines (plot name, group name, protection mode, door setting, minY, maxY) may be edited: valid values are applied, invalid values are ignored. Display-only lines are never accepted as input. After every edit, page 1 is regenerated from the canonical state.
- Page 1 shows at least: plot name, group name, four marker coordinates and marker status, dimension, minY/maxY, protection mode, direct door opening setting and plot status.
- Pages 2 and following contain the editable member list.
- Valid changes to the member list are applied immediately. Invalid entries are rejected or not stored as active membership and are reported understandably.
- The original book identity must be linked server-side to a stable ID and a verifiable registration; item NBT/components alone must not serve as the sole proof of authority.
- When a replacement book is issued, the old original book ID becomes invalid. Copies remain invalid.

## 10. Marker removal, repair and deletion

- A registered Lodestone can only be broken manually if the player has the associated original book in their inventory.
- When a marker is removed, a configurable repair countdown starts. The owner is notified; the book status turns orange/yellow.
- If a new Lodestone is registered as a replacement with the associated original book before the countdown expires, the plot is reactivated, the countdown is cancelled and the status turns green.
- If the countdown expires, the plot is disabled and the status turns red. The plot is not deleted automatically.
- If all four markers have been broken, the owner receives a chat message with a clickable option to delete. Deletion only happens after explicit confirmation.
- On deletion, plot and group are removed. The original book is invalidated and, if it can be found, removed.
- An admin can obtain a valid book via the replacement book command, then remove markers and delete the plot.
- The interaction between permitted marker removal, regeneration and the countdown must be implemented so that legitimate removal remains possible, but unwanted destruction does not have a permanent effect. Details see open questions.

## 11. Commands

See `COMMANDS.md` for exact syntax, authorization, output and error cases. All commands are validated server-side.

## 12. Persistence and configuration

### World-related data

Dedicated persistent data file in the world folder for:
- plots including dimension, bounds, protection mode and marker status
- groups, owner UUID and member UUIDs
- mapping plot ↔ group
- marker registrations and original book IDs
- running repair countdowns
- regeneration jobs and saved block/block entity/inventory data
- schema version

### Global configuration

Separate Fabric configuration file in the usual configuration location. Format or config library is determined after choosing the toolchain. At least:
- default height below marker: 20 blocks
- default height above marker: 40 blocks
- regeneration delay
- regeneration duration
- repair countdown
- backup count and retention
- optional performance/batching parameters

### Reliability

- Atomic writes (temporary file + safe replacement, as far as the platform supports it).
- Backup before replacing a valid data file.
- Schema versioning and explicit migrations.
- Integrity check at startup.
- On a corrupt file: search for a valid backup and log the recovery.
- If no backup is valid: safe startup abort or disabled management state; never save empty data as supposedly valid.
- Regeneration states must be resumable after a restart and must not be applied twice.

## 13. Status display

- Green: active, all four markers valid.
- Orange/yellow: marker missing; repair countdown running.
- Red: disabled, for example because the repair countdown has expired.
- The status color is a display, not a permission decision. The permission logic must check the actual status.
