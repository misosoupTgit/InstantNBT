package com.github.misosouptgit.instantnbt.ownership;

/**
 * Ownership state machine (Project Plan 6.3).
 */
public enum OwnershipState {
	UNIQUE,
	SHARED,
	FROZEN,
	DETACHED
}
