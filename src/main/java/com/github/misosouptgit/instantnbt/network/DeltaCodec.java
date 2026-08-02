package com.github.misosouptgit.instantnbt.network;

import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.serializer.BinaryNbtCodec;
import com.github.misosouptgit.instantnbt.serializer.ValidationGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generation-aware delta / full codec with optional deflate (Project Plan 11.2–11.4).
 */
public final class DeltaCodec {
	private static final byte[] MAGIC = "INBT".getBytes(StandardCharsets.US_ASCII);
	private static final byte VERSION = 2;
	private static final byte VERSION_LEGACY = 1;
	private static final byte MODE_FULL = 0;
	private static final byte MODE_DELTA = 1;

	private final ValidationGuard guard;
	private final AtomicInteger compressions = new AtomicInteger();

	public DeltaCodec(ValidationGuard guard) {
		this.guard = guard == null ? ValidationGuard.defaults() : guard;
	}

	public static boolean isFullPacket(byte[] packet) {
		return packet != null && packet.length > 6 && packet[5] == MODE_FULL && looksLikeMagic(packet);
	}

	public static boolean isDeltaPacket(byte[] packet) {
		return packet != null && packet.length > 6 && packet[5] == MODE_DELTA && looksLikeMagic(packet);
	}

	private static boolean looksLikeMagic(byte[] packet) {
		return packet[0] == MAGIC[0] && packet[1] == MAGIC[1] && packet[2] == MAGIC[2] && packet[3] == MAGIC[3];
	}

	public byte[] encodeFull(OwnedTag tag) throws IOException {
		CompoundTag compound = asCompound(tag.payload());
		ByteArrayOutputStream rest = new ByteArrayOutputStream(256);
		try (DataOutputStream body = new DataOutputStream(rest)) {
			byte[] nbt = BinaryNbtCodec.encodeCompound(compound);
			body.writeInt(nbt.length);
			body.write(nbt);
		}
		return wrap(MODE_FULL, tag.generation(), tag.generation(), rest.toByteArray());
	}

	public byte[] encodeDelta(OwnedTag previous, OwnedTag current) throws IOException {
		if (previous == null || current == null) {
			return encodeFull(current);
		}
		if (!current.dirty() || current.generation() == previous.generation()) {
			return new byte[0];
		}
		CompoundTag before = asCompound(previous.payload());
		CompoundTag after = asCompound(current.payload());
		List<String> removed = new ArrayList<>();
		List<String> changed = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		keys.addAll(before.getAllKeys());
		keys.addAll(after.getAllKeys());
		for (String key : keys) {
			boolean hasBefore = before.contains(key);
			boolean hasAfter = after.contains(key);
			if (hasBefore && !hasAfter) {
				removed.add(key);
			} else if (!hasBefore && hasAfter) {
				changed.add(key);
			} else if (hasBefore && hasAfter) {
				Tag a = before.get(key);
				Tag b = after.get(key);
				if (a == null || b == null || !a.equals(b)) {
					changed.add(key);
				}
			}
		}
		if (removed.isEmpty() && changed.isEmpty()) {
			return new byte[0];
		}
		if (removed.size() + changed.size() > Math.max(8, keys.size() / 2)) {
			return encodeFull(current);
		}

		ByteArrayOutputStream rest = new ByteArrayOutputStream(256);
		try (DataOutputStream body = new DataOutputStream(rest)) {
			body.writeInt(removed.size());
			for (String key : removed) {
				body.writeUTF(key);
			}
			body.writeInt(changed.size());
			for (String key : changed) {
				body.writeUTF(key);
				byte[] nbt = BinaryNbtCodec.encode(after.get(key));
				body.writeInt(nbt.length);
				body.write(nbt);
			}
		}
		return wrap(MODE_DELTA, previous.generation(), current.generation(), rest.toByteArray());
	}

