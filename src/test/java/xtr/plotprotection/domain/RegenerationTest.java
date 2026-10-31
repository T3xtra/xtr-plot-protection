package xtr.plotprotection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegenerationTest {
	private static final String WORLD = "minecraft:overworld";
	private final BlockPosition a = new BlockPosition(1, 64, 1);
	private final BlockPosition b = new BlockPosition(2, 64, 1);

	private static RegenerationEntry snapshot(BlockPosition p, String state, long restoreAt) {
		return new RegenerationEntry(WORLD, p, state, null, restoreAt, false);
	}

	@Test
	void secondChangeKeepsOriginalSnapshotAndClock() {
		var data = Regeneration.capture(WorldData.EMPTY, List.of(snapshot(a, "stone", 100)));
		var again = Regeneration.capture(data, List.of(snapshot(a, "dirt", 500)));
		assertSame(data, again);
		assertEquals("stone", again.regeneration().getFirst().blockState());
		assertEquals(100, again.regeneration().getFirst().restoreAt());
	}

	@Test
	void dueEntriesAreOldestFirstAndLimited() {
		var data = Regeneration.capture(WorldData.EMPTY, List.of(snapshot(b, "stone", 200), snapshot(a, "stone", 100), snapshot(new BlockPosition(3, 64, 1), "stone", 900)));
		assertEquals(List.of(), Regeneration.due(data, 50, 10));
		assertEquals(List.of(a), Regeneration.due(data, 300, 1).stream().map(RegenerationEntry::position).toList());
		assertEquals(List.of(a, b), Regeneration.due(data, 300, 10).stream().map(RegenerationEntry::position).toList());
	}

	@Test
	void restoredEntriesStayUntilSavedAndAreNeverDueTwice() {
		var data = Regeneration.capture(WorldData.EMPTY, List.of(snapshot(a, "stone", 100), snapshot(b, "stone", 100)));
		var restored = Regeneration.markRestored(data, Regeneration.due(data, 100, 1));
		assertEquals(2, restored.regeneration().size());
		assertEquals(List.of(b), Regeneration.due(restored, 100, 10).stream().map(RegenerationEntry::position).toList());
		assertFalse(Regeneration.isPending(restored, WORLD, a));
		assertEquals(List.of(b), Regeneration.pruneRestored(restored).regeneration().stream().map(RegenerationEntry::position).toList());
	}

	@Test
	void unsavedRestorationIsReappliedAfterRestart() {
		var data = Regeneration.capture(WorldData.EMPTY, List.of(snapshot(a, "stone", 100)));
		var restored = Regeneration.markRestored(data, Regeneration.due(data, 100, 1));
		var restarted = Regeneration.reopenRestored(restored);
		assertEquals(data, restarted, "same snapshot, due again");
	}

	@Test
	void changeAfterUnsavedRestorationReopensWithOriginalSnapshot() {
		var data = Regeneration.capture(WorldData.EMPTY, List.of(snapshot(a, "stone", 100)));
		var restored = Regeneration.markRestored(data, Regeneration.due(data, 100, 1));
		var changedAgain = Regeneration.capture(restored, List.of(snapshot(a, "air", 700)));
		assertTrue(Regeneration.isPending(changedAgain, WORLD, a));
		assertEquals("stone", changedAgain.regeneration().getFirst().blockState());
		assertEquals(700, changedAgain.regeneration().getFirst().restoreAt());
	}

	@Test
	void duplicateEntriesViolateInvariants() {
		var twice = WorldData.EMPTY.withRegeneration(List.of(snapshot(a, "stone", 1), snapshot(a, "dirt", 2)));
		assertEquals(1, Invariants.validate(twice).size());
	}
}
