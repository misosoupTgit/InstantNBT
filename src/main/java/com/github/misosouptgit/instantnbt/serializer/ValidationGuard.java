package com.github.misosouptgit.instantnbt.serializer;

import com.github.misosouptgit.instantnbt.ownership.CowEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Decode/encode guards (Project Plan 18.2).
 */
public final class ValidationGuard {
	private final int maxBytes;
	private final int maxDepth;
	private final int maxElements;

	public ValidationGuard(int maxBytes, int maxDepth, int maxElements) {
		this.maxBytes = Math.max(1024, maxBytes);
		this.maxDepth = Math.max(1, maxDepth);
		this.maxElements = Math.max(16, maxElements);
	}

	public static ValidationGuard defaults() {
		return new ValidationGuard(2 * 1024 * 1024, 64, 100_000);
	}

	public void validateEncodedSize(byte[] data) {
		if (data != null && data.length > maxBytes) {
			throw new IllegalArgumentException("NBT payload exceeds maxBytes=" + maxBytes + " actual=" + data.length);
		}
	}

	public void validateTag(Tag tag) {
		if (tag == null) {
			return;
		}
		int depth = CowEngine.nestDepth(tag, 0, maxDepth + 1);
		if (depth > maxDepth) {
			throw new IllegalArgumentException("NBT nest depth exceeds maxDepth=" + maxDepth);
		}
		int elements = countElements(tag, 0);
		if (elements > maxElements) {
			throw new IllegalArgumentException("NBT element count exceeds maxElements=" + maxElements);
		}
	}

	private int countElements(Tag tag, int acc) {
		if (acc > maxElements) {
			return acc;
		}
		if (tag instanceof CompoundTag) {
			CompoundTag compound = (CompoundTag) tag;
			int total = acc + compound.size();
			for (String key : compound.getAllKeys()) {
				total = countElements(compound.get(key), total);
				if (total > maxElements) {
					return total;
				}
			}
			return total;
		}
		if (tag instanceof ListTag) {
			ListTag list = (ListTag) tag;
			int total = acc + list.size();
			for (int i = 0; i < list.size(); i++) {
				total = countElements(list.get(i), total);
				if (total > maxElements) {
					return total;
				}
			}
			return total;
		}
		return acc + 1;
	}
}
