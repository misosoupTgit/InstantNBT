package com.github.misosouptgit.instantnbt.runtime;

/**
 * Runtime lifecycle phases (Project Plan 4.2).
 */
public enum RuntimePhase {
	BOOTSTRAP,
	CAPABILITY_SCAN,
	RUNTIME_INIT,
	WARMUP,
	RUNNING,
	DEGRADED,
	SHUTDOWN
}
