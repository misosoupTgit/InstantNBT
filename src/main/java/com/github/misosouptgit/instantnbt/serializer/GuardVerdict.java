package com.github.misosouptgit.instantnbt.serializer;

/**
 * Soft validation outcome — prefer skip/optimize-off over throwing into game paths.
 */
public enum GuardVerdict {
	OK,
	/** Too large/deep for InstantNBT fast path; use vanilla-compatible path without degrade. */
	SKIP_FAST_PATH,
	/** InstantNBT-owned packet looks corrupt; escalate via SafetyCoordinator. */
	REJECT_OWNED
}
