package xtr.plotprotection.gametest;

import static xtr.plotprotection.gametest.MarkerAndBookTest.activePlot;
import static xtr.plotprotection.gametest.MarkerAndBookTest.plotOf;
import static xtr.plotprotection.gametest.MarkerAndBookTest.use;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.ProtectionMode;
import xtr.plotprotection.marker.MarkerHandler;
import xtr.plotprotection.regeneration.WorldRegeneration;

@SuppressWarnings("removal")
public class RegenerationTest {
	private static final BlockPos INSIDE = new BlockPos(3, 2, 3);

	private static void regenerativePlot(GameTestHelper helper) {
		var plot = plotOf(activePlot(helper, helper.makeMockServerPlayerInLevel(), "Regenerative"));
		var runtime = PlotProtectionMod.runtime();
		runtime.commit(PlotService.setMode(runtime.data(), plot.id(), ProtectionMode.REGENERATIVE, 1));
	}

	/** Makes this test's entries due and restores them, without touching entries of tests running in parallel. */
	private static void restoreNow(GameTestHelper helper, BlockPos... relative) {
		var runtime = PlotProtectionMod.runtime();
		String dimension = MarkerHandler.dimension(helper.getLevel());
		var positions = java.util.Arrays.stream(relative).map(p -> MarkerHandler.position(helper.absolutePos(p))).toList();
		runtime.commit(runtime.data().withRegeneration(runtime.data().regeneration().stream()
			.map(e -> e.dimension().equals(dimension) && positions.contains(e.position()) ? e.reopened(0) : e).toList()));
		WorldRegeneration.restoreDue(runtime, helper.getLevel().getServer());
	}

	private static boolean pending(GameTestHelper helper, BlockPos relative) {
		return xtr.plotprotection.domain.Regeneration.isPending(PlotProtectionMod.runtime().data(),
			MarkerHandler.dimension(helper.getLevel()), MarkerHandler.position(helper.absolutePos(relative)));
	}

	private static int itemsAround(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(relative)).inflate(3)).size();
	}

	@GameTest
	public void brokenChestDropsNothingAndComesBackOnce(GameTestHelper helper) {
		regenerativePlot(helper);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.CHEST);
		helper.getBlockEntity(INSIDE, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 3));

		stranger.gameMode.destroyBlock(helper.absolutePos(INSIDE));
		helper.assertBlockPresent(Blocks.AIR, INSIDE);
		helper.assertValueEqual(itemsAround(helper, INSIDE), 0, "dropped items");
		helper.assertTrue(pending(helper, INSIDE), "snapshot pending");

		restoreNow(helper, INSIDE);
		helper.assertBlockPresent(Blocks.CHEST, INSIDE);
		helper.assertValueEqual(helper.getBlockEntity(INSIDE, ChestBlockEntity.class).getItem(0).getCount(), 3, "diamonds after restore");

		// a crash before the next world save applies the entry again; that must not duplicate anything
		var runtime = PlotProtectionMod.runtime();
		runtime.commit(xtr.plotprotection.domain.Regeneration.reopenRestored(runtime.data()));
		restoreNow(helper, INSIDE);
		helper.assertValueEqual(helper.getBlockEntity(INSIDE, ChestBlockEntity.class).getItem(0).getCount(), 3, "diamonds after second restore");
		helper.assertValueEqual(helper.getBlockEntity(INSIDE, ChestBlockEntity.class).getItem(1).getCount(), 0, "no extra stack");
		helper.succeed();
	}

	@GameTest
	public void restoreTimeFollowsGameTicks(GameTestHelper helper) {
		regenerativePlot(helper);
		var runtime = PlotProtectionMod.runtime();
		helper.setBlock(INSIDE, Blocks.STONE);
		long before = runtime.gameTime();
		helper.makeMockServerPlayerInLevel().gameMode.destroyBlock(helper.absolutePos(INSIDE));
		var pos = MarkerHandler.position(helper.absolutePos(INSIDE));
		long restoreAt = runtime.data().regeneration().stream().filter(e -> e.position().equals(pos)).findFirst().orElseThrow().restoreAt();
		var config = runtime.config();
		helper.assertTrue(restoreAt >= before + config.regenerationDelaySeconds() * 20
			&& restoreAt <= runtime.gameTime() + (config.regenerationDelaySeconds() + config.regenerationSpreadSeconds()) * 20, "restore time in game ticks");
		helper.succeed();
	}

	@GameTest
	public void placedBlockIsRemovedAgain(GameTestHelper helper) {
		regenerativePlot(helper);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.STONE);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
		use(helper, stranger, INSIDE);
		helper.assertBlockPresent(Blocks.DIRT, INSIDE.above());

		restoreNow(helper, INSIDE, INSIDE.above());
		helper.assertBlockPresent(Blocks.AIR, INSIDE.above());
		helper.assertBlockPresent(Blocks.STONE, INSIDE);
		helper.succeed();
	}

	@GameTest
	public void strangersCannotPourFluids(GameTestHelper helper) {
		regenerativePlot(helper);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.setBlock(INSIDE, Blocks.STONE);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
		use(helper, stranger, INSIDE);
		helper.assertBlockPresent(Blocks.AIR, INSIDE.above());
		helper.succeed();
	}

	@GameTest
	public void explosionDamageIsRegeneratedWithoutDrops(GameTestHelper helper) {
		regenerativePlot(helper);
		BlockPos block = new BlockPos(2, 1, 2);
		helper.setBlock(block, Blocks.OAK_PLANKS);
		Vec3 center = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		helper.getLevel().explode(null, center.x, center.y, center.z, 3, Level.ExplosionInteraction.TNT);
		helper.assertBlockPresent(Blocks.AIR, block);
		helper.assertValueEqual(itemsAround(helper, block), 0, "dropped items");
		helper.assertBlockPresent(Blocks.LODESTONE, MarkerAndBookTest.CORNERS[0]);

		restoreNow(helper, block);
		helper.assertBlockPresent(Blocks.OAK_PLANKS, block);
		helper.succeed();
	}
}
