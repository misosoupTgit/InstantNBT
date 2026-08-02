package com.github.misosouptgit.instantnbt.network;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.ownership.ModuleDomain;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.Owner;
import com.github.misosouptgit.instantnbt.ownership.ThreadDomain;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import com.github.misosouptgit.instantnbt.runtime.SafetyCoordinator;

/**
 * Integrated Server helpers (Project Plan 12). Soft-fail handoff — never crash game threads.
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
			InstantNbtRuntime.get().safety().report(SafetyCoordinator.Severity.SOFT, "integrated", "null handoff");
			OwnedTag empty = OwnedTag.of(new net.minecraft.nbt.CompoundTag());
			empty.freeze();
			return new SnapshotHandle(empty, SyncMode.SNAPSHOT);
		}
		try {
			Owner clientOwner = new Owner(ThreadDomain.MAIN, ModuleDomain.NETWORK);
			if (integrated && InstantNbtRuntime.get().optimizationsActive()) {
				SnapshotHandle handle = network.directPass(serverTag);
				handle.tag().acquire(clientOwner);
				return handle;
			}
			OwnedTag copy = serverTag.copyUnique(clientOwner);
			return network.snapshot(copy);
		} catch (Throwable ex) {
			InstantNBT.LOGGER.warn("Integrated handoff soft-failed: {}", ex.toString());
			InstantNbtRuntime.get().safety().report(SafetyCoordinator.Severity.FEATURE, "integrated-direct", ex.toString());
			try {
				OwnedTag copy = serverTag.copyUnique(new Owner(ThreadDomain.MAIN, ModuleDomain.NETWORK));
				copy.freeze();
				return new SnapshotHandle(copy, SyncMode.SNAPSHOT);
			} catch (Throwable nested) {
				OwnedTag empty = OwnedTag.of(serverTag.payload().copy());
				empty.freeze();
				return new SnapshotHandle(empty, SyncMode.SNAPSHOT);
			}
		}
	}

	public void enterServerThread() {
		ThreadDomain.setCurrent(ThreadDomain.INTEGRATED_SERVER);
	}

	public void enterMainThread() {
		ThreadDomain.setCurrent(ThreadDomain.MAIN);
	}
}
