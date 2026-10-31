package xtr.plotprotection.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtr.plotprotection.protection.EntityProtection;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow
	public abstract Player getPlayerOwner();

	@Inject(method = "pullEntity", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$pullEntity(Entity entity, CallbackInfo ci) {
		if (!EntityProtection.mayPull(getPlayerOwner(), entity)) {
			ci.cancel();
		}
	}
}
