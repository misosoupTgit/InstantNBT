package com.github.misosouptgit.instantnbt.serializer;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hand-rolled binary NBT codec with thread-local buffers (Project Plan 10.2 Fast Binary).
 * Falls back to {@link BinaryNbtCodec} legacy {@code NbtIo} on any mismatch/failure.
 */
public final class FastBinaryCodec {
	private static final ThreadLocal<ByteArrayOutputStream> BUFFERS =
		ThreadLocal.withInitial(() -> new ByteArrayOutputStream(1024));
	private static final ConcurrentHashMap<String, String> KEY_INTERN = new ConcurrentHashMap<>(512);
	private static final AtomicLong HITS = new AtomicLong();
	private static final AtomicLong FALLBACKS = new AtomicLong();

	private FastBinaryCodec() {}

	public static byte[] encodeCompound(CompoundTag tag) throws IOException {
		com.github.misosouptgit.instantnbt.ownership.TagWriteHooks.enterInternal();
		try {
			ByteArrayOutputStream bos = BUFFERS.get();
			bos.reset();
			DataOutputStream out = new DataOutputStream(bos);
			writeNamedCompound(tag, out);
			out.flush();
			HITS.incrementAndGet();
			return bos.toByteArray();
		} finally {
			com.github.misosouptgit.instantnbt.ownership.TagWriteHooks.leaveInternal();
		}
	}

	/** NbtIo-compatible named root compound write (used by vanilla save paths). */
	public static void writeNamedCompound(CompoundTag tag, DataOutput out) throws IOException {
		out.writeByte(Tag.TAG_COMPOUND);
		out.writeUTF("");
		writeCompoundPayload(tag, out);
	}

