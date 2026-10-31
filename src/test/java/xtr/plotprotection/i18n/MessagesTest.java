package xtr.plotprotection.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MessagesTest {
	private static final Pattern PLACEHOLDER = Pattern.compile("%\\d+\\$s");

	@Test
	void everyLanguageHasTheSameKeysAndPlaceholdersAsEnglish() {
		var english = Messages.keys(Messages.FALLBACK);
		for (String locale : Messages.LOCALES) {
			var lang = Messages.keys(locale);
			assertEquals(english.keySet(), lang.keySet(), locale);
			english.forEach((key, text) -> assertEquals(placeholders(text), placeholders(lang.get(key)), locale + " " + key));
		}
	}

	@Test
	void formatsDurationsCompactly() {
		assertEquals("10 min", Messages.duration(600));
		assertEquals("24 h", Messages.duration(86_400));
		assertEquals("1 h 30 min 5 s", Messages.duration(5405));
		assertEquals("0 s", Messages.duration(0));
	}

	@Test
	void fallsBackToRegionThenEnglishThenKey() {
		assertEquals("Mitglied", Messages.forLocale("de_de").get("role.member"));
		assertEquals("Mitglied", Messages.forLocale("de_AT").get("role.member"));
		assertEquals("Member", Messages.forLocale("pl_pl").get("role.member"));
		assertEquals("Member", Messages.forLocale(null).get("role.member"));
		assertEquals("unknown.key", Messages.forLocale("de_de").get("unknown.key"));
		assertEquals("Türen: frei", Messages.forLocale("de_de").get("book.doors", "frei"));
	}

	private static String placeholders(String text) {
		return PLACEHOLDER.matcher(text).results().map(r -> r.group()).sorted().collect(Collectors.joining(","));
	}
}
