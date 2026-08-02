package com.github.misosouptgit.instantnbt.serializer;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.ownership.ModuleDomain;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.Owner;
import com.github.misosouptgit.instantnbt.runtime.DegradedMode;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serializer facade with fast + legacy fallback + ValidationGuard (Project Plan 10 / 18.2).
 */
public final class SerializerFacade {
	private volatile boolean fastCodec = true;
	private volatile boolean legacyFallback = true;
	private volatile boolean forceLegacy;
	private volatile boolean lazyDeserialize;
	private volatile int chunkEncodeThreshold = 64 * 1024;
	private volatile ValidationGuard guard = ValidationGuard.defaults();
	private final AtomicInteger consecutiveFailures = new AtomicInteger();
	private final AtomicInteger encodeCount = new AtomicInteger();
	private final AtomicInteger decodeCount = new AtomicInteger();
	private final AtomicInteger legacyRetries = new AtomicInteger();
	private final AtomicInteger guardRejections = new AtomicInteger();
	private final AtomicInteger chunkEncodes = new AtomicInteger();
	private final AtomicInteger lazyDecodes = new AtomicInteger();
	private final AtomicInteger snbtFastHits = new AtomicInteger();
	private final AtomicInteger snbtFallbacks = new AtomicInteger();

	public void configure(boolean fastCodec, boolean legacyFallback, boolean forceLegacy) {
		this.fastCodec = fastCodec;
		this.legacyFallback = legacyFallback;
		this.forceLegacy = forceLegacy;
	}

	public void configureLazy(boolean lazyDeserialize, int chunkEncodeThresholdBytes) {
		this.lazyDeserialize = lazyDeserialize;
		this.chunkEncodeThreshold = Math.max(0, chunkEncodeThresholdBytes);
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
		try {
			guard.validateTag(tag);
			encodeCount.incrementAndGet();
			return SnbtCodec.encode(VersionAdapter.adaptForEncode(tag));
		} catch (IllegalArgumentException ex) {
			guardRejections.incrementAndGet();
			noteGuardFailure(ex);
			throw new IOException(ex.getMessage(), ex);
		}
	}

	public Tag decodeSnbt(String snbt) throws IOException {
		decodeCount.incrementAndGet();
		try {
			Tag tag = SnbtFastParser.parse(snbt == null ? "" : snbt.trim());
			snbtFastHits.incrementAndGet();
			guard.validateTag(tag);
			consecutiveFailures.set(0);
			return VersionAdapter.adaptAfterDecode(tag);
		} catch (Exception fastFail) {
			if (!legacyFallback) {
				noteFailure(new IOException(fastFail));
				throw new IOException(fastFail);
			}
			snbtFallbacks.incrementAndGet();
			legacyRetries.incrementAndGet();
			try {
				Tag tag = SnbtCodec.decodeLegacy(snbt == null ? "" : snbt.trim());
				guard.validateTag(tag);
				consecutiveFailures.set(0);
				return VersionAdapter.adaptAfterDecode(tag);
			} catch (Exception legacy) {
				noteFailure(new IOException(legacy));
				throw new IOException(legacy);
			}
		}
	}

	public byte[] encodeChunked(Tag tag, int maxChunkBytes) throws IOException {
		if (!(tag instanceof CompoundTag)) {
			return encode(tag);
		}
		guard.validateTag(tag);
		byte[] data = ChunkedNbtCodec.encode((CompoundTag) VersionAdapter.adaptForEncode(tag));
		if (maxChunkBytes > 0) {
			guard.validateEncodedSize(data);
		}
		chunkEncodes.incrementAndGet();
		encodeCount.incrementAndGet();
		return data;
	}

	public Tag decodeChunked(byte[] data) throws IOException {
		guard.validateEncodedSize(data);
		decodeCount.incrementAndGet();
		CompoundTag tag = ChunkedNbtCodec.decode(data);
		guard.validateTag(tag);
		return VersionAdapter.adaptAfterDecode(tag);
	}

	public byte[] encode(OwnedTag owned) throws IOException {
		return encode(owned == null ? null : owned.payload());
	}

