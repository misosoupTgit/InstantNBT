package com.github.misosouptgit.instantnbt.serializer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Version / format adapter hook (Project Plan 10.2).
 * Currently identity; overlays can specialize per MC version later.
 */
public final class VersionAdapter {
	private VersionAdapter() {}

	public static Tag adaptForEncode(Tag tag) {
		return tag;
	}

	public static Tag adaptAfterDecode(Tag tag) {
		return tag;
	}

	public static CompoundTag asCompound(Tag tag) {
		if (tag instanceof CompoundTag) {
			return (CompoundTag) tag;
		}
		CompoundTag wrap = new CompoundTag();
		if (tag != null) {
			wrap.put("value", tag);
		}
		return wrap;
	}
}
