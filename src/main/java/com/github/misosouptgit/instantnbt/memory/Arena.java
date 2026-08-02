package com.github.misosouptgit.instantnbt.memory;

import java.util.ArrayDeque;
import java.util.function.Supplier;

/**
 * Tick / thread-local allocation arena (Project Plan 5.1 / 5.3 SMALL path).
 */
public final class Arena {
	private final ArrayDeque<Object> recycled = new ArrayDeque<>();
	private int live;
	private long generation;
	private boolean open = true;

	public boolean isOpen() {
		return open;
	}

	public long generation() {
		return generation;
	}

	public int live() {
		return live;
	}

	@SuppressWarnings("unchecked")
	public <T> T acquire(Supplier<T> factory) {
		ensureOpen();
		Object pooled = recycled.pollFirst();
		if (pooled != null) {
			live++;
			return (T) pooled;
		}
		live++;
		return factory.get();
	}

	public void release(Object value) {
		if (value == null || !open) {
			return;
		}
		recycled.offerLast(value);
		live = Math.max(0, live - 1);
	}

	/**
	 * End-of-tick compaction: drop excess recycled slots and bump generation.
	 */
	public void compact(int keep) {
		while (recycled.size() > keep) {
			recycled.pollFirst();
		}
		generation++;
	}

	public void close() {
		recycled.clear();
		live = 0;
		open = false;
		generation++;
	}

	public void reopen() {
		open = true;
	}

	private void ensureOpen() {
		if (!open) {
			throw new IllegalStateException("arena is closed");
		}
	}
}
