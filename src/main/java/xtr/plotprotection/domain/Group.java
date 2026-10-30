package xtr.plotprotection.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @param members members besides the owner; the owner is stored separately and is always a member
 * @param bookId  id of the only valid original book of this group
 */
public record Group(UUID id, String name, UUID plotId, UUID owner, Set<UUID> members, UUID bookId) {
	public Group {
		Objects.requireNonNull(id);
		Objects.requireNonNull(name);
		Objects.requireNonNull(plotId);
		Objects.requireNonNull(owner);
		Objects.requireNonNull(bookId);
		members = Set.copyOf(members);
	}

	public boolean isMember(UUID player) {
		return owner.equals(player) || members.contains(player);
	}

	public Group withMembers(Set<UUID> members) {
		return new Group(id, name, plotId, owner, members, bookId);
	}

	public Group withOwner(UUID owner, Set<UUID> members) {
		return new Group(id, name, plotId, owner, members, bookId);
	}

	public Group withName(String name) {
		return new Group(id, name, plotId, owner, members, bookId);
	}

	public Group withBook(UUID bookId) {
		return new Group(id, name, plotId, owner, members, bookId);
	}
}
