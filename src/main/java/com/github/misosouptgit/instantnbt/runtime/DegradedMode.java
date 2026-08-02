package com.github.misosouptgit.instantnbt.runtime;

/**
 * Degraded operating modes ordered by severity (Project Plan 4.4).
 */
public enum DegradedMode {
	NONE,
	/** Light: delta sync only. */
	DEGRADED_COMPAT,
	/** Medium: delta + direct-pass off; tracking remains. */
	DEGRADED_SAFE,
	/** Heavy: all optimizations off (kill-switch path). */
	DEGRADED_MINIMAL
}
