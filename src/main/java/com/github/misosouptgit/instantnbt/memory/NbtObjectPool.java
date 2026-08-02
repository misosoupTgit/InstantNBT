package com.github.misosouptgit.instantnbt.memory;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;

/**
 * Typed NBT object pool facade (Project Plan 9.1).
 */
public final class NbtObjectPool {
	private final TagPool pool;
	private int warmupSize = 32;

	public NbtObjectPool(TagPool pool) {
		this.pool = pool;
	}

	public void setWarmupSize(int warmupSize) {
		this.warmupSize = Math.max(0, warmupSize);
	}

	public void warmup() {
		for (int i = 0; i < warmupSize; i++) {
			pool.release(TagPool.Slot.COMPOUND, new CompoundTag());
			pool.release(TagPool.Slot.LIST, new ListTag());
		}
	}

	public CompoundTag acquireCompound() {
		CompoundTag tag = pool.acquire(TagPool.Slot.COMPOUND, CompoundTag::new);
		clearCompound(tag);
		return tag;
	}

	public void releaseCompound(CompoundTag tag) {
		if (tag == null) {
			return;
		}
		clearCompound(tag);
		pool.release(TagPool.Slot.COMPOUND, tag);
	}

	public ListTag acquireList() {
		ListTag tag = pool.acquire(TagPool.Slot.LIST, ListTag::new);
		while (!tag.isEmpty()) {
			tag.remove(0);
		}
		return tag;
	}

	public void releaseList(ListTag tag) {
		if (tag == null) {
			return;
		}
		while (!tag.isEmpty()) {
			tag.remove(0);
		}
		pool.release(TagPool.Slot.LIST, tag);
	}

	private static void clearCompound(CompoundTag tag) {
		java.util.ArrayList<String> keys = new java.util.ArrayList<>(tag.getAllKeys());
		for (String key : keys) {
			tag.remove(key);
		}
	}

	public ByteArrayTag acquireByteArray(int size) {
		return pool.acquire(TagPool.Slot.BYTE_ARRAY, () -> new ByteArrayTag(new byte[Math.max(0, size)]));
	}

	public IntArrayTag acquireIntArray(int size) {
		return pool.acquire(TagPool.Slot.INT_ARRAY, () -> new IntArrayTag(new int[Math.max(0, size)]));
	}

	public LongArrayTag acquireLongArray(int size) {
		return pool.acquire(TagPool.Slot.LONG_ARRAY, () -> new LongArrayTag(new long[Math.max(0, size)]));
	}

	public TagPool raw() {
		return pool;
	}
}
