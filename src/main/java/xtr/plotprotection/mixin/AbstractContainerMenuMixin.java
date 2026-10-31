package xtr.plotprotection.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.protection.PlayerProtection;

/** Every slot click of every menu passes through here; Fabric API has no inventory click event. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$clicked(int slotIndex, int button, ContainerInput input, Player player, CallbackInfo ci) {
		var runtime = PlotProtectionMod.runtime();
		var menu = (AbstractContainerMenu) (Object) this;
		if (runtime != null && player instanceof ServerPlayer serverPlayer && !PlayerProtection.mayClick(runtime, menu, slotIndex, input, serverPlayer)) {
			ci.cancel();
			menu.sendAllDataToRemote();
		}
	}
}
