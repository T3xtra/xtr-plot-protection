package xtr.plotprotection.protection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import xtr.plotprotection.PlotRuntime;
import xtr.plotprotection.domain.PermissionResolver;
import xtr.plotprotection.domain.PermissionResolver.Action;
import xtr.plotprotection.domain.PermissionResolver.Decision;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.i18n.Messages;
import xtr.plotprotection.marker.MarkerHandler;
import xtr.plotprotection.mixin.CompoundContainerAccessor;
import xtr.plotprotection.regeneration.WorldRegeneration;

/** Translates direct player actions into {@link PermissionResolver} questions and enforces the answer. */
public final class PlayerProtection {
	/**
	 * Blocks whose use changes nothing that needs protection (SPEC §6: buttons and levers stay usable).
	 * Each of them always consumes the click, so a held item never acts on a neighbouring block instead.
	 */
	private static final List<Class<? extends Block>> HARMLESS = List.of(
		ButtonBlock.class, LeverBlock.class, EnderChestBlock.class, CraftingTableBlock.class, StonecutterBlock.class, LoomBlock.class,
		CartographyTableBlock.class, GrindstoneBlock.class, EnchantingTableBlock.class, AnvilBlock.class
	);
	/** Blocks that hand items to the player without a menu; regenerating them would duplicate the items. */
	private static final List<Class<? extends Block>> YIELDING = List.of(
		LecternBlock.class, FlowerPotBlock.class, SweetBerryBushBlock.class, CaveVinesBlock.class, CaveVinesPlantBlock.class,
		BeehiveBlock.class, ComposterBlock.class, PumpkinBlock.class, BrushableBlock.class, AbstractCauldronBlock.class, VaultBlock.class,
		ChiseledBookShelfBlock.class, DecoratedPotBlock.class, JukeboxBlock.class, ShelfBlock.class
	);

	private PlayerProtection() {
	}

	/** Unauthorized breaking in regenerative plots is done here without drops, so vanilla's break is always cancelled then. */
	public static boolean beforeBreak(PlotRuntime runtime, ServerPlayer player, Level level, BlockPos pos) {
		Decision decision = decide(runtime, player, level, pos, Action.BREAK);
		if (decision == Decision.REGENERATE) {
			try {
				WorldRegeneration.breakWithoutDrops(runtime, level, List.of(pos));
			} catch (PlotException e) {
				deny(player);
			}
			return false;
		}
		return enforce(runtime, player, level, decision, List.of(pos), false);
	}

	/** Left-clicking can already change blocks (e.g. dragon eggs); denied areas reject the click outright. */
	public static InteractionResult onAttackBlock(PlotRuntime runtime, ServerPlayer player, Level level, BlockPos pos) {
		return decide(runtime, player, level, pos, Action.BREAK) == Decision.DENY ? InteractionResult.FAIL : InteractionResult.PASS;
	}

