package xtr.plotprotection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import xtr.plotprotection.i18n.Messages;

class BookPagesTest {
	private static final List<Translator> LANGUAGES = Messages.LOCALES.stream().map(Messages::forLocale).toList();
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID carol = UUID.randomUUID();
	private final Map<String, UUID> known = Map.of("Alice", alice, "Bob", bob, "Carol", carol);
	private final Function<String, Optional<UUID>> resolve = name -> known.entrySet().stream()
		.filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst();
	private final Function<UUID, String> names = uuid -> known.entrySet().stream()
		.filter(e -> e.getValue().equals(uuid)).map(Map.Entry::getKey).findFirst().orElse(uuid.toString());

	private Group group(Set<UUID> members) {
		return new Group(UUID.randomUUID(), "g", UUID.randomUUID(), alice, members, UUID.randomUUID());
	}

	@Test
	void systemPageIsRenderedFromCanonicalState() {
		var created = Fixtures.activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var pages = BookPages.render(created.plot(), created.group(), names, Messages.forLocale("en_us"));
		assertTrue(pages.get(0).contains("Owner: Alice"));
		assertTrue(pages.get(0).contains("Min Y: 44\nMax Y: 104"));
		assertTrue(pages.get(0).contains("NW §a0 64 0"));
		assertEquals("", pages.get(1), "empty member page to write into");
	}

	@Test
	void unchangedSystemPageChangesNothingInAnyLanguage() {
		var created = Fixtures.activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		for (String locale : Messages.LOCALES) {
			var page = BookPages.render(created.plot(), created.group(), names, Messages.forLocale(locale)).get(0);
			var edit = BookPages.applySystemPage(created.data(), created.plot().id(), "", page, LANGUAGES, 5);
			assertEquals(created.data(), edit.data(), locale);
			assertEquals(List.of(), edit.rejected(), locale);
		}
	}

	@Test
	void validSettingLinesAreAppliedInAnyLanguage() {
		var created = Fixtures.activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var page = "Grundstück: Garten\nGroupe : Amis\nmode: regenerative\nTüren: frei\nMin Y: 10\nMax Y: 200\nOwner: Mallory\nStatus: Disabled";
		var edit = BookPages.applySystemPage(created.data(), created.plot().id(), "", page, LANGUAGES, 5);
		var plot = edit.data().plot(created.plot().id()).orElseThrow();
		assertEquals(List.of(), edit.rejected());
		assertEquals("Garten", plot.name());
		assertEquals("Amis", edit.data().groupOf(plot).name());
		assertEquals(ProtectionMode.REGENERATIVE, plot.mode());
		assertTrue(plot.allowDirectDoorInteraction());
		assertEquals(new Bounds(0, 10, 0, 10, 200, 10), plot.bounds());
		assertEquals(alice, edit.data().groupOf(plot).owner(), "display lines are never input");
		assertEquals(PlotStatus.ACTIVE, plot.status());
	}

	@Test
	void invalidSettingLinesAreRejectedAndKeepTheirValue() {
		var created = Fixtures.activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var page = "Plot: \nMode: invincible\nDoors: maybe\nMin Y: 300\nMax Y: 100\nGroup: Valid";
		var edit = BookPages.applySystemPage(created.data(), created.plot().id(), "", page, LANGUAGES, 5);
		var plot = edit.data().plot(created.plot().id()).orElseThrow();
		assertEquals(4, edit.rejected().size());
		assertEquals(created.plot().name(), plot.name());
		assertEquals(ProtectionMode.PROTECTED, plot.mode());
		assertEquals(created.plot().bounds(), plot.bounds());
		assertEquals("Valid", edit.data().groupOf(plot).name(), "valid lines still apply");
	}

	@Test
	void linesUnchangedSinceTheLastSyncDoNotRevertNewerChanges() {
		var created = Fixtures.activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var stale = BookPages.render(created.plot(), created.group(), names, Messages.forLocale("en_us")).get(0);
		var newer = PlotService.setMode(created.data(), created.plot().id(), ProtectionMode.UNPROTECTED, 3);
		var edit = BookPages.applySystemPage(newer, created.plot().id(), stale, stale.replace("Doors: locked", "Doors: open"), LANGUAGES, 5);
		var plot = edit.data().plot(created.plot().id()).orElseThrow();
		assertEquals(ProtectionMode.UNPROTECTED, plot.mode());
		assertTrue(plot.allowDirectDoorInteraction());
	}

	@Test
	void heightsCannotBeSetDuringSetup() {
		var created = PlotService.create(WorldData.EMPTY, alice, "p", "g", 0);
		var edit = BookPages.applySystemPage(created.data(), created.plot().id(), "", "Min Y: 0", LANGUAGES, 5);
		assertEquals("plot.no_bounds", edit.rejected().get(0).getMessage());
		var unchanged = BookPages.render(created.plot(), created.group(), names, Messages.forLocale("en_us")).get(0);
		assertEquals(List.of(), BookPages.applySystemPage(created.data(), created.plot().id(), "", unchanged, LANGUAGES, 5).rejected());
	}

	@Test
	void validEditIsAppliedAndUnknownNamesRejected() {
		var edit = BookPages.applyMemberPages(group(Set.of()), List.of("Bob\n  carol \nNobody\nnot a name!", ""), names, resolve);
		assertEquals(Set.of(bob, carol), edit.group().members());
		assertEquals(List.of("Nobody", "not a name!"), edit.rejected());
	}

	@Test
	void removingAllNamesRemovesMembersButNeverTheOwner() {
		var edit = BookPages.applyMemberPages(group(Set.of(bob)), List.of("Alice"), names, resolve);
		assertEquals(Set.of(), edit.group().members());
		assertEquals(alice, edit.group().owner());
	}

	@Test
	void memberWithStaleNameIsKeptByUuid() {
		var renamed = UUID.randomUUID();
		Function<UUID, String> stale = uuid -> uuid.equals(renamed) ? "OldName" : names.apply(uuid);
		var edit = BookPages.applyMemberPages(group(Set.of(renamed)), List.of("OldName"), stale, resolve);
		assertEquals(Set.of(renamed), edit.group().members());
	}

	@Test
	void membersRenderedOnePerLineAcrossPages() {
		var many = new java.util.HashSet<UUID>();
		for (int i = 0; i < 20; i++) {
			many.add(UUID.randomUUID());
		}
		var created = Fixtures.activePlot(WorldData.EMPTY, alice, 0, 0, 10, 10, 64);
		var pages = BookPages.render(created.plot(), group(many), names, Messages.forLocale("en_us"));
		assertEquals(3, pages.size());
		assertEquals(14, pages.get(1).split("\n").length);
	}
}
