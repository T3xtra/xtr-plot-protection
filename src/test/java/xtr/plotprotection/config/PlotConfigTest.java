package xtr.plotprotection.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotConfigTest {
	@TempDir
	Path dir;

	@Test
	void writesAndReadsDefaults() throws IOException {
		var file = dir.resolve("config.json");
		assertEquals(PlotConfig.DEFAULTS, PlotConfig.load(file));
		assertEquals(PlotConfig.DEFAULTS, PlotConfig.load(file));
	}

	@Test
	void addsDefaultsForMissingKeysAndKeepsSetValues() throws IOException {
		var file = dir.resolve("config.json");
		Files.writeString(file, "{\"heightBelowMarker\": 7}");
		var config = PlotConfig.load(file);
		assertEquals(7, config.heightBelowMarker());
		assertEquals(PlotConfig.DEFAULTS.regenerationDelaySeconds(), config.regenerationDelaySeconds());
		assertEquals(config, PlotConfig.load(file), "defaults were written back");
	}

	@Test
	void rejectsUnparseableOrInvalidConfig() throws IOException {
		var file = dir.resolve("config.json");
		Files.writeString(file, "{ broken");
		assertThrows(RuntimeException.class, () -> PlotConfig.load(file));
		Files.writeString(file, "{\"heightBelowMarker\": 20, \"heightAboveMarker\": 40, \"repairCountdownSeconds\": 0, \"backupCount\": 3, "
			+ "\"regenerationDelaySeconds\": 60, \"regenerationBlocksPerTick\": 5, \"boundsViewRange\": 32, \"boundsViewIntervalTicks\": 10}");
		assertThrows(IOException.class, () -> PlotConfig.load(file));
	}
}
