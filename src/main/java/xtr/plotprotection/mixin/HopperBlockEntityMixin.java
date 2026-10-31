package xtr.plotprotection.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.protection.IndirectProtection;

/** Hoppers and hopper minecarts outside a plot must not pull items out of its containers. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
	@Inject(method = "getSourceContainer", at = @At("HEAD"), cancellable = true)
	private static void xtrPlotProtection$getSourceContainer(Level level, Hopper hopper, BlockPos pos, BlockState state, CallbackInfoReturnable<Container> cir) {
		if (!IndirectProtection.allows(level, BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ()), pos)) {
			cir.setReturnValue(null);
		}
	}
}
