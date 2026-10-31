package xtr.plotprotection.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import xtr.plotprotection.domain.Translator;

/**
 * Server-side translations: vanilla clients do not ship this mod's language files,
 * so texts are resolved on the server using the language each player selected in their client.
 */
public final class Messages {
	public static final String FALLBACK = "en_us";
	public static final List<String> LOCALES = List.of("en_us", "de_de", "fr_fr", "it_it", "es_es", "zh_cn", "ja_jp");

	private static final Map<String, Map<String, String>> LANGUAGES = LOCALES.stream().collect(Collectors.toMap(Function.identity(), Messages::read));

	private Messages() {
	}

	/** Exact locale, then same language in another region (e.g. de_at → de_de), then English. */
	public static Translator forLocale(String locale) {
		String normalized = locale == null ? FALLBACK : locale.toLowerCase(Locale.ROOT);
		String language = normalized.split("_")[0];
		Map<String, String> selected = LANGUAGES.getOrDefault(normalized, LOCALES.stream()
			.filter(l -> l.startsWith(language + "_")).findFirst().map(LANGUAGES::get).orElse(LANGUAGES.get(FALLBACK)));
		Map<String, String> fallback = LANGUAGES.get(FALLBACK);
		return (key, args) -> String.format(Locale.ROOT, selected.getOrDefault(key, fallback.getOrDefault(key, key)), args);
	}

	public static Translator forPlayer(ServerPlayer player) {
		return forLocale(player.clientInformation().language());
	}

	/** Compact, language-neutral duration such as "24 h", "1 h 30 min" or "45 s". */
	public static String duration(long seconds) {
		long h = seconds / 3600, min = seconds % 3600 / 60, s = seconds % 60;
		var parts = new ArrayList<String>();
		if (h > 0) {
			parts.add(h + " h");
		}
		if (min > 0) {
			parts.add(min + " min");
		}
		if (s > 0 || parts.isEmpty()) {
			parts.add(s + " s");
		}
		return String.join(" ", parts);
	}

	public static Translator forSource(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null ? forLocale(FALLBACK) : forPlayer(player);
	}

	static Map<String, String> keys(String locale) {
		return LANGUAGES.get(locale);
	}

	private static Map<String, String> read(String locale) {
		String path = "/assets/xtr_plot_protection/lang/" + locale + ".json";
		try (var in = Messages.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing language file " + path);
			}
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