	public static InteractionResult onUseBlock(PlotRuntime runtime, ServerPlayer player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		ItemStack stack = player.getItemInHand(hand);
		boolean skipsBlockUse = player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty());
		Action interaction = skipsBlockUse ? null : interaction(level, pos);
		var decisions = new ArrayList<Decision>();
		var touched = new ArrayList<BlockPos>();
		if (interaction != null) {
			decisions.add(decide(runtime, player, level, pos, interaction));
			touched.add(pos);
		}
		// the item acts only if the block does not consume the click; it may change the clicked or the adjacent block
		if (!stack.isEmpty() && (skipsBlockUse || interaction == Action.MODIFY)) {
			for (BlockPos p : List.of(pos, pos.relative(hit.getDirection()))) {
				decisions.add(decide(runtime, player, level, p, Action.PLACE));
				touched.add(p);
			}
		}
		Decision decision = decisions.stream().max(Comparator.naturalOrder()).orElse(Decision.ALLOW);
		return enforce(runtime, player, level, decision, touched, !(stack.getItem() instanceof BucketItem)) ? InteractionResult.PASS : InteractionResult.FAIL;
	}

	/** Buckets and lily pads act on the block the player looks at, without a block use event. */
	public static InteractionResult onUseItem(PlotRuntime runtime, ServerPlayer player, Level level, InteractionHand hand) {
		var item = player.getItemInHand(hand).getItem();
		if (!(item instanceof BucketItem) && !(item instanceof PlaceOnWaterBlockItem)) {
			return InteractionResult.PASS;
		}
		var decisions = new ArrayList<Decision>();
		var touched = new ArrayList<BlockPos>();
		for (boolean fluids : new boolean[] {true, false}) {
			if (player.pick(player.blockInteractionRange(), 1.0f, fluids) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
				for (BlockPos p : List.of(hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection()))) {
					decisions.add(decide(runtime, player, level, p, Action.PLACE));
					touched.add(p);
				}
			}
		}
		Decision decision = decisions.stream().max(Comparator.naturalOrder()).orElse(Decision.ALLOW);
		return enforce(runtime, player, level, decision, touched, !(item instanceof BucketItem)) ? InteractionResult.PASS : InteractionResult.FAIL;
	}

	/**
	 * Unauthorized players may put items into a protected container but never take any out (SPEC §5.2, A2b).
	 * Ender chests are not block entity containers and are therefore never restricted.
	 */
	public static boolean mayClick(PlotRuntime runtime, AbstractContainerMenu menu, int slotIndex, ContainerInput input, ServerPlayer player) {
		if (input == ContainerInput.PICKUP_ALL || input == ContainerInput.QUICK_CRAFT) {
			return menu.slots.stream().noneMatch(s -> isRestricted(runtime, s, player));
		}
		if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
			return true;
		}
		Slot slot = menu.slots.get(slotIndex);
		if (!isRestricted(runtime, slot, player)) {
			return true;
		}
		boolean putsIntoEmptySlot = input == ContainerInput.PICKUP && !slot.hasItem() && !menu.getCarried().isEmpty();
		if (!putsIntoEmptySlot) {
			deny(player);
		}
		return putsIntoEmptySlot;
	}

	private static boolean isRestricted(PlotRuntime runtime, Slot slot, ServerPlayer player) {
		for (BlockEntity be : blockEntities(slot.container)) {
			if (be.getLevel() != null && decide(runtime, player, be.getLevel(), be.getBlockPos(), Action.TAKE_ITEM) != Decision.ALLOW) {
				return true;
			}
		}
		return false;
	}

	private static List<BlockEntity> blockEntities(Container container) {
		if (container instanceof BlockEntity be) {
			return List.of(be);
		}
		if (container instanceof CompoundContainer compound) {
			var accessor = (CompoundContainerAccessor) compound;
			var parts = new ArrayList<BlockEntity>(blockEntities(accessor.xtrPlotProtection$container1()));
			parts.addAll(blockEntities(accessor.xtrPlotProtection$container2()));
			return parts;
		}
		return List.of();
	}

	/** Null for harmless interactions that need no check. */
	private static Action interaction(Level level, BlockPos pos) {
		Block block = level.getBlockState(pos).getBlock();
		// iron doors ignore the click, so the held item would act instead
		if (block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR) {
			return Action.MODIFY;
		}
		if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
			return Action.USE_DOOR;
		}
		if (HARMLESS.stream().anyMatch(c -> c.isInstance(block))) {
			return null;
		}
		if (YIELDING.stream().anyMatch(c -> c.isInstance(block))) {
			return Action.TAKE_ITEM;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof Container) {
			return blockEntity instanceof MenuProvider ? Action.OPEN_CONTAINER : Action.TAKE_ITEM;
		}
		return Action.MODIFY;
	}

	private static Decision decide(PlotRuntime runtime, ServerPlayer player, Level level, BlockPos pos, Action action) {
		return PermissionResolver.decide(runtime.data(), MarkerHandler.dimension(level), MarkerHandler.position(pos), player.getUUID(), action);
	}

	/**
	 * Returns whether the action may proceed; regenerative changes are snapshotted first. Fluids spread beyond
	 * the snapshotted blocks, so they are refused instead of regenerated.
	 */
	private static boolean enforce(PlotRuntime runtime, ServerPlayer player, Level level, Decision decision, List<BlockPos> touched, boolean canRegenerate) {
		if (decision == Decision.ALLOW) {
			return true;
		}
		if (decision == Decision.REGENERATE && canRegenerate) {
			try {
				WorldRegeneration.capture(runtime, level, touched);
				return true;
			} catch (PlotException e) {
				// fall through to refusing the change
			}
		}
		deny(player);
		player.containerMenu.sendAllDataToRemote();
		return false;
	}

	private static void deny(ServerPlayer player) {
		player.sendSystemMessage(Component.literal(Messages.forPlayer(player).get("protection.denied")).withStyle(ChatFormatting.RED), true);
	}
}
