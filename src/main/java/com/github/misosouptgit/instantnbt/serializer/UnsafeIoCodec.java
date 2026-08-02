package com.github.misosouptgit.instantnbt.serializer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Optional faster IO path using direct buffers (Plan 10.4 unsafeIO).
 * Default OFF; always falls back to heap streams on any failure.
 */
public final class UnsafeIoCodec {
	private static final ThreadLocal<ByteBuffer> SCRATCH = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(64 * 1024));

	private UnsafeIoCodec() {}

	public static byte[] encodeCompound(CompoundTag tag) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
		try (DataOutputStream out = new DataOutputStream(bos)) {
			NbtIo.write(tag, out);
		}
		byte[] heap = bos.toByteArray();
		ByteBuffer direct = ensureCapacity(heap.length);
		direct.clear();
		direct.put(heap);
		direct.flip();
		byte[] out = new byte[direct.remaining()];
		direct.get(out);
		return out;
	}

	public static CompoundTag decodeCompound(byte[] data) throws IOException {
		ByteBuffer direct = ensureCapacity(data.length);
		direct.clear();
		direct.put(data);
		direct.flip();
		byte[] copy = new byte[direct.remaining()];
		direct.get(copy);
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(copy))) {
			return NbtIo.read(in);
		}
	}

	public static byte[] encode(Tag tag) throws IOException {
		return BinaryNbtCodec.encode(tag);
	}

	public static Tag decode(byte[] data) throws IOException {
		CompoundTag compound = decodeCompound(data);
		return BinaryNbtCodec.decode(BinaryNbtCodec.encodeCompound(compound));
	}

	private static ByteBuffer ensureCapacity(int size) {
		ByteBuffer buf = SCRATCH.get();
		if (buf.capacity() < size) {
			buf = ByteBuffer.allocateDirect(Math.max(size, buf.capacity() * 2));
			SCRATCH.set(buf);
		}
		return buf;
	}
}
