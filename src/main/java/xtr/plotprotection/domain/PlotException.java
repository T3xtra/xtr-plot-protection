package xtr.plotprotection.domain;

/** A rejected change; translated into the player's language before it is shown. */
public class PlotException extends RuntimeException {
	private final String key;
	private final Object[] args;

	public PlotException(String key, Object... args) {
		super(key);
		this.key = key;
		this.args = args;
	}

	public String translate(Translator translator) {
		return translator.get(key, args);
	}
}
