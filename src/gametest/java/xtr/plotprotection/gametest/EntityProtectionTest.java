package xtr.plotprotection.gametest;

import static xtr.plotprotection.gametest.MarkerAndBookTest.activePlot;
import static xtr.plotprotection.gametest.MarkerAndBookTest.plotOf;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.ProtectionMode;

@SuppressWarnings("removal")
public class EntityProtectionTest {
	private static final BlockPos INSIDE = new BlockPos(3, 2, 3);

	private static ServerPlayer protectedPlot(GameTestHelper helper) {
		var owner = helper.makeMockServerPlayerInLevel();
		var plot = plotOf(activePlot(helper, owner, "Entities"));
		var runtime = PlotProtectionMod.runtime();
		runtime.commit(PlotService.setMode(runtime.data(), plot.id(), ProtectionMode.PROTECTED, 1));
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				if (helper.getBlockState(new BlockPos(x, 1, z)).isAir()) {
					helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				}
			}
		}
		return survival(owner);
	}

	/** Mock players start as "client not loaded" and would have every interaction packet ignored. */
	private static ServerPlayer survival(ServerPlayer player) {
		player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		return player;
	}

	private static void interact(ServerPlayer player, Entity target, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.snapTo(target.getX(), target.getY(), target.getZ());
		player.connection.handleInteract(new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
	}

	@GameTest
	public void strangersCannotHurtAnimalsButPvpStaysVanilla(GameTestHelper helper) {
		var owner = protectedPlot(helper);
		var stranger = survival(helper.makeMockServerPlayerInLevel());
		var cow = helper.spawn(EntityTypes.COW, INSIDE);
		var level = helper.getLevel();
		float full = cow.getHealth();

		cow.hurtServer(level, level.damageSources().playerAttack(stranger), 4);
		helper.assertValueEqual(cow.getHealth(), full, "cow health after stranger hit");
		cow.hurtServer(level, level.damageSources().playerAttack(owner), 4);
		helper.assertTrue(cow.getHealth() < full, "owner may hurt own animals");

		level.getGameRules().set(GameRules.PVP, true, level.getServer());
		float ownerHealth = owner.getHealth();
		owner.setInvulnerableTime(0);
		owner.hurtServer(level, level.damageSources().playerAttack(stranger), 4);
		helper.assertTrue(owner.getHealth() < ownerHealth, "PvP stays vanilla inside protected plots");
		helper.succeed();
	}

	@GameTest
	public void strangersMayFeedButNotLeashAnimals(GameTestHelper helper) {
		protectedPlot(helper);
		var stranger = survival(helper.makeMockServerPlayerInLevel());
		var cow = helper.spawn(EntityTypes.COW, INSIDE);

		interact(stranger, cow, new ItemStack(Items.LEAD));
		helper.assertTrue(!cow.isLeashed(), "cow must not be leashed");
		interact(stranger, cow, new ItemStack(Items.WHEAT));
		helper.assertTrue(cow.isInLove(), "feeding is allowed");
		helper.succeed();
	}

	@GameTest
	public void boatsOnlyCarryAnimalsWithAnAuthorizedRider(GameTestHelper helper) {
		var owner = protectedPlot(helper);
		var cow = helper.spawn(EntityTypes.COW, INSIDE);
		var boat = helper.spawn(EntityTypes.OAK_BOAT, INSIDE.east());
		helper.assertTrue(!cow.startRiding(boat), "cow boards an empty boat");

		var stranger = helper.makeMockServerPlayerInLevel();
		stranger.startRiding(boat, true, false);
		helper.assertTrue(!cow.startRiding(boat), "cow boards a stranger's boat");
		stranger.stopRiding();

		owner.startRiding(boat, true, false);
		helper.assertTrue(cow.startRiding(boat), "cow boards the owner's boat");
		helper.succeed();
	}
}
