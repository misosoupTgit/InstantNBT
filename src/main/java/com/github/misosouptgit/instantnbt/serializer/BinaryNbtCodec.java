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
 * Binary NBT codecs — FastBinary first, NbtIo legacy fallback (Project Plan 10).
 */
public final class BinaryNbtCodec {
	private static final String WRAP_KEY = "_i";
	private static final ThreadLocal<ByteArrayOutputStream> LEGACY_BUF =
		ThreadLocal.withInitial(() -> new ByteArrayOutputStream(512));

	private BinaryNbtCodec() {}

	public static byte[] encode(Tag tag) throws IOException {
		if (tag instanceof CompoundTag) {
			return encodeCompound((CompoundTag) tag);
		}
		CompoundTag wrap = FastBinaryCodec.Pooling.newCompound();
		wrap.put(WRAP_KEY, tag);
		try {
			return encodeCompound(wrap);
		} finally {
			wrap.remove(WRAP_KEY);
		}
	}

	public static Tag decode(byte[] data) throws IOException {
		CompoundTag compound = decodeCompound(data);
		if (compound.contains(WRAP_KEY) && compound.size() == 1) {
			return compound.get(WRAP_KEY);
		}
		return compound;
	}

	public static byte[] encodeCompound(CompoundTag tag) throws IOException {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		try {
			return FastBinaryCodec.encodeCompound(tag);
		} catch (Throwable fastFail) {
			FastBinaryCodec.noteFallback();
			return encodeLegacy(tag);
		}
	}

	public static CompoundTag decodeCompound(byte[] data) throws IOException {
		if (data == null) {
			throw new IllegalArgumentException("data");
		}
		try {
			return FastBinaryCodec.decodeCompound(data);
		} catch (Throwable fastFail) {
			FastBinaryCodec.noteFallback();
			return decodeLegacy(data);
		}
	}

	private static byte[] encodeLegacy(CompoundTag tag) throws IOException {
		ByteArrayOutputStream bos = LEGACY_BUF.get();
		bos.reset();
		DataOutputStream out = new DataOutputStream(bos);
		NbtIo.write(tag, out);
		out.flush();
		return bos.toByteArray();
	}

	private static CompoundTag decodeLegacy(byte[] data) throws IOException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			return NbtIo.read(in);
		}
	}
}
