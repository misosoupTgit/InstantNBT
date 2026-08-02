package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.config.InstantNbtConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Heuristic: tiny compounds are not worth freeze/CoW; inventories and nested blobs are.
 */
public final class NbtSizeHeuristics {
	private NbtSizeHeuristics() {}

	public static boolean worthOwnership(CompoundTag tag, InstantNbtConfig config) {
		if (tag == null) {
			return false;
		}
		int minKeys = Math.max(1, config.minFreezeKeys);
		if (tag.size() >= minKeys) {
			return true;
		}
		Tag items = tag.get("Items");
		if (items instanceof ListTag && ((ListTag) items).size() >= 9) {
			return true;
		}
		for (String key : tag.getAllKeys()) {
			Tag child = tag.get(key);
			if (child instanceof ListTag && ((ListTag) child).size() >= 9) {
				return true;
			}
			if (child instanceof CompoundTag && ((CompoundTag) child).size() >= minKeys) {
				return true;
			}
		}
		return false;
	}
}
