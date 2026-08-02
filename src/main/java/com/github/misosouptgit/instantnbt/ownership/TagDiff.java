package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;

/**
 * Structural NBT diff / merge helpers (Project Plan 14.4).
 */
public final class TagDiff {
	public static final String REMOVED = "_removed";
	public static final String CHANGED = "_changed";

	private TagDiff() {}

	public static CompoundTag diff(Tag oldTag, Tag newTag) {
		CompoundTag before = asCompound(oldTag);
		CompoundTag after = asCompound(newTag);
		ListTag removed = new ListTag();
		CompoundTag changed = new CompoundTag();

		Set<String> keys = new HashSet<>();
		keys.addAll(before.getAllKeys());
		keys.addAll(after.getAllKeys());
		for (String key : keys) {
			boolean hasBefore = before.contains(key);
			boolean hasAfter = after.contains(key);
			if (hasBefore && !hasAfter) {
				removed.add(StringTag.valueOf(key));
			} else if (!hasBefore && hasAfter) {
				changed.put(key, after.get(key).copy());
			} else if (hasBefore && hasAfter) {
				Tag a = before.get(key);
				Tag b = after.get(key);
				if (a instanceof CompoundTag && b instanceof CompoundTag) {
					CompoundTag nested = diff(a, b);
					if (!nested.isEmpty()) {
						changed.put(key, nested);
					}
				} else if (a == null || b == null || !a.equals(b)) {
					changed.put(key, b.copy());
				}
			}
		}

		CompoundTag out = new CompoundTag();
		if (!removed.isEmpty()) {
			out.put(REMOVED, removed);
		}
		if (!changed.isEmpty()) {
			out.put(CHANGED, changed);
		}
		return out;
	}

	public static CompoundTag merge(Tag base, Tag deltaTag) {
		CompoundTag result = asCompound(base).copy();
		CompoundTag delta = asCompound(deltaTag);
		if (delta.contains(REMOVED)) {
			Tag removedTag = delta.get(REMOVED);
			if (removedTag instanceof ListTag) {
				ListTag removed = (ListTag) removedTag;
				for (int i = 0; i < removed.size(); i++) {
					result.remove(removed.getString(i));
				}
			}
		}
		if (delta.contains(CHANGED)) {
			Tag changedTag = delta.get(CHANGED);
			if (changedTag instanceof CompoundTag) {
				CompoundTag changed = (CompoundTag) changedTag;
				for (String key : changed.getAllKeys()) {
					Tag value = changed.get(key);
					if (value instanceof CompoundTag && result.get(key) instanceof CompoundTag) {
						CompoundTag nested = (CompoundTag) value;
						if (nested.contains(CHANGED) || nested.contains(REMOVED)) {
							result.put(key, merge(result.get(key), value));
							continue;
						}
					}
					result.put(key, value.copy());
				}
			}
		} else {
			for (String key : delta.getAllKeys()) {
				if (REMOVED.equals(key) || CHANGED.equals(key)) {
					continue;
				}
				result.put(key, delta.get(key).copy());
			}
		}
		return result;
	}

	public static String trace(OwnedTag tag) {
		if (tag == null) {
			return "null";
		}
		OwnedMeta meta = tag.meta();
		StringBuilder sb = new StringBuilder();
		sb.append("OwnedTag{hasMeta=").append(tag.hasMeta());
		sb.append(", pinned=").append(tag.isPinned());
		sb.append(", generation=").append(tag.generation());
		sb.append(", dirty=").append(tag.dirty());
		if (meta != null) {
			sb.append(", state=").append(meta.state());
			sb.append(", refCount=").append(meta.refCount());
			sb.append(", immutable=").append(meta.immutable());
			sb.append(", owner=").append(meta.owner());
		}
		sb.append(", payloadType=").append(tag.payload().getClass().getSimpleName());
		sb.append(", size=").append(CowEngine.estimateSize(tag.payload()));
		sb.append('}');
		return sb.toString();
	}

	private static CompoundTag asCompound(Tag tag) {
		if (tag instanceof CompoundTag) {
			return (CompoundTag) tag;
		}
		CompoundTag wrap = new CompoundTag();
		if (tag != null) {
			wrap.put("value", tag.copy());
		}
		return wrap;
	}
}
