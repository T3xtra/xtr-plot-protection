package xtr.plotprotection.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure state transitions; every method returns a new {@link WorldData} or throws {@link PlotException}. */
public final class PlotService {
	public static final int MAX_NAME_LENGTH = 32;

	public record Created(WorldData data, Plot plot, Group group) {
	}

	public record MarkerRemoved(WorldData data, Plot plot, boolean allMarkersMissing) {
	}

	public record BookIssued(WorldData data, UUID bookId) {
	}

	private PlotService() {
	}

	public static Created create(WorldData data, UUID owner, String plotName, String groupName, long now) {
		var plotId = UUID.randomUUID();
		var groupId = UUID.randomUUID();
		var plot = new Plot(plotId, requireName(plotName), groupId, null, null, ProtectionMode.PROTECTED, false, PlotStatus.SETUP, List.of(), null, now, now);
		var group = new Group(groupId, requireName(groupName), plotId, owner, Set.of(), UUID.randomUUID());
		return new Created(data.with(plot).with(group), plot, group);
	}

	public static WorldData registerMarker(WorldData data, UUID bookId, String dimension, BlockPosition position, long now, int heightBelow, int heightAbove) {
		Group group = data.groupByBook(bookId).orElseThrow(() -> new PlotException("book.invalid"));
		Plot plot = data.plotOf(group);

		Optional<Plot> holder = data.plotWithMarkerAt(dimension, position);
		if (holder.isPresent() && (!holder.get().id().equals(plot.id()) || plot.validMarkers().contains(position))) {
			throw new PlotException("marker.already_registered");
		}
		if (plot.dimension() != null && !plot.dimension().equals(dimension)) {
			throw new PlotException("marker.wrong_dimension", plot.dimension());
		}

		var valid = new ArrayList<>(plot.validMarkers());
		valid.add(position);
		if (valid.size() > 4) {
			throw new PlotException("marker.too_many");
		}
		if (!Bounds.canBePartOfRectangle(valid)) {
			throw new PlotException("marker.not_rectangle");
		}

		var markers = new ArrayList<>(plot.markers());
		markers.removeIf(m -> m.position().equals(position));
		markers.add(new Marker(position, MarkerStatus.VALID, now));
		if (valid.size() < 4) {
			return data.with(plot.withState(markers, dimension, plot.bounds(), plot.status(), plot.repairDeadline(), now));
		}

		var rectangle = Bounds.fromCorners(valid, heightBelow, heightAbove);
		var bounds = plot.bounds() == null
			? rectangle
			: new Bounds(rectangle.minX(), plot.bounds().minY(), rectangle.minZ(), rectangle.maxX(), plot.bounds().maxY(), rectangle.maxZ());
		requireNoForeignOverlap(data, plot, dimension, bounds, group.owner());
		markers.removeIf(m -> !m.isValid());
		return data.with(plot.withState(markers, dimension, bounds, PlotStatus.ACTIVE, null, now));
	}

	/** Returns empty if no valid marker is registered at the position. */
	public static Optional<MarkerRemoved> removeMarker(WorldData data, String dimension, BlockPosition position, long now, long repairCountdownMillis) {
		Optional<Plot> found = data.plotWithMarkerAt(dimension, position).filter(p -> p.validMarkers().contains(position));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Plot plot = found.get();
		var markers = new ArrayList<>(plot.markers());
		markers.removeIf(m -> m.position().equals(position));

		Plot next;
		if (plot.status() == PlotStatus.SETUP) {
			next = plot.withState(markers, plot.dimension(), null, PlotStatus.SETUP, null, now);
		} else {
			markers.add(new Marker(position, MarkerStatus.MISSING, now));
			next = plot.status() == PlotStatus.ACTIVE
				? plot.withState(markers, plot.dimension(), plot.bounds(), PlotStatus.REPAIR_PENDING, now + repairCountdownMillis, now)
				: plot.withState(markers, plot.dimension(), plot.bounds(), plot.status(), plot.repairDeadline(), now);
		}
		boolean allMissing = next.status() != PlotStatus.SETUP && next.validMarkers().isEmpty();
		return Optional.of(new MarkerRemoved(data.with(next), next, allMissing));
	}

