package xtr.plotprotection.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.protection.EntityProtection;

/** These entities override damage handling without Fabric's living-entity damage event. */
@Mixin({BlockAttachedEntity.class, ItemFrame.class, VehicleEntity.class, ArmorStand.class})
public abstract class DecorationHurtMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$hurtServer(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!EntityProtection.allowDecorationDamage((Entity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}
