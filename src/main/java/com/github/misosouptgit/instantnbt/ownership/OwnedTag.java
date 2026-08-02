package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.Tag;

/**
 * Lazy-ownership wrapper around a vanilla {@link Tag} (Project Plan 6).
 * Meta is created only on share / freeze / acquire / ensureWritable promotion paths.
 */
public final class OwnedTag {
	private Tag payload;
	private OwnedMeta meta;

	private OwnedTag(Tag payload, OwnedMeta meta) {
		if (payload == null) {
			throw new IllegalArgumentException("payload must not be null");
		}
		this.payload = payload;
		this.meta = meta;
	}

	public static OwnedTag of(Tag payload) {
		return new OwnedTag(payload, null);
	}

	public static OwnedTag owned(Tag payload, Owner owner) {
		return new OwnedTag(payload, new OwnedMeta(owner == null ? Owner.current(ModuleDomain.RUNTIME) : owner));
	}

	public Tag payload() {
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
		meta.addRefCount(-1);
		if (meta.refCount() == 0) {
			meta.setState(OwnershipState.DETACHED);
		}
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
	 */
	public Tag ensureWritable() {
		if (meta == null) {
			return payload;
		}
		if (meta.state() == OwnershipState.DETACHED) {
			throw new IllegalStateException("cannot write DETACHED tag");
		}
		if (meta.state() == OwnershipState.FROZEN || meta.immutable()) {
			throw new IllegalStateException("cannot write FROZEN/immutable tag; copy first");
		}
		if (meta.state() == OwnershipState.SHARED) {
			split();
		}
		return payload;
	}

	/**
	 * Shallow-first CoW split: copy payload, move this instance to UNIQUE.
	 */
	public OwnedTag split() {
		OwnedMeta m = promote();
		Tag copy = payload.copy();
		payload = copy;
		m.setState(OwnershipState.UNIQUE);
		m.setImmutable(false);
		m.setRefCount(1);
		m.bumpGeneration();
		return this;
	}

	/**
	 * Produce a UNIQUE writable copy from a FROZEN (or any) tag.
	 */
	public OwnedTag copyUnique(Owner owner) {
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
