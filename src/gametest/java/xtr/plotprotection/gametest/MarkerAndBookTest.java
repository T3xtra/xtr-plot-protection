package xtr.plotprotection.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.book.OriginalBooks;
import xtr.plotprotection.domain.Plot;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.PlotStatus;
import xtr.plotprotection.domain.ProtectionMode;
import xtr.plotprotection.visual.BoundsView;

@SuppressWarnings("removal")
public class MarkerAndBookTest {
	static final BlockPos[] CORNERS = {new BlockPos(1, 1, 1), new BlockPos(6, 1, 1), new BlockPos(1, 1, 6), new BlockPos(6, 1, 6)};

	/** Creates a plot by using a fresh Book and Quill named {@code name} on four lodestones; returns the original book. */
	static ItemStack activePlot(GameTestHelper helper, ServerPlayer owner, String name) {
		var book = new ItemStack(Items.WRITABLE_BOOK);
		book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough(name))));
		owner.setItemInHand(InteractionHand.MAIN_HAND, book);
		for (BlockPos corner : CORNERS) {
			helper.setBlock(corner, Blocks.LODESTONE);
			use(helper, owner, corner);
		}
		return book;
	}

	static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		BlockPos pos = helper.absolutePos(relative);
		var stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	static Plot plotOf(ItemStack book) {
		var data = PlotProtectionMod.runtime().data();
		return data.plotOf(OriginalBooks.group(data, book).orElseThrow(() -> new AssertionError("not an original book")));
	}

	@GameTest
	public void bookOnLodestonesCreatesActivePlot(GameTestHelper helper) {
		var owner = helper.makeMockServerPlayerInLevel();
		var book = activePlot(helper, owner, "GT Plot");
		Plot plot = plotOf(book);
		helper.assertValueEqual(plot.name(), "GT Plot", "plot name from first line");
		helper.assertValueEqual(plot.status(), PlotStatus.ACTIVE, "status");
		helper.assertValueEqual(PlotProtectionMod.runtime().data().groupOf(plot).owner(), owner.getUUID(), "owner");
		helper.succeed();
	}

	@GameTest
	public void forgedOrReplacedBooksCannotRegisterMarkers(GameTestHelper helper) {
		var owner = helper.makeMockServerPlayerInLevel();
		var book = new ItemStack(Items.WRITABLE_BOOK);
		owner.setItemInHand(InteractionHand.MAIN_HAND, book);
		helper.setBlock(CORNERS[0], Blocks.LODESTONE);
		use(helper, owner, CORNERS[0]);
		Plot plot = plotOf(book);

		var forged = book.copy();
		var tag = new CompoundTag();
		tag.putString("xtr_plot_protection:book_id", UUID.randomUUID().toString());
		forged.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		var stranger = helper.makeMockServerPlayerInLevel();
		stranger.setItemInHand(InteractionHand.MAIN_HAND, forged);
		helper.setBlock(CORNERS[1], Blocks.LODESTONE);
		use(helper, stranger, CORNERS[1]);
		helper.assertValueEqual(markers(plot), 1, "markers after forged book use");

		var group = PlotProtectionMod.runtime().data().groupOf(plot);
		PlotProtectionMod.runtime().commit(PlotService.issueBook(PlotProtectionMod.runtime().data(), group.id()).data());
		use(helper, owner, CORNERS[1]);
		helper.assertValueEqual(markers(plot), 1, "markers after replaced original use");
		helper.succeed();
	}

	private static int markers(Plot plot) {
		return PlotProtectionMod.runtime().data().plot(plot.id()).orElseThrow().validMarkers().size();
	}

	@GameTest
	public void bookEditAppliesValidSettingsAndResetsInvalidOnes(GameTestHelper helper) {
		var owner = helper.makeMockServerPlayerInLevel();
		var book = activePlot(helper, owner, "Edit Plot");
		int maxY = plotOf(book).bounds().maxY();
		var pages = new ArrayList<>(book.get(DataComponents.WRITABLE_BOOK_CONTENT).getPages(false).toList());
		pages.set(0, pages.get(0).replace("Mode: Protected", "Mode: Unprotected").replace("Max Y: " + maxY, "Max Y: lots"));
		owner.connection.handleEditBook(new ServerboundEditBookPacket(owner.getInventory().getSelectedSlot(), pages, Optional.empty()));
		helper.succeedWhen(() -> {
			Plot plot = plotOf(book);
			helper.assertValueEqual(plot.mode(), ProtectionMode.UNPROTECTED, "mode");
			helper.assertValueEqual(plot.bounds().maxY(), maxY, "max y");
			String page = book.get(DataComponents.WRITABLE_BOOK_CONTENT).getPages(false).findFirst().orElseThrow();
			helper.assertTrue(page.contains("Max Y: " + maxY), "invalid value re-rendered: " + page);
		});
	}

	@GameTest
	public void outlinesAreDrawnForBookHolders(GameTestHelper helper) {
		var owner = helper.makeMockServerPlayerInLevel();
		activePlot(helper, owner, "Outline Plot");
		var corner = helper.absolutePos(CORNERS[0]);
		owner.snapTo(corner.getX(), corner.getY(), corner.getZ());
		BoundsView.tick(PlotProtectionMod.runtime());
		helper.succeed();
	}

	@GameTest
	public void markerCanOnlyBeBrokenWithOriginalBook(GameTestHelper helper) {
		var owner = helper.makeMockServerPlayerInLevel();
		var book = activePlot(helper, owner, "Break Plot");
		var stranger = helper.makeMockServerPlayerInLevel();
		BlockPos corner = helper.absolutePos(CORNERS[0]);

		helper.assertTrue(!stranger.gameMode.destroyBlock(corner), "stranger must not break the marker");
		helper.assertBlockPresent(Blocks.LODESTONE, CORNERS[0]);
		helper.assertValueEqual(plotOf(book).status(), PlotStatus.ACTIVE, "status after denied break");

		helper.assertTrue(owner.gameMode.destroyBlock(corner), "holder of the original may break the marker");
		helper.assertValueEqual(plotOf(book).status(), PlotStatus.REPAIR_PENDING, "status after removal");
		helper.succeed();
	}
}
