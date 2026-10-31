package xtr.plotprotection.protection;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xtr.plotprotection.PlotProtectionMod;
import xtr.plotprotection.domain.PermissionResolver;
import xtr.plotprotection.domain.PermissionResolver.Action;
import xtr.plotprotection.domain.PermissionResolver.Cause;
import xtr.plotprotection.domain.PermissionResolver.Decision;
import xtr.plotprotection.i18n.Messages;
import xtr.plotprotection.marker.MarkerHandler;

/**
 * Non-player entities inside a plot (SPEC §7). Players are never targets here, so PvP stays vanilla.
 * Entities cannot be regenerated, so every decision other than ALLOW refuses.
 */
public final class EntityProtection {
	private EntityProtection() {
	}

	/** Living entities: only damage attributable to a player (projectiles, TNT, tamed animals included) is restricted. */
	public static boolean allowDamage(Entity target, DamageSource source) {
		if (target instanceof Player) {
			return true;
		}
		UUID actor = responsiblePlayer(source.getEntity());
		return actor == null || decide(target, actor) == Decision.ALLOW;
	}

	/** Item frames, paintings, armor stands, boats and minecarts are treated like blocks: explosions and mobs count too. */
	public static boolean allowDecorationDamage(Entity target, DamageSource source) {
		UUID actor = responsiblePlayer(source.getEntity());
		if (actor != null) {
			return decide(target, actor) == Decision.ALLOW;
		}
		return IndirectProtection.decide(target.level(), null, target.blockPosition(), Cause.ENVIRONMENT, null) == Decision.ALLOW;
	}

	public static InteractionResult onAttack(ServerPlayer player, Entity target) {
		if (target instanceof Player || decide(target, player.getUUID()) == Decision.ALLOW) {
			return InteractionResult.PASS;
		}
		deny(player);
		return InteractionResult.FAIL;
	}

	/** Feeding, breeding, milking, shearing and trading stay allowed (SPEC §7); leashing, riding and taming do not. */
	public static InteractionResult onUse(ServerPlayer player, InteractionHand hand, Entity target) {
		if (target instanceof Player || decide(target, player.getUUID()) == Decision.ALLOW || isHarmless(target, player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		deny(player);
		return InteractionResult.FAIL;
	}

	/** Boats and minecarts only pick up protected entities while an authorized player rides along. */
	public static boolean mayBoard(Entity passenger, Entity vehicle) {
		if (passenger instanceof Player) {
			return true;
		}
		UUID rider = vehicle.getPassengers().stream().filter(p -> p instanceof ServerPlayer).map(Entity::getUUID).findFirst().orElse(null);
		return decide(passenger, rider) == Decision.ALLOW;
	}

	public static boolean mayPull(Player angler, Entity target) {
		return target instanceof Player || decide(target, angler == null ? null : angler.getUUID()) == Decision.ALLOW;
	}

	private static boolean isHarmless(Entity target, ItemStack stack) {
		if (target instanceof AbstractVillager) {
			return true;
		}
		if (target instanceof Animal animal && animal.isFood(stack)) {
			// taming an untamed animal would let a stranger lead it away
			return !(target instanceof TamableAnimal tamable) || tamable.isTame();
		}
		return stack.is(Items.SHEARS) && target instanceof Shearable
			|| stack.is(Items.BUCKET) && (target instanceof AbstractCow || target instanceof Goat)
			|| stack.is(Items.BOWL) && target instanceof AbstractCow;
	}

	private static UUID responsiblePlayer(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			return player.getUUID();
		}
		if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer owner) {
			return owner.getUUID();
		}
		return null;
	}

	private static Decision decide(Entity target, UUID actor) {
		var runtime = PlotProtectionMod.runtime();
		if (runtime == null) {
			return Decision.ALLOW;
		}
		return PermissionResolver.decide(runtime.data(), MarkerHandler.dimension(target.level()), MarkerHandler.position(target.blockPosition()), actor, Action.ENTITY);
	}

	private static void deny(ServerPlayer player) {
		player.sendSystemMessage(Component.literal(Messages.forPlayer(player).get("protection.denied")).withStyle(ChatFormatting.RED), true);
	}
}
