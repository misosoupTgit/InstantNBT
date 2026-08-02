package com.github.misosouptgit.instantnbt.compat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Minimal Feature Registry stub (Project Plan 13.2). Full negotiation lands later.
 */
public final class FeatureRegistry {
	public static final String FEAT_OWNERSHIP = "ownership";
	public static final String FEAT_MEMORY_POOL = "memory.pool";
	public static final String FEAT_COW = "cow";
	public static final String FEAT_DELTA_SYNC = "network.delta";
	public static final String FEAT_DIRECT_PASS = "network.direct_pass";

	private final Set<String> enabled = new LinkedHashSet<>();
	private boolean scanned;

	public void scanDefaults() {
		enabled.clear();
		enabled.add(FEAT_OWNERSHIP);
		enabled.add(FEAT_MEMORY_POOL);
		enabled.add(FEAT_COW);
		// network features stay off until Phase 3 wiring
		scanned = true;
	}

	public boolean isScanned() {
		return scanned;
	}

	public boolean isEnabled(String feature) {
		return enabled.contains(feature);
	}

	public void disable(String feature) {
		enabled.remove(feature);
	}

	public void enable(String feature) {
		enabled.add(feature);
	}

	public Set<String> enabledFeatures() {
		return Collections.unmodifiableSet(enabled);
	}

	public void disableAllOptimizations() {
		enabled.clear();
	}
}
