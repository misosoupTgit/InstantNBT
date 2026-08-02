package com.github.misosouptgit.instantnbt.network;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transport stage of the sync pipeline (Project Plan 11.3).
 * DirectPass uses an in-process queue; encoded packets go through {@link #offerPacket}.
 */
public final class TransportPipeline {
	private final Queue<SnapshotHandle> directQueue = new ArrayDeque<>();
	private final Queue<byte[]> packetQueue = new ArrayDeque<>();
	private final AtomicLong directOffered = new AtomicLong();
	private final AtomicLong packetsOffered = new AtomicLong();
	private final AtomicLong packetsDelivered = new AtomicLong();

	public synchronized void offerDirect(SnapshotHandle handle) {
		if (handle == null) {
			return;
		}
		directQueue.offer(handle);
		directOffered.incrementAndGet();
	}

	public synchronized SnapshotHandle pollDirect() {
		return directQueue.poll();
	}

	public synchronized void offerPacket(byte[] packet) {
		if (packet == null || packet.length == 0) {
			return;
		}
		packetQueue.offer(packet);
		packetsOffered.incrementAndGet();
	}

	public synchronized byte[] pollPacket() {
		byte[] packet = packetQueue.poll();
		if (packet != null) {
			packetsDelivered.incrementAndGet();
		}
		return packet;
	}

	public synchronized void drainDirectNoop() {
		// Keeps queue bounded for unused DirectPass handles in single-sided tests.
		while (directQueue.size() > 64) {
			directQueue.poll();
		}
		while (packetQueue.size() > 64) {
			packetQueue.poll();
		}
	}

	public long directOffered() {
		return directOffered.get();
	}

	public long packetsOffered() {
		return packetsOffered.get();
	}

	public long packetsDelivered() {
		return packetsDelivered.get();
	}
}
