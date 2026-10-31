package xtr.plotprotection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xtr.plotprotection.domain.Fixtures.OVERWORLD;
import static xtr.plotprotection.domain.Fixtures.activePlot;
import static xtr.plotprotection.domain.PermissionResolver.Action.BREAK;
import static xtr.plotprotection.domain.PermissionResolver.Action.ENTITY;
import static xtr.plotprotection.domain.PermissionResolver.Action.MODIFY;
import static xtr.plotprotection.domain.PermissionResolver.Action.OPEN_CONTAINER;
import static xtr.plotprotection.domain.PermissionResolver.Action.TAKE_ITEM;
import static xtr.plotprotection.domain.PermissionResolver.Action.USE_DOOR;
import static xtr.plotprotection.domain.PermissionResolver.Cause.CREEPER;
import static xtr.plotprotection.domain.PermissionResolver.Cause.ENVIRONMENT;
import static xtr.plotprotection.domain.PermissionResolver.Cause.MECHANIC;
import static xtr.plotprotection.domain.PermissionResolver.Decision.ALLOW;
import static xtr.plotprotection.domain.PermissionResolver.Decision.DENY;
import static xtr.plotprotection.domain.PermissionResolver.Decision.REGENERATE;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionResolverTest {
	private final UUID owner = UUID.randomUUID();
	private final UUID member = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();
	private final BlockPosition inside = new BlockPosition(5, 64, 5);
	private final BlockPosition outside = new BlockPosition(50, 64, 50);

	private WorldData plot(ProtectionMode mode, boolean doors) {
		var created = activePlot(WorldData.EMPTY, owner, 0, 0, 10, 10, 64);
		var data = created.data().with(created.group().withMembers(Set.of(member)));
		data = PlotService.setMode(data, created.plot().id(), mode, 2);
		return PlotService.setDoors(data, created.plot().id(), doors, 2);
	}

	private PermissionResolver.Decision direct(WorldData data, UUID actor, PermissionResolver.Action action) {
		return PermissionResolver.decide(data, OVERWORLD, inside, actor, action);
	}

	@Test
	void membersAndUnprotectedPlotsAllowEverything() {
		var data = plot(ProtectionMode.PROTECTED, false);
		for (var action : PermissionResolver.Action.values()) {
			assertEquals(ALLOW, direct(data, owner, action), action.name());
			assertEquals(ALLOW, direct(data, member, action), action.name());
			assertEquals(ALLOW, direct(plot(ProtectionMode.UNPROTECTED, false), stranger, action), action.name());
		}
		assertEquals(ALLOW, PermissionResolver.decide(data, OVERWORLD, outside, stranger, BREAK));
		assertEquals(ALLOW, PermissionResolver.decide(data, Fixtures.NETHER, inside, stranger, BREAK));
	}

	@Test
	void protectedDeniesStrangersExceptDoorsWhenAllowed() {
		var data = plot(ProtectionMode.PROTECTED, false);
		for (var action : PermissionResolver.Action.values()) {
			assertEquals(DENY, direct(data, stranger, action), action.name());
		}
		assertEquals(DENY, direct(data, null, BREAK), "unknown actor");
		assertEquals(ALLOW, direct(plot(ProtectionMode.PROTECTED, true), stranger, USE_DOOR));
		assertEquals(DENY, direct(plot(ProtectionMode.PROTECTED, true), stranger, OPEN_CONTAINER), "door setting allows nothing else");
	}

	@Test
	void regenerativeLetsStrangersLookButNotTake() {
		var data = plot(ProtectionMode.REGENERATIVE, false);
		assertEquals(REGENERATE, direct(data, stranger, BREAK));
		assertEquals(REGENERATE, direct(data, stranger, MODIFY));
		assertEquals(ALLOW, direct(data, stranger, OPEN_CONTAINER));
		assertEquals(DENY, direct(data, stranger, TAKE_ITEM));
		assertEquals(DENY, direct(data, stranger, USE_DOOR));
		assertEquals(DENY, direct(data, stranger, ENTITY));
		assertEquals(ALLOW, direct(plot(ProtectionMode.REGENERATIVE, true), stranger, USE_DOOR));
	}

	@Test
	void strictestOverlappingPlotWinsAndPermissiveDoorsDoNotLiftIt() {
		var loose = activePlot(WorldData.EMPTY, owner, 0, 0, 10, 10, 64);
		var strict = activePlot(loose.data(), owner, 5, 5, 15, 15, 64);
		var data = PlotService.setMode(strict.data(), loose.plot().id(), ProtectionMode.REGENERATIVE, 2);
		data = PlotService.setDoors(data, loose.plot().id(), true, 2);
		var overlap = new BlockPosition(7, 64, 7);
		assertEquals(DENY, PermissionResolver.decide(data, OVERWORLD, overlap, stranger, BREAK));
		assertEquals(DENY, PermissionResolver.decide(data, OVERWORLD, overlap, stranger, USE_DOOR));
		assertEquals(REGENERATE, PermissionResolver.decide(data, OVERWORLD, new BlockPosition(2, 64, 2), stranger, BREAK));
	}

	@Test
	void repairPendingStillProtectsAndDisabledDoesNot() {
		var data = plot(ProtectionMode.PROTECTED, false);
		var removed = PlotService.removeMarker(data, OVERWORLD, new BlockPosition(0, 64, 0), 5, 100).orElseThrow();
		assertEquals(DENY, PermissionResolver.decide(removed.data(), OVERWORLD, inside, stranger, BREAK));
		var expired = PlotService.expireRepairs(removed.data(), 1000);
		assertEquals(ALLOW, PermissionResolver.decide(expired, OVERWORLD, inside, stranger, BREAK));
	}

	@Test
	void mechanicsOnlyActFromInsideTheSamePlot() {
		var data = plot(ProtectionMode.PROTECTED, false);
		assertEquals(ALLOW, PermissionResolver.decideIndirect(data, OVERWORLD, new BlockPosition(1, 64, 1), inside, null, MECHANIC));
		assertEquals(DENY, PermissionResolver.decideIndirect(data, OVERWORLD, outside, inside, null, MECHANIC));
		assertEquals(DENY, PermissionResolver.decideIndirect(data, OVERWORLD, null, inside, null, MECHANIC));
		assertEquals(DENY, PermissionResolver.decideIndirect(plot(ProtectionMode.REGENERATIVE, false), OVERWORLD, outside, inside, null, MECHANIC));
		assertEquals(ALLOW, PermissionResolver.decideIndirect(data, OVERWORLD, inside, outside, null, MECHANIC));
	}

	@Test
	void explosionsAndFireFollowTheInitiator() {
		var regenerative = plot(ProtectionMode.REGENERATIVE, false);
		assertEquals(ALLOW, PermissionResolver.decideIndirect(regenerative, OVERWORLD, null, inside, member, ENVIRONMENT));
		assertEquals(REGENERATE, PermissionResolver.decideIndirect(regenerative, OVERWORLD, null, inside, stranger, ENVIRONMENT));
		assertEquals(REGENERATE, PermissionResolver.decideIndirect(regenerative, OVERWORLD, null, inside, null, ENVIRONMENT));
		assertEquals(REGENERATE, PermissionResolver.decideIndirect(regenerative, OVERWORLD, null, inside, member, CREEPER));
		var protectedPlot = plot(ProtectionMode.PROTECTED, false);
		assertEquals(DENY, PermissionResolver.decideIndirect(protectedPlot, OVERWORLD, null, inside, null, ENVIRONMENT));
		assertEquals(ALLOW, PermissionResolver.decideIndirect(protectedPlot, OVERWORLD, null, inside, owner, ENVIRONMENT));
	}

	@Test
	void markersAreNeverChangedIndirectly() {
		var marker = new BlockPosition(0, 64, 0);
		for (var mode : ProtectionMode.values()) {
			var data = plot(mode, false);
			assertEquals(DENY, PermissionResolver.decideIndirect(data, OVERWORLD, marker, marker, owner, ENVIRONMENT), mode.name());
			assertEquals(DENY, PermissionResolver.decideIndirect(data, OVERWORLD, inside, marker, null, MECHANIC), mode.name());
		}
	}
}
