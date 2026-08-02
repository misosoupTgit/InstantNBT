package com.github.misosouptgit.instantnbt.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Optional payload compression for InstantNBT sync packets (Plan 11 / 19.1 light).
 */
public final class PacketCompressor {
	public static final int DEFAULT_THRESHOLD = 512;

	private PacketCompressor() {}

	public static byte[] maybeCompress(byte[] raw, int threshold) throws IOException {
		if (raw == null || raw.length < threshold) {
			return null;
		}
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try {
			deflater.setInput(raw);
			deflater.finish();
			ByteArrayOutputStream bos = new ByteArrayOutputStream(raw.length / 2 + 16);
			byte[] buf = new byte[1024];
			while (!deflater.finished()) {
				int n = deflater.deflate(buf);
				bos.write(buf, 0, n);
			}
			byte[] compressed = bos.toByteArray();
			if (compressed.length >= raw.length) {
				return null;
			}
			return compressed;
		} finally {
			deflater.end();
		}
	}

	public static byte[] decompress(byte[] compressed, int expectedRawLen) throws IOException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(compressed);
			byte[] out = new byte[Math.max(expectedRawLen, compressed.length * 2)];
			ByteArrayOutputStream bos = new ByteArrayOutputStream(out.length);
			byte[] buf = new byte[1024];
			while (!inflater.finished()) {
				int n = inflater.inflate(buf);
				if (n == 0 && inflater.needsInput()) {
					break;
				}
				bos.write(buf, 0, n);
			}
			return bos.toByteArray();
		} catch (Exception ex) {
			throw new IOException("decompress failed", ex);
		} finally {
			inflater.end();
		}
	}
}
