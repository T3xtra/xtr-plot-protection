package xtr.plotprotection.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Invariants {
	private Invariants() {
	}

	public static List<String> validate(WorldData data) {
		var errors = new ArrayList<String>();
		var plotIds = new HashSet<UUID>();
		var groupIds = new HashSet<UUID>();
		var bookIds = new HashSet<UUID>();
		var markerKeys = new HashSet<String>();

		for (Plot plot : data.plots()) {
			String p = "plot " + plot.id();
			if (!plotIds.add(plot.id())) errors.add(p + ": duplicate id");
			if (plot.name().isBlank()) errors.add(p + ": blank name");
			if (data.group(plot.groupId()).filter(g -> g.plotId().equals(plot.id())).isEmpty()) {
				errors.add(p + ": group " + plot.groupId() + " missing or not linked back");
			}
			if (!plot.markers().isEmpty() && plot.dimension() == null) errors.add(p + ": markers without dimension");
			if (plot.validMarkers().size() > 4) errors.add(p + ": more than four valid markers");
			for (Marker m : plot.markers()) {
				if (!markerKeys.add(plot.dimension() + "@" + m.position())) errors.add(p + ": marker " + m.position() + " registered twice");
			}
			if (plot.status() == PlotStatus.SETUP) {
				if (plot.bounds() != null) errors.add(p + ": bounds set during setup");
				if (!Bounds.canBePartOfRectangle(plot.validMarkers())) errors.add(p + ": markers cannot form a rectangle");
			} else if (plot.bounds() == null || !plot.bounds().isValid()) {
				errors.add(p + ": missing or invalid bounds");
			}
			if (plot.status() == PlotStatus.ACTIVE && plot.bounds() != null) {
				var valid = plot.validMarkers();
				if (!Bounds.isRectangle(valid) || !Bounds.fromCorners(valid, 0, 0).sameHorizontalArea(plot.bounds())) {
					errors.add(p + ": active without four markers matching its bounds");
				}
			}
			if ((plot.status() == PlotStatus.REPAIR_PENDING) != (plot.repairDeadline() != null)) {
				errors.add(p + ": repair deadline must be set exactly while repair is pending");
			}
		}

		for (Group group : data.groups()) {
			String g = "group " + group.id();
			if (!groupIds.add(group.id())) errors.add(g + ": duplicate id");
			if (group.name().isBlank()) errors.add(g + ": blank name");
			if (data.plot(group.plotId()).filter(p -> p.groupId().equals(group.id())).isEmpty()) {
				errors.add(g + ": plot " + group.plotId() + " missing or not linked back");
			}
			if (group.members().contains(group.owner())) errors.add(g + ": owner also stored as member");
			if (!bookIds.add(group.bookId())) errors.add(g + ": book id shared with another group");
		}

		var regenerating = new HashSet<String>();
		for (RegenerationEntry entry : data.regeneration()) {
			if (!regenerating.add(entry.dimension() + "@" + entry.position())) {
				errors.add("regeneration entry " + entry.dimension() + " " + entry.position() + " exists twice");
			}
		}

		var plots = data.plots();
		for (int i = 0; i < plots.size(); i++) {
			for (int j = i + 1; j < plots.size(); j++) {
				Plot a = plots.get(i), b = plots.get(j);
				if (overlap(a, b) && !Objects.equals(owner(data, a), owner(data, b))) {
					errors.add("plots " + a.id() + " and " + b.id() + " overlap with different owners");
				}
			}
		}
		return errors;
	}

	private static boolean overlap(Plot a, Plot b) {
		return a.bounds() != null && b.bounds() != null && a.dimension().equals(b.dimension()) && a.bounds().intersects(b.bounds());
	}

	private static UUID owner(WorldData data, Plot plot) {
		return data.group(plot.groupId()).map(Group::owner).orElse(null);
	}
}
