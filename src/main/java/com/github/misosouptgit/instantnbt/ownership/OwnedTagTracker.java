package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.Tag;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ownership tracker: mixin identity slot is source of truth (no content-hash WeakHashMap).
 */
public final class OwnedTagTracker {
	private final AtomicInteger approxSize = new AtomicInteger();

	public void track(OwnedTag tag) {
		if (tag == null) {
			return;
		}
		Tag payload = tag.payload();
		OwnedTag prev = TrackedTagAccess.peek(payload);
		TrackedTagAccess.bind(payload, tag);
		if (prev == null) {
			approxSize.incrementAndGet();
		}
	}

	public OwnedTag find(Tag payload) {
		return TrackedTagAccess.peek(payload);
	}

	public void untrack(Tag payload) {
		if (payload == null) {
			return;
		}
		if (TrackedTagAccess.peek(payload) != null) {
			TrackedTagAccess.bind(payload, null);
			approxSize.decrementAndGet();
		}
	}

	public int size() {
		return Math.max(0, approxSize.get());
	}

	public void clear() {
		approxSize.set(0);
	}
}
