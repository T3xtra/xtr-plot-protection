package xtr.plotprotection.gametest;

import static xtr.plotprotection.gametest.MarkerAndBookTest.CORNERS;
import static xtr.plotprotection.gametest.MarkerAndBookTest.activePlot;
import static xtr.plotprotection.gametest.MarkerAndBookTest.plotOf;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.PlotStatus;
import xtr.plotprotection.domain.ProtectionMode;

@SuppressWarnings("removal")
public class IndirectProtectionTest {
	private static void plot(GameTestHelper helper, ProtectionMode mode) {
		var plot = plotOf(activePlot(helper, helper.makeMockServerPlayerInLevel(), "Indirect " + mode));
		var runtime = PlotProtectionMod.runtime();
		runtime.commit(PlotService.setMode(runtime.data(), plot.id(), mode, 1));
	}

	@GameTest
	public void explosionDestroysNeitherProtectedBlocksNorMarkers(GameTestHelper helper) {
		plot(helper, ProtectionMode.PROTECTED);
		BlockPos block = new BlockPos(2, 1, 2);
		helper.setBlock(block, Blocks.STONE);
		Vec3 center = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		helper.getLevel().explode(null, center.x, center.y, center.z, 4, Level.ExplosionInteraction.TNT);
		helper.assertBlockPresent(Blocks.STONE, block);
		helper.assertBlockPresent(Blocks.LODESTONE, CORNERS[0]);
		helper.succeed();
	}

	@GameTest
	public void markersSurviveExplosionsEvenInUnprotectedPlots(GameTestHelper helper) {
		var plot = plotOf(activePlot(helper, helper.makeMockServerPlayerInLevel(), "Unprotected markers"));
		var runtime = PlotProtectionMod.runtime();
		runtime.commit(PlotService.setMode(runtime.data(), plot.id(), ProtectionMode.UNPROTECTED, 1));
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		Vec3 center = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		helper.getLevel().explode(null, center.x, center.y, center.z, 4, Level.ExplosionInteraction.TNT);
		helper.assertBlockPresent(Blocks.LODESTONE, CORNERS[0]);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 2));
		helper.assertValueEqual(runtime.data().plot(plot.id()).orElseThrow().status(), PlotStatus.ACTIVE, "status");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void waterFromOutsideDoesNotFlowIn(GameTestHelper helper) {
		plot(helper, ProtectionMode.PROTECTED);
		for (int x = 0; x <= 2; x++) {
			helper.setBlock(new BlockPos(x, 2, 3), Blocks.STONE);
		}
		helper.setBlock(new BlockPos(0, 3, 3), Blocks.WATER);
		helper.runAfterDelay(60, () -> {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 3, 3));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void pistonFromOutsideCannotPushIntoPlot(GameTestHelper helper) {
		plot(helper, ProtectionMode.PROTECTED);
		BlockPos piston = new BlockPos(0, 3, 3);
		helper.setBlock(new BlockPos(1, 3, 3), Blocks.STONE);
		helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(piston.below(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(20, () -> {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 3, 3));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 3, 3));
			helper.succeed();
		});
	}
}