	private byte[] wrap(byte mode, long baseGen, long newGen, byte[] rest) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(rest.length + 32);
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.write(MAGIC);
			out.writeByte(VERSION);
			out.writeByte(mode);
			out.writeLong(baseGen);
			out.writeLong(newGen);
			byte[] compressed = PacketCompressor.maybeCompress(rest, PacketCompressor.DEFAULT_THRESHOLD);
			if (compressed != null) {
				out.writeBoolean(true);
				out.writeInt(rest.length);
				out.writeInt(compressed.length);
				out.write(compressed);
				compressions.incrementAndGet();
			} else {
				out.writeBoolean(false);
				out.writeInt(rest.length);
				out.write(rest);
			}
		}
		return bos.toByteArray();
	}

	public ApplyResult apply(CompoundTag base, byte[] packet, long expectedBaseGeneration) throws IOException {
		if (packet == null || packet.length == 0) {
			return ApplyResult.noop(base);
		}
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet))) {
			byte[] magic = in.readNBytes(4);
			if (!java.util.Arrays.equals(magic, MAGIC)) {
				throw new IOException("bad InstantNBT packet magic");
			}
			int version = in.readUnsignedByte();
			int mode = in.readUnsignedByte();
			long baseGen = in.readLong();
			long newGen = in.readLong();
			if (expectedBaseGeneration >= 0 && baseGen != expectedBaseGeneration && mode == MODE_DELTA) {
				throw new IOException("generation mismatch base=" + baseGen + " expected=" + expectedBaseGeneration);
			}
			byte[] rest;
			if (version == VERSION) {
				rest = readRestV2(in);
			} else if (version == VERSION_LEGACY) {
				rest = readRestV1(in, mode);
			} else {
				throw new IOException("unsupported packet version " + version);
			}
			try (DataInputStream body = new DataInputStream(new ByteArrayInputStream(rest))) {
				if (mode == MODE_FULL) {
					int len = body.readInt();
					byte[] nbt = body.readNBytes(len);
					CompoundTag decoded = BinaryNbtCodec.decodeCompound(nbt);
					return new ApplyResult(decoded, newGen, SyncMode.FULL, false);
				}
				if (mode != MODE_DELTA) {
					throw new IOException("unknown sync mode " + mode);
				}
				CompoundTag target = base == null ? new CompoundTag() : base.copy();
				int removedCount = body.readInt();
				for (int i = 0; i < removedCount; i++) {
					target.remove(body.readUTF());
				}
				int changedCount = body.readInt();
				for (int i = 0; i < changedCount; i++) {
					String key = body.readUTF();
					int len = body.readInt();
					byte[] nbt = body.readNBytes(len);
					target.put(key, BinaryNbtCodec.decode(nbt));
				}
				return new ApplyResult(target, newGen, SyncMode.DELTA, false);
			}
		}
	}

	private static byte[] readRestV2(DataInputStream in) throws IOException {
		boolean compressed = in.readBoolean();
		if (compressed) {
			int rawLen = in.readInt();
			int len = in.readInt();
			return PacketCompressor.decompress(in.readNBytes(len), rawLen);
		}
		int len = in.readInt();
		return in.readNBytes(len);
	}

	private static byte[] readRestV1(DataInputStream in, int mode) throws IOException {
		// Legacy: body fields follow immediately (full: int+bytes, delta: lists).
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		int n;
		while ((n = in.read(buf)) > 0) {
			bos.write(buf, 0, n);
		}
		return bos.toByteArray();
	}

	private static CompoundTag asCompound(Tag tag) {
		if (tag instanceof CompoundTag) {
			return (CompoundTag) tag;
		}
		CompoundTag wrap = new CompoundTag();
		if (tag != null) {
			wrap.put("value", tag);
		}
		return wrap;
	}

	public int compressions() {
		return compressions.get();
	}

	public static final class ApplyResult {
		private final CompoundTag tag;
		private final long generation;
		private final SyncMode mode;
		private final boolean noop;

		ApplyResult(CompoundTag tag, long generation, SyncMode mode, boolean noop) {
			this.tag = tag;
			this.generation = generation;
			this.mode = mode;
			this.noop = noop;
		}

		static ApplyResult noop(CompoundTag tag) {
			return new ApplyResult(tag, 0L, SyncMode.DELTA, true);
		}

		public CompoundTag tag() {
			return tag;
		}

		public long generation() {
			return generation;
		}

		public SyncMode mode() {
			return mode;
		}

		public boolean noop() {
			return noop;
		}
	}
}
