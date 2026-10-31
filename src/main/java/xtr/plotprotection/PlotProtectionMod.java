package xtr.plotprotection;

import java.io.IOException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtr.plotprotection.book.OriginalBooks;
import xtr.plotprotection.command.PlotCommands;
import xtr.plotprotection.config.PlotConfig;
import xtr.plotprotection.domain.PermissionResolver;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.Regeneration;
import xtr.plotprotection.domain.WorldData;
import xtr.plotprotection.marker.MarkerHandler;
import xtr.plotprotection.persistence.WorldDataStore;
import xtr.plotprotection.protection.EntityProtection;
import xtr.plotprotection.protection.PlayerProtection;
import xtr.plotprotection.regeneration.WorldRegeneration;
import xtr.plotprotection.visual.BoundsView;

public class PlotProtectionMod implements ModInitializer {
	public static final String MOD_ID = "xtr_plot_protection";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Static only because mixins cannot receive it otherwise; null while no server runs. */
	private static PlotRuntime runtime;

	public static PlotRuntime runtime() {
		return runtime;
	}

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PlotCommands.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTING.register(PlotProtectionMod::load);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> runtime = null);
		ServerTickEvents.END_SERVER_TICK.register(PlotProtectionMod::tick);
		ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> {
			if (runtime != null) {
				commitLogged(Regeneration.pruneRestored(runtime.data()), "pruned regeneration entries");
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (runtime != null) {
				OriginalBooks.syncInventory(runtime, handler.player);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (runtime == null || !(player instanceof ServerPlayer p)) {
				return InteractionResult.PASS;
			}
			var result = MarkerHandler.onUse(runtime, p, level, hand, hit);
			return result != InteractionResult.PASS ? result : PlayerProtection.onUseBlock(runtime, p, level, hand, hit);
		});
		UseItemCallback.EVENT.register((player, level, hand) -> runtime != null && player instanceof ServerPlayer p
			? PlayerProtection.onUseItem(runtime, p, level, hand)
			: InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> runtime != null && player instanceof ServerPlayer p
			&& !PermissionResolver.isMarker(runtime.data(), MarkerHandler.dimension(level), MarkerHandler.position(pos))
			? PlayerProtection.onAttackBlock(runtime, p, level, pos)
			: InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> runtime != null && player instanceof ServerPlayer p
			? EntityProtection.onAttack(p, entity)
			: InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> runtime != null && player instanceof ServerPlayer p
			? EntityProtection.onUse(p, hand, entity)
			: InteractionResult.PASS);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> EntityProtection.allowDamage(entity, source));
		// registered markers follow their own rule (original book required), even for admins inside foreign plots
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (runtime == null || !(player instanceof ServerPlayer p)) {
				return true;
			}
			return PermissionResolver.isMarker(runtime.data(), MarkerHandler.dimension(level), MarkerHandler.position(pos))
				? MarkerHandler.beforeBreak(runtime, p, level, pos, state)
				: PlayerProtection.beforeBreak(runtime, p, level, pos);
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (runtime != null && player instanceof ServerPlayer p) {
				MarkerHandler.afterBreak(runtime, p, level, pos, state);
			}
		});
	}

	private static void load(MinecraftServer server) {
		try {
			var configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
			var config = PlotConfig.load(configFile);
			var store = WorldDataStore.open(server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID), config.backupCount());
			runtime = new PlotRuntime(server, store, configFile, config);
			runtime.commit(Regeneration.reopenRestored(runtime.data()));
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("Plot protection data could not be loaded; aborting start to protect existing data", e);
		}
	}

	private static void tick(MinecraftServer server) {
		if (runtime == null) {
			return;
		}
		if (server.getTickCount() % runtime.config().boundsViewIntervalTicks() == 0) {
			BoundsView.tick(runtime);
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		commitLogged(PlotService.expireRepairs(runtime.data(), System.currentTimeMillis()), "expired repair countdowns");
		WorldRegeneration.restoreDue(runtime, server);
	}

	private static void commitLogged(WorldData next, String what) {
		try {
			runtime.commit(next);
		} catch (PlotException e) {
			LOGGER.error("Could not persist {}: {}", what, e.getMessage());
		}
	}
}
