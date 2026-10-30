package xtr.plotprotection.domain;

import java.util.UUID;

final class Fixtures {
	static final String OVERWORLD = "minecraft:overworld";
	static final String NETHER = "minecraft:the_nether";

	private Fixtures() {
	}

	/** Creates a plot owned by {@code owner} and registers the four corners (x0..x1, z0..z1) at the given marker height. */
	static PlotService.Created activePlot(WorldData data, UUID owner, int x0, int z0, int x1, int z1, int y) {
		var created = PlotService.create(data, owner, "Plot " + x0 + "/" + z0, "Group " + x0 + "/" + z0, 0);
		var book = created.group().bookId();
		var next = created.data();
		for (var p : new BlockPosition[] {new BlockPosition(x0, y, z0), new BlockPosition(x1, y, z0), new BlockPosition(x0, y, z1), new BlockPosition(x1, y, z1)}) {
			next = PlotService.registerMarker(next, book, OVERWORLD, p, 1, 20, 40);
		}
		return new PlotService.Created(next, next.plot(created.plot().id()).orElseThrow(), created.group());
	}
}
