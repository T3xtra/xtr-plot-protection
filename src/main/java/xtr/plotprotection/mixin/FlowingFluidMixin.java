package xtr.plotprotection.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtr.plotprotection.protection.IndirectProtection;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
	@Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluid, CallbackInfo ci) {
		if (!IndirectProtection.allows(level, pos.relative(direction.getOpposite()), pos)) {
			ci.cancel();
		}
	}
}
