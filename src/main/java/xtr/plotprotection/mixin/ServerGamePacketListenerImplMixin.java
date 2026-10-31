package xtr.plotprotection.mixin;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.book.OriginalBooks;
import xtr.plotprotection.i18n.Messages;

/** Fabric API has no book edit event; every Book and Quill edit and signing ends up in these two methods. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "updateBookContents", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$updateBookContents(List<FilteredText> pages, int slot, CallbackInfo ci) {
		var runtime = PlotProtectionMod.runtime();
		if (runtime != null && OriginalBooks.onEdit(runtime, player, slot, pages.stream().map(FilteredText::raw).toList())) {
			ci.cancel();
		}
	}

	@Inject(method = "signBook", at = @At("HEAD"), cancellable = true)
	private void xtrPlotProtection$signBook(FilteredText title, List<FilteredText> pages, int slot, CallbackInfo ci) {
		if (OriginalBooks.bookId(player.getInventory().getItem(slot)).isPresent()) {
			player.sendSystemMessage(Component.literal(Messages.forPlayer(player).get("book.no_sign")).withStyle(ChatFormatting.RED));
			var runtime = PlotProtectionMod.runtime();
			if (runtime != null) {
				OriginalBooks.syncInventory(runtime, player);
			}
			ci.cancel();
		}
	}
}
