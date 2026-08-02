package com.github.misosouptgit.instantnbt.serializer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Binary NBT codecs via CompoundTag (stable across 1.16.5–26.x).
 */
public final class BinaryNbtCodec {
	private static final String WRAP_KEY = "_i";

	private BinaryNbtCodec() {}

	public static byte[] encode(Tag tag) throws IOException {
		if (tag instanceof CompoundTag) {
			return encodeCompound((CompoundTag) tag);
		}
		CompoundTag wrap = new CompoundTag();
		wrap.put(WRAP_KEY, tag);
		return encodeCompound(wrap);
	}

	public static Tag decode(byte[] data) throws IOException {
		CompoundTag compound = decodeCompound(data);
		if (compound.contains(WRAP_KEY) && compound.size() == 1) {
			return compound.get(WRAP_KEY);
		}
		return compound;
	}

	public static byte[] encodeCompound(CompoundTag tag) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
		try (DataOutputStream out = new DataOutputStream(bos)) {
			NbtIo.write(tag, out);
		}
		return bos.toByteArray();
	}

	public static CompoundTag decodeCompound(byte[] data) throws IOException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			return NbtIo.read(in);
		}
	}
}
