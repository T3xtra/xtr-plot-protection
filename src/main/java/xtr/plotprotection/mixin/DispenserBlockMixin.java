package xtr.plotprotection.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtr.plotprotection.protection.IndirectProtection;

/** Dispensers outside a plot must not place fluids, blocks or fire into it; inside they work normally. */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
	@Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$dispenseFrom(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
		if (!IndirectProtection.allows(level, pos, pos.relative(state.getValue(DispenserBlock.FACING)))) {
			ci.cancel();
		}
	}
}
