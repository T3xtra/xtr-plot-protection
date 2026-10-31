package xtr.plotprotection.marker;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import xtr.plotprotection.PlotRuntime;
import xtr.plotprotection.book.OriginalBooks;
import xtr.plotprotection.domain.BlockPosition;
import xtr.plotprotection.domain.Group;
import xtr.plotprotection.domain.Plot;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.PlotStatus;
import xtr.plotprotection.domain.Translator;
import xtr.plotprotection.domain.WorldData;
import xtr.plotprotection.i18n.Messages;

/** Lodestone markers: registration and plot creation with a book, removal only with the plot's original book. */
public final class MarkerHandler {
	private MarkerHandler() {
	}

	public static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	public static BlockPosition position(BlockPos pos) {
		return new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
	}

	public static InteractionResult onUse(PlotRuntime runtime, ServerPlayer player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.WRITABLE_BOOK) || !level.getBlockState(pos).is(Blocks.LODESTONE)) {
			return InteractionResult.PASS;
		}
		Translator t = Messages.forPlayer(player);
		var config = runtime.config();
		long now = System.currentTimeMillis();
		try {
			Optional<UUID> bookId = OriginalBooks.bookId(stack);
			if (bookId.isEmpty()) {
				String name = firstLine(stack).orElse(t.get("plot.default_name", player.getPlainTextName()));
				var created = PlotService.create(runtime.data(), player.getUUID(), name, name, now);
				var next = PlotService.registerMarker(created.data(), created.group().bookId(), dimension(level), position(pos), now, config.heightBelowMarker(), config.heightAboveMarker());
				runtime.commit(next);
				OriginalBooks.makeOriginal(runtime, stack, created.group(), player);
				player.sendSystemMessage(Component.literal(t.get("plot.created_by_book", created.plot().name())).withStyle(ChatFormatting.GREEN));
			} else {
				var next = PlotService.registerMarker(runtime.data(), bookId.get(), dimension(level), position(pos), now, config.heightBelowMarker(), config.heightAboveMarker());
				runtime.commit(next);
				Plot plot = next.plotOf(next.groupByBook(bookId.get()).orElseThrow());
				player.sendSystemMessage(plot.status() == PlotStatus.ACTIVE
					? Component.literal(t.get("marker.registered_active", plot.name())).withStyle(ChatFormatting.GREEN)
					: Component.literal(t.get("marker.registered", plot.name(), plot.validMarkers().size())).withStyle(ChatFormatting.YELLOW));
			}
		} catch (PlotException e) {
			player.sendSystemMessage(Component.literal(e.translate(t)).withStyle(ChatFormatting.RED));
		}
		// the vanilla client predicts the item use and opens the book screen; this closes it again
		player.connection.send(new ClientboundContainerClosePacket(player.containerMenu.containerId));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** A registered marker may only be mined by a player carrying the plot's original book. */
	public static boolean beforeBreak(PlotRuntime runtime, ServerPlayer player, Level level, BlockPos pos, BlockState state) {
		if (!state.is(Blocks.LODESTONE)) {
			return true;
		}
		WorldData data = runtime.data();
		Optional<Plot> plot = data.plotWithMarkerAt(dimension(level), position(pos)).filter(p -> p.validMarkers().contains(position(pos)));
		if (plot.isEmpty() || OriginalBooks.holdsOriginal(player, data.groupOf(plot.get()))) {
			return true;
		}
		player.sendSystemMessage(Component.literal(Messages.forPlayer(player).get("marker.protected")).withStyle(ChatFormatting.RED));
		return false;
	}

	public static void afterBreak(PlotRuntime runtime, ServerPlayer player, Level level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.LODESTONE)) {
			removed(runtime, dimension(level), position(pos), player);
		}
	}

	/** Records the loss of a marker block and notifies the owner; {@code actor} may be null. */
	public static void removed(PlotRuntime runtime, String dimension, BlockPosition position, ServerPlayer actor) {
		long countdown = runtime.config().repairCountdownSeconds() * 1000;
		var removed = PlotService.removeMarker(runtime.data(), dimension, position, System.currentTimeMillis(), countdown);
		if (removed.isEmpty()) {
			return;
		}
		try {
			runtime.commit(removed.get().data());
		} catch (PlotException e) {
			if (actor != null) {
				actor.sendSystemMessage(Component.literal(e.translate(Messages.forPlayer(actor))).withStyle(ChatFormatting.RED));
			}
			return;
		}
		Plot plot = removed.get().plot();
		Group group = removed.get().data().groupOf(plot);
		ServerPlayer owner = runtime.server().getPlayerList().getPlayer(group.owner());
		for (ServerPlayer recipient : new ServerPlayer[] {owner, actor == owner ? null : actor}) {
			if (recipient == null) {
				continue;
			}
			Translator t = Messages.forPlayer(recipient);
			recipient.sendSystemMessage(plot.status() == PlotStatus.REPAIR_PENDING
				? Component.literal(t.get("marker.removed_repair", plot.name(), position, Messages.duration(runtime.config().repairCountdownSeconds()))).withStyle(ChatFormatting.GOLD)
				: Component.literal(t.get("marker.removed", plot.name(), position)).withStyle(ChatFormatting.GOLD));
		}
		if (owner != null && removed.get().allMarkersMissing()) {
			owner.sendSystemMessage(deleteOffer(group, Messages.forPlayer(owner)));
		}
	}

	public static Component deleteOffer(Group group, Translator t) {
		String command = "/plot delete " + group.id();
		return Component.literal(t.get("plot.delete_offer") + " ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("[" + t.get("plot.delete_button") + "]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
	}

	private static Optional<String> firstLine(ItemStack stack) {
		return stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY).getPages(false)
			.findFirst().flatMap(page -> page.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst());
	}
}
