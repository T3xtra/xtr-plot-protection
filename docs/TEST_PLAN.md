# Test Plan and Acceptance Criteria

## 1. Unit tests

### Geometry
- Four corners result in correct inclusive X/Z bounds.
- Markers with different Y values result in the agreed default heights.
- Manually set heights are preserved when other book fields change.
- Points exactly on each boundary are inside; one block outside each boundary is outside.
- Dimension is part of the plot area.

### Groups and identity
- New group and new plot are created atomically as a 1:1 pair.
- Owner stays separate from the member list.
- UUID remains the permission identity on a name change.
- Unknown player name is not added as an active member.
- A player can belong to multiple groups.
- Replacement book invalidates exactly the old original; copies are never valid.

### Overlaps and protection mode
- Different owners: overlap is rejected.
- Same owner: overlap is allowed.
- Effective mode follows `PROTECTED > REGENERATIVE > UNPROTECTED`.
- A door permission from a more permissive plot does not lift stricter protection.
- Owner transfer that creates an inadmissible overlap is rejected.

### Book validation
- First page is regenerated from the canonical state.
- Display-only lines of the system page are not accepted as a data source; valid setting lines are applied, invalid ones are reset.
- A valid member change is applied immediately, regardless of the editor's identity.
- Invalid names do not create membership.
- Owner cannot be replaced/removed via member pages.
- A copied or invalidated book cannot register markers or change configuration.

### Persistence
- Round trip for all data types.
- Migration from every supported schema version.
- Invalid record is rejected without overwriting valid data.
- Atomic write failure leaves the last valid file intact.
- Corrupt main file leads to backup recovery if the backup is valid.
- No valid file/backup does not lead to a silently empty world state.

## 2. Protection integration tests

- Unprotected allows ordinary block interaction.
- Regenerative prevents drops on unauthorized mining.
- Regenerative prevents taking items from block inventories; ender chest is the exception.
- Destroyed chest: contents are saved, player receives no items, contents are reset on restoration.
- Protected blocks unauthorized mining and direct inventory interaction.
- Direct doors/trapdoors/fence gates follow the setting; buttons, levers and redstone keep working according to the specification.
- Dispensers/droppers can be triggered by redstone.
- Explosion and fire damage without an authorized initiator is regenerated.
- Creeper damage always regenerates.
- Explosion/fire with an authorized initiator follows vanilla, apart from markers and the creeper special rule.
- Liquids and pistons cannot permanently remove registered markers or bypass protection bounds.
- Actions outside a plot cannot change protected contents inside it.
- Entities cannot be damaged without authorization or moved out of the plot via boats, minecarts, leads etc.
- PvP stays vanilla in all modes.

## 3. Marker lifecycle tests

- A marker can only be registered via the original book.
- A marker cannot be assigned to two plots.
- A marker from another dimension is rejected.
- Marker removal without the associated original book is prevented.
- Marker removal starts the countdown and owner notification; status turns yellow/orange.
- A replacement marker before the deadline reactivates the plot and clears the countdown.
- Countdown expiry disables the plot but does not delete it.
- All four markers removed: owner receives a clickable delete action.
- Deletion only happens after explicit confirmation.
- Deletion removes group and plot, invalidates the book and removes it as far as it can be found.

## 4. Restart and load tests

- Restart during a repair countdown restores the same status and the same deadline.
- Restart during regeneration restores jobs and snapshots.
- Multiple jobs on the same block cause no duplication or data loss.
- Large regeneration is processed in controlled batches and does not block the server unnecessarily.
- Backup rotation respects the configured count/retention.
- Server start with corrupt data produces clear logs and safe recovery.

## 5. Manual acceptance

At least one test server with the same pinned version:
- Dedicated server.
- Singleplayer with integrated server.
- Two normal players, one OP, multiple groups, overlapping plots with the same owner.
- Book copy, replacement book, full inventories, player name/UUID resolution.
- Restart with running timers and regeneration.
- Tests for mechanics whose behavior cannot be reliably reproduced in unit tests.

## 6. Release gate

No release if:
- data loss/item duplication during regeneration is known,
- inadmissible overlap loosens protection rules,
- copies are accepted as the original book,
- corrupt data is silently replaced by empty data,
- PvP behavior was changed,
- central open product decisions are unresolved and implemented without explicit approval.
