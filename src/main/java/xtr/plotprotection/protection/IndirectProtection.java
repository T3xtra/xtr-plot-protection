package xtr.plotprotection.protection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerExplosion;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PermissionResolver;
import xtr.plotprotection.domain.PermissionResolver.Action;
import xtr.plotprotection.domain.PermissionResolver.Cause;
import xtr.plotprotection.domain.PermissionResolver.Decision;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.marker.MarkerHandler;
import xtr.plotprotection.regeneration.WorldRegeneration;

/** Entry point for mixins on mechanics that change blocks without a player acting directly. */
public final class IndirectProtection {
	private IndirectProtection() {
	}

	/** {@code source} may be null if the mechanic has no position; worldgen regions are never restricted. */
	public static Decision decide(LevelReader reader, BlockPos source, BlockPos target, Cause cause, UUID actor) {
		var runtime = PlotProtectionMod.runtime();
		if (runtime == null || !(reader instanceof Level level)) {
			return Decision.ALLOW;
		}
		return PermissionResolver.decideIndirect(runtime.data(), MarkerHandler.dimension(level),
			source == null ? null : MarkerHandler.position(source), MarkerHandler.position(target), actor, cause);
	}

	/** Pistons, liquids, dispensers, hoppers: never regenerative, a mechanic either may act or not. */
	public static boolean allows(LevelReader reader, BlockPos source, BlockPos target) {
		return decide(reader, source, target, Cause.MECHANIC, null) == Decision.ALLOW;
	}

	/** Removes protected positions in place and takes regenerative ones out without drops; vanilla shuffles the list afterwards. */
	public static void filterExplosion(Level level, ServerExplosion explosion, List<BlockPos> positions) {
		Cause cause = explosion.getDirectSourceEntity() instanceof Creeper ? Cause.CREEPER : Cause.ENVIRONMENT;
		UUID actor = explosion.getIndirectSourceEntity() instanceof ServerPlayer player ? player.getUUID() : null;
		var regenerate = new ArrayList<BlockPos>();
		positions.removeIf(pos -> switch (decide(level, null, pos, cause, actor)) {
			case ALLOW -> false;
			case REGENERATE -> regenerate.add(pos);
			case DENY -> true;
		});
		if (!regenerate.isEmpty()) {
			try {
				WorldRegeneration.breakWithoutDrops(PlotProtectionMod.runtime(), level, regenerate);
			} catch (PlotException e) {
				PlotProtectionMod.LOGGER.error("Could not snapshot exploded blocks; they stay intact", e);
			}
		}
	}

	/** Fire spread and burning cannot be attributed to a player, so it is an unauthorized cause. */
	public static Decision fireDecision(LevelReader reader, BlockPos target) {
		return decide(reader, null, target, Cause.ENVIRONMENT, null);
	}

	/** Block changes by entities, e.g. withers, endermen, trampling; players are judged like direct actions. */
	public static Decision entityDecision(Level level, BlockPos target, Entity entity) {
		var runtime = PlotProtectionMod.runtime();
		if (runtime != null && entity instanceof ServerPlayer player) {
			String dimension = MarkerHandler.dimension(level);
			return PermissionResolver.isMarker(runtime.data(), dimension, MarkerHandler.position(target))
				? Decision.DENY
				: PermissionResolver.decide(runtime.data(), dimension, MarkerHandler.position(target), player.getUUID(), Action.BREAK);
		}
		return decide(level, null, target, Cause.ENVIRONMENT, null);
	}

	/** Whether a change may proceed; a regenerative change is snapshotted first. */
	public static boolean proceed(Level level, BlockPos target, Decision decision) {
		if (decision != Decision.REGENERATE) {
			return decision == Decision.ALLOW;
		}
		try {
			WorldRegeneration.capture(PlotProtectionMod.runtime(), level, List.of(target));
			return true;
		} catch (PlotException e) {
			return false;
		}
	}
}
