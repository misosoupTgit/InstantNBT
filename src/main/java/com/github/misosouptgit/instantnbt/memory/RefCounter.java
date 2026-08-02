package com.github.misosouptgit.instantnbt.memory;

/**
 * Thread-local ref-count delta buffer (Project Plan 6.7).
 * Accrues acquire/release deltas and flushes into OwnedMeta at tick/handoff boundaries.
 */
public final class RefCounter {
	private static final ThreadLocal<int[]> BUFFER = ThreadLocal.withInitial(() -> new int[1]);

	private long flushCount;
	private long bufferedOps;

	public void bufferDelta(int delta) {
		BUFFER.get()[0] += delta;
		bufferedOps++;
	}

	public int pending() {
		return BUFFER.get()[0];
	}

	/**
	 * Drain thread-local delta into the supplied sink and reset the buffer.
	 */
	public int flush(IntSink sink) {
		int delta = BUFFER.get()[0];
		BUFFER.get()[0] = 0;
		if (delta != 0 && sink != null) {
			sink.accept(delta);
		}
		flushCount++;
		return delta;
	}

	public void clearThreadLocal() {
		BUFFER.remove();
	}

	public long flushCount() {
		return flushCount;
	}

	public long bufferedOps() {
		return bufferedOps;
	}

	@FunctionalInterface
	public interface IntSink {
		void accept(int delta);
	}
}
