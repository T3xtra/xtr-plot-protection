package xtr.plotprotection.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xtr.plotprotection.domain.PermissionResolver.Decision;
import xtr.plotprotection.protection.IndirectProtection;

/** Protected blocks look like air to endermen; regenerating a carried block would duplicate it, so that is denied too. */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {
	@Shadow
	@Final
	private Enderman enderman;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState xtrPlotProtection$getBlockState(Level level, BlockPos pos, Operation<BlockState> original) {
		return IndirectProtection.entityDecision(level, pos, enderman) == Decision.ALLOW ? original.call(level, pos) : Blocks.AIR.defaultBlockState();
	}
}
