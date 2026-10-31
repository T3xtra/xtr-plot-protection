package xtr.plotprotection.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @param dimension      dimension key, set by the first registered marker
 * @param bounds         set on the first complete marker registration, null while in {@link PlotStatus#SETUP}
 * @param repairDeadline epoch millis, only while {@link PlotStatus#REPAIR_PENDING}
 */
public record Plot(
	UUID id,
	String name,
	UUID groupId,
	String dimension,
	Bounds bounds,
	ProtectionMode mode,
	boolean allowDirectDoorInteraction,
	PlotStatus status,
	List<Marker> markers,
	Long repairDeadline,
	long createdAt,
	long updatedAt
) {
	public Plot {
		Objects.requireNonNull(id);
		Objects.requireNonNull(name);
		Objects.requireNonNull(groupId);
		Objects.requireNonNull(mode);
		Objects.requireNonNull(status);
		markers = List.copyOf(markers);
	}

	public List<BlockPosition> validMarkers() {
		return markers.stream().filter(Marker::isValid).map(Marker::position).toList();
	}

	public boolean hasMarkerAt(String dimension, BlockPosition position) {
		return dimension.equals(this.dimension) && markers.stream().anyMatch(m -> m.position().equals(position));
	}

	public boolean contains(String dimension, BlockPosition position) {
		return bounds != null && dimension.equals(this.dimension) && bounds.contains(position);
	}

	public Plot withState(List<Marker> markers, String dimension, Bounds bounds, PlotStatus status, Long repairDeadline, long now) {
		return new Plot(id, name, groupId, dimension, bounds, mode, allowDirectDoorInteraction, status, markers, repairDeadline, createdAt, now);
	}

	public Plot withSettings(String name, ProtectionMode mode, boolean allowDirectDoorInteraction, Bounds bounds, long now) {
		return new Plot(id, name, groupId, dimension, bounds, mode, allowDirectDoorInteraction, status, markers, repairDeadline, createdAt, now);
	}
}
