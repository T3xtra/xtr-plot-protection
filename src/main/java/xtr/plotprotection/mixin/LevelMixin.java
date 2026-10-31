package xtr.plotprotection.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.protection.IndirectProtection;
import xtr.plotprotection.regeneration.WorldRegeneration;

/** Withers, ravagers, boats and other entities break blocks through this method. */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$destroyBlock(BlockPos pos, boolean drop, Entity entity, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
		if (entity == null) {
			return;
		}
		var level = (Level) (Object) this;
		switch (IndirectProtection.entityDecision(level, pos, entity)) {
			case ALLOW -> {
			}
			case REGENERATE -> {
				try {
					WorldRegeneration.breakWithoutDrops(PlotProtectionMod.runtime(), level, List.of(pos));
					cir.setReturnValue(true);
				} catch (PlotException e) {
					cir.setReturnValue(false);
				}
			}
			case DENY -> cir.setReturnValue(false);
		}
	}
}
