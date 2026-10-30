package xtr.plotprotection.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.protection.IndirectProtection;

/** Zombies and vindicators do not start breaking doors of protected plots. */
@Mixin(BreakDoorGoal.class)
public abstract class BreakDoorGoalMixin extends DoorInteractGoal {
	private BreakDoorGoalMixin(Mob mob) {
		super(mob);
	}

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void xtrPlotProtection$canUse(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !IndirectProtection.proceed(mob.level(), doorPos, IndirectProtection.entityDecision(mob.level(), doorPos, mob))) {
			cir.setReturnValue(false);
		}
	}
}
