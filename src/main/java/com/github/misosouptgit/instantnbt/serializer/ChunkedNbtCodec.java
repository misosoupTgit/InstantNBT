package com.github.misosouptgit.instantnbt.serializer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Chunked compound encode/decode for large tags (Project Plan 10.3).
 */
public final class ChunkedNbtCodec {
	private static final byte[] MAGIC = "ICHK".getBytes(StandardCharsets.US_ASCII);
	private static final byte VERSION = 1;

	private ChunkedNbtCodec() {}

	public static boolean isChunked(byte[] data) {
		return data != null
			&& data.length >= 5
			&& data[0] == MAGIC[0]
			&& data[1] == MAGIC[1]
			&& data[2] == MAGIC[2]
			&& data[3] == MAGIC[3];
	}

	public static byte[] encode(CompoundTag compound) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(256, compound.size() * 32));
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.write(MAGIC);
			out.writeByte(VERSION);
			out.writeInt(compound.size());
			for (String key : compound.getAllKeys()) {
				CompoundTag part = new CompoundTag();
				part.put(key, compound.get(key));
				byte[] body = BinaryNbtCodec.encodeCompound(part);
				out.writeUTF(key);
				out.writeInt(body.length);
				out.write(body);
			}
		}
		return bos.toByteArray();
	}

	public static CompoundTag decode(byte[] data) throws IOException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			byte[] magic = in.readNBytes(4);
			if (!java.util.Arrays.equals(magic, MAGIC)) {
				throw new IOException("bad chunked NBT magic");
			}
			int version = in.readUnsignedByte();
			if (version != VERSION) {
				throw new IOException("unsupported chunked NBT version " + version);
			}
			int count = in.readInt();
			CompoundTag out = new CompoundTag();
			for (int i = 0; i < count; i++) {
				String key = in.readUTF();
				int len = in.readInt();
				byte[] body = in.readNBytes(len);
				CompoundTag part = BinaryNbtCodec.decodeCompound(body);
				Tag value = part.contains(key) ? part.get(key) : part.get(part.getAllKeys().iterator().next());
				out.put(key, value);
			}
			return out;
		}
	}
}
