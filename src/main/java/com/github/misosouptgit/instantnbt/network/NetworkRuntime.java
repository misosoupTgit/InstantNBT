package com.github.misosouptgit.instantnbt.network;

import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.serializer.SerializerFacade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Network sync runtime (Project Plan 11).
 * Encodes dirty OwnedTags into batches; DirectPass bypasses serializer when safe.
 */
public final class NetworkRuntime {
	private final SerializerFacade serializer;
	private volatile boolean deltaSync = true;
	private volatile boolean snapshotSync = true;
	private volatile boolean directPass = true;
	private volatile boolean packetBatching = true;
	private volatile int batchWindow = 16;
	private volatile int maxBatchBytes = 256 * 1024;

	private final List<OwnedTag> pending = new ArrayList<>();
	private final AtomicInteger fullSyncs = new AtomicInteger();
	private final AtomicInteger deltaSyncs = new AtomicInteger();
	private final AtomicInteger snapshotSyncs = new AtomicInteger();
	private final AtomicInteger directPasses = new AtomicInteger();
	private final AtomicInteger fallbacks = new AtomicInteger();

	public NetworkRuntime(SerializerFacade serializer) {
		this.serializer = serializer;
	}

	public void configure(boolean deltaSync, boolean snapshotSync, boolean directPass, boolean packetBatching) {
		this.deltaSync = deltaSync;
		this.snapshotSync = snapshotSync;
		this.directPass = directPass;
		this.packetBatching = packetBatching;
	}

	public synchronized void collectDirty(OwnedTag tag) {
		if (tag == null || !tag.dirty()) {
			return;
		}
		pending.add(tag);
		if (!packetBatching || pending.size() >= batchWindow) {
			flushBatch();
		}
	}

	public synchronized byte[] buildFull(OwnedTag tag) throws IOException {
		fullSyncs.incrementAndGet();
		return serializer.encode(tag);
	}

	public synchronized byte[] buildDelta(OwnedTag tag, long peerGeneration) throws IOException {
		if (!deltaSync || tag == null) {
			return buildFull(tag);
		}
		if (!tag.dirty() || tag.generation() == peerGeneration) {
			return new byte[0];
		}
		deltaSyncs.incrementAndGet();
		try {
			return serializer.encode(tag);
		} catch (IOException ex) {
			fallbacks.incrementAndGet();
			return buildFull(tag);
		}
	}

	public SnapshotHandle snapshot(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		if (!snapshotSync) {
			OwnedTag copy = tag.copyUnique(null);
			copy.freeze();
			snapshotSyncs.incrementAndGet();
			return new SnapshotHandle(copy, SyncMode.SNAPSHOT);
		}
		tag.freeze();
		snapshotSyncs.incrementAndGet();
		return new SnapshotHandle(tag, SyncMode.SNAPSHOT);
	}

	/**
	 * Integrated-server Direct Pass: freeze + hand off handle without Netty (Plan 12.4).
	 */
	public SnapshotHandle directPass(OwnedTag tag) {
		if (!directPass) {
			fallbacks.incrementAndGet();
			return snapshot(tag);
		}
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		if (!tag.isFrozen()) {
			tag.freeze();
		}
		directPasses.incrementAndGet();
		return new SnapshotHandle(tag, SyncMode.DIRECT_PASS);
	}

	public OwnedTag apply(byte[] payload, long expectedGeneration) throws IOException {
		OwnedTag decoded = serializer.decodeOwned(payload);
		if (expectedGeneration >= 0 && decoded.generation() != 0 && decoded.generation() < expectedGeneration) {
			fallbacks.incrementAndGet();
			throw new IOException("generation mismatch: got " + decoded.generation() + " expected >=" + expectedGeneration);
		}
		if (decoded.hasMeta()) {
			decoded.meta().clearDirty();
		}
		return decoded;
	}

	public synchronized int flushBatch() {
		int count = pending.size();
		pending.clear();
		return count;
	}

	public synchronized void onTickEnd() {
		if (!pending.isEmpty()) {
			flushBatch();
		}
	}

	public void shrinkBatchWindow() {
		batchWindow = Math.max(1, batchWindow / 2);
	}

	public int fullSyncs() {
		return fullSyncs.get();
	}

	public int deltaSyncs() {
		return deltaSyncs.get();
	}

	public int snapshotSyncs() {
		return snapshotSyncs.get();
	}

	public int directPasses() {
		return directPasses.get();
	}

	public int fallbacks() {
		return fallbacks.get();
	}
}
