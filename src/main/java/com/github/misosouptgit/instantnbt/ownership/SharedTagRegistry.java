package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Content-addressed SharedTag registry (Project Plan 8).
 * Lock order: Arena -> OwnedMeta -> SharedTag registry (Plan 12.3).
 */
public final class SharedTagRegistry {
	private final Map<Long, List<Entry>> buckets = new ConcurrentHashMap<>();
	private final AtomicLong hits = new AtomicLong();
	private final AtomicLong misses = new AtomicLong();
	private final AtomicLong collisions = new AtomicLong();
	private volatile boolean suppressed;
	private volatile long collisionBudget = 64;

	public OwnedTag intern(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		if (suppressed) {
			tag.share();
			return tag;
		}
		com.github.misosouptgit.instantnbt.runtime.LockOrderGuard.enter(
			com.github.misosouptgit.instantnbt.runtime.LockOrderGuard.Domain.SHARED_TAG);
		try {
			OwnedMeta meta = tag.promote();
			meta.setImmutable(true);
			tag.share();

			long key = structuralKey(tag.payload(), meta.generation());
			List<Entry> list = buckets.computeIfAbsent(key, k -> new ArrayList<>(2));
			synchronized (list) {
				for (Entry entry : list) {
					if (entry.tag.payload().equals(tag.payload())) {
						hits.incrementAndGet();
						entry.tag.acquire(meta.owner());
						return entry.tag;
					}
					collisions.incrementAndGet();
				}
				misses.incrementAndGet();
				list.add(new Entry(tag, key));
				maybeSuppress();
				return tag;
			}
		} finally {
			com.github.misosouptgit.instantnbt.runtime.LockOrderGuard.leave(
				com.github.misosouptgit.instantnbt.runtime.LockOrderGuard.Domain.SHARED_TAG);
		}
	}

	public void releaseIfOrphaned(OwnedTag tag) {
		if (tag == null || !tag.hasMeta()) {
			return;
		}
		OwnedMeta meta = tag.meta();
		if (meta.refCount() > 0 || meta.state() != OwnershipState.DETACHED) {
			return;
		}
		long key = structuralKey(tag.payload(), meta.generation());
		List<Entry> list = buckets.get(key);
		if (list == null) {
			return;
		}
		synchronized (list) {
			Iterator<Entry> it = list.iterator();
			while (it.hasNext()) {
				if (it.next().tag == tag) {
					it.remove();
					break;
				}
			}
			if (list.isEmpty()) {
				buckets.remove(key, list);
			}
		}
	}

	public void clear() {
		buckets.clear();
	}

	public void setSuppressed(boolean suppressed) {
		this.suppressed = suppressed;
	}

	public boolean isSuppressed() {
		return suppressed;
	}

	public long hits() {
		return hits.get();
	}

	public long misses() {
		return misses.get();
	}

	public long collisions() {
		return collisions.get();
	}

	public int size() {
		int total = 0;
		for (List<Entry> list : buckets.values()) {
			synchronized (list) {
				total += list.size();
			}
		}
		return total;
	}

	private void maybeSuppress() {
		if (collisions.get() > collisionBudget && hits.get() > 0) {
			double ratio = (double) collisions.get() / (double) (hits.get() + misses.get());
			if (ratio > 0.35) {
				suppressed = true;
			}
		}
	}

	static long structuralKey(Tag payload, long generation) {
		int size = CowEngine.estimateSize(payload);
		int hash = payload.hashCode();
		return (((long) hash) << 32) ^ (((long) size) << 16) ^ (generation & 0xFFFFL);
	}

	private static final class Entry {
		final OwnedTag tag;
		final long key;

		Entry(OwnedTag tag, long key) {
			this.tag = tag;
			this.key = key;
		}
	}
}
