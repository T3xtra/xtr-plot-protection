package xtr.plotprotection.visual;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import xtr.plotprotection.PlotRuntime;
import xtr.plotprotection.book.OriginalBooks;
import xtr.plotprotection.domain.BoundsOutline;
import xtr.plotprotection.domain.BoundsOutline.Point;
import xtr.plotprotection.domain.Group;
import xtr.plotprotection.domain.Plot;
import xtr.plotprotection.marker.MarkerHandler;

/** Shows plot outlines (A1) only to players holding a Book and Quill, so normal play stays free of lines. */
public final class BoundsView {
	private static final DustParticleOptions FOREIGN = new DustParticleOptions(0xA040FF, 1.0f);
	private static final DustParticleOptions OWN_BOOK = new DustParticleOptions(0x40FF60, 1.0f);

	private BoundsView() {
	}

	public static void tick(PlotRuntime runtime) {
		for (ServerPlayer player : runtime.server().getPlayerList().getPlayers()) {
			if (holdsBook(player)) {
				show(runtime, player);
			}
		}
	}

	private static boolean holdsBook(ServerPlayer player) {
		return player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.WRITABLE_BOOK) || player.getItemInHand(InteractionHand.OFF_HAND).is(Items.WRITABLE_BOOK);
	}

	// ponytail: one particle packet per point; batch them if many players hold books at once
	private static void show(PlotRuntime runtime, ServerPlayer player) {
		var config = runtime.config();
		var data = runtime.data();
		String dimension = MarkerHandler.dimension(player.level());
		Optional<UUID> heldPlot = heldPlot(runtime, player);
		var viewer = new Point(player.getX(), player.getY(), player.getZ());
		for (Plot plot : data.plots()) {
			if (!dimension.equals(plot.dimension())) {
				continue;
			}
			List<Point> points = plot.bounds() != null
				? BoundsOutline.edges(plot.bounds(), viewer, config.boundsViewRange())
				: plot.validMarkers().stream()
					.flatMap(m -> BoundsOutline.column(m, config.heightBelowMarker(), config.heightAboveMarker(), viewer, config.boundsViewRange()).stream())
					.toList();
			var color = heldPlot.filter(plot.id()::equals).isPresent() ? OWN_BOOK : FOREIGN;
			for (Point p : points) {
				player.level().sendParticles(player, color, true, false, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0);
			}
		}
	}

	private static Optional<UUID> heldPlot(PlotRuntime runtime, ServerPlayer player) {
		return OriginalBooks.group(runtime.data(), player.getItemInHand(InteractionHand.MAIN_HAND))
			.or(() -> OriginalBooks.group(runtime.data(), player.getItemInHand(InteractionHand.OFF_HAND)))
			.map(Group::plotId);
	}
}
