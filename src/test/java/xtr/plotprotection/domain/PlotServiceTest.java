package xtr.plotprotection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xtr.plotprotection.domain.Fixtures.NETHER;
import static xtr.plotprotection.domain.Fixtures.OVERWORLD;
import static xtr.plotprotection.domain.Fixtures.activePlot;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlotServiceTest {
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();

	@Test
	void createsPlotAndGroupAsOneToOnePair() {
		var created = PlotService.create(WorldData.EMPTY, alice, "Farm", "Farmers", 0);
		assertEquals(created.group().id(), created.plot().groupId());
		assertEquals(created.plot().id(), created.group().plotId());
		assertEquals(alice, created.group().owner());
		assertTrue(created.group().isMember(alice));
		assertEquals(PlotStatus.SETUP, created.plot().status());
		assertTrue(Invariants.validate(created.data()).isEmpty());
	}

	@Test
	void becomesActiveWithFourMarkersAndComputesHeights() {
		var plot = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64).plot();
		assertEquals(PlotStatus.ACTIVE, plot.status());
		assertEquals(new Bounds(0, 44, 0, 10, 104, 10), plot.bounds());
	}

	@Test
	void staysInSetupBelowFourMarkers() {
		var created = PlotService.create(WorldData.EMPTY, alice, "Farm", "Farmers", 0);
		var data = PlotService.registerMarker(created.data(), created.group().bookId(), OVERWORLD, new BlockPosition(0, 64, 0), 1, 20, 40);
		var plot = data.plot(created.plot().id()).orElseThrow();
		assertEquals(PlotStatus.SETUP, plot.status());
		assertNull(plot.bounds());
	}

	@Test
	void rejectsCopiedOrReplacedBooks() {
		var created = PlotService.create(WorldData.EMPTY, alice, "Farm", "Farmers", 0);
		var pos = new BlockPosition(0, 64, 0);
		assertThrows(PlotException.class, () -> PlotService.registerMarker(created.data(), UUID.randomUUID(), OVERWORLD, pos, 1, 20, 40));

		var oldBook = created.group().bookId();
		var issued = PlotService.issueBook(created.data(), created.group().id());
		assertNotEquals(oldBook, issued.bookId());
		assertThrows(PlotException.class, () -> PlotService.registerMarker(issued.data(), oldBook, OVERWORLD, pos, 1, 20, 40));
		PlotService.registerMarker(issued.data(), issued.bookId(), OVERWORLD, pos, 1, 20, 40);
	}

	@Test
	void rejectsMarkerInOtherDimension() {
		var created = PlotService.create(WorldData.EMPTY, alice, "Farm", "Farmers", 0);
		var book = created.group().bookId();
		var data = PlotService.registerMarker(created.data(), book, OVERWORLD, new BlockPosition(0, 64, 0), 1, 20, 40);
		assertThrows(PlotException.class, () -> PlotService.registerMarker(data, book, NETHER, new BlockPosition(10, 64, 0), 1, 20, 40));
	}

	@Test
	void lodestoneBelongsToAtMostOnePlot() {
		var first = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var second = PlotService.create(first.data(), alice, "Other", "Others", 0);
		assertThrows(PlotException.class,
			() -> PlotService.registerMarker(second.data(), second.group().bookId(), OVERWORLD, new BlockPosition(0, 64, 0), 1, 20, 40));
		assertThrows(PlotException.class,
			() -> PlotService.registerMarker(first.data(), first.group().bookId(), OVERWORLD, new BlockPosition(0, 64, 0), 1, 20, 40));
	}

	@Test
	void rejectsFifthMarkerAndNonRectangles() {
		var active = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		assertThrows(PlotException.class,
			() -> PlotService.registerMarker(active.data(), active.group().bookId(), OVERWORLD, new BlockPosition(5, 64, 5), 1, 20, 40));

		var created = PlotService.create(WorldData.EMPTY, alice, "Farm", "Farmers", 0);
		var book = created.group().bookId();
		var data = PlotService.registerMarker(created.data(), book, OVERWORLD, new BlockPosition(0, 64, 0), 1, 20, 40);
		var two = PlotService.registerMarker(data, book, OVERWORLD, new BlockPosition(10, 64, 0), 1, 20, 40);
		assertThrows(PlotException.class, () -> PlotService.registerMarker(two, book, OVERWORLD, new BlockPosition(20, 64, 0), 1, 20, 40));
	}

	@Test
	void overlapIsOnlyAllowedForSameOwner() {
		var first = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		assertThrows(PlotException.class, () -> activePlot(first.data(), bob, 5, 5, 15, 15, 64));
		var same = activePlot(first.data(), alice, 5, 5, 15, 15, 64);
		assertTrue(Invariants.validate(same.data()).isEmpty());
		activePlot(first.data(), bob, 5, 5, 15, 15, 64 + 200);
	}

	@Test
	void sameCoordinatesInOtherDimensionDoNotOverlap() {
		var first = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var created = PlotService.create(first.data(), bob, "Nether", "Nether", 0);
		var data = created.data();
		for (var p : new BlockPosition[] {new BlockPosition(0, 64, 0), new BlockPosition(10, 64, 0), new BlockPosition(0, 64, 10), new BlockPosition(10, 64, 10)}) {
			data = PlotService.registerMarker(data, created.group().bookId(), NETHER, p, 1, 20, 40);
		}
		assertEquals(PlotStatus.ACTIVE, data.plot(created.plot().id()).orElseThrow().status());
	}

	@Test
	void markerRemovalStartsRepairAndReplacementReactivates() {
		var active = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var removed = PlotService.removeMarker(active.data(), OVERWORLD, new BlockPosition(10, 64, 10), 1000, 500).orElseThrow();
		assertEquals(PlotStatus.REPAIR_PENDING, removed.plot().status());
		assertEquals(1500L, removed.plot().repairDeadline());
		assertTrue(Invariants.validate(removed.data()).isEmpty());

		assertThrows(PlotException.class, () -> PlotService.registerMarker(removed.data(), active.group().bookId(), OVERWORLD, new BlockPosition(12, 64, 10), 1100, 20, 40),
			"replacement must complete the same rectangle");
		var repaired = PlotService.registerMarker(removed.data(), active.group().bookId(), OVERWORLD, new BlockPosition(10, 70, 10), 1100, 20, 40);
		var plot = repaired.plot(active.plot().id()).orElseThrow();
		assertEquals(PlotStatus.ACTIVE, plot.status());
		assertNull(plot.repairDeadline());
	}

	@Test
	void manuallyChangedHeightsSurviveMarkerReplacement() {
		var active = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var b = active.plot().bounds();
		var custom = active.plot().withState(active.plot().markers(), OVERWORLD, new Bounds(b.minX(), 0, b.minZ(), b.maxX(), 200, b.maxZ()), PlotStatus.ACTIVE, null, 2);
		var removed = PlotService.removeMarker(active.data().with(custom), OVERWORLD, new BlockPosition(0, 64, 0), 3, 100).orElseThrow();
		var repaired = PlotService.registerMarker(removed.data(), active.group().bookId(), OVERWORLD, new BlockPosition(0, 90, 0), 4, 20, 40);
		var bounds = repaired.plot(active.plot().id()).orElseThrow().bounds();
		assertEquals(0, bounds.minY());
		assertEquals(200, bounds.maxY());
	}

	@Test
	void expiredRepairDisablesButDoesNotDelete() {
		var active = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var removed = PlotService.removeMarker(active.data(), OVERWORLD, new BlockPosition(0, 64, 0), 1000, 500).orElseThrow().data();
		assertSame(removed, PlotService.expireRepairs(removed, 1499));
		var expired = PlotService.expireRepairs(removed, 1500);
		var plot = expired.plot(active.plot().id()).orElseThrow();
		assertEquals(PlotStatus.DISABLED, plot.status());
		assertTrue(expired.group(active.group().id()).isPresent());
		assertTrue(Invariants.validate(expired).isEmpty());
	}

	@Test
	void reportsWhenAllFourMarkersAreGone() {
		var active = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var data = active.data();
		PlotService.MarkerRemoved last = null;
		for (var p : new BlockPosition[] {new BlockPosition(0, 64, 0), new BlockPosition(10, 64, 0), new BlockPosition(0, 64, 10), new BlockPosition(10, 64, 10)}) {
			last = PlotService.removeMarker(data, OVERWORLD, p, 1, 100).orElseThrow();
			data = last.data();
		}
		assertTrue(last.allMarkersMissing());
		var deleted = PlotService.delete(data, active.plot().id());
		assertTrue(deleted.plots().isEmpty());
		assertTrue(deleted.groups().isEmpty());
	}

	@Test
	void ownerTransferRequiresMembershipAndKeepsOldOwnerAsMember() {
		var created = PlotService.create(WorldData.EMPTY, alice, "Farm", "Farmers", 0);
		var groupId = created.group().id();
		assertThrows(PlotException.class, () -> PlotService.transferOwner(created.data(), groupId, bob));

		var withBob = created.data().with(created.group().withMembers(Set.of(bob)));
		var group = PlotService.transferOwner(withBob, groupId, bob).group(groupId).orElseThrow();
		assertEquals(bob, group.owner());
		assertEquals(Set.of(alice), group.members());
	}

	@Test
	void ownerTransferCreatingForeignOverlapIsRejected() {
		var mine = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var other = activePlot(mine.data(), alice, 5, 5, 15, 15, 64);
		var data = other.data().with(other.group().withMembers(Set.of(bob)));
		assertThrows(PlotException.class, () -> PlotService.transferOwner(data, other.group().id(), bob));
	}

	@Test
	void heightChangeCreatingForeignOverlapIsRejected() {
		var low = activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var high = activePlot(low.data(), bob, 0, 0, 10, 10, 200);
		assertThrows(PlotException.class, () -> PlotService.setHeights(high.data(), low.plot().id(), 0, 190, 5));
		assertThrows(PlotException.class, () -> PlotService.setHeights(high.data(), low.plot().id(), 50, 40, 5));
		var changed = PlotService.setHeights(high.data(), low.plot().id(), 0, 150, 5);
		assertEquals(150, changed.plot(low.plot().id()).orElseThrow().bounds().maxY());
	}

	@Test
	void rejectsInvalidNames() {
		assertThrows(PlotException.class, () -> PlotService.create(WorldData.EMPTY, alice, " ", "g", 0));
		assertThrows(PlotException.class, () -> PlotService.create(WorldData.EMPTY, alice, "p", "x".repeat(33), 0));
	}
}
