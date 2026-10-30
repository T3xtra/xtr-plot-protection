package xtr.plotprotection;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import xtr.plotprotection.book.OriginalBooks;
import xtr.plotprotection.config.PlotConfig;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.WorldData;
import xtr.plotprotection.mixin.CachedUserNameToIdResolverAccessor;
import xtr.plotprotection.persistence.WorldDataStore;

/** Server-bound state: loaded data, config and the glue every hook needs. Only exists while a server runs. */
public final class PlotRuntime {
	private final MinecraftServer server;
	private final WorldDataStore store;
	private final PlotConfig config;

	PlotRuntime(MinecraftServer server, WorldDataStore store, PlotConfig config) {
		this.server = server;
		this.store = store;
		this.config = config;
	}

	public MinecraftServer server() {
		return server;
	}

	public PlotConfig config() {
		return config;
	}

	public WorldData data() {
		return store.current();
	}

	/** Validates and saves, then re-renders the original books held by online players. */
	public void commit(WorldData next) {
		if (next == store.current()) {
			return;
		}
		try {
			store.commit(next);
		} catch (IOException e) {
			PlotProtectionMod.LOGGER.error("Could not save plot data; the change was rejected", e);
			throw new PlotException("error.save_failed");
		}
		server.getPlayerList().getPlayers().forEach(p -> OriginalBooks.syncInventory(this, p));
	}

	public String name(UUID player) {
		return server.services().nameToIdCache().get(player).map(NameAndId::name).orElse(player.toString());
	}

	/** Only players the server already knows; unknown names are never looked up remotely. */
	public Optional<UUID> resolve(String name) {
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			return Optional.of(online.getUUID());
		}
		var cache = server.services().nameToIdCache();
		// ponytail: an expired cache entry still triggers vanilla's blocking profile lookup for this known name
		if (!(cache instanceof CachedUserNameToIdResolverAccessor known) || !known.xtrPlotProtection$profilesByName().containsKey(name.toLowerCase(Locale.ROOT))) {
			return Optional.empty();
		}
		return cache.get(name).map(NameAndId::id);
	}

	public static boolean isAdmin(ServerPlayer player) {
		return Commands.LEVEL_ADMINS.check(player.permissions());
	}
}
