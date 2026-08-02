package com.github.misosouptgit.instantnbt.serializer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

/**
 * SNBT encode/decode with Fast Parser + vanilla fallback (Project Plan 10.2).
 */
public final class SnbtCodec {
	private SnbtCodec() {}

	public static String encode(Tag tag) {
		return tag == null ? "{}" : tag.getAsString();
	}

	public static Tag decode(String snbt) throws Exception {
		if (snbt == null || snbt.isBlank()) {
			return new CompoundTag();
		}
		String trimmed = snbt.trim();
		try {
			return SnbtFastParser.parse(trimmed);
		} catch (SnbtFastParser.SnbtParseException ignored) {
			return decodeLegacy(trimmed);
		}
	}

	public static Tag decodeLegacy(String snbt) throws Exception {
		//? if >=1.21.6 {
		/*return TagParser.parseCompoundFully(snbt);
		*///?} else {
		return TagParser.parseTag(snbt);
		//?}
	}

	public static CompoundTag decodeCompound(String snbt) throws Exception {
		Tag tag = decode(snbt);
		if (tag instanceof CompoundTag) {
			return (CompoundTag) tag;
		}
		CompoundTag wrap = new CompoundTag();
		wrap.put("value", tag);
		return wrap;
	}
}
