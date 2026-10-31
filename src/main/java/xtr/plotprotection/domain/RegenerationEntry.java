package xtr.plotprotection.domain;

import java.util.Objects;

/**
 * Snapshot of one block taken before an unauthorized change (A3: every block has its own clock).
 *
 * @param blockState  block state as SNBT
 * @param blockEntity block entity with full metadata as SNBT, null if the block had none
 * @param restoreAt   overworld game time in ticks, so /tick sprint and /tick freeze apply
 * @param restored    already applied to the world; kept until the next world save so a crash re-applies it,
 *                    which is safe because restoring overwrites instead of adding
 */
public record RegenerationEntry(String dimension, BlockPosition position, String blockState, String blockEntity, long restoreAt, boolean restored) {
	public RegenerationEntry {
		Objects.requireNonNull(dimension);
		Objects.requireNonNull(position);
		Objects.requireNonNull(blockState);
	}

	public boolean isAt(String dimension, BlockPosition position) {
		return this.dimension.equals(dimension) && this.position.equals(position);
	}

	public RegenerationEntry reopened(long restoreAt) {
		return new RegenerationEntry(dimension, position, blockState, blockEntity, restoreAt, false);
	}

	public RegenerationEntry markedRestored() {
		return new RegenerationEntry(dimension, position, blockState, blockEntity, restoreAt, true);
	}
}
