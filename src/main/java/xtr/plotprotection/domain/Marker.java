package xtr.plotprotection.domain;

import java.util.Objects;

public record Marker(BlockPosition position, MarkerStatus status, long registeredAt) {
	public Marker {
		Objects.requireNonNull(position);
		Objects.requireNonNull(status);
	}

	public boolean isValid() {
		return status == MarkerStatus.VALID;
	}
}
