package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Copy-on-Write helpers (Project Plan 7).
 */
public final class CowEngine {
	public static final int DEFAULT_DEEP_THRESHOLD = 4;

	private CowEngine() {}

	public static Tag splitPayload(Tag payload, CowStrategy strategy, int deepThreshold) {
		if (payload == null) {
			throw new IllegalArgumentException("payload");
		}
		// Structural shallow split first — unique root, shared leaves (Plan 7.2 / 7.3).
		if (strategy == CowStrategy.SHALLOW_FIRST
			|| nestDepth(payload, 0, deepThreshold + 1) < deepThreshold) {
			if (payload instanceof CompoundTag) {
				return shallowStructureCopyCompound((CompoundTag) payload);
			}
			if (payload instanceof ListTag) {
				return shallowStructureCopyList((ListTag) payload);
			}
			return payload.copy();
		}
		return deepCopy(payload);
	}

	public static int nestDepth(Tag tag, int depth, int max) {
		if (tag == null || depth >= max) {
			return depth;
		}
		if (tag instanceof CompoundTag) {
			CompoundTag compound = (CompoundTag) tag;
			int deepest = depth;
			for (String key : compound.getAllKeys()) {
				deepest = Math.max(deepest, nestDepth(compound.get(key), depth + 1, max));
				if (deepest >= max) {
					return deepest;
				}
			}
			return deepest;
		}
		if (tag instanceof ListTag) {
			ListTag list = (ListTag) tag;
			int deepest = depth;
			for (int i = 0; i < list.size(); i++) {
				deepest = Math.max(deepest, nestDepth(list.get(i), depth + 1, max));
				if (deepest >= max) {
					return deepest;
				}
			}
			return deepest;
		}
		return depth;
	}

	/**
	 * Explicit deep copy for nested compounds/lists; primitives use Tag#copy.
	 */
	public static Tag deepCopy(Tag tag) {
		if (tag instanceof CompoundTag) {
			CompoundTag src = (CompoundTag) tag;
			CompoundTag dst = new CompoundTag();
			for (String key : src.getAllKeys()) {
				Tag child = src.get(key);
				dst.put(key, child == null ? null : deepCopy(child));
			}
			return dst;
		}
		if (tag instanceof ListTag) {
			ListTag src = (ListTag) tag;
			ListTag dst = new ListTag();
			for (int i = 0; i < src.size(); i++) {
				dst.add(deepCopy(src.get(i)));
			}
			return dst;
		}
		return tag.copy();
	}

	/**
	 * Structural shallow copy: new compound/list nodes, shared immutable leaf tags.
	 * Nested compounds/lists are unique wrappers so in-place writes cannot corrupt the source tree.
	 */
	public static CompoundTag shallowStructureCopyCompound(CompoundTag src) {
		TagWriteHooks.enterInternal();
		try {
			return copyCompound0(src);
		} finally {
			TagWriteHooks.leaveInternal();
		}
	}

	public static ListTag shallowStructureCopyList(ListTag src) {
		TagWriteHooks.enterInternal();
		try {
			return copyList0(src);
		} finally {
			TagWriteHooks.leaveInternal();
		}
	}

	private static CompoundTag copyCompound0(CompoundTag src) {
		CompoundTag dst = new CompoundTag();
		for (String key : src.getAllKeys()) {
			Tag child = src.get(key);
			if (child == null) {
				continue;
			}
			if (child instanceof CompoundTag) {
				dst.put(key, copyCompound0((CompoundTag) child));
			} else if (child instanceof ListTag) {
				dst.put(key, copyList0((ListTag) child));
			} else {
				dst.put(key, child);
			}
		}
		return dst;
	}

	private static ListTag copyList0(ListTag src) {
		ListTag dst = new ListTag();
		for (int i = 0; i < src.size(); i++) {
			Tag child = src.get(i);
			if (child instanceof CompoundTag) {
				dst.add(copyCompound0((CompoundTag) child));
			} else if (child instanceof ListTag) {
				dst.add(copyList0((ListTag) child));
			} else {
				dst.add(child);
			}
		}
		return dst;
	}

	public static int estimateSize(Tag tag) {
		if (tag == null) {
			return 0;
		}
		if (tag instanceof CompoundTag) {
			return ((CompoundTag) tag).size();
		}
		if (tag instanceof ListTag) {
			return ((ListTag) tag).size();
		}
		return 1;
	}
}
