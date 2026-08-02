package com.github.misosouptgit.instantnbt.network;

import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.serializer.SerializerFacade;
import com.github.misosouptgit.instantnbt.serializer.ValidationGuard;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Network sync runtime (Project Plan 11).
 */
public final class NetworkRuntime {
	private final SerializerFacade serializer;
	private final DeltaCodec deltaCodec;
	private final TransportPipeline transport = new TransportPipeline();

	private volatile boolean deltaSync = true;
	private volatile boolean snapshotSync = true;
	private volatile boolean directPass = true;
	private volatile boolean packetBatching = true;
	private volatile int batchWindow = 16;

	private final List<OwnedTag> pending = new ArrayList<>();
	private final AtomicInteger fullSyncs = new AtomicInteger();
	private final AtomicInteger deltaSyncs = new AtomicInteger();
	private final AtomicInteger snapshotSyncs = new AtomicInteger();
	private final AtomicInteger directPasses = new AtomicInteger();
	private final AtomicInteger fallbacks = new AtomicInteger();

	public NetworkRuntime(SerializerFacade serializer) {
		this.serializer = serializer;
		this.deltaCodec = new DeltaCodec(ValidationGuard.defaults());
	}

	public void configure(boolean deltaSync, boolean snapshotSync, boolean directPass, boolean packetBatching) {
		this.deltaSync = deltaSync;
		this.snapshotSync = snapshotSync;
		this.directPass = directPass;
		this.packetBatching = packetBatching;
	}

	public TransportPipeline transport() {
		return transport;
	}

	public DeltaCodec deltaCodec() {
		return deltaCodec;
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
		return deltaCodec.encodeFull(tag);
	}

	public synchronized byte[] buildDelta(OwnedTag previous, OwnedTag current) throws IOException {
		if (!deltaSync || current == null) {
			return buildFull(current);
		}
		try {
			byte[] packet = deltaCodec.encodeDelta(previous, current);
			if (packet.length == 0) {
				return packet;
			}
			deltaSyncs.incrementAndGet();
			return packet;
		} catch (IOException ex) {
			fallbacks.incrementAndGet();
			shrinkBatchWindow();
			return buildFull(current);
		}
	}

	@Deprecated
	public synchronized byte[] buildDelta(OwnedTag tag, long peerGeneration) throws IOException {
		if (!deltaSync || tag == null) {
			return buildFull(tag);
		}
		if (!tag.dirty() || tag.generation() == peerGeneration) {
			return new byte[0];
		}
		return buildFull(tag);
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
		SnapshotHandle handle = new SnapshotHandle(tag, SyncMode.DIRECT_PASS);
		transport.offerDirect(handle);
		return handle;
	}

	public DeltaCodec.ApplyResult applyDelta(CompoundTag base, byte[] packet, long expectedGeneration) throws IOException {
		try {
			return deltaCodec.apply(base, packet, expectedGeneration);
		} catch (IOException ex) {
			fallbacks.incrementAndGet();
			shrinkBatchWindow();
			throw ex;
		}
	}

	public OwnedTag apply(byte[] payload, long expectedGeneration) throws IOException {
		try {
			DeltaCodec.ApplyResult result = deltaCodec.apply(new CompoundTag(), payload, expectedGeneration);
			OwnedTag owned = OwnedTag.owned(result.tag(), null);
			if (owned.hasMeta()) {
				owned.meta().clearDirty();
			}
			return owned;
		} catch (IOException ex) {
			fallbacks.incrementAndGet();
			OwnedTag decoded = serializer.decodeOwned(payload);
			if (decoded.hasMeta()) {
				decoded.meta().clearDirty();
			}
			return decoded;
		}
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
		transport.drainDirectNoop();
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
