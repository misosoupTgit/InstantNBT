package com.github.misosouptgit.instantnbt.network;

import com.github.misosouptgit.instantnbt.ownership.OwnedTag;

/**
 * Immutable payload handle for Direct Pass / Snapshot handoff (Project Plan 12.4).
 */
public final class SnapshotHandle {
	private final OwnedTag tag;
	private final long generation;
	private final SyncMode mode;

	public SnapshotHandle(OwnedTag tag, SyncMode mode) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		this.tag = tag;
		this.generation = tag.generation();
		this.mode = mode == null ? SyncMode.SNAPSHOT : mode;
	}

	public OwnedTag tag() {
		return tag;
	}

	public long generation() {
		return generation;
	}

	public SyncMode mode() {
		return mode;
	}

	public boolean matches(OwnedTag other) {
		return other != null && other.generation() == generation;
	}
}
