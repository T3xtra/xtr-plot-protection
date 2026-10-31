package xtr.plotprotection.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.protection.IndirectProtection;

@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanLeaveBlockGoal")
public abstract class EndermanLeaveBlockGoalMixin {
	@Shadow
	@Final
	private Enderman enderman;

	@Inject(method = "canPlaceBlock", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$canPlaceBlock(Level level, BlockPos pos, BlockState carried, BlockState current, BlockState below, BlockPos belowPos, CallbackInfoReturnable<Boolean> cir) {
		if (!IndirectProtection.proceed(level, pos, IndirectProtection.entityDecision(level, pos, enderman))) {
			cir.setReturnValue(false);
		}
	}
}
