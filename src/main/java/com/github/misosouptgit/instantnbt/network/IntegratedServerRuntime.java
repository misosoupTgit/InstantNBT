package com.github.misosouptgit.instantnbt.network;

import com.github.misosouptgit.instantnbt.ownership.ModuleDomain;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.Owner;
import com.github.misosouptgit.instantnbt.ownership.ThreadDomain;

/**
 * Integrated Server helpers (Project Plan 12).
 */
public final class IntegratedServerRuntime {
	private volatile boolean integrated;
	private final NetworkRuntime network;

	public IntegratedServerRuntime(NetworkRuntime network) {
		this.network = network;
	}

	public void setIntegrated(boolean integrated) {
		this.integrated = integrated;
	}

	public boolean isIntegrated() {
		return integrated;
	}

	/**
	 * Handoff mutable server tag to client domain as frozen snapshot / direct pass.
	 */
	public SnapshotHandle handoffToClient(OwnedTag serverTag) {
		if (serverTag == null) {
			throw new IllegalArgumentException("serverTag");
		}
		Owner clientOwner = new Owner(ThreadDomain.MAIN, ModuleDomain.NETWORK);
		if (integrated) {
			SnapshotHandle handle = network.directPass(serverTag);
			handle.tag().acquire(clientOwner);
			return handle;
		}
		OwnedTag copy = serverTag.copyUnique(clientOwner);
		return network.snapshot(copy);
	}

	public void enterServerThread() {
		ThreadDomain.setCurrent(ThreadDomain.INTEGRATED_SERVER);
	}

	public void enterMainThread() {
		ThreadDomain.setCurrent(ThreadDomain.MAIN);
	}
}
