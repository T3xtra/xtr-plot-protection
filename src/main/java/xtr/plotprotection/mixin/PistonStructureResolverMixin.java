package xtr.plotprotection.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtr.plotprotection.protection.IndirectProtection;

/** A piston may only move, destroy or extend into blocks of plots it stands in; markers never move. */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
	@Shadow
	@Final
	private Level level;
	@Shadow
	@Final
	private BlockPos pistonPos;
	@Shadow
	@Final
	private Direction pushDirection;
	@Shadow
	@Final
	private List<BlockPos> toPush;
	@Shadow
	@Final
	private List<BlockPos> toDestroy;

	@Unique
	private BlockPos xtrPlotProtection$head;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void xtrPlotProtection$init(Level level, BlockPos pos, Direction direction, boolean extending, CallbackInfo ci) {
		xtrPlotProtection$head = extending ? pos.relative(direction) : null;
	}

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void xtrPlotProtection$resolve(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		boolean allowed = (xtrPlotProtection$head == null || IndirectProtection.allows(level, pistonPos, xtrPlotProtection$head))
			&& toPush.stream().allMatch(p -> IndirectProtection.allows(level, pistonPos, p) && IndirectProtection.allows(level, pistonPos, p.relative(pushDirection)))
			&& toDestroy.stream().allMatch(p -> IndirectProtection.allows(level, pistonPos, p));
		if (!allowed) {
			cir.setReturnValue(false);
		}
	}
}
