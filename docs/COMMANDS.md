# Command Specification

All commands run server-side and are available to every player (OP level 0). Acting on a group the player does not own requires OP level 3 (admin), checked inside the command; no custom permission engine.

`<group>` accepts the stable group ID or the group name (quote names with spaces). Tab completion suggests the groups the player may act on, already quoted where needed. For normal players, names are matched only against their own groups. An ambiguous name is rejected with a list of matching IDs.

| Command | Normal player | OP level 3 |
|---|---|---|
| `/plot help` | yes | yes (also lists `/plot config`) |
| `/plot list`, `/plot groups` | own data | own data |
| `/plot create <name>` | yes | yes |
| `/plot book [<group>]` | own groups only | any group |
| `/plot set <group> …` | owner only | any group |
| `/plot transfer <group> <player>` | owner only | any group (emergency) |
| `/plot delete <group> [confirm]` | owner only | any group |
| `/plot config [<key> <value>]` | no (hidden) | yes |

## 1. `/plot list`

- First shows plots owned by the executing player.
- Then plots in whose group the player is a member.
- No duplicates, even if the player is both owner and member.
- Per entry: plot name, group name, protection mode and status.
- Plain text output: no clickable actions, no teleport and no hidden actions.
- An understandable message if there is no assignment.
- **Implemented.** Permission: OP level 0 (every player, own data only).

## 2. `/plot groups`

- Lists all groups the player belongs to.
- Per entry: group name, role (`Owner`/`Member`), plot name and plot status.
- Players who are in multiple groups get one entry per group.
- An understandable message if there is no group.
- **Implemented.** Permission: OP level 0 (every player, own data only).

## 3. `/plot book`

- If the player belongs to no group: error message, no item.
- With exactly one group: issue a valid original book for this group.
- With multiple groups: clickable chat selection by group; the selection leads to issuing the book for that group.
- The book is given to the inventory of the executing player.
- If the inventory is full, no existing original may be invalidated and no book may be lost. First ensure free space, then transfer/replace the original book authority.
- Issuing a replacement book invalidates the previous original book. Copies remain invalid.
- If a player belongs to multiple groups but is not the owner, the permission to issue a management book must be checked according to the safe rule in section 4.
- **Implemented.** Only groups the player owns are considered; a mere member gets an error. The clickable selection runs `/plot book <group-id>`.

## 4. `/plot book <group-name-or-id>`

- For normal players: only for groups for which the player may request a book according to the defined rule; the safe default is owner-only.
- An OP may issue a book for any existing group.
- For a unique group ID or unique name, a replacement book is issued.
- For an ambiguous name: no random selection; error message with a hint to use the stable group ID.
- Non-existent group: error message, no item.
- Inventory check before invalidating the old original.
- Since the original book's member list is intentionally not bound to the editor, possession of the original is a security-relevant capability.
- **Implemented.** Permission: owner (OP level 0) or OP level 3 for any group.

## 5. Owner transfer

Syntax: `/plot transfer <group> <player>`. The normal owner can transfer the group to an already existing group member. An OP (level 3) can perform an emergency transfer with the same validation. The player must be known to the server.

- Validate all invariants and overlaps before the commit.
- Safe default: reject the transfer if it would create inadmissible overlaps.
- The new owner is stored as a separate field.
- The member list must not overwrite the owner.
- An emergency transfer must not create inconsistent group/plot references.
- If the new owner is not yet a member, the transfer is rejected (safe default, `OPEN_QUESTIONS.md` A5).
- **Implemented.**

## 6. `/plot create <name>`

- Creates a plot in status `SETUP` and its group, both named `<name>` (1–32 characters). The player becomes owner.
- Requires a free inventory slot; the first original book is put into the inventory.
- Alternative without a command: using an unregistered Book and Quill on an unregistered Lodestone creates the plot (name = first line of page 1, otherwise `<player>'s plot`) and registers that Lodestone as the first marker.
- **Implemented.** Permission: OP level 0.

## 7. `/plot set <group> …`

- `mode <unprotected|regenerative|protected>`, `doors <open|locked>`, `name <text>`, `groupname <text>`, `heights <minY> <maxY>`.
- Heights can only be set once the plot has bounds (all four markers registered once); they are validated against foreign overlaps.
- The same settings can be edited on page 1 of the original book (see `SPEC.md` §9).
- **Implemented.** Permission: owner or OP level 3.

## 8. `/plot delete <group> [confirm]`

- Only possible when the plot has no valid markers left. Without `confirm`, a clickable confirmation is shown; with `confirm`, plot and group are deleted and the original book is removed from online players' inventories.
- When the fourth marker is removed, the owner receives a clickable message that runs `/plot delete <group-id>`.
- **Implemented.** Permission: owner or OP level 3.

## 9. Admin/support commands

Further admin commands may only be added if they are necessary and preserve the core invariants. An admin may create a replacement book for any group to restore a lost management interface. Marker coordinates from a command may only reference already registered markers; commands must not register markers by mere coordinate input.

## 10. `/plot help`

- Lists all commands with a one-line description (clicking a line puts the command into the chat), `/plot config` only for OP level 3, plus the hint to quote group names with spaces.
- **Implemented.** Permission: OP level 0.

## 11. `/plot config [<key> <value>]`

- Without arguments: shows regeneration delay, spread, blocks per tick and the marker repair countdown.
- Keys: `repairCountdownSeconds` (≥ 1), `regenerationDelaySeconds` (≥ 0), `regenerationSpreadSeconds` (≥ 0), `regenerationBlocksPerTick` (≥ 1). The value is written to `config/xtr_plot_protection.json` first, then used. New values apply to changes from then on; running countdowns and already scheduled blocks keep their time.
- Works from the server console as well.
- **Implemented.** Permission: OP level 3 (`Commands.LEVEL_ADMINS`); hidden from other players.

## 12. General command rules

- Validate every input server-side.
- Never make a permission decision exclusively client-side.
- Invalid input must not trigger partial changes.
- Commands that change data save the canonical state and update the original book.
- Feedback must clearly say whether a change was successful or rejected.
- No silent automatic deletion of a plot when a marker repair countdown expires.
