package xtr.plotprotection.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.PlotRuntime;
import xtr.plotprotection.book.OriginalBooks;
import xtr.plotprotection.config.PlotConfig;
import xtr.plotprotection.domain.BookPages;
import xtr.plotprotection.domain.Group;
import xtr.plotprotection.domain.Plot;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.ProtectionMode;
import xtr.plotprotection.domain.Translator;
import xtr.plotprotection.domain.WorldData;
import xtr.plotprotection.i18n.Messages;

public final class PlotCommands {
	private PlotCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("plot")
			.then(Commands.literal("help").executes(PlotCommands::help))
			.then(Commands.literal("config").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.executes(PlotCommands::showConfig)
				.then(configValue("repairCountdownSeconds", 1,
					(c, v) -> c.withTimings(v, c.regenerationDelaySeconds(), c.regenerationSpreadSeconds(), c.regenerationBlocksPerTick())))
				.then(configValue("regenerationDelaySeconds", 0,
					(c, v) -> c.withTimings(c.repairCountdownSeconds(), v, c.regenerationSpreadSeconds(), c.regenerationBlocksPerTick())))
				.then(configValue("regenerationSpreadSeconds", 0,
					(c, v) -> c.withTimings(c.repairCountdownSeconds(), c.regenerationDelaySeconds(), v, c.regenerationBlocksPerTick())))
				.then(configValue("regenerationBlocksPerTick", 1,
					(c, v) -> c.withTimings(c.repairCountdownSeconds(), c.regenerationDelaySeconds(), c.regenerationSpreadSeconds(), v))))
			.then(Commands.literal("list").executes(ctx -> list(ctx, runtime().data())))
			.then(Commands.literal("groups").executes(ctx -> groups(ctx, runtime().data())))
			.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString())
				.executes(ctx -> run(ctx, PlotCommands::create))))
			.then(Commands.literal("book")
				.executes(ctx -> run(ctx, PlotCommands::bookSelection))
				.then(groupArgument().executes(ctx -> run(ctx, (c, rt, p, t) -> issueBook(c, rt, p, t, ownedGroup(c, rt, p))))))
			.then(Commands.literal("set").then(groupArgument()
				.then(Commands.literal("mode").then(Commands.argument("mode", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ProtectionMode.values()).map(m -> m.name().toLowerCase(Locale.ROOT)), b))
					.executes(ctx -> set(ctx, (rt, g, now) -> PlotService.setMode(rt.data(), g.plotId(), mode(ctx), now)))))
				.then(Commands.literal("doors")
					.then(Commands.literal("open").executes(ctx -> set(ctx, (rt, g, now) -> PlotService.setDoors(rt.data(), g.plotId(), true, now))))
					.then(Commands.literal("locked").executes(ctx -> set(ctx, (rt, g, now) -> PlotService.setDoors(rt.data(), g.plotId(), false, now)))))
				.then(Commands.literal("name").then(Commands.argument("value", StringArgumentType.greedyString())
					.executes(ctx -> set(ctx, (rt, g, now) -> PlotService.renamePlot(rt.data(), g.plotId(), StringArgumentType.getString(ctx, "value"), now)))))
				.then(Commands.literal("groupname").then(Commands.argument("value", StringArgumentType.greedyString())
					.executes(ctx -> set(ctx, (rt, g, now) -> PlotService.renameGroup(rt.data(), g.id(), StringArgumentType.getString(ctx, "value"))))))
				.then(Commands.literal("heights").then(Commands.argument("minY", IntegerArgumentType.integer()).then(Commands.argument("maxY", IntegerArgumentType.integer())
					.executes(ctx -> set(ctx, (rt, g, now) -> PlotService.setHeights(rt.data(), g.plotId(),
						IntegerArgumentType.getInteger(ctx, "minY"), IntegerArgumentType.getInteger(ctx, "maxY"), now))))))))
			.then(Commands.literal("transfer").then(groupArgument().then(Commands.argument("player", StringArgumentType.word())
				.executes(ctx -> run(ctx, PlotCommands::transfer)))))
			.then(Commands.literal("delete").then(groupArgument()
				.executes(ctx -> run(ctx, (c, rt, p, t) -> delete(c, rt, p, t, false)))
				.then(Commands.literal("confirm").executes(ctx -> run(ctx, (c, rt, p, t) -> delete(c, rt, p, t, true)))))));
	}

	@FunctionalInterface
	private interface Action {
		int run(CommandContext<CommandSourceStack> ctx, PlotRuntime runtime, ServerPlayer player, Translator t) throws CommandSyntaxException;
	}

	@FunctionalInterface
	private interface Change {
		WorldData apply(PlotRuntime runtime, Group group, long now);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> groupArgument() {
		return Commands.argument("group", StringArgumentType.string()).suggests(PlotCommands::suggestGroups);
	}

	/** Suggests the groups the player may act on, already quoted when the name contains spaces. */
	private static CompletableFuture<Suggestions> suggestGroups(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null || runtime() == null) {
			return builder.buildFuture();
		}
		boolean admin = PlotRuntime.isAdmin(player);
		return SharedSuggestionProvider.suggest(runtime().data().groups().stream()
			.filter(g -> admin || g.owner().equals(player.getUUID()))
			.map(g -> StringArgumentType.escapeIfRequired(g.name())), builder);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> configValue(String key, int min, BiFunction<PlotConfig, Integer, PlotConfig> change) {
		return Commands.literal(key).then(Commands.argument("value", IntegerArgumentType.integer(min)).executes(ctx -> {
			Translator t = Messages.forSource(ctx.getSource());
			int value = IntegerArgumentType.getInteger(ctx, "value");
			try {
				runtime().updateConfig(change.apply(runtime().config(), value));
			} catch (PlotException e) {
				ctx.getSource().sendFailure(Component.literal(e.translate(t)));
				return 0;
			}
			PlotProtectionMod.LOGGER.info("{} set {} to {}", ctx.getSource().getTextName(), key, value);
			return reply(ctx, Component.literal(t.get("command.config.done", key, value)).withStyle(ChatFormatting.GREEN));
		}));
	}

	private static int showConfig(CommandContext<CommandSourceStack> ctx) {
		Translator t = Messages.forSource(ctx.getSource());
		PlotConfig c = runtime().config();
		return reply(ctx, Component.literal(t.get("command.config.show", Messages.duration(c.regenerationDelaySeconds()),
			Messages.duration(c.regenerationSpreadSeconds()), c.regenerationBlocksPerTick(), Messages.duration(c.repairCountdownSeconds()))));
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		Translator t = Messages.forSource(ctx.getSource());
		var out = Component.literal(t.get("command.help.header")).withStyle(ChatFormatting.BOLD);
		var lines = new LinkedHashMap<String, String>();
		lines.put("/plot list", "list");
		lines.put("/plot groups", "groups");
		lines.put("/plot create <name>", "create");
		lines.put("/plot book [<group>]", "book");
		lines.put("/plot set <group> mode|doors|name|groupname|heights …", "set");
		lines.put("/plot transfer <group> <player>", "transfer");
		lines.put("/plot delete <group>", "delete");
		if (Commands.LEVEL_ADMINS.check(ctx.getSource().permissions())) {
			lines.put("/plot config [<key> <value>]", "config");
		}
		lines.forEach((command, key) -> out.append(Component.literal("\n" + command).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(false)
				.withClickEvent(new ClickEvent.SuggestCommand(command.replaceAll(" [<\\[].*", " ")))))
			.append(Component.literal(" – " + t.get("command.help." + key)).withStyle(ChatFormatting.RESET)));
		out.append(Component.literal("\n" + t.get("command.help.quotes")).withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)));
		return reply(ctx, out);
	}

	private static PlotRuntime runtime() {
		return PlotProtectionMod.runtime();
	}

	/** Runs a player command and turns rejected changes into a translated error message. */
	private static int run(CommandContext<CommandSourceStack> ctx, Action action) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Translator t = Messages.forPlayer(player);
		try {
			return action.run(ctx, runtime(), player, t);
		} catch (PlotException e) {
			ctx.getSource().sendFailure(Component.literal(e.translate(t)));
			return 0;
		}
	}

	private static int set(CommandContext<CommandSourceStack> ctx, Change change) throws CommandSyntaxException {
		return run(ctx, (c, rt, player, t) -> {
			Group group = ownedGroup(c, rt, player);
			rt.commit(change.apply(rt, group, System.currentTimeMillis()));
			return reply(c, Component.literal(t.get("command.set.done", rt.data().plotOf(group).name())).withStyle(ChatFormatting.GREEN));
		});
	}

	private static ProtectionMode mode(CommandContext<CommandSourceStack> ctx) {
		String value = StringArgumentType.getString(ctx, "mode");
		return Arrays.stream(ProtectionMode.values()).filter(m -> m.name().equalsIgnoreCase(value)).findFirst()
			.orElseThrow(() -> new PlotException("command.mode.invalid", value));
	}

	private static int create(CommandContext<CommandSourceStack> ctx, PlotRuntime rt, ServerPlayer player, Translator t) {
		requireFreeSlot(player);
		String name = StringArgumentType.getString(ctx, "name");
		var created = PlotService.create(rt.data(), player.getUUID(), name, name, System.currentTimeMillis());
		rt.commit(created.data());
		player.getInventory().add(OriginalBooks.create(rt, created.group(), player));
		return reply(ctx, Component.literal(t.get("plot.created_by_command", created.plot().name())).withStyle(ChatFormatting.GREEN));
	}

	/** Owners get their only book directly or a clickable selection; members cannot request books (COMMANDS.md §3). */
	private static int bookSelection(CommandContext<CommandSourceStack> ctx, PlotRuntime rt, ServerPlayer player, Translator t) {
		var data = rt.data();
		List<Group> owned = data.groups().stream().filter(g -> g.owner().equals(player.getUUID())).toList();
		if (owned.isEmpty()) {
			throw new PlotException(data.groups().stream().anyMatch(g -> g.isMember(player.getUUID())) ? "book.owner_only" : "command.groups.none");
		}
		if (owned.size() == 1) {
			return issueBook(ctx, rt, player, t, owned.getFirst());
		}
		var out = Component.literal(t.get("command.book.choose")).withStyle(ChatFormatting.BOLD);
		for (Group g : owned) {
			String command = "/plot book " + g.id();
			out.append(Component.literal("\n- ").withStyle(ChatFormatting.RESET))
				.append(Component.literal("[" + g.name() + " / " + data.plotOf(g).name() + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(false)
					.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
		}
		return reply(ctx, out);
	}

	/** Checks the inventory before the old original loses its authority, so no book can get lost. */
	private static int issueBook(CommandContext<CommandSourceStack> ctx, PlotRuntime rt, ServerPlayer player, Translator t, Group group) {
		requireFreeSlot(player);
		var issued = PlotService.issueBook(rt.data(), group.id());
		rt.commit(issued.data());
		player.getInventory().add(OriginalBooks.create(rt, issued.data().group(group.id()).orElseThrow(), player));
		return reply(ctx, Component.literal(t.get("command.book.issued", group.name())).withStyle(ChatFormatting.GREEN));
	}

	private static int transfer(CommandContext<CommandSourceStack> ctx, PlotRuntime rt, ServerPlayer player, Translator t) {
		Group group = ownedGroup(ctx, rt, player);
		String name = StringArgumentType.getString(ctx, "player");
		UUID newOwner = rt.resolve(name).orElseThrow(() -> new PlotException("player.unknown", name));
		rt.commit(PlotService.transferOwner(rt.data(), group.id(), newOwner));
		return reply(ctx, Component.literal(t.get("command.transfer.done", group.name(), rt.name(newOwner))).withStyle(ChatFormatting.GREEN));
	}

	/** Only plots without valid markers can be deleted; the first call asks for explicit confirmation. */
	private static int delete(CommandContext<CommandSourceStack> ctx, PlotRuntime rt, ServerPlayer player, Translator t, boolean confirmed) {
		Group group = ownedGroup(ctx, rt, player);
		Plot plot = rt.data().plotOf(group);
		if (!plot.validMarkers().isEmpty()) {
			throw new PlotException("plot.delete_markers_left", plot.validMarkers().size());
		}
		if (!confirmed) {
			String command = "/plot delete " + group.id() + " confirm";
			return reply(ctx, Component.literal(t.get("plot.delete_confirm", plot.name()) + " ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("[" + t.get("plot.delete_button") + "]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))))));
		}
		rt.commit(PlotService.delete(rt.data(), plot.id()));
		rt.server().getPlayerList().getPlayers().forEach(p -> OriginalBooks.removeBook(p, group.bookId()));
		return reply(ctx, Component.literal(t.get("plot.deleted", plot.name())).withStyle(ChatFormatting.GREEN));
	}

	/** Resolves a group by id or unique name; normal players only reach their own groups, OPs (level 3) any. */
	private static Group ownedGroup(CommandContext<CommandSourceStack> ctx, PlotRuntime rt, ServerPlayer player) {
		String input = StringArgumentType.getString(ctx, "group");
		boolean admin = PlotRuntime.isAdmin(player);
		var data = rt.data();
		Group group;
		try {
			group = data.group(UUID.fromString(input)).orElseThrow(() -> new PlotException("group.not_found"));
		} catch (IllegalArgumentException notAnId) {
			List<Group> named = data.groups().stream()
				.filter(g -> g.name().equalsIgnoreCase(input) && (admin || g.owner().equals(player.getUUID()))).toList();
			if (named.size() > 1) {
				throw new PlotException("group.ambiguous", named.stream().map(g -> g.id().toString()).collect(Collectors.joining(", ")));
			}
			group = named.stream().findFirst().orElseThrow(() -> new PlotException("group.not_found"));
		}
		if (!admin && !group.owner().equals(player.getUUID())) {
			throw new PlotException("group.not_owner");
		}
		return group;
	}

	private static void requireFreeSlot(ServerPlayer player) {
		if (player.getInventory().getFreeSlot() < 0) {
			throw new PlotException("inventory.full");
		}
	}

	private static int list(CommandContext<CommandSourceStack> ctx, WorldData data) throws CommandSyntaxException {
		UUID player = ctx.getSource().getPlayerOrException().getUUID();
		Translator t = Messages.forSource(ctx.getSource());
		List<Group> owned = data.groups().stream().filter(g -> g.owner().equals(player)).toList();
		List<Group> member = data.groups().stream().filter(g -> g.members().contains(player)).toList();
		if (owned.isEmpty() && member.isEmpty()) {
			return reply(ctx, Component.literal(t.get("command.list.none")));
		}
		var out = Component.empty();
		if (!owned.isEmpty()) {
			out.append(Component.literal(t.get("command.list.owned")).withStyle(ChatFormatting.BOLD));
			owned.forEach(g -> out.append("\n").append(plotLine(data.plotOf(g), g, t)));
		}
		if (!member.isEmpty()) {
			if (!owned.isEmpty()) {
				out.append("\n");
			}
			out.append(Component.literal(t.get("command.list.member")).withStyle(ChatFormatting.BOLD));
			member.forEach(g -> out.append("\n").append(plotLine(data.plotOf(g), g, t)));
		}
		return reply(ctx, out);
	}

	private static int groups(CommandContext<CommandSourceStack> ctx, WorldData data) throws CommandSyntaxException {
		UUID player = ctx.getSource().getPlayerOrException().getUUID();
		Translator t = Messages.forSource(ctx.getSource());
		List<Group> groups = data.groups().stream().filter(g -> g.isMember(player)).toList();
		if (groups.isEmpty()) {
			return reply(ctx, Component.literal(t.get("command.groups.none")));
		}
		var out = Component.literal(t.get("command.groups.header")).withStyle(ChatFormatting.BOLD);
		for (Group g : groups) {
			Plot plot = data.plotOf(g);
			String role = t.get(g.owner().equals(player) ? "role.owner" : "role.member");
			out.append(Component.literal("\n" + t.get("command.groups.entry", g.name(), role, plot.name()) + " ").withStyle(ChatFormatting.RESET))
				.append(status(plot, t));
		}
		return reply(ctx, out);
	}

	private static MutableComponent plotLine(Plot plot, Group group, Translator t) {
		return Component.literal(t.get("command.list.entry", plot.name(), group.name(), t.get(BookPages.modeKey(plot.mode()))) + " ")
			.withStyle(ChatFormatting.RESET)
			.append(status(plot, t));
	}

	private static MutableComponent status(Plot plot, Translator t) {
		return Component.literal(t.get(BookPages.statusKey(plot.status()))).withStyle(ChatFormatting.getByCode(plot.status().colorCode));
	}

	private static int reply(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> message, false);
		return 1;
	}
}
