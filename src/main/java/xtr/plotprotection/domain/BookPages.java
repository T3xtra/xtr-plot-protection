package xtr.plotprotection.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.UUID;

/** Renders and parses the pages of the original book. Page 1 is system generated with editable setting lines, pages 2+ list members. */
public final class BookPages {
	private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final int NAMES_PER_PAGE = 14;
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final List<String> SETTINGS = List.of("book.plot", "book.group", "book.mode", "book.doors", "book.min_y", "book.max_y");
	private static final String VALUE = "\u0000";

	public record MemberEdit(Group group, List<String> rejected) {
	}

	public record SettingsEdit(WorldData data, List<PlotException> rejected) {
	}

	private BookPages() {
	}

	public static List<String> render(Plot plot, Group group, Function<UUID, String> names, Translator t) {
		var pages = new ArrayList<String>();
		pages.add(systemPage(plot, group, names, t));
		var memberNames = group.members().stream().map(names).sorted(String.CASE_INSENSITIVE_ORDER).toList();
		for (int i = 0; i < memberNames.size(); i += NAMES_PER_PAGE) {
			pages.add(String.join("\n", memberNames.subList(i, Math.min(i + NAMES_PER_PAGE, memberNames.size()))));
		}
		if (pages.size() == 1) {
			pages.add("");
		}
		return pages;
	}

	static String systemPage(Plot plot, Group group, Function<UUID, String> names, Translator t) {
		var lines = new ArrayList<String>();
		lines.add(settingLine("book.plot", plot, group, t));
		lines.add(settingLine("book.group", plot, group, t));
		lines.add(t.get("book.owner", names.apply(group.owner())));
		lines.add(t.get("book.status", "§" + plot.status().colorCode + t.get(statusKey(plot.status())) + "§r"));
		lines.add(settingLine("book.mode", plot, group, t));
		lines.add(settingLine("book.doors", plot, group, t));
		lines.add(settingLine("book.min_y", plot, group, t));
		lines.add(settingLine("book.max_y", plot, group, t));
		lines.add(t.get("book.dimension", plot.dimension() == null ? "-" : plot.dimension()));
		plot.markers().stream()
			.sorted(Comparator.comparingInt((Marker m) -> m.position().z()).thenComparingInt(m -> m.position().x()))
			.forEach(m -> lines.add(corner(plot, m.position(), t) + (m.isValid() ? "§a" : "§c") + m.position() + "§r"));
		return String.join("\n", lines);
	}

	private static String settingLine(String key, Plot plot, Group group, Translator t) {
		return t.get(key, switch (key) {
			case "book.plot" -> plot.name();
			case "book.group" -> group.name();
			case "book.mode" -> t.get(modeKey(plot.mode()));
			case "book.doors" -> t.get(doorsKey(plot.allowDirectDoorInteraction()));
			case "book.min_y" -> plot.bounds() == null ? "-" : String.valueOf(plot.bounds().minY());
			case "book.max_y" -> plot.bounds() == null ? "-" : String.valueOf(plot.bounds().maxY());
			default -> throw new IllegalArgumentException(key);
		});
	}

