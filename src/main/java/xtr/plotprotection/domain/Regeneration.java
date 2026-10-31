package xtr.plotprotection.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;

/** Pure bookkeeping of regeneration entries; the world access lives in the Minecraft layer. */
public final class Regeneration {
	private Regeneration() {
	}

	/**
	 * Adds snapshots for blocks about to change. A block that already waits for restoration keeps its original
	 * snapshot and clock (A3); a block restored but not yet saved reopens with its original snapshot.
	 */
	public static WorldData capture(WorldData data, Collection<RegenerationEntry> snapshots) {
		var entries = new ArrayList<>(data.regeneration());
		boolean changed = false;
		for (RegenerationEntry snapshot : snapshots) {
			int existing = indexOf(entries, snapshot.dimension(), snapshot.position());
			if (existing < 0) {
				entries.add(snapshot);
				changed = true;
			} else if (entries.get(existing).restored()) {
				entries.set(existing, entries.get(existing).reopened(snapshot.restoreAt()));
				changed = true;
			}
		}
		return changed ? data.withRegeneration(entries) : data;
	}

	/**
	 * Random, ascending restore times within {@code [earliest, earliest + spreadMillis]}. Callers assign them to blocks
	 * ordered bottom-up, so supporting blocks return before the blocks resting on them (sand, torches, plants).
	 */
	public static long[] restoreTimes(int count, long earliest, long spreadMillis, RandomGenerator random) {
		return LongStream.generate(() -> earliest + (spreadMillis == 0 ? 0 : random.nextLong(spreadMillis + 1))).limit(count).sorted().toArray();
	}

	public static boolean isPending(WorldData data, String dimension, BlockPosition position) {
		return data.regeneration().stream().anyMatch(e -> !e.restored() && e.isAt(dimension, position));
	}

	public static List<RegenerationEntry> due(WorldData data, long now, int limit) {
		return data.regeneration().stream()
			.filter(e -> !e.restored() && e.restoreAt() <= now)
			.sorted(Comparator.comparingLong(RegenerationEntry::restoreAt))
			.limit(limit)
			.toList();
	}

	public static WorldData markRestored(WorldData data, Collection<RegenerationEntry> restored) {
		if (restored.isEmpty()) {
			return data;
		}
		return data.withRegeneration(data.regeneration().stream().map(e -> restored.contains(e) ? e.markedRestored() : e).toList());
	}

	/** Keeps an entry that could not be restored and retries it later instead of dropping its snapshot (A4). */
	public static WorldData postpone(WorldData data, RegenerationEntry entry, long restoreAt) {
		return data.withRegeneration(data.regeneration().stream().map(e -> e.equals(entry) ? e.reopened(restoreAt) : e).toList());
	}

	/** After a world save the restored blocks are on disk and their entries can go. */
	public static WorldData pruneRestored(WorldData data) {
		if (data.regeneration().stream().noneMatch(RegenerationEntry::restored)) {
			return data;
		}
		return data.withRegeneration(data.regeneration().stream().filter(e -> !e.restored()).toList());
	}

	/** At startup the world may predate a restoration that was not saved yet, so apply those entries again. */
	public static WorldData reopenRestored(WorldData data) {
		if (data.regeneration().stream().noneMatch(RegenerationEntry::restored)) {
			return data;
		}
		return data.withRegeneration(data.regeneration().stream().map(e -> e.restored() ? e.reopened(e.restoreAt()) : e).toList());
	}

	private static int indexOf(List<RegenerationEntry> entries, String dimension, BlockPosition position) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).isAt(dimension, position)) {
				return i;
			}
		}
		return -1;
	}
}