	public static CompoundTag decodeCompound(byte[] data) throws IOException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			CompoundTag tag = readNamedCompound(in);
			HITS.incrementAndGet();
			return tag;
		}
	}

	/** NbtIo-compatible named root compound read. */
	public static CompoundTag readNamedCompound(DataInput in) throws IOException {
		com.github.misosouptgit.instantnbt.ownership.TagWriteHooks.enterInternal();
		try {
			byte type = in.readByte();
			if (type != Tag.TAG_COMPOUND) {
				throw new IOException("root is not compound: " + type);
			}
			in.readUTF();
			return readCompoundPayload(in);
		} finally {
			com.github.misosouptgit.instantnbt.ownership.TagWriteHooks.leaveInternal();
		}
	}

	public static void noteFallback() {
		FALLBACKS.incrementAndGet();
	}

	public static void noteHit() {
		HITS.incrementAndGet();
	}

	public static long hits() {
		return HITS.get();
	}

	public static long fallbacks() {
		return FALLBACKS.get();
	}

	private static void writeCompoundPayload(CompoundTag tag, DataOutput out) throws IOException {
		for (String key : tag.getAllKeys()) {
			Tag child = tag.get(key);
			if (child == null) {
				continue;
			}
			out.writeByte(child.getId());
			out.writeUTF(key);
			writePayload(child, out);
		}
		out.writeByte(Tag.TAG_END);
	}

	private static void writePayload(Tag tag, DataOutput out) throws IOException {
		byte id = tag.getId();
		switch (id) {
			case Tag.TAG_BYTE:
				out.writeByte(((ByteTag) tag).getAsByte());
				break;
			case Tag.TAG_SHORT:
				out.writeShort(((ShortTag) tag).getAsShort());
				break;
			case Tag.TAG_INT:
				out.writeInt(((IntTag) tag).getAsInt());
				break;
			case Tag.TAG_LONG:
				out.writeLong(((LongTag) tag).getAsLong());
				break;
			case Tag.TAG_FLOAT:
				out.writeFloat(((FloatTag) tag).getAsFloat());
				break;
			case Tag.TAG_DOUBLE:
				out.writeDouble(((DoubleTag) tag).getAsDouble());
				break;
			case Tag.TAG_BYTE_ARRAY: {
				byte[] arr = ((ByteArrayTag) tag).getAsByteArray();
				out.writeInt(arr.length);
				out.write(arr);
				break;
			}
			case Tag.TAG_STRING:
				out.writeUTF(((StringTag) tag).getAsString());
				break;
			case Tag.TAG_LIST:
				writeListPayload((ListTag) tag, out);
				break;
			case Tag.TAG_COMPOUND:
				writeCompoundPayload((CompoundTag) tag, out);
				break;
			case Tag.TAG_INT_ARRAY: {
				int[] arr = ((IntArrayTag) tag).getAsIntArray();
				out.writeInt(arr.length);
				for (int v : arr) {
					out.writeInt(v);
				}
				break;
			}
			case Tag.TAG_LONG_ARRAY: {
				long[] arr = ((LongArrayTag) tag).getAsLongArray();
				out.writeInt(arr.length);
				for (long v : arr) {
					out.writeLong(v);
				}
				break;
			}
			default:
				throw new IOException("unsupported tag id " + id);
		}
	}

	private static void writeListPayload(ListTag list, DataOutput out) throws IOException {
		byte type = list.isEmpty() ? Tag.TAG_END : list.get(0).getId();
		out.writeByte(type);
		out.writeInt(list.size());
		for (int i = 0; i < list.size(); i++) {
			writePayload(list.get(i), out);
		}
	}

	private static CompoundTag readCompoundPayload(DataInput in) throws IOException {
		CompoundTag tag = Pooling.newCompound();
		while (true) {
			byte type = in.readByte();
			if (type == Tag.TAG_END) {
				return tag;
			}
			String key = internKey(in.readUTF());
			tag.put(key, readPayload(type, in));
		}
	}

	private static Tag readPayload(byte type, DataInput in) throws IOException {
		switch (type) {
			case Tag.TAG_BYTE:
				return ByteTag.valueOf(in.readByte());
			case Tag.TAG_SHORT:
				return ShortTag.valueOf(in.readShort());
			case Tag.TAG_INT:
				return IntTag.valueOf(in.readInt());
			case Tag.TAG_LONG:
				return LongTag.valueOf(in.readLong());
			case Tag.TAG_FLOAT:
				return FloatTag.valueOf(in.readFloat());
			case Tag.TAG_DOUBLE:
				return DoubleTag.valueOf(in.readDouble());
			case Tag.TAG_BYTE_ARRAY: {
				int len = in.readInt();
				byte[] arr = new byte[len];
				in.readFully(arr);
				return new ByteArrayTag(arr);
			}
			case Tag.TAG_STRING:
				return StringTag.valueOf(internKey(in.readUTF()));
			case Tag.TAG_LIST:
				return readListPayload(in);
			case Tag.TAG_COMPOUND:
				return readCompoundPayload(in);
			case Tag.TAG_INT_ARRAY: {
				int len = in.readInt();
				int[] arr = new int[len];
				for (int i = 0; i < len; i++) {
					arr[i] = in.readInt();
				}
				return new IntArrayTag(arr);
			}
			case Tag.TAG_LONG_ARRAY: {
				int len = in.readInt();
				long[] arr = new long[len];
				for (int i = 0; i < len; i++) {
					arr[i] = in.readLong();
				}
				return new LongArrayTag(arr);
			}
			default:
				throw new IOException("unsupported tag id " + type);
		}
	}

	private static ListTag readListPayload(DataInput in) throws IOException {
		byte type = in.readByte();
		int size = in.readInt();
		ListTag list = Pooling.newList();
		for (int i = 0; i < size; i++) {
			list.add(readPayload(type, in));
		}
		return list;
	}

	private static String internKey(String key) {
		if (key == null || key.length() > 64) {
			return key;
		}
		if (KEY_INTERN.size() > 8192) {
			KEY_INTERN.clear();
		}
		String existing = KEY_INTERN.putIfAbsent(key, key);
		return existing == null ? key : existing;
	}

	/**
	 * Tiny pool bridge used by decode / structure copy to cut Compound/List churn.
	 */
	public static final class Pooling {
		private Pooling() {}

		public static CompoundTag newCompound() {
			try {
				com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime runtime =
					com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime.get();
				if (runtime.optimizationsActive() && runtime.config().poolEnabled && runtime.memory().isStarted()) {
					return runtime.memory().nbtPool().acquireCompound();
				}
			} catch (Throwable ignored) {
			}
			return new CompoundTag();
		}

		public static ListTag newList() {
			try {
				com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime runtime =
					com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime.get();
				if (runtime.optimizationsActive() && runtime.config().poolEnabled && runtime.memory().isStarted()) {
					return runtime.memory().nbtPool().acquireList();
				}
			} catch (Throwable ignored) {
			}
			return new ListTag();
		}
	}
}
