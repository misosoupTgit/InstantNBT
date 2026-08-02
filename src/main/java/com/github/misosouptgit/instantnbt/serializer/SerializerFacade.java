package com.github.misosouptgit.instantnbt.serializer;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.ownership.ModuleDomain;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.Owner;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import com.github.misosouptgit.instantnbt.runtime.SafetyCoordinator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serializer facade with soft guards + optional unsafeIO (Project Plan 10 / 18.2).
 */
public final class SerializerFacade {
	private volatile boolean fastCodec = true;
	private volatile boolean legacyFallback = true;
	private volatile boolean forceLegacy;
	private volatile boolean lazyDeserialize;
	private volatile boolean unsafeIO;
	private volatile int chunkEncodeThreshold = 64 * 1024;
	private volatile ValidationGuard guard = ValidationGuard.defaults();
	private final AtomicInteger consecutiveFailures = new AtomicInteger();
	private final AtomicInteger encodeCount = new AtomicInteger();
	private final AtomicInteger decodeCount = new AtomicInteger();
	private final AtomicInteger legacyRetries = new AtomicInteger();
	private final AtomicInteger guardSkips = new AtomicInteger();
	private final AtomicInteger guardRejections = new AtomicInteger();
	private final AtomicInteger chunkEncodes = new AtomicInteger();
	private final AtomicInteger lazyDecodes = new AtomicInteger();
	private final AtomicInteger snbtFastHits = new AtomicInteger();
	private final AtomicInteger snbtFallbacks = new AtomicInteger();
	private final AtomicInteger unsafeIoHits = new AtomicInteger();

	public void configure(boolean fastCodec, boolean legacyFallback, boolean forceLegacy) {
		this.fastCodec = fastCodec;
		this.legacyFallback = legacyFallback;
		this.forceLegacy = forceLegacy;
	}

	public void configureLazy(boolean lazyDeserialize, int chunkEncodeThresholdBytes) {
		this.lazyDeserialize = lazyDeserialize;
		this.chunkEncodeThreshold = Math.max(0, chunkEncodeThresholdBytes);
	}

	public void configureUnsafeIo(boolean unsafeIO) {
		this.unsafeIO = unsafeIO;
	}

	public void setGuard(ValidationGuard guard) {
		this.guard = guard == null ? ValidationGuard.defaults() : guard;
	}

	public CodecPath preferredPath() {
		return forceLegacy || !fastCodec ? CodecPath.LEGACY : CodecPath.FAST_BINARY;
	}

	public String encodeSnbt(Tag tag) throws IOException {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		GuardVerdict verdict = guard.checkTag(tag, false);
		if (verdict == GuardVerdict.SKIP_FAST_PATH) {
			guardSkips.incrementAndGet();
			encodeCount.incrementAndGet();
			return SnbtCodec.encode(tag);
		}
		encodeCount.incrementAndGet();
		return SnbtCodec.encode(VersionAdapter.adaptForEncode(tag));
	}

	public Tag decodeSnbt(String snbt) throws IOException {
		decodeCount.incrementAndGet();
		try {
			Tag tag = SnbtFastParser.parse(snbt == null ? "" : snbt.trim());
			snbtFastHits.incrementAndGet();
			if (guard.checkTag(tag, false) == GuardVerdict.SKIP_FAST_PATH) {
				guardSkips.incrementAndGet();
			}
			consecutiveFailures.set(0);
			return VersionAdapter.adaptAfterDecode(tag);
		} catch (Exception fastFail) {
			if (!legacyFallback) {
				noteSoftFailure(fastFail);
				throw new IOException(fastFail);
			}
			snbtFallbacks.incrementAndGet();
			legacyRetries.incrementAndGet();
			try {
				Tag tag = SnbtCodec.decodeLegacy(snbt == null ? "" : snbt.trim());
				consecutiveFailures.set(0);
				return VersionAdapter.adaptAfterDecode(tag);
			} catch (Exception legacy) {
				noteSoftFailure(legacy);
				throw new IOException(legacy);
			}
		}
	}

	public byte[] encodeChunked(Tag tag, int maxChunkBytes) throws IOException {
		if (!(tag instanceof CompoundTag)) {
			return encode(tag);
		}
		if (guard.checkTag(tag, false) == GuardVerdict.SKIP_FAST_PATH) {
			guardSkips.incrementAndGet();
			return encodeBinary((CompoundTag) tag);
		}
		byte[] data = ChunkedNbtCodec.encode((CompoundTag) VersionAdapter.adaptForEncode(tag));
		chunkEncodes.incrementAndGet();
		encodeCount.incrementAndGet();
		return data;
	}

	public Tag decodeChunked(byte[] data) throws IOException {
		decodeCount.incrementAndGet();
		CompoundTag tag = ChunkedNbtCodec.decode(data);
		return VersionAdapter.adaptAfterDecode(tag);
	}

	public byte[] encode(OwnedTag owned) throws IOException {
		return encode(owned == null ? null : owned.payload());
	}

