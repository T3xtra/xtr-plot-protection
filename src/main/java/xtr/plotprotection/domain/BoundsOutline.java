package xtr.plotprotection.domain;

import java.util.ArrayList;
import java.util.List;

/** Particle positions outlining plots, one per block of edge, limited to the part near the viewer (A1). */
public final class BoundsOutline {
	public record Point(double x, double y, double z) {
	}

	private BoundsOutline() {
	}

	/**
	 * The twelve edges of the inclusive block box, drawn on the outer block faces, plus a ring around the side
	 * faces at the viewer's feet so a viewer far from every edge still sees the wall next to them.
	 */
	public static List<Point> edges(Bounds b, Point viewer, int range) {
		double x0 = b.minX(), x1 = b.maxX() + 1, y0 = b.minY(), y1 = b.maxY() + 1, z0 = b.minZ(), z1 = b.maxZ() + 1;
		var points = new ArrayList<Point>();
		for (double y : new double[] {y0, y1}) {
			for (double z : new double[] {z0, z1}) {
				line(points, new Point(x0, y, z), 0, x1 - x0, viewer, range);
			}
			for (double x : new double[] {x0, x1}) {
				line(points, new Point(x, y, z0), 2, z1 - z0, viewer, range);
			}
		}
		for (double x : new double[] {x0, x1}) {
			for (double z : new double[] {z0, z1}) {
				line(points, new Point(x, y0, z), 1, y1 - y0, viewer, range);
			}
		}
		double feet = Math.floor(viewer.y());
		if (feet > y0 && feet < y1) {
			for (double z : new double[] {z0, z1}) {
				line(points, new Point(x0, feet, z), 0, x1 - x0, viewer, range);
			}
			for (double x : new double[] {x0, x1}) {
				line(points, new Point(x, feet, z0), 2, z1 - z0, viewer, range);
			}
		}
		return points;
	}

	/** A vertical column through the middle of a marker block, for plots that have no bounds yet. */
	public static List<Point> column(BlockPosition marker, int below, int above, Point viewer, int range) {
		var points = new ArrayList<Point>();
		line(points, new Point(marker.x() + 0.5, marker.y() - below, marker.z() + 0.5), 1, below + above, viewer, range);
		return points;
	}

	/** Adds the points of a line from {@code start} along an axis (0 = x, 1 = y, 2 = z) that lie within range. */
	private static void line(List<Point> out, Point start, int axis, double length, Point viewer, int range) {
		double origin = coordinate(start, axis);
		double from = Math.max(0, Math.ceil(coordinate(viewer, axis) - range - origin));
		double to = Math.min(length, Math.floor(coordinate(viewer, axis) + range - origin));
		for (double t = from; t <= to; t++) {
			var p = new Point(start.x() + (axis == 0 ? t : 0), start.y() + (axis == 1 ? t : 0), start.z() + (axis == 2 ? t : 0));
			double dx = p.x() - viewer.x(), dy = p.y() - viewer.y(), dz = p.z() - viewer.z();
			if (dx * dx + dy * dy + dz * dz <= (double) range * range) {
				out.add(p);
			}
		}
	}

	private static double coordinate(Point p, int axis) {
		return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
	}
}
