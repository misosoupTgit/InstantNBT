package com.github.misosouptgit.instantnbt.serializer;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges FastBinaryCodec into vanilla {@code NbtIo} hot paths (region/player/structure IO).
 * Write buffers fully before touching the sink so failure never half-writes.
 * Read has no mid-stream fallback (stream is consumed).
 */
public final class NbtIoHooks {
	private static final AtomicLong WRITES = new AtomicLong();
	private static final AtomicLong READS = new AtomicLong();
	private static final AtomicLong FALLBACKS = new AtomicLong();

	private NbtIoHooks() {}

	public static boolean enabled() {
		try {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			return runtime.optimizationsActive()
				&& runtime.config().fastCodec
				&& runtime.config().nbtIoRedirect;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean tryWrite(CompoundTag tag, DataOutput output) {
		if (!enabled() || tag == null || output == null) {
			return false;
		}
		try {
			byte[] data = FastBinaryCodec.encodeCompound(tag);
			output.write(data, 0, data.length);
			WRITES.incrementAndGet();
			return true;
		} catch (Throwable ex) {
			FastBinaryCodec.noteFallback();
			FALLBACKS.incrementAndGet();
			return false;
		}
	}

	/**
	 * @return decoded tag when fast path is enabled; {@code null} when disabled
	 */
	public static CompoundTag tryRead(DataInput input) {
		if (!enabled() || input == null) {
			return null;
		}
		try {
			CompoundTag tag = FastBinaryCodec.readNamedCompound(input);
			READS.incrementAndGet();
			FastBinaryCodec.noteHit();
			return tag;
		} catch (Throwable ex) {
			FastBinaryCodec.noteFallback();
			FALLBACKS.incrementAndGet();
			throw new IllegalStateException("InstantNBT FastBinary NbtIo read failed", ex);
		}
	}

	public static long writes() {
		return WRITES.get();
	}

	public static long reads() {
		return READS.get();
	}

	public static long fallbacks() {
		return FALLBACKS.get();
	}
}
