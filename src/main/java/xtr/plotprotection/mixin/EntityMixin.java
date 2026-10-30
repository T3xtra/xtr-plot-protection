package xtr.plotprotection.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.protection.EntityProtection;

/** Boats and minecarts pick up mobs on contact; that must not carry protected entities out of a plot. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$startRiding(Entity vehicle, boolean force, boolean sendGameEvent, CallbackInfoReturnable<Boolean> cir) {
		if (!force && vehicle instanceof VehicleEntity && !EntityProtection.mayBoard((Entity) (Object) this, vehicle)) {
			cir.setReturnValue(false);
		}
	}
}
