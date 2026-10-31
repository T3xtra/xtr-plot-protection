package xtr.plotprotection.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Mixins apply when their target loads; loading every target here makes a broken injection fail the build. */
public class MixinTargetsTest {
	private static final List<String> TARGETS = List.of(
		"net.minecraft.server.players.CachedUserNameToIdResolver",
		"net.minecraft.server.network.ServerGamePacketListenerImpl",
		"net.minecraft.world.inventory.AbstractContainerMenu",
		"net.minecraft.world.CompoundContainer",
		"net.minecraft.world.level.ServerExplosion",
		"net.minecraft.world.level.block.FireBlock",
		"net.minecraft.world.level.material.LavaFluid",
		"net.minecraft.world.level.material.FlowingFluid",
		"net.minecraft.world.level.block.piston.PistonStructureResolver",
		"net.minecraft.world.level.block.DispenserBlock",
		"net.minecraft.world.level.block.entity.HopperBlockEntity",
		"net.minecraft.world.level.Level",
		"net.minecraft.world.level.block.FarmlandBlock",
		"net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal",
		"net.minecraft.world.entity.monster.Enderman$EndermanLeaveBlockGoal",
		"net.minecraft.world.entity.ai.goal.BreakDoorGoal",
		"net.minecraft.world.entity.decoration.BlockAttachedEntity",
		"net.minecraft.world.entity.decoration.ItemFrame",
		"net.minecraft.world.entity.vehicle.VehicleEntity",
		"net.minecraft.world.entity.decoration.ArmorStand",
		"net.minecraft.world.entity.Entity",
		"net.minecraft.world.entity.projectile.FishingHook"
	);

	@GameTest
	public void everyMixinTargetLoads(GameTestHelper helper) {
		for (String target : TARGETS) {
			try {
				Class.forName(target, true, MixinTargetsTest.class.getClassLoader());
			} catch (ClassNotFoundException | LinkageError e) {
				throw new AssertionError("Mixin target failed to load: " + target, e);
			}
		}
		helper.succeed();
	}
}
