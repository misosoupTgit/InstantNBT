package com.github.misosouptgit.instantnbt.ownership;

/**
 * Lazy ownership metadata. Null on OwnedTag means plain NBT cost (Project Plan 6.1).
 * Phase-1 fields only: state + generation + refCount + dirty.
 */
public final class OwnedMeta {
	private Owner owner;
	private int refCount;
	private OwnershipState state;
	private boolean immutable;
	private long generation;
	private boolean dirty;

	OwnedMeta(Owner owner) {
		this.owner = owner;
		this.refCount = 1;
		this.state = OwnershipState.UNIQUE;
		this.immutable = false;
		this.generation = 0L;
		this.dirty = false;
	}

	public Owner owner() {
		return owner;
	}

	void setOwner(Owner owner) {
		this.owner = owner;
	}

	public int refCount() {
		return refCount;
	}

	void setRefCount(int refCount) {
		this.refCount = Math.max(0, refCount);
	}

	void addRefCount(int delta) {
		this.refCount = Math.max(0, this.refCount + delta);
	}

	public OwnershipState state() {
		return state;
	}

	void setState(OwnershipState state) {
		this.state = state;
	}

	public boolean immutable() {
		return immutable;
	}

	void setImmutable(boolean immutable) {
		this.immutable = immutable;
	}

	public long generation() {
		return generation;
	}

	void bumpGeneration() {
		this.generation++;
		this.dirty = true;
	}

	public boolean dirty() {
		return dirty;
	}

	void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	void clearDirty() {
		this.dirty = false;
	}
}
