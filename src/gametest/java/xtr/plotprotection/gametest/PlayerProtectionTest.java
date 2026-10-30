package xtr.plotprotection.gametest;

import static xtr.plotprotection.gametest.MarkerAndBookTest.activePlot;
import static xtr.plotprotection.gametest.MarkerAndBookTest.plotOf;
import static xtr.plotprotection.gametest.MarkerAndBookTest.use;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.ProtectionMode;

@SuppressWarnings("removal")
public class PlayerProtectionTest {
	private static final BlockPos INSIDE = new BlockPos(3, 2, 3);

	private static ServerPlayer protectedPlot(GameTestHelper helper, ProtectionMode mode, boolean doors) {
		var owner = helper.makeMockServerPlayerInLevel();
		var plot = plotOf(activePlot(helper, owner, "Protection " + mode));
		var runtime = PlotProtectionMod.runtime();
		var data = PlotService.setMode(runtime.data(), plot.id(), mode, 1);
		runtime.commit(PlotService.setDoors(data, plot.id(), doors, 1));
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		return owner;
	}

	@GameTest
	public void strangerCannotBreakOrPlaceButOwnerCan(GameTestHelper helper) {
		var owner = protectedPlot(helper, ProtectionMode.PROTECTED, false);
		var stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.STONE);
		helper.assertTrue(!stranger.gameMode.destroyBlock(helper.absolutePos(INSIDE)), "stranger breaks");
		helper.assertBlockPresent(Blocks.STONE, INSIDE);

		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
		use(helper, stranger, INSIDE);
		helper.assertBlockPresent(Blocks.AIR, INSIDE.above());

		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
		use(helper, owner, INSIDE);
		helper.assertBlockPresent(Blocks.DIRT, INSIDE.above());
		helper.assertTrue(owner.gameMode.destroyBlock(helper.absolutePos(INSIDE)), "owner breaks");
		helper.succeed();
	}

	@GameTest
	public void strangerCannotOpenProtectedChestButUsesLevers(GameTestHelper helper) {
		protectedPlot(helper, ProtectionMode.PROTECTED, false);
		var stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.CHEST);
		use(helper, stranger, INSIDE);
		helper.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "chest must stay closed");

		BlockPos lever = INSIDE.east();
		helper.setBlock(lever, Blocks.LEVER);
		use(helper, stranger, lever);
		helper.assertBlockProperty(lever, BlockStateProperties.POWERED, true);
		helper.succeed();
	}

	@GameTest
	public void doorSettingOnlyOpensDoors(GameTestHelper helper) {
		protectedPlot(helper, ProtectionMode.PROTECTED, true);
		var stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.OAK_TRAPDOOR);
		use(helper, stranger, INSIDE);
		helper.assertBlockProperty(INSIDE, BlockStateProperties.OPEN, true);
		helper.succeed();
	}

	@GameTest
	public void doorsStayLockedWithoutSetting(GameTestHelper helper) {
		protectedPlot(helper, ProtectionMode.PROTECTED, false);
		var stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.OAK_TRAPDOOR);
		use(helper, stranger, INSIDE);
		helper.assertBlockProperty(INSIDE, BlockStateProperties.OPEN, false);
		helper.succeed();
	}

	@GameTest
	public void regenerativeBlocksThatHandOutItemsStayClosed(GameTestHelper helper) {
		protectedPlot(helper, ProtectionMode.REGENERATIVE, false);
		var stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.POTTED_POPPY);
		use(helper, stranger, INSIDE);
		helper.assertBlockPresent(Blocks.POTTED_POPPY, INSIDE);
		helper.assertTrue(stranger.getInventory().isEmpty(), "stranger received nothing");
		helper.succeed();
	}

	@GameTest
	public void ironDoorsDoNotLetHeldBlocksThrough(GameTestHelper helper) {
		protectedPlot(helper, ProtectionMode.PROTECTED, true);
		var stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.IRON_TRAPDOOR);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
		use(helper, stranger, INSIDE);
		helper.assertBlockPresent(Blocks.AIR, INSIDE.above());
		helper.succeed();
	}

	@GameTest
	public void regenerativeChestCanBeViewedButNotEmptied(GameTestHelper helper) {
		protectedPlot(helper, ProtectionMode.REGENERATIVE, false);
		var stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.CHEST);
		helper.getBlockEntity(INSIDE, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 3));
		use(helper, stranger, INSIDE);
		var menu = stranger.containerMenu;
		helper.assertTrue(menu != stranger.inventoryMenu, "chest opens for viewing");

		menu.clicked(0, 0, ContainerInput.PICKUP, stranger);
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, stranger);
		menu.clicked(0, 0, ContainerInput.PICKUP_ALL, stranger);
		helper.assertTrue(menu.getCarried().isEmpty(), "nothing carried");
		helper.assertValueEqual(helper.getBlockEntity(INSIDE, ChestBlockEntity.class).getItem(0).getCount(), 3, "diamonds left in chest");

		menu.setCarried(new ItemStack(Items.STICK));
		menu.clicked(1, 0, ContainerInput.PICKUP, stranger);
		helper.assertTrue(helper.getBlockEntity(INSIDE, ChestBlockEntity.class).getItem(1).is(Items.STICK), "putting items in is allowed");
		helper.succeed();
	}
}
