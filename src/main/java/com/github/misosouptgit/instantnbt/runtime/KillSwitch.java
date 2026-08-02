package com.github.misosouptgit.instantnbt.runtime;

import com.github.misosouptgit.instantnbt.InstantNBT;

/**
 * Emergency disable of all optimizations (Project Plan 18.4).
 */
public final class KillSwitch {
	private volatile boolean engaged;
	private volatile String reason = "";

	public boolean isEngaged() {
		return engaged;
	}

	public String reason() {
		return reason;
	}

	public synchronized void engage(String reason, InstantNbtRuntime runtime) {
		if (engaged) {
			return;
		}
		this.engaged = true;
		this.reason = reason == null ? "unspecified" : reason;
		InstantNBT.LOGGER.error("InstantNBT Kill Switch engaged: {}", this.reason);
		if (runtime != null) {
			runtime.enterDegraded(DegradedMode.DEGRADED_MINIMAL, "kill-switch:" + this.reason);
		}
	}

	public synchronized void reset() {
		engaged = false;
		reason = "";
	}
}