	public byte[] encode(Tag tag) throws IOException {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		GuardVerdict verdict = guard.checkTag(tag, false);
		if (verdict == GuardVerdict.SKIP_FAST_PATH) {
			guardSkips.incrementAndGet();
			encodeCount.incrementAndGet();
			safety().report(SafetyCoordinator.Severity.SOFT, "serializer-size", "skip fast path on large tag");
			return encodeBinary(tag);
		}
		Tag adapted = VersionAdapter.adaptForEncode(tag);
		encodeCount.incrementAndGet();
		try {
			byte[] data = encodeBinary(adapted);
			if (chunkEncodeThreshold > 0
				&& data.length >= chunkEncodeThreshold
				&& adapted instanceof CompoundTag
				&& !forceLegacy) {
				byte[] chunked = ChunkedNbtCodec.encode((CompoundTag) adapted);
				chunkEncodes.incrementAndGet();
				return chunked;
			}
			return data;
		} catch (IOException primary) {
			if (!legacyFallback || forceLegacy) {
				noteSoftFailure(primary);
				throw primary;
			}
			legacyRetries.incrementAndGet();
			try {
				return BinaryNbtCodec.encode(adapted);
			} catch (IOException legacy) {
				noteSoftFailure(legacy);
				throw legacy;
			}
		}
	}

	public OwnedTag decodeOwned(byte[] data) throws IOException {
		if (lazyDeserialize) {
			lazyDecodes.incrementAndGet();
			decodeCount.incrementAndGet();
			OwnedTag deferred = OwnedTag.deferred(data.clone());
			deferred.promote(Owner.current(ModuleDomain.SERIALIZER));
			return deferred;
		}
		Tag tag = decode(data);
		return OwnedTag.owned(tag, Owner.current(ModuleDomain.SERIALIZER));
	}

	public Tag decode(byte[] data) throws IOException {
		if (data == null) {
			throw new IllegalArgumentException("data");
		}
		GuardVerdict sizeVerdict = guard.checkEncodedSize(data, false);
		if (sizeVerdict == GuardVerdict.SKIP_FAST_PATH) {
			guardSkips.incrementAndGet();
			safety().report(SafetyCoordinator.Severity.SOFT, "serializer-size", "skip fast path on large payload");
		}
		decodeCount.incrementAndGet();
		try {
			Tag tag;
			if (ChunkedNbtCodec.isChunked(data)) {
				tag = ChunkedNbtCodec.decode(data);
			} else {
				tag = decodeBinary(data);
			}
			consecutiveFailures.set(0);
			return VersionAdapter.adaptAfterDecode(tag);
		} catch (IOException primary) {
			if (!legacyFallback) {
				noteSoftFailure(primary);
				throw primary;
			}
			legacyRetries.incrementAndGet();
			try {
				Tag tag = BinaryNbtCodec.decode(data);
				consecutiveFailures.set(0);
				return VersionAdapter.adaptAfterDecode(tag);
			} catch (IOException legacy) {
				noteSoftFailure(legacy);
				throw legacy;
			}
		}
	}

	/**
	 * Validate InstantNBT-owned network packets. Reject only those — never vanilla world NBT.
	 */
	public GuardVerdict checkOwnedPacket(byte[] data, Tag tag) {
		GuardVerdict size = guard.checkEncodedSize(data, true);
		if (size != GuardVerdict.OK) {
			guardRejections.incrementAndGet();
			safety().report(SafetyCoordinator.Severity.CRITICAL, "owned-packet", "owned packet size rejected");
			return size;
		}
		GuardVerdict body = guard.checkTag(tag, true);
		if (body != GuardVerdict.OK) {
			guardRejections.incrementAndGet();
			safety().report(SafetyCoordinator.Severity.CRITICAL, "owned-packet", "owned packet structure rejected");
			return body;
		}
		return GuardVerdict.OK;
	}

	private byte[] encodeBinary(Tag tag) throws IOException {
		if (unsafeIO && tag instanceof CompoundTag) {
			try {
				unsafeIoHits.incrementAndGet();
				return UnsafeIoCodec.encodeCompound((CompoundTag) tag);
			} catch (Throwable ex) {
				safety().report(SafetyCoordinator.Severity.FEATURE, "unsafe-io", ex.toString());
				configureUnsafeIo(false);
			}
		}
		return BinaryNbtCodec.encode(tag);
	}

	private Tag decodeBinary(byte[] data) throws IOException {
		if (unsafeIO) {
			try {
				unsafeIoHits.incrementAndGet();
				return UnsafeIoCodec.decodeCompound(data);
			} catch (Throwable ex) {
				safety().report(SafetyCoordinator.Severity.FEATURE, "unsafe-io", ex.toString());
				configureUnsafeIo(false);
			}
		}
		return BinaryNbtCodec.decode(data);
	}

	private void noteSoftFailure(Exception error) {
		int failures = consecutiveFailures.incrementAndGet();
		InstantNBT.LOGGER.warn("Serializer soft-failure #{}: {}", failures, error.toString());
		if (failures >= 8) {
			safety().report(SafetyCoordinator.Severity.FEATURE, "serializer", "consecutive soft failures");
			consecutiveFailures.set(0);
		} else if (failures >= 3) {
			safety().report(SafetyCoordinator.Severity.SOFT, "serializer", "repeated soft failures");
		}
	}

	private static SafetyCoordinator safety() {
		return InstantNbtRuntime.get().safety();
	}

	public int encodeCount() {
		return encodeCount.get();
	}

	public int decodeCount() {
		return decodeCount.get();
	}

	public int legacyRetries() {
		return legacyRetries.get();
	}

	public int guardRejections() {
		return guardRejections.get();
	}

	public int guardSkips() {
		return guardSkips.get();
	}

	public int chunkEncodes() {
		return chunkEncodes.get();
	}

	public int lazyDecodes() {
		return lazyDecodes.get();
	}

	public int snbtFastHits() {
		return snbtFastHits.get();
	}

	public int snbtFallbacks() {
		return snbtFallbacks.get();
	}

	public int unsafeIoHits() {
		return unsafeIoHits.get();
	}
}
