package com.github.misosouptgit.instantnbt.network;

/**
 * Network synchronization modes (Project Plan 11.1).
 */
public enum SyncMode {
	FULL,
	DELTA,
	SNAPSHOT,
	DIRECT_PASS
}