	public byte[] encode(Tag tag) throws IOException {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		try {
			guard.validateTag(tag);
		} catch (IllegalArgumentException ex) {
			guardRejections.incrementAndGet();
			noteGuardFailure(ex);
			throw new IOException(ex.getMessage(), ex);
		}
		Tag adapted = VersionAdapter.adaptForEncode(tag);
		encodeCount.incrementAndGet();
		try {
			byte[] data = BinaryNbtCodec.encode(adapted);
			guard.validateEncodedSize(data);
			if (chunkEncodeThreshold > 0
				&& data.length >= chunkEncodeThreshold
				&& adapted instanceof CompoundTag
				&& !forceLegacy) {
				byte[] chunked = ChunkedNbtCodec.encode((CompoundTag) adapted);
				guard.validateEncodedSize(chunked);
				chunkEncodes.incrementAndGet();
				return chunked;
			}
			return data;
		} catch (IOException | IllegalArgumentException primary) {
			if (primary instanceof IllegalArgumentException) {
				guardRejections.incrementAndGet();
				noteGuardFailure(primary);
				throw new IOException(primary.getMessage(), primary);
			}
			if (!legacyFallback || forceLegacy) {
				noteFailure((IOException) primary);
				throw (IOException) primary;
			}
			legacyRetries.incrementAndGet();
			try {
				return BinaryNbtCodec.encode(adapted);
			} catch (IOException legacy) {
				noteFailure(legacy);
				throw legacy;
			}
		}
	}

	public OwnedTag decodeOwned(byte[] data) throws IOException {
		if (lazyDeserialize) {
			guard.validateEncodedSize(data);
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
		try {
			guard.validateEncodedSize(data);
		} catch (IllegalArgumentException ex) {
			guardRejections.incrementAndGet();
			noteGuardFailure(ex);
			throw new IOException(ex.getMessage(), ex);
		}
		decodeCount.incrementAndGet();
		try {
			Tag tag;
			if (ChunkedNbtCodec.isChunked(data)) {
				tag = ChunkedNbtCodec.decode(data);
			} else {
				tag = BinaryNbtCodec.decode(data);
			}
			guard.validateTag(tag);
			consecutiveFailures.set(0);
			return VersionAdapter.adaptAfterDecode(tag);
		} catch (IOException | IllegalArgumentException primary) {
			if (primary instanceof IllegalArgumentException) {
				guardRejections.incrementAndGet();
				noteGuardFailure(primary);
				throw new IOException(primary.getMessage(), primary);
			}
			if (!legacyFallback) {
				noteFailure((IOException) primary);
				throw (IOException) primary;
			}
			legacyRetries.incrementAndGet();
			try {
				Tag tag = BinaryNbtCodec.decode(data);
				guard.validateTag(tag);
				consecutiveFailures.set(0);
				return VersionAdapter.adaptAfterDecode(tag);
			} catch (IOException | IllegalArgumentException legacy) {
				if (legacy instanceof IllegalArgumentException) {
					guardRejections.incrementAndGet();
					noteGuardFailure(legacy);
					throw new IOException(legacy.getMessage(), legacy);
				}
				noteFailure((IOException) legacy);
				throw (IOException) legacy;
			}
		}
	}

	private void noteFailure(IOException error) {
		int failures = consecutiveFailures.incrementAndGet();
		InstantNBT.LOGGER.warn("Serializer failure #{}: {}", failures, error.toString());
		if (failures >= 3) {
			InstantNbtRuntime.get().enterDegraded(DegradedMode.DEGRADED_SAFE, "serializer-consecutive-failures");
		}
	}

	private void noteGuardFailure(Exception error) {
		InstantNBT.LOGGER.error("Serializer guard rejected payload: {}", error.toString());
		InstantNbtRuntime runtime = InstantNbtRuntime.get();
		if (guardRejections.get() >= 5) {
			runtime.killSwitch().engage("serializer-guard-threshold", runtime);
		} else {
			runtime.enterDegraded(DegradedMode.DEGRADED_SAFE, "serializer-guard");
		}
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
}
