package xtr.plotprotection.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtr.plotprotection.protection.IndirectProtection;

/** Trampling; without an entity the farmland dries out or is covered, which is no griefing. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
	@Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$turnToBaseBlock(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (entity != null && !IndirectProtection.proceed(level, pos, IndirectProtection.entityDecision(level, pos, entity))) {
			ci.cancel();
		}
	}
}
