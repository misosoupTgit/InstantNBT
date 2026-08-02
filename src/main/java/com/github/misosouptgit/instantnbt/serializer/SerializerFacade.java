package com.github.misosouptgit.instantnbt.serializer;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.ownership.ModuleDomain;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.Owner;
import com.github.misosouptgit.instantnbt.runtime.DegradedMode;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serializer facade with fast + legacy fallback (Project Plan 10).
 */
public final class SerializerFacade {
	private volatile boolean fastCodec = true;
	private volatile boolean legacyFallback = true;
	private volatile boolean forceLegacy;
	private final AtomicInteger consecutiveFailures = new AtomicInteger();
	private final AtomicInteger encodeCount = new AtomicInteger();
	private final AtomicInteger decodeCount = new AtomicInteger();
	private final AtomicInteger legacyRetries = new AtomicInteger();

	public void configure(boolean fastCodec, boolean legacyFallback, boolean forceLegacy) {
		this.fastCodec = fastCodec;
		this.legacyFallback = legacyFallback;
		this.forceLegacy = forceLegacy;
	}

	public CodecPath preferredPath() {
		return forceLegacy || !fastCodec ? CodecPath.LEGACY : CodecPath.FAST_BINARY;
	}

	public byte[] encode(OwnedTag owned) throws IOException {
		return encode(owned == null ? null : owned.payload());
	}

	public byte[] encode(Tag tag) throws IOException {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		Tag adapted = VersionAdapter.adaptForEncode(tag);
		encodeCount.incrementAndGet();
		try {
			return BinaryNbtCodec.encode(adapted);
		} catch (IOException primary) {
			if (!legacyFallback || forceLegacy) {
				noteFailure(primary);
				throw primary;
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
		Tag tag = decode(data);
		return OwnedTag.owned(tag, Owner.current(ModuleDomain.SERIALIZER));
	}

	public Tag decode(byte[] data) throws IOException {
		if (data == null) {
			throw new IllegalArgumentException("data");
		}
		decodeCount.incrementAndGet();
		try {
			Tag tag = BinaryNbtCodec.decode(data);
			consecutiveFailures.set(0);
			return VersionAdapter.adaptAfterDecode(tag);
		} catch (IOException primary) {
			if (!legacyFallback) {
				noteFailure(primary);
				throw primary;
			}
			legacyRetries.incrementAndGet();
			try {
				Tag tag = BinaryNbtCodec.decode(data);
				consecutiveFailures.set(0);
				return VersionAdapter.adaptAfterDecode(tag);
			} catch (IOException legacy) {
				noteFailure(legacy);
				throw legacy;
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

	public int encodeCount() {
		return encodeCount.get();
	}

	public int decodeCount() {
		return decodeCount.get();
	}

	public int legacyRetries() {
		return legacyRetries.get();
	}
}
