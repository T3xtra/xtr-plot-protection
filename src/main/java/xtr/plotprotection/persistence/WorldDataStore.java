package xtr.plotprotection.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtr.plotprotection.domain.Invariants;
import xtr.plotprotection.domain.PlotException;
import xtr.plotprotection.domain.WorldData;

/**
 * Versioned JSON storage with atomic replacement, rotating backups and backup recovery.
 * Never falls back to empty data when existing data cannot be read.
 */
public final class WorldDataStore {
	/** Entry i migrates schema version i + 1 to i + 2. */
	private static final List<UnaryOperator<JsonObject>> MIGRATIONS = List.of(
		v1 -> {
			v1.add("regeneration", new JsonArray());
			return v1;
		},
		// restoreAt changed from epoch millis to game ticks; the old values cannot be mapped, so pending blocks restore right away
		v2 -> {
			v2.getAsJsonArray("regeneration").forEach(entry -> entry.getAsJsonObject().addProperty("restoreAt", 0));
			return v2;
		}
	);
	public static final int SCHEMA_VERSION = MIGRATIONS.size() + 1;

	private static final Logger LOGGER = LoggerFactory.getLogger(WorldDataStore.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "plots.json";

	private final Path file;
	private final Path backupDir;
	private final int backupCount;
	private WorldData current;

	private WorldDataStore(Path dir, int backupCount) {
		this.file = dir.resolve(FILE_NAME);
		this.backupDir = dir.resolve("backups");
		this.backupCount = backupCount;
	}

	public static WorldDataStore open(Path dir, int backupCount) throws IOException {
		var store = new WorldDataStore(dir, backupCount);
		store.current = store.load();
		return store;
	}

	public synchronized WorldData current() {
		return current;
	}

	/** Validates and persists the new state; on any failure the previous state stays current and on disk. */
	public synchronized void commit(WorldData next) throws IOException {
		var errors = Invariants.validate(next);
		if (!errors.isEmpty()) {
			throw new PlotException("error.invalid_data", String.join("; ", errors));
		}
		write(next);
		current = next;
	}

	static String serialize(WorldData data) {
		var json = GSON.toJsonTree(data).getAsJsonObject();
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		return GSON.toJson(json);
	}

	static WorldData parse(String content) {
		var json = JsonParser.parseString(content).getAsJsonObject();
		if (!json.has("schemaVersion")) {
			throw new IllegalArgumentException("missing schemaVersion");
		}
		int version = json.get("schemaVersion").getAsInt();
		if (version < 1 || version > SCHEMA_VERSION) {
			throw new IllegalArgumentException("unsupported schemaVersion " + version + ", supported up to " + SCHEMA_VERSION);
		}
		for (int v = version; v < SCHEMA_VERSION; v++) {
			json = MIGRATIONS.get(v - 1).apply(json);
		}
		var data = GSON.fromJson(json, WorldData.class);
		var errors = Invariants.validate(data);
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("invalid data: " + String.join("; ", errors));
		}
		return data;
	}

	private WorldData load() throws IOException {
		boolean mainExists = Files.exists(file);
		if (mainExists) {
			try {
				return parse(Files.readString(file));
			} catch (Exception e) {
				LOGGER.error("Plot data file {} is unreadable, trying backups", file, e);
			}
		}
		var backups = backupsNewestFirst();
		if (!mainExists && backups.isEmpty()) {
			return WorldData.EMPTY;
		}
		for (Path backup : backups) {
			try {
				var data = parse(Files.readString(backup));
				if (mainExists) {
					Path aside = file.resolveSibling(FILE_NAME + ".corrupt-" + System.currentTimeMillis());
					Files.move(file, aside);
					LOGGER.warn("Moved unreadable plot data to {}", aside);
				}
				write(data);
				LOGGER.warn("Recovered plot data from backup {}", backup);
				return data;
			} catch (Exception e) {
				LOGGER.error("Plot data backup {} is unreadable", backup, e);
			}
		}
		throw new IOException("No valid plot data or backup in " + file.getParent() + "; refusing to start with empty plot data");
	}

	private void write(WorldData data) throws IOException {
		Files.createDirectories(backupDir);
		Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
		try (var channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
			channel.write(ByteBuffer.wrap(serialize(data).getBytes(StandardCharsets.UTF_8)));
			channel.force(true);
		}
		if (Files.exists(file)) {
			Files.copy(file, backupDir.resolve("plots-" + System.currentTimeMillis() + ".json"), StandardCopyOption.REPLACE_EXISTING);
			pruneBackups();
		}
		try {
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private List<Path> backupsNewestFirst() throws IOException {
		if (!Files.isDirectory(backupDir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(backupDir)) {
			return files.filter(p -> p.getFileName().toString().matches("plots-\\d+\\.json"))
				.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
				.toList();
		}
	}

	private void pruneBackups() throws IOException {
		var backups = backupsNewestFirst();
		for (Path old : backups.subList(Math.min(backupCount, backups.size()), backups.size())) {
			Files.delete(old);
		}
	}
}
