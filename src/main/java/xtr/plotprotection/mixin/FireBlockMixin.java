package xtr.plotprotection.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PermissionResolver.Decision;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.protection.IndirectProtection;
import xtr.plotprotection.regeneration.WorldRegeneration;

/** Fire may spread into regenerative plots; blocks it burns there are snapshotted first. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$checkBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		switch (IndirectProtection.fireDecision(level, pos)) {
			case ALLOW -> {
			}
			case REGENERATE -> {
				try {
					WorldRegeneration.captureWithoutDrops(PlotProtectionMod.runtime(), level, pos);
				} catch (PlotException e) {
					ci.cancel();
				}
			}
			case DENY -> ci.cancel();
		}
	}

	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$getIgniteOdds(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (IndirectProtection.fireDecision(level, pos) == Decision.DENY) {
			cir.setReturnValue(0);
		}
	}
}
