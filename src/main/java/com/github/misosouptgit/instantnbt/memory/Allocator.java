package com.github.misosouptgit.instantnbt.memory;

/**
 * Selects allocation policy by size class (Project Plan 5.2 / 5.3).
 */
public final class Allocator {
	private final TagPool pool;
	private final ThreadLocal<Arena> threadArena = ThreadLocal.withInitial(Arena::new);

	public Allocator(TagPool pool) {
		this.pool = pool;
	}

	public Arena arena() {
		return threadArena.get();
	}

	public AllocSizeClass classify(int estimatedBytes) {
		return AllocSizeClass.ofBytes(estimatedBytes);
	}

	public <T> T allocate(AllocSizeClass sizeClass, TagPool.Slot slot, java.util.function.Supplier<T> factory) {
		switch (sizeClass) {
			case SMALL:
				return arena().acquire(factory);
			case MEDIUM:
				return pool.acquire(slot, factory);
			case LARGE:
			default:
				return factory.get();
		}
	}

	public void free(AllocSizeClass sizeClass, TagPool.Slot slot, Object value) {
		switch (sizeClass) {
			case SMALL:
				arena().release(value);
				break;
			case MEDIUM:
				pool.release(slot, value);
				break;
			case LARGE:
			default:
				// direct alloc — rely on GC
				break;
		}
	}

	public void endTick() {
		arena().compact(64);
	}

	public void clearThreadLocal() {
		Arena arena = threadArena.get();
		arena.close();
		threadArena.remove();
	}
}
