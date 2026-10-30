package xtr.plotprotection.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xtr.plotprotection.domain.BlockPosition;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.PlotService;
import xtr.plotprotection.domain.Regeneration;
import xtr.plotprotection.domain.RegenerationEntry;
import xtr.plotprotection.domain.WorldData;

class WorldDataStoreTest {
	@TempDir
	Path dir;

	private WorldData sample() {
		var alice = UUID.randomUUID();
		var created = PlotService.create(WorldData.EMPTY, alice, "Farm", "Farmers", 0);
		var data = created.data().with(created.group().withMembers(Set.of(UUID.randomUUID())));
		var book = created.group().bookId();
		for (var p : new BlockPosition[] {new BlockPosition(0, 64, 0), new BlockPosition(10, 64, 0), new BlockPosition(0, 64, 10), new BlockPosition(10, 64, 10)}) {
			data = PlotService.registerMarker(data, book, "minecraft:overworld", p, 1, 20, 40);
		}
		data = PlotService.removeMarker(data, "minecraft:overworld", new BlockPosition(0, 64, 0), 5, 100).orElseThrow().data();
		data = PlotService.create(data, alice, "Empty", "Setup", 0).data();
		return Regeneration.capture(data, List.of(
			new RegenerationEntry("minecraft:overworld", new BlockPosition(3, 64, 3), "{Name:\"minecraft:chest\"}", "{id:\"minecraft:chest\",Items:[]}", 10, false),
			new RegenerationEntry("minecraft:overworld", new BlockPosition(4, 64, 3), "{Name:\"minecraft:stone\"}", null, 10, true)));
	}

	private long backups() throws IOException {
		try (Stream<Path> files = Files.list(dir.resolve("backups"))) {
			return files.count();
		}
	}

	@Test
	void roundTripsAllTypes() throws IOException {
		var data = sample();
		var store = WorldDataStore.open(dir, 5);
		store.commit(data);
		assertEquals(data, WorldDataStore.open(dir, 5).current());
		assertTrue(Files.readString(dir.resolve("plots.json")).contains("\"schemaVersion\": " + WorldDataStore.SCHEMA_VERSION));
	}

	@Test
	void startsEmptyOnlyWithoutAnyData() throws IOException {
		assertEquals(WorldData.EMPTY, WorldDataStore.open(dir, 5).current());
	}

	@Test
	void invalidChangeIsRejectedWithoutTouchingDisk() throws IOException {
		var store = WorldDataStore.open(dir, 5);
		var data = sample();
		store.commit(data);
		String before = Files.readString(dir.resolve("plots.json"));
		var orphanPlot = new WorldData(data.plots(), java.util.List.of(), java.util.List.of());
		assertThrows(PlotException.class, () -> store.commit(orphanPlot));
		assertEquals(before, Files.readString(dir.resolve("plots.json")));
		assertEquals(data, store.current());
	}

	@Test
	void failedWriteKeepsLastValidFile() throws IOException {
		var store = WorldDataStore.open(dir, 5);
		var data = sample();
		store.commit(data);
		String before = Files.readString(dir.resolve("plots.json"));
		Files.createDirectory(dir.resolve("plots.json.tmp"));
		assertThrows(IOException.class, () -> store.commit(WorldData.EMPTY));
		assertEquals(before, Files.readString(dir.resolve("plots.json")));
		assertEquals(data, store.current());
	}

	@Test
	void corruptFileIsRecoveredFromNewestValidBackup() throws IOException {
		var store = WorldDataStore.open(dir, 5);
		var first = sample();
		store.commit(first);
		store.commit(sample());
		Files.writeString(dir.resolve("plots.json"), "{ broken");

		assertEquals(first, WorldDataStore.open(dir, 5).current());
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("plots.json.corrupt-")), "corrupt file kept for inspection");
		}
	}

	@Test
	void refusesToStartWhenNothingIsRecoverable() throws IOException {
		var store = WorldDataStore.open(dir, 5);
		store.commit(sample());
		store.commit(sample());
		Files.writeString(dir.resolve("plots.json"), "{ broken");
		try (Stream<Path> files = Files.list(dir.resolve("backups"))) {
			for (Path p : files.toList()) {
				Files.writeString(p, "{\"schemaVersion\": 1, \"plots\": [], \"groups\": [{\"id\": \"x\"}]}");
			}
		}
		assertThrows(IOException.class, () -> WorldDataStore.open(dir, 5));
		assertEquals("{ broken", Files.readString(dir.resolve("plots.json")), "nothing overwritten");
	}

	@Test
	void migratesVersionOneFileAndBacksItUpBeforeReplacing() throws IOException {
		var sample = sample();
		var v1 = WorldDataStore.serialize(new WorldData(sample.plots(), sample.groups(), List.of()))
			.replace("\"schemaVersion\": " + WorldDataStore.SCHEMA_VERSION, "\"schemaVersion\": 1")
			.replaceAll(",\\s*\"regeneration\": \\[\\]", "");
		assertFalse(v1.contains("regeneration"));
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("plots.json"), v1);

		var store = WorldDataStore.open(dir, 5);
		assertEquals(List.of(), store.current().regeneration());
		assertEquals(sample.plots(), store.current().plots());
		store.commit(store.current());
		assertTrue(Files.readString(dir.resolve("plots.json")).contains("\"schemaVersion\": 2"));
		try (Stream<Path> files = Files.list(dir.resolve("backups"))) {
			assertTrue(files.anyMatch(p -> readUnchecked(p).equals(v1)), "v1 file kept as backup");
		}
	}

	private static String readUnchecked(Path p) {
		try {
			return Files.readString(p);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	@Test
	void rejectsUnknownSchemaVersions() {
		assertThrows(IllegalArgumentException.class, () -> WorldDataStore.parse("{\"plots\": [], \"groups\": []}"));
		assertThrows(IllegalArgumentException.class,
			() -> WorldDataStore.parse("{\"schemaVersion\": " + (WorldDataStore.SCHEMA_VERSION + 1) + ", \"plots\": [], \"groups\": []}"));
	}

	@Test
	void backupRotationKeepsConfiguredCount() throws IOException, InterruptedException {
		var store = WorldDataStore.open(dir, 2);
		for (int i = 0; i < 5; i++) {
			store.commit(sample());
			Thread.sleep(2);
		}
		assertEquals(2, backups());
	}
}
