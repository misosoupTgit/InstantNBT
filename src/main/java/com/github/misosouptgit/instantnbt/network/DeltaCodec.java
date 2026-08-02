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

/**
 * Generation-aware delta / full codec (Project Plan 11.2–11.4).
 */
public final class DeltaCodec {
	private static final byte[] MAGIC = "INBT".getBytes(StandardCharsets.US_ASCII);
	private static final byte VERSION = 1;
	private static final byte MODE_FULL = 0;
	private static final byte MODE_DELTA = 1;

	private final ValidationGuard guard;

	public DeltaCodec(ValidationGuard guard) {
		this.guard = guard == null ? ValidationGuard.defaults() : guard;
	}

	public byte[] encodeFull(OwnedTag tag) throws IOException {
		CompoundTag compound = asCompound(tag.payload());
		guard.validateTag(compound);
		ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.write(MAGIC);
			out.writeByte(VERSION);
			out.writeByte(MODE_FULL);
			out.writeLong(tag.generation());
			out.writeLong(tag.generation());
			byte[] body = BinaryNbtCodec.encodeCompound(compound);
			out.writeInt(body.length);
			out.write(body);
		}
		byte[] data = bos.toByteArray();
		guard.validateEncodedSize(data);
		return data;
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
		// Large churn -> full sync is cheaper / safer.
		if (removed.size() + changed.size() > Math.max(8, keys.size() / 2)) {
			return encodeFull(current);
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.write(MAGIC);
			out.writeByte(VERSION);
			out.writeByte(MODE_DELTA);
			out.writeLong(previous.generation());
			out.writeLong(current.generation());
			out.writeInt(removed.size());
			for (String key : removed) {
				out.writeUTF(key);
			}
			out.writeInt(changed.size());
			for (String key : changed) {
				out.writeUTF(key);
				byte[] body = BinaryNbtCodec.encode(after.get(key));
				out.writeInt(body.length);
				out.write(body);
			}
		}
		byte[] data = bos.toByteArray();
		guard.validateEncodedSize(data);
		return data;
	}

	public ApplyResult apply(CompoundTag base, byte[] packet, long expectedBaseGeneration) throws IOException {
		if (packet == null || packet.length == 0) {
			return ApplyResult.noop(base);
		}
		guard.validateEncodedSize(packet);
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet))) {
			byte[] magic = in.readNBytes(4);
			if (!java.util.Arrays.equals(magic, MAGIC)) {
				throw new IOException("bad InstantNBT packet magic");
			}
			int version = in.readUnsignedByte();
			if (version != VERSION) {
				throw new IOException("unsupported packet version " + version);
			}
			int mode = in.readUnsignedByte();
			long baseGen = in.readLong();
			long newGen = in.readLong();
			if (expectedBaseGeneration >= 0 && baseGen != expectedBaseGeneration && mode == MODE_DELTA) {
				throw new IOException("generation mismatch base=" + baseGen + " expected=" + expectedBaseGeneration);
			}
			if (mode == MODE_FULL) {
				int len = in.readInt();
				byte[] body = in.readNBytes(len);
				CompoundTag decoded = BinaryNbtCodec.decodeCompound(body);
				guard.validateTag(decoded);
				return new ApplyResult(decoded, newGen, SyncMode.FULL, false);
			}
			if (mode != MODE_DELTA) {
				throw new IOException("unknown sync mode " + mode);
			}
			CompoundTag target = base == null ? new CompoundTag() : base.copy();
			int removedCount = in.readInt();
			for (int i = 0; i < removedCount; i++) {
				target.remove(in.readUTF());
			}
			int changedCount = in.readInt();
			for (int i = 0; i < changedCount; i++) {
				String key = in.readUTF();
				int len = in.readInt();
				byte[] body = in.readNBytes(len);
				Tag value = BinaryNbtCodec.decode(body);
				target.put(key, value);
			}
			guard.validateTag(target);
			return new ApplyResult(target, newGen, SyncMode.DELTA, false);
		}
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
			return new ApplyResult(tag, tag == null ? 0L : 0L, SyncMode.DELTA, true);
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
