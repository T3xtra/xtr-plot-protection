package xtr.plotprotection.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable snapshot of all plots, groups and pending regeneration; changes produce a new snapshot that is validated before commit. */
// ponytail: linear scans, add a spatial index once protection hooks query this per block event
public record WorldData(List<Plot> plots, List<Group> groups, List<RegenerationEntry> regeneration) {
	public static final WorldData EMPTY = new WorldData(List.of(), List.of(), List.of());

	public WorldData {
		plots = List.copyOf(plots);
		groups = List.copyOf(groups);
		regeneration = List.copyOf(regeneration);
	}

	public Optional<Plot> plot(UUID id) {
		return plots.stream().filter(p -> p.id().equals(id)).findFirst();
	}

	public Optional<Group> group(UUID id) {
		return groups.stream().filter(g -> g.id().equals(id)).findFirst();
	}

	public Optional<Group> groupByBook(UUID bookId) {
		return groups.stream().filter(g -> g.bookId().equals(bookId)).findFirst();
	}

	public Group groupOf(Plot plot) {
		return group(plot.groupId()).orElseThrow();
	}

	public Plot plotOf(Group group) {
		return plot(group.plotId()).orElseThrow();
	}

	public List<Plot> plotsAt(String dimension, BlockPosition position) {
		return plots.stream().filter(p -> p.contains(dimension, position)).toList();
	}

	public Optional<Plot> plotWithMarkerAt(String dimension, BlockPosition position) {
		return plots.stream().filter(p -> p.hasMarkerAt(dimension, position)).findFirst();
	}

	public WorldData with(Plot plot) {
		var next = new ArrayList<>(plots);
		next.removeIf(p -> p.id().equals(plot.id()));
		next.add(plot);
		return new WorldData(next, groups, regeneration);
	}

	public WorldData with(Group group) {
		var next = new ArrayList<>(groups);
		next.removeIf(g -> g.id().equals(group.id()));
		next.add(group);
		return new WorldData(plots, next, regeneration);
	}

	public WorldData withRegeneration(List<RegenerationEntry> entries) {
		return new WorldData(plots, groups, entries);
	}

	public WorldData withoutPlot(UUID plotId) {
		return new WorldData(
			plots.stream().filter(p -> !p.id().equals(plotId)).toList(),
			groups.stream().filter(g -> !g.plotId().equals(plotId)).toList(),
			regeneration
		);
	}
}
