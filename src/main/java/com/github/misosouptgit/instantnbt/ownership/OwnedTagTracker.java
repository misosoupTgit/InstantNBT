package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Weak identity map from vanilla Tag -> OwnedTag for optional API tracking.
 */
public final class OwnedTagTracker {
	private final Map<Tag, OwnedTag> byPayload = new WeakHashMap<>();

	public synchronized void track(OwnedTag tag) {
		if (tag == null) {
			return;
		}
		byPayload.put(tag.payload(), tag);
	}

	public synchronized OwnedTag find(Tag payload) {
		if (payload == null) {
			return null;
		}
		return byPayload.get(payload);
	}

	public synchronized void untrack(Tag payload) {
		if (payload != null) {
			byPayload.remove(payload);
		}
	}

	public synchronized int size() {
		return byPayload.size();
	}

	public synchronized void clear() {
		byPayload.clear();
	}
}
