package xtr.plotprotection.domain;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The single place that decides whether a change at a position is allowed. Pure, so every rule is unit-testable. */
public final class PermissionResolver {
	public enum Action {
		BREAK,
		PLACE,
		/** Any other block state change, e.g. a repeater delay or a note block pitch. */
		MODIFY,
		OPEN_CONTAINER,
		TAKE_ITEM,
		USE_DOOR,
		/** Damaging, killing, leashing or riding a non-player entity. */
		ENTITY
	}

	/** Declared from least to most strict; ordinal order is the priority order. */
	public enum Decision {
		ALLOW,
		REGENERATE,
		DENY
	}

	/** Who or what caused an indirect change. */
	public enum Cause {
		/** Pistons, liquids, redstone: allowed when the source lies inside every protecting plot of the target. */
		MECHANIC,
		/** Explosions, fire and mobs, attributed to the responsible player if known. */
		ENVIRONMENT,
		/** Creeper explosions always regenerate, even when an authorized player lured the creeper. */
		CREEPER
	}

	private PermissionResolver() {
	}

	/** A player acting directly. With overlapping plots the strictest per-plot decision wins. */
	public static Decision decide(WorldData data, String dimension, BlockPosition target, UUID actor, Action action) {
		return protecting(data, dimension, target).stream()
			.map(p -> decide(p, data.groupOf(p), actor, action))
			.max(Comparator.naturalOrder())
			.orElse(Decision.ALLOW);
	}

	/**
	 * A change not made directly by a player. {@code source} is where the mechanic sits (null if unknown),
	 * {@code actor} the responsible player if known. Registered markers are never changed indirectly.
	 */
	public static Decision decideIndirect(WorldData data, String dimension, BlockPosition source, BlockPosition target, UUID actor, Cause cause) {
		if (isMarker(data, dimension, target)) {
			return Decision.DENY;
		}
		return protecting(data, dimension, target).stream()
			.map(p -> decideIndirect(p, data.groupOf(p), dimension, source, actor, cause))
			.max(Comparator.naturalOrder())
			.orElse(Decision.ALLOW);
	}

	public static boolean isMarker(WorldData data, String dimension, BlockPosition position) {
		return data.plotWithMarkerAt(dimension, position).filter(p -> p.validMarkers().contains(position)).isPresent();
	}

	/** A2: a plot protects while active and during the repair countdown. */
	public static List<Plot> protecting(WorldData data, String dimension, BlockPosition position) {
		return data.plotsAt(dimension, position).stream()
			.filter(p -> p.status() == PlotStatus.ACTIVE || p.status() == PlotStatus.REPAIR_PENDING)
			.toList();
	}

	static Decision decide(Plot plot, Group group, UUID actor, Action action) {
		if (plot.mode() == ProtectionMode.UNPROTECTED || actor != null && group.isMember(actor)) {
			return Decision.ALLOW;
		}
		if (action == Action.USE_DOOR) {
			return plot.allowDirectDoorInteraction() ? Decision.ALLOW : Decision.DENY;
		}
		if (plot.mode() == ProtectionMode.PROTECTED) {
			return Decision.DENY;
		}
		return switch (action) {
			case OPEN_CONTAINER -> Decision.ALLOW;
			case TAKE_ITEM, ENTITY -> Decision.DENY;
			default -> Decision.REGENERATE;
		};
	}

	private static Decision decideIndirect(Plot plot, Group group, String dimension, BlockPosition source, UUID actor, Cause cause) {
		if (plot.mode() == ProtectionMode.UNPROTECTED) {
			return Decision.ALLOW;
		}
		boolean authorized = switch (cause) {
			case MECHANIC -> source != null && plot.contains(dimension, source);
			case ENVIRONMENT -> actor != null && group.isMember(actor);
			case CREEPER -> false;
		};
		if (authorized) {
			return Decision.ALLOW;
		}
		return plot.mode() == ProtectionMode.REGENERATIVE && cause != Cause.MECHANIC ? Decision.REGENERATE : Decision.DENY;
	}
}
