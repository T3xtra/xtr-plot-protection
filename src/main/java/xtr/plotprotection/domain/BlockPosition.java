package xtr.plotprotection.domain;

public record BlockPosition(int x, int y, int z) {
	@Override
	public String toString() {
		return x + " " + y + " " + z;
	}
}
