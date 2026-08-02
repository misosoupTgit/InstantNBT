package com.github.misosouptgit.instantnbt.memory;

/**
 * Memory runtime facade (Project Plan 5).
 */
public final class MemoryManager {
	private final TagPool pool = new TagPool();
	private final Allocator allocator = new Allocator(pool);
	private final RefCounter refCounter = new RefCounter();
	private final GarbageMonitor garbageMonitor = new GarbageMonitor();
	private boolean started;

	public void start() {
		started = true;
		pool.setHighWater(256);
	}

	public void shutdown() {
		pool.clear();
		allocator.clearThreadLocal();
		refCounter.clearThreadLocal();
		started = false;
	}

	public boolean isStarted() {
		return started;
	}

	public TagPool pool() {
		return pool;
	}

	public Allocator allocator() {
		return allocator;
	}

	public RefCounter refCounter() {
		return refCounter;
	}

	public GarbageMonitor garbageMonitor() {
		return garbageMonitor;
	}

	/**
	 * Tick-end maintenance: flush ref deltas locally, compact arenas, react to pressure.
	 */
	public void onTickEnd() {
		if (!started) {
			return;
		}
		refCounter.flush(null);
		allocator.endTick();
		if (garbageMonitor.shouldShrinkPools()) {
			pool.shrink();
		}
	}

	/**
	 * Apply Project Plan 5.4 pressure response steps that are local to Memory Manager.
	 */
	public void respondToPressure() {
		if (garbageMonitor.shouldShrinkPools()) {
			pool.shrink();
			allocator.arena().compact(16);
		}
	}
}
