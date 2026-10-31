package xtr.plotprotection.regeneration;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.PlotRuntime;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.Regeneration;
import xtr.plotprotection.domain.RegenerationEntry;
import xtr.plotprotection.marker.MarkerHandler;

/** World side of regeneration: snapshots before unauthorized changes and restores them in batches. */
public final class WorldRegeneration {
	private WorldRegeneration() {
	}

	/** Snapshots the blocks, including the other half of doors, beds and tall plants, before they change. */
	public static void capture(PlotRuntime runtime, Level level, Collection<BlockPos> positions) {
		long restoreAt = System.currentTimeMillis() + runtime.config().regenerationDelaySeconds() * 1000;
		String dimension = MarkerHandler.dimension(level);
		var data = runtime.data();
		var snapshots = new ArrayList<RegenerationEntry>();
		for (BlockPos pos : withPartners(level, positions)) {
			if (!Regeneration.isPending(data, dimension, MarkerHandler.position(pos))) {
				snapshots.add(snapshot(level, dimension, pos, restoreAt));
			}
		}
		runtime.commit(Regeneration.capture(data, snapshots));
	}

	/**
	 * Removes blocks without drops after snapshotting them; inventories are emptied first so nothing spills.
	 * Neighbours are not updated, so attached blocks like torches stay until the restoration.
	 */
	public static void breakWithoutDrops(PlotRuntime runtime, Level level, Collection<BlockPos> positions) {
		var all = withPartners(level, positions);
		capture(runtime, level, all);
		for (BlockPos pos : all) {
			BlockState state = level.getBlockState(pos);
			clearContent(level, pos);
			level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		}
	}

	/** Snapshots and empties an inventory that is about to be destroyed by a mechanic that would drop it. */
	public static void captureWithoutDrops(PlotRuntime runtime, Level level, BlockPos pos) {
		capture(runtime, level, List.of(pos));
		clearContent(level, pos);
	}

	/** Restores due entries; called once per second with twenty ticks' worth of the configured batch. */
	public static void restoreDue(PlotRuntime runtime, MinecraftServer server) {
		var due = Regeneration.due(runtime.data(), System.currentTimeMillis(), runtime.config().regenerationBlocksPerTick() * 20);
		if (due.isEmpty()) {
			return;
		}
		var restored = new ArrayList<RegenerationEntry>();
		var next = runtime.data();
		for (RegenerationEntry entry : due) {
			try {
				Level level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.dimension())));
				if (level == null) {
					throw new IllegalStateException("unknown dimension");
				}
				restore(level, entry);
				restored.add(entry);
			} catch (RuntimeException | CommandSyntaxException e) {
				PlotProtectionMod.LOGGER.error("Could not restore {} {}; keeping the snapshot and retrying later", entry.dimension(), entry.position(), e);
				next = Regeneration.postpone(next, entry, System.currentTimeMillis() + runtime.config().regenerationDelaySeconds() * 1000);
			}
		}
		try {
			runtime.commit(Regeneration.markRestored(next, restored));
		} catch (PlotException e) {
			PlotProtectionMod.LOGGER.error("Could not persist restored regeneration entries; they will be applied again", e);
		}
	}

	/** Overwrites the block and its inventory with the snapshot, so applying an entry twice changes nothing. */
	static void restore(Level level, RegenerationEntry entry) throws CommandSyntaxException {
		BlockPos pos = new BlockPos(entry.position().x(), entry.position().y(), entry.position().z());
		level.getChunk(pos);
		BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), TagParser.parseCompoundFully(entry.blockState()));
		clearContent(level, pos);
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (entry.blockEntity() != null) {
			BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, TagParser.parseCompoundFully(entry.blockEntity()), level.registryAccess());
			if (blockEntity == null) {
				throw new IllegalStateException("block entity could not be recreated");
			}
			level.setBlockEntity(blockEntity);
		}
	}

	private static RegenerationEntry snapshot(Level level, String dimension, BlockPos pos, long restoreAt) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return new RegenerationEntry(dimension, MarkerHandler.position(pos), NbtUtils.writeBlockState(level.getBlockState(pos)).toString(),
			blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess()).toString(), restoreAt, false);
	}

	private static void clearContent(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof Clearable clearable) {
			clearable.clearContent();
		}
	}

	private static Set<BlockPos> withPartners(Level level, Collection<BlockPos> positions) {
		var all = new LinkedHashSet<BlockPos>(positions);
		for (BlockPos pos : positions) {
			BlockState state = level.getBlockState(pos);
			if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
				all.add(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
			} else if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
				all.add(state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite()));
			}
		}
		return all;
	}
}
