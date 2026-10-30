package xtr.plotprotection.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.domain.PermissionResolver.Decision;
import xtr.plotprotection.protection.IndirectProtection;

@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin {
	@Inject(method = "isFlammable", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$isFlammable(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (IndirectProtection.fireDecision(level, pos) == Decision.DENY) {
			cir.setReturnValue(false);
		}
	}
}
