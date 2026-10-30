package xtr.plotprotection.book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import xtr.plotprotection.PlotRuntime;
import xtr.plotprotection.domain.BookPages;
import xtr.plotprotection.domain.Group;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.Translator;
import xtr.plotprotection.domain.WorldData;
import xtr.plotprotection.i18n.Messages;

/**
 * Book and Quill items carrying a book id. The id only marks the item; authority comes from the server-side
 * registration in {@link Group#bookId()}, so copies and replaced originals carry an id that no group accepts.
 */
public final class OriginalBooks {
	private static final String BOOK_ID = "xtr_plot_protection:book_id";
	private static final List<Translator> LANGUAGES = Messages.LOCALES.stream().map(Messages::forLocale).toList();

	private OriginalBooks() {
	}

	public static Optional<UUID> bookId(ItemStack stack) {
		if (!stack.is(Items.WRITABLE_BOOK)) {
			return Optional.empty();
		}
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(BOOK_ID).map(UUID::fromString);
	}

	public static Optional<Group> group(WorldData data, ItemStack stack) {
		return bookId(stack).flatMap(data::groupByBook);
	}

	public static boolean holdsOriginal(ServerPlayer player, Group group) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (bookId(inventory.getItem(slot)).filter(group.bookId()::equals).isPresent()) {
				return true;
			}
		}
		return false;
	}

	public static ItemStack create(PlotRuntime runtime, Group group, ServerPlayer holder) {
		var stack = new ItemStack(Items.WRITABLE_BOOK);
		makeOriginal(runtime, stack, group, holder);
		return stack;
	}

	/** Turns the stack into the original of the group; the caller must already have registered {@code group.bookId()}. */
	public static void makeOriginal(PlotRuntime runtime, ItemStack stack, Group group, ServerPlayer holder) {
		var tag = new CompoundTag();
		tag.putString(BOOK_ID, group.bookId().toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		render(runtime, stack, group, Messages.forPlayer(holder));
	}

	/** Re-renders every original in the inventory from the canonical state. */
	public static void syncInventory(PlotRuntime runtime, ServerPlayer player) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			group(runtime.data(), stack).ifPresent(g -> render(runtime, stack, g, Messages.forPlayer(player)));
		}
	}

	public static void removeBook(ServerPlayer player, UUID bookId) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (bookId(inventory.getItem(slot)).filter(bookId::equals).isPresent()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * Applies an edit of an original book; returns false for any other book so vanilla handles it.
	 * The editor's identity is deliberately not checked (SPEC §8.11).
	 */
	public static boolean onEdit(PlotRuntime runtime, ServerPlayer player, int slot, List<String> pages) {
		ItemStack stack = player.getInventory().getItem(slot);
		if (bookId(stack).isEmpty()) {
			return false;
		}
		Translator t = Messages.forPlayer(player);
		WorldData data = runtime.data();
		Optional<Group> found = group(data, stack);
		if (found.isEmpty()) {
			player.sendSystemMessage(Component.literal(t.get("book.invalid")).withStyle(ChatFormatting.RED));
			return true;
		}
		Group group = found.get();
		var previous = stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY).getPages(false).toList();
		long now = System.currentTimeMillis();
		var settings = BookPages.applySystemPage(data, group.plotId(), previous.isEmpty() ? "" : previous.getFirst(), pages.isEmpty() ? "" : pages.getFirst(), LANGUAGES, now);
		var members = BookPages.applyMemberPages(settings.data().group(group.id()).orElseThrow(), pages.stream().skip(1).toList(), runtime::name, runtime::resolve);
		try {
			runtime.commit(settings.data().with(members.group()));
			settings.rejected().forEach(e -> player.sendSystemMessage(Component.literal(e.translate(t)).withStyle(ChatFormatting.RED)));
			members.rejected().forEach(name -> player.sendSystemMessage(Component.literal(t.get("member.unknown", name)).withStyle(ChatFormatting.RED)));
		} catch (PlotException e) {
			player.sendSystemMessage(Component.literal(e.translate(t)).withStyle(ChatFormatting.RED));
		}
		syncInventory(runtime, player);
		return true;
	}

	private static void render(PlotRuntime runtime, ItemStack stack, Group group, Translator t) {
		var plot = runtime.data().plotOf(group);
		var pages = BookPages.render(plot, group, runtime::name, t).stream().map(Filterable::passThrough).toList();
		var content = new WritableBookContent(pages);
		if (!content.equals(stack.get(DataComponents.WRITABLE_BOOK_CONTENT))) {
			stack.set(DataComponents.WRITABLE_BOOK_CONTENT, content);
		}
		Component name = Component.literal(t.get("book.title", plot.name()));
		if (!name.equals(stack.get(DataComponents.CUSTOM_NAME))) {
			stack.set(DataComponents.CUSTOM_NAME, name);
		}
	}
}
