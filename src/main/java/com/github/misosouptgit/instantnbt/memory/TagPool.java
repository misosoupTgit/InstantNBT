package com.github.misosouptgit.instantnbt.memory;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Type-keyed object pool with size-class queues (Project Plan 9).
 */
public final class TagPool {
	public enum Slot {
		COMPOUND,
		LIST,
		BYTE_ARRAY,
		INT_ARRAY,
		LONG_ARRAY,
		GENERIC
	}

	private final Map<Slot, ArrayDeque<Object>> queues = new EnumMap<>(Slot.class);
	private int highWater = 256;
	private long hits;
	private long misses;
	private long shrinkEvents;

	public TagPool() {
		for (Slot slot : Slot.values()) {
			queues.put(slot, new ArrayDeque<>());
		}
	}

	public void setHighWater(int highWater) {
		this.highWater = Math.max(8, highWater);
	}

	@SuppressWarnings("unchecked")
	public <T> T acquire(Slot slot, Supplier<T> factory) {
		ArrayDeque<Object> q = queues.get(slot);
		Object value = q.pollFirst();
		if (value != null) {
			hits++;
			return (T) value;
		}
		misses++;
		return factory.get();
	}

	public void release(Slot slot, Object value) {
		if (value == null) {
			return;
		}
		ArrayDeque<Object> q = queues.get(slot);
		if (q.size() >= highWater) {
			return;
		}
		q.offerLast(value);
	}

	public void shrink() {
		for (ArrayDeque<Object> q : queues.values()) {
			int target = Math.max(4, q.size() / 2);
			while (q.size() > target) {
				q.pollFirst();
			}
		}
		shrinkEvents++;
	}

	public int inventory(Slot slot) {
		return queues.get(slot).size();
	}

	public long shrinkEvents() {
		return shrinkEvents;
	}

	public void clear() {
		for (ArrayDeque<Object> q : queues.values()) {
			q.clear();
		}
	}

	public double hitRate() {
		long total = hits + misses;
		return total == 0 ? 0.0 : (double) hits / (double) total;
	}

	public long hits() {
		return hits;
	}

	public long misses() {
		return misses;
	}
}
