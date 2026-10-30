package xtr.plotprotection.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * @param regenerationDelaySeconds  time from a block's first unauthorized change to its restoration (A3)
 * @param regenerationBlocksPerTick restoration batch limit, the configurable "duration" of A3
 * @param boundsViewRange           distance in blocks up to which plot outlines are shown to players holding a book
 * @param boundsViewIntervalTicks   how often the outlines are redrawn
 */
public record PlotConfig(int heightBelowMarker, int heightAboveMarker, long repairCountdownSeconds, int backupCount,
	long regenerationDelaySeconds, int regenerationBlocksPerTick, int boundsViewRange, int boundsViewIntervalTicks) {
	public static final PlotConfig DEFAULTS = new PlotConfig(20, 40, 86_400, 20, 300, 20, 32, 10);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Writes the defaults if the file is missing and adds documented defaults for keys introduced by newer versions;
	 * rejects unparseable files and invalid values instead of guessing.
	 */
	public static PlotConfig load(Path file) throws IOException {
		if (Files.notExists(file)) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(DEFAULTS));
			return DEFAULTS;
		}
		var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		List<String> missing = Arrays.stream(PlotConfig.class.getRecordComponents()).map(c -> c.getName()).filter(n -> !json.has(n)).toList();
		if (!missing.isEmpty()) {
			var defaults = GSON.toJsonTree(DEFAULTS).getAsJsonObject();
			missing.forEach(key -> json.add(key, defaults.get(key)));
			Files.writeString(file, GSON.toJson(json));
			LoggerFactory.getLogger(PlotConfig.class).warn("Config {} was missing {}; added their defaults", file, missing);
		}
		var config = GSON.fromJson(json, PlotConfig.class);
		if (config.heightBelowMarker < 0 || config.heightAboveMarker < 0 || config.repairCountdownSeconds <= 0 || config.backupCount < 1
			|| config.regenerationDelaySeconds < 0 || config.regenerationBlocksPerTick < 1 || config.boundsViewRange < 1 || config.boundsViewIntervalTicks < 1) {
			throw new IOException("Config " + file + " has invalid values: " + config);
		}
		return config;
	}
}
