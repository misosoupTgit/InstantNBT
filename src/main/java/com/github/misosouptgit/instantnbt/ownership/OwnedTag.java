package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.Tag;

/**
 * Lazy-ownership wrapper around a vanilla {@link Tag} (Project Plan 6).
 * Meta is created only on share / freeze / acquire / ensureWritable promotion paths.
 */
public final class OwnedTag {
	private static volatile CowStrategy defaultStrategy = CowStrategy.SHALLOW_FIRST;
	private static volatile int deepThreshold = CowEngine.DEFAULT_DEEP_THRESHOLD;
	private static volatile boolean cowEnabled = true;

	private Tag payload;
	private OwnedMeta meta;
	private int pinCount;
	private byte[] deferredBytes;

	private OwnedTag(Tag payload, OwnedMeta meta) {
		if (payload == null) {
			throw new IllegalArgumentException("payload must not be null");
		}
		this.payload = payload;
		this.meta = meta;
	}

	/**
	 * Lazy-decode holder: bytes materialize on first {@link #payload()} access (Project Plan 10.4).
	 */
	public static OwnedTag deferred(byte[] encoded) {
		if (encoded == null) {
			throw new IllegalArgumentException("encoded");
		}
		OwnedTag tag = new OwnedTag(new net.minecraft.nbt.CompoundTag(), null);
		tag.deferredBytes = encoded;
		return tag;
	}

	public boolean isDeferred() {
		return deferredBytes != null;
	}

	private void materializeIfNeeded() {
		if (deferredBytes == null) {
			return;
		}
		synchronized (this) {
			if (deferredBytes == null) {
				return;
			}
			try {
				if (com.github.misosouptgit.instantnbt.serializer.ChunkedNbtCodec.isChunked(deferredBytes)) {
					payload = com.github.misosouptgit.instantnbt.serializer.ChunkedNbtCodec.decode(deferredBytes);
				} else {
					payload = com.github.misosouptgit.instantnbt.serializer.BinaryNbtCodec.decode(deferredBytes);
				}
			} catch (Exception ex) {
				throw new IllegalStateException("lazy NBT materialize failed", ex);
			}
			deferredBytes = null;
		}
	}

	public static void configureCow(boolean enabled, CowStrategy strategy, int threshold) {
		cowEnabled = enabled;
		if (strategy != null) {
			defaultStrategy = strategy;
		}
		deepThreshold = Math.max(1, threshold);
	}

	public static OwnedTag of(Tag payload) {
		return new OwnedTag(payload, null);
	}

	public static OwnedTag owned(Tag payload, Owner owner) {
		return new OwnedTag(payload, new OwnedMeta(owner == null ? Owner.current(ModuleDomain.RUNTIME) : owner));
	}

	public Tag payload() {
		materializeIfNeeded();
		return payload;
	}

	public OwnedMeta meta() {
		return meta;
	}

	public boolean hasMeta() {
		return meta != null;
	}

	/**
	 * Lazily attach ownership metadata (Project Plan 6.6).
	 */
	public OwnedMeta promote(Owner owner) {
		if (meta == null) {
			meta = new OwnedMeta(owner == null ? Owner.current(ModuleDomain.RUNTIME) : owner);
		}
		return meta;
	}

	public OwnedMeta promote() {
		return promote(Owner.current(ModuleDomain.RUNTIME));
	}

	public void acquire(Owner owner) {
		OwnedMeta m = promote(owner);
		if (m.state() == OwnershipState.DETACHED) {
			m.setState(OwnershipState.UNIQUE);
			m.setImmutable(false);
		}
		m.setOwner(owner);
		m.addRefCount(1);
	}

	public void release() {
		if (meta == null) {
			return;
		}
		if (pinCount > 0) {
			throw new IllegalStateException("cannot release pinned tag");
		}
		meta.addRefCount(-1);
		if (meta.refCount() == 0) {
			meta.setState(OwnershipState.DETACHED);
		}
	}

	public void pin() {
		promote();
		pinCount++;
	}

