package com.github.misosouptgit.instantnbt.serializer;

import com.github.misosouptgit.instantnbt.ownership.CowEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Decode/encode guards (Project Plan 18.2) — soft by default.
 */
public final class ValidationGuard {
	private final int maxBytes;
	private final int maxDepth;
	private final int maxElements;
	/** Soft ceiling: skip InstantNBT fast path but do not reject vanilla payloads. */
	private final int softMaxBytes;

	public ValidationGuard(int maxBytes, int maxDepth, int maxElements) {
		this.maxBytes = Math.max(1024, maxBytes);
		this.maxDepth = Math.max(1, maxDepth);
		this.maxElements = Math.max(16, maxElements);
		this.softMaxBytes = this.maxBytes;
	}

	public static ValidationGuard defaults() {
		// 8 MiB soft / hard — large chunk NBT must not trip kill-switch.
		return new ValidationGuard(8 * 1024 * 1024, 128, 500_000);
	}

	public GuardVerdict checkEncodedSize(byte[] data, boolean ownedPacket) {
		if (data == null) {
			return GuardVerdict.OK;
		}
		if (data.length > softMaxBytes) {
			return ownedPacket ? GuardVerdict.REJECT_OWNED : GuardVerdict.SKIP_FAST_PATH;
		}
		return GuardVerdict.OK;
	}

	public GuardVerdict checkTag(Tag tag, boolean ownedPacket) {
		if (tag == null) {
			return GuardVerdict.OK;
		}
		int depth = CowEngine.nestDepth(tag, 0, maxDepth + 1);
		if (depth > maxDepth) {
			return ownedPacket ? GuardVerdict.REJECT_OWNED : GuardVerdict.SKIP_FAST_PATH;
		}
		int elements = countElements(tag, 0);
		if (elements > maxElements) {
			return ownedPacket ? GuardVerdict.REJECT_OWNED : GuardVerdict.SKIP_FAST_PATH;
		}
		return GuardVerdict.OK;
	}

	/** @deprecated Prefer {@link #checkEncodedSize(byte[], boolean)} */
	@Deprecated
	public void validateEncodedSize(byte[] data) {
		if (checkEncodedSize(data, false) != GuardVerdict.OK) {
			throw new IllegalArgumentException("NBT payload exceeds maxBytes=" + maxBytes + " actual=" + (data == null ? -1 : data.length));
		}
	}

	/** @deprecated Prefer {@link #checkTag(Tag, boolean)} */
	@Deprecated
	public void validateTag(Tag tag) {
		if (checkTag(tag, false) != GuardVerdict.OK) {
			throw new IllegalArgumentException("NBT nest/elements exceed guard limits");
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
