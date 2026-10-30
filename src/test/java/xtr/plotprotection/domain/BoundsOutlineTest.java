package xtr.plotprotection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import xtr.plotprotection.domain.BoundsOutline.Point;

class BoundsOutlineTest {
	private final Bounds box = new Bounds(0, 10, 0, 1, 11, 2);

	@Test
	void outlinesAllTwelveEdgesOnTheOuterFaces() {
		var points = new HashSet<>(BoundsOutline.edges(box, new Point(1, 50, 1), 100));
		// box spans 2 x 2 x 3 blocks: 8 corners plus inner points 4 * (1 + 1 + 2)
		assertEquals(8 + 16, points.size());
		assertTrue(points.contains(new Point(0, 10, 0)));
		assertTrue(points.contains(new Point(2, 12, 3)), "maximum corner lies on the outer face");
		assertTrue(points.stream().allMatch(p -> p.x() == 0 || p.x() == 2 ? true : p.y() == 10 || p.y() == 12));
	}

	@Test
	void onlyDrawsTheNearbyPartOfLargePlots() {
		var huge = new Bounds(-10_000, -64, -10_000, 10_000, 320, 10_000);
		var viewer = new Point(-10_000, 64, 0);
		var points = BoundsOutline.edges(huge, viewer, 32);
		assertEquals(65, points.size(), "all edges are out of range; only the ring at foot height on the west face remains");
		assertTrue(points.stream().allMatch(p -> p.x() == -10_000 && p.y() == 64));
	}

	@Test
	void columnCoversMarkerHeightRange() {
		var points = BoundsOutline.column(new BlockPosition(5, 64, 5), 20, 40, new Point(5, 64, 5), 100);
		assertEquals(61, points.size());
		assertEquals(new Point(5.5, 44, 5.5), points.getFirst());
	}
}