	/**
	 * Applies the setting lines of page 1, written in any of the given languages. Only lines that differ from
	 * {@code previousPage} count as edits, so a book that missed a sync does not revert newer changes. Each edited value
	 * is applied on its own; invalid values are rejected and stay as they were. Display-only and unknown lines are ignored.
	 */
	public static SettingsEdit applySystemPage(WorldData data, UUID plotId, String previousPage, String page, List<Translator> languages, long now) {
		var previous = previousPage.lines().map(l -> FORMATTING.matcher(l).replaceAll("").strip()).toList();
		var rejected = new ArrayList<PlotException>();
		WorldData next = data;
		Integer minY = null;
		Integer maxY = null;
		for (String raw : page.split("\n")) {
			String line = FORMATTING.matcher(raw).replaceAll("").strip();
			if (previous.contains(line)) {
				continue;
			}
			Plot plot = next.plot(plotId).orElseThrow(() -> new PlotException("plot.not_found"));
			Group group = next.groupOf(plot);
			for (String key : SETTINGS) {
				Optional<String> value = valueOf(line, key, languages);
				if (value.isEmpty()) {
					continue;
				}
				if (languages.stream().anyMatch(t -> FORMATTING.matcher(settingLine(key, plot, group, t)).replaceAll("").strip().equals(line))) {
					break;
				}
				String v = value.get();
				try {
					switch (key) {
						case "book.plot" -> next = PlotService.renamePlot(next, plotId, v, now);
						case "book.group" -> next = PlotService.renameGroup(next, group.id(), v);
						case "book.mode" -> next = PlotService.setMode(next, plotId, parse(ProtectionMode.values(), BookPages::modeKey, v, line, languages), now);
						case "book.doors" -> next = PlotService.setDoors(next, plotId, parse(new Boolean[] {true, false}, BookPages::doorsKey, v, line, languages), now);
						case "book.min_y" -> minY = parseInt(v, line);
						case "book.max_y" -> maxY = parseInt(v, line);
						default -> throw new IllegalStateException(key);
					}
				} catch (PlotException e) {
					rejected.add(e);
				}
				break;
			}
		}
		if (minY != null || maxY != null) {
			Plot plot = next.plot(plotId).orElseThrow();
			try {
				if (plot.bounds() == null) {
					throw new PlotException("plot.no_bounds");
				}
				next = PlotService.setHeights(next, plotId, minY != null ? minY : plot.bounds().minY(), maxY != null ? maxY : plot.bounds().maxY(), now);
			} catch (PlotException e) {
				rejected.add(e);
			}
		}
		return new SettingsEdit(next, rejected);
	}

	private static Optional<String> valueOf(String line, String key, List<Translator> languages) {
		for (Translator t : languages) {
			String label = t.get(key, VALUE).split(VALUE)[0].strip();
			if (line.regionMatches(true, 0, label, 0, label.length())) {
				return Optional.of(line.substring(label.length()).strip());
			}
		}
		return Optional.empty();
	}

	private static <T> T parse(T[] candidates, Function<T, String> key, String value, String line, List<Translator> languages) {
		for (T candidate : candidates) {
			String k = key.apply(candidate);
			if (k.substring(k.indexOf('.') + 1).equalsIgnoreCase(value) || languages.stream().anyMatch(t -> t.get(k).equalsIgnoreCase(value))) {
				return candidate;
			}
		}
		throw new PlotException("book.setting_invalid", line);
	}

	private static int parseInt(String value, String line) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new PlotException("book.setting_invalid", line);
		}
	}

	/**
	 * Applies the member pages (everything after page 1). Names not resolvable to a known UUID are rejected,
	 * the owner cannot be removed or duplicated, and the editor's identity is deliberately not checked.
	 */
	public static MemberEdit applyMemberPages(Group group, List<String> memberPages, Function<UUID, String> names, Function<String, Optional<UUID>> resolve) {
		var members = new HashSet<UUID>();
		var rejected = new ArrayList<String>();
		for (String page : memberPages) {
			for (String line : page.split("\n")) {
				String name = line.strip();
				if (name.isEmpty()) {
					continue;
				}
				Optional<UUID> uuid = PLAYER_NAME.matcher(name).matches()
					? currentMember(group, name, names).or(() -> resolve.apply(name))
					: Optional.empty();
				uuid.ifPresentOrElse(members::add, () -> rejected.add(name));
			}
		}
		members.remove(group.owner());
		return new MemberEdit(group.withMembers(members), rejected);
	}

	/** Keeps a member whose stored display name is no longer resolvable, e.g. after a rename the server has not seen. */
	private static Optional<UUID> currentMember(Group group, String name, Function<UUID, String> names) {
		return group.members().stream().filter(uuid -> names.apply(uuid).equalsIgnoreCase(name)).findFirst();
	}

	public static String statusKey(PlotStatus status) {
		return "status." + status.name().toLowerCase(Locale.ROOT);
	}

	public static String doorsKey(boolean allowDirectDoorInteraction) {
		return allowDirectDoorInteraction ? "doors.open" : "doors.locked";
	}

	public static String modeKey(ProtectionMode mode) {
		return "mode." + mode.name().toLowerCase(Locale.ROOT);
	}

	private static String corner(Plot plot, BlockPosition p, Translator t) {
		if (plot.bounds() == null) {
			return "";
		}
		return t.get("book.corner." + (p.z() == plot.bounds().minZ() ? "n" : "s") + (p.x() == plot.bounds().maxX() ? "e" : "w")) + " ";
	}
}
