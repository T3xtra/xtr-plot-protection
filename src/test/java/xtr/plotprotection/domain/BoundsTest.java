package xtr.plotprotection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoundsTest {
	private static final List<BlockPosition> CORNERS = List.of(
		new BlockPosition(10, 64, 20), new BlockPosition(0, 70, 20), new BlockPosition(10, 60, 0), new BlockPosition(0, 65, 0));

	@Test
	void cornersGiveInclusiveBoundsAndDefaultHeights() {
		assertEquals(new Bounds(0, 40, 0, 10, 110, 20), Bounds.fromCorners(CORNERS, 20, 40));
	}

	@Test
	void edgesAreInsideAndOneBlockBeyondIsOutside() {
		var b = new Bounds(0, 40, 0, 10, 110, 20);
		for (var inside : List.of(new BlockPosition(0, 40, 0), new BlockPosition(10, 110, 20), new BlockPosition(5, 50, 10))) {
			assertTrue(b.contains(inside), inside.toString());
		}
		for (var outside : List.of(new BlockPosition(-1, 50, 10), new BlockPosition(11, 50, 10), new BlockPosition(5, 39, 10),
			new BlockPosition(5, 111, 10), new BlockPosition(5, 50, -1), new BlockPosition(5, 50, 21))) {
			assertFalse(b.contains(outside), outside.toString());
		}
	}

	@Test
	void rectangleDetection() {
		assertTrue(Bounds.isRectangle(CORNERS));
		assertFalse(Bounds.isRectangle(CORNERS.subList(0, 3)));
		assertTrue(Bounds.canBePartOfRectangle(CORNERS.subList(0, 3)));
		assertFalse(Bounds.canBePartOfRectangle(List.of(new BlockPosition(0, 0, 0), new BlockPosition(5, 0, 0), new BlockPosition(9, 0, 0))));
		assertFalse(Bounds.canBePartOfRectangle(List.of(new BlockPosition(0, 0, 0), new BlockPosition(0, 9, 0))), "same corner twice");
		assertThrows(IllegalArgumentException.class, () -> Bounds.fromCorners(CORNERS.subList(0, 3), 0, 0));
	}

	@Test
	void intersection() {
		var a = new Bounds(0, 0, 0, 10, 10, 10);
		assertTrue(a.intersects(new Bounds(10, 10, 10, 20, 20, 20)), "touching edge shares blocks");
		assertFalse(a.intersects(new Bounds(11, 0, 0, 20, 10, 10)));
		assertFalse(a.intersects(new Bounds(0, 11, 0, 10, 20, 10)), "stacked vertically");
	}
}