	/** Returns the same instance if no repair deadline has passed. */
	public static WorldData expireRepairs(WorldData data, long now) {
		WorldData next = data;
		for (Plot plot : data.plots()) {
			if (plot.status() == PlotStatus.REPAIR_PENDING && plot.repairDeadline() <= now) {
				next = next.with(plot.withState(plot.markers(), plot.dimension(), plot.bounds(), PlotStatus.DISABLED, null, now));
			}
		}
		return next;
	}

	public static WorldData delete(WorldData data, UUID plotId) {
		data.plot(plotId).orElseThrow(() -> new PlotException("plot.not_found"));
		return data.withoutPlot(plotId);
	}

	/** The previous owner stays in the group as a regular member. */
	public static WorldData transferOwner(WorldData data, UUID groupId, UUID newOwner) {
		Group group = data.group(groupId).orElseThrow(() -> new PlotException("group.not_found"));
		if (group.owner().equals(newOwner)) {
			throw new PlotException("transfer.already_owner");
		}
		if (!group.members().contains(newOwner)) {
			throw new PlotException("transfer.not_member");
		}
		Plot plot = data.plotOf(group);
		if (plot.bounds() != null) {
			requireNoForeignOverlap(data, plot, plot.dimension(), plot.bounds(), newOwner);
		}
		var members = new HashSet<>(group.members());
		members.remove(newOwner);
		members.add(group.owner());
		return data.with(group.withOwner(newOwner, members));
	}

	public static WorldData renamePlot(WorldData data, UUID plotId, String name, long now) {
		Plot plot = requirePlot(data, plotId);
		return data.with(plot.withSettings(requireName(name), plot.mode(), plot.allowDirectDoorInteraction(), plot.bounds(), now));
	}

	public static WorldData renameGroup(WorldData data, UUID groupId, String name) {
		Group group = data.group(groupId).orElseThrow(() -> new PlotException("group.not_found"));
		return data.with(group.withName(requireName(name)));
	}

	public static WorldData setMode(WorldData data, UUID plotId, ProtectionMode mode, long now) {
		Plot plot = requirePlot(data, plotId);
		return data.with(plot.withSettings(plot.name(), mode, plot.allowDirectDoorInteraction(), plot.bounds(), now));
	}

	public static WorldData setDoors(WorldData data, UUID plotId, boolean allowDirectDoorInteraction, long now) {
		Plot plot = requirePlot(data, plotId);
		return data.with(plot.withSettings(plot.name(), plot.mode(), allowDirectDoorInteraction, plot.bounds(), now));
	}

	public static WorldData setHeights(WorldData data, UUID plotId, int minY, int maxY, long now) {
		Plot plot = requirePlot(data, plotId);
		if (plot.bounds() == null) {
			throw new PlotException("plot.no_bounds");
		}
		if (minY > maxY) {
			throw new PlotException("height.invalid");
		}
		var b = plot.bounds();
		var bounds = new Bounds(b.minX(), minY, b.minZ(), b.maxX(), maxY, b.maxZ());
		requireNoForeignOverlap(data, plot, plot.dimension(), bounds, data.groupOf(plot).owner());
		return data.with(plot.withSettings(plot.name(), plot.mode(), plot.allowDirectDoorInteraction(), bounds, now));
	}

	/** Issues a new original book id; the previous original becomes invalid. */
	public static BookIssued issueBook(WorldData data, UUID groupId) {
		Group group = data.group(groupId).orElseThrow(() -> new PlotException("group.not_found"));
		var bookId = UUID.randomUUID();
		return new BookIssued(data.with(group.withBook(bookId)), bookId);
	}

	private static Plot requirePlot(WorldData data, UUID plotId) {
		return data.plot(plotId).orElseThrow(() -> new PlotException("plot.not_found"));
	}

	private static void requireNoForeignOverlap(WorldData data, Plot plot, String dimension, Bounds bounds, UUID owner) {
		for (Plot other : data.plots()) {
			if (!other.id().equals(plot.id()) && other.bounds() != null && dimension.equals(other.dimension())
				&& other.bounds().intersects(bounds) && !data.groupOf(other).owner().equals(owner)) {
				throw new PlotException("plot.overlaps_foreign", other.name());
			}
		}
	}

	private static String requireName(String name) {
		var stripped = name == null ? "" : name.strip();
		if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
			throw new PlotException("name.invalid_length", MAX_NAME_LENGTH);
		}
		return stripped;
	}
}
