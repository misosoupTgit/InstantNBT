package com.github.misosouptgit.instantnbt.compat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Feature Registry (Project Plan 13.2).
 */
public final class FeatureRegistry {
	public static final String FEAT_OWNERSHIP = "ownership";
	public static final String FEAT_MEMORY_POOL = "memory.pool";
	public static final String FEAT_COW = "cow";
	public static final String FEAT_SHARED_TAG = "memory.shared_tag";
	public static final String FEAT_FAST_CODEC = "serializer.fast";
	public static final String FEAT_DELTA_SYNC = "network.delta";
	public static final String FEAT_SNAPSHOT_SYNC = "network.snapshot";
	public static final String FEAT_DIRECT_PASS = "network.direct_pass";

	private final Set<String> enabled = new LinkedHashSet<>();
	private boolean scanned;

	public void scanDefaults() {
		enabled.clear();
		enabled.add(FEAT_OWNERSHIP);
		enabled.add(FEAT_MEMORY_POOL);
		enabled.add(FEAT_COW);
		enabled.add(FEAT_SHARED_TAG);
		enabled.add(FEAT_FAST_CODEC);
		enabled.add(FEAT_DELTA_SYNC);
		enabled.add(FEAT_SNAPSHOT_SYNC);
		enabled.add(FEAT_DIRECT_PASS);
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
