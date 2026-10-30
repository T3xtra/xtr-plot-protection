package xtr.plotprotection.domain;

import java.util.Collection;
import java.util.stream.Collectors;

/** Inclusive, axis-aligned plot volume. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public boolean isValid() {
		return minX <= maxX && minY <= maxY && minZ <= maxZ;
	}

	public boolean contains(BlockPosition p) {
		return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ;
	}

	public boolean intersects(Bounds o) {
		return minX <= o.maxX && o.minX <= maxX && minY <= o.maxY && o.minY <= maxY && minZ <= o.maxZ && o.minZ <= maxZ;
	}

	public boolean sameHorizontalArea(Bounds o) {
		return minX == o.minX && maxX == o.maxX && minZ == o.minZ && maxZ == o.maxZ;
	}

	/** True if the positions are distinct horizontal corners that can still complete an axis-aligned rectangle. */
	public static boolean canBePartOfRectangle(Collection<BlockPosition> positions) {
		long xs = positions.stream().map(BlockPosition::x).distinct().count();
		long zs = positions.stream().map(BlockPosition::z).distinct().count();
		long corners = positions.stream().map(p -> p.x() + ":" + p.z()).distinct().count();
		return positions.size() <= 4 && xs <= 2 && zs <= 2 && corners == positions.size();
	}

	public static boolean isRectangle(Collection<BlockPosition> positions) {
		return positions.size() == 4 && canBePartOfRectangle(positions)
			&& positions.stream().map(BlockPosition::x).collect(Collectors.toSet()).size() == 2
			&& positions.stream().map(BlockPosition::z).collect(Collectors.toSet()).size() == 2;
	}

	public static Bounds fromCorners(Collection<BlockPosition> corners, int heightBelow, int heightAbove) {
		if (!isRectangle(corners)) {
			throw new IllegalArgumentException("Corners do not form an axis-aligned rectangle: " + corners);
		}
		var x = corners.stream().mapToInt(BlockPosition::x).summaryStatistics();
		var y = corners.stream().mapToInt(BlockPosition::y).summaryStatistics();
		var z = corners.stream().mapToInt(BlockPosition::z).summaryStatistics();
		return new Bounds(x.getMin(), y.getMin() - heightBelow, z.getMin(), x.getMax(), y.getMax() + heightAbove, z.getMax());
	}
}