	public void unpin() {
		if (pinCount <= 0) {
			throw new IllegalStateException("tag is not pinned");
		}
		pinCount--;
	}

	public boolean isPinned() {
		return pinCount > 0;
	}

	public boolean isShared() {
		return meta != null && meta.state() == OwnershipState.SHARED;
	}

	public boolean isFrozen() {
		return meta != null && (meta.state() == OwnershipState.FROZEN || meta.immutable());
	}

	public void share() {
		OwnedMeta m = promote();
		if (m.state() == OwnershipState.DETACHED) {
			throw new IllegalStateException("cannot share DETACHED tag");
		}
		if (m.state() == OwnershipState.FROZEN) {
			return;
		}
		m.setState(OwnershipState.SHARED);
	}

	public void freeze() {
		OwnedMeta m = promote();
		if (m.state() == OwnershipState.DETACHED) {
			throw new IllegalStateException("cannot freeze DETACHED tag");
		}
		m.setState(OwnershipState.FROZEN);
		m.setImmutable(true);
	}

	/**
	 * Ensure the payload is writable. SHARED / immutable triggers CoW split (Project Plan 6.3 / 7.2).
	 * Soft variant used by mixins never throws into vanilla write paths.
	 */
	public Tag ensureWritable() {
		return ensureWritable(false);
	}

	public Tag ensureWritableSoft() {
		return ensureWritable(true);
	}

	private Tag ensureWritable(boolean soft) {
		materializeIfNeeded();
		if (meta == null) {
			return payload;
		}
		if (meta.state() == OwnershipState.DETACHED) {
			if (soft) {
				return payload;
			}
			throw new IllegalStateException("cannot write DETACHED tag");
		}
		if (meta.state() == OwnershipState.FROZEN || meta.immutable()) {
			if (!cowEnabled) {
				if (soft) {
					try {
						payload = payload.copy();
						meta.setState(OwnershipState.UNIQUE);
						meta.setImmutable(false);
						meta.bumpGeneration();
					} catch (RuntimeException ignored) {
						// leave payload as-is
					}
					return payload;
				}
				throw new IllegalStateException("cannot write FROZEN/immutable tag; copy first");
			}
			return splitSoft(soft).payload;
		}
		if (meta.state() == OwnershipState.SHARED) {
			if (!cowEnabled) {
				if (soft) {
					try {
						payload = payload.copy();
						meta.setState(OwnershipState.UNIQUE);
						meta.setImmutable(false);
						meta.bumpGeneration();
					} catch (RuntimeException ignored) {
					}
					return payload;
				}
				throw new IllegalStateException("cannot write SHARED tag while CoW disabled");
			}
			splitSoft(soft);
		}
		return payload;
	}

	/**
	 * CoW split using configured shallow/adaptive strategy (Project Plan 7).
	 */
	public OwnedTag split() {
		return splitSoft(false);
	}

	private OwnedTag splitSoft(boolean soft) {
		materializeIfNeeded();
		OwnedMeta m = promote();
		try {
			Tag copy = cowEnabled
				? CowEngine.splitPayload(payload, defaultStrategy, deepThreshold)
				: payload.copy();
			payload = copy;
			m.setState(OwnershipState.UNIQUE);
			m.setImmutable(false);
			m.setRefCount(1);
			m.bumpGeneration();
		} catch (RuntimeException ex) {
			if (!soft) {
				throw ex;
			}
		}
		return this;
	}

	/**
	 * Produce a UNIQUE writable copy from a FROZEN (or any) tag.
	 */
	public OwnedTag copyUnique(Owner owner) {
		materializeIfNeeded();
		OwnedTag copy = owned(payload.copy(), owner == null ? Owner.current(ModuleDomain.RUNTIME) : owner);
		copy.meta.setState(OwnershipState.UNIQUE);
		copy.meta.setImmutable(false);
		return copy;
	}

	public void markDirty() {
		if (meta != null) {
			meta.bumpGeneration();
		}
	}

	public long generation() {
		return meta == null ? 0L : meta.generation();
	}

	public boolean dirty() {
		return meta != null && meta.dirty();
	}
}
