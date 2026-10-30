package xtr.plotprotection.domain;

public enum PlotStatus {
	/** Fewer than four markers have ever been registered; the plot never protected anything. */
	SETUP('7'),
	ACTIVE('a'),
	REPAIR_PENDING('6'),
	DISABLED('c');

	/** Minecraft legacy formatting code used for the status colour. */
	public final char colorCode;

	PlotStatus(char colorCode) {
		this.colorCode = colorCode;
	}
}
