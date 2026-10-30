package xtr.plotprotection.domain;

/** Resolves a message key in the viewer's language; arguments use positional {@code %1$s} placeholders. */
@FunctionalInterface
public interface Translator {
	String get(String key, Object... args);
}
