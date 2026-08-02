package com.github.misosouptgit.instantnbt.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single compatibility rule from the Compatibility Database (Project Plan 13.4).
 */
public final class CompatRule {
	public enum Action {
		DISABLE_FEATURE,
		FORCE_LEGACY_CODEC,
		FORCE_FULL_SYNC,
		APPLY_SAFE_PROFILE,
		WARN
	}

	private final String id;
	private final String modId;
	private final String versionRange;
	private final Action action;
	private final String feature;
	private final String reason;
	private final int tier;

	public CompatRule(String id, String modId, String versionRange, Action action, String feature, String reason, int tier) {
		this.id = id;
		this.modId = modId;
		this.versionRange = versionRange == null ? "*" : versionRange;
		this.action = action == null ? Action.WARN : action;
		this.feature = feature;
		this.reason = reason == null ? "" : reason;
		this.tier = tier;
	}

	public String id() {
		return id;
	}

	public String modId() {
		return modId;
	}

	public String versionRange() {
		return versionRange;
	}

	public Action action() {
		return action;
	}

	public String feature() {
		return feature;
	}

	public String reason() {
		return reason;
	}

	public int tier() {
		return tier;
	}

	public boolean matchesVersion(String installedVersion) {
		if ("*".equals(versionRange) || versionRange.isEmpty()) {
			return true;
		}
		if (installedVersion == null || installedVersion.isEmpty()) {
			return true;
		}
		// Minimal matcher: exact, prefix*, or comma-separated list.
		String[] parts = versionRange.split(",");
		for (String part : parts) {
			String p = part.trim();
			if (p.endsWith("*")) {
				if (installedVersion.startsWith(p.substring(0, p.length() - 1))) {
					return true;
				}
			} else if (p.equals(installedVersion) || installedVersion.startsWith(p)) {
				return true;
			}
		}
		return false;
	}

	public static List<CompatRule> builtinDefaults() {
		List<CompatRule> rules = new ArrayList<>();
		rules.add(new CompatRule(
			"ae2-delta-caution",
			"ae2",
			"*",
			Action.DISABLE_FEATURE,
			FeatureRegistry.FEAT_DELTA_SYNC,
			"AE2 stores dense NBT; prefer snapshot/full sync until Tier1 verified",
			1
		));
		rules.add(new CompatRule(
			"create-shared-caution",
			"create",
			"*",
			Action.DISABLE_FEATURE,
			FeatureRegistry.FEAT_SHARED_TAG,
			"Create mutates compound graphs aggressively; disable SharedTag by default",
			1
		));
		rules.add(new CompatRule(
			"curios-safe",
			"curios",
			"*",
			Action.WARN,
			null,
			"Curios present — ownership acquire/release recommended for cross-thread item NBT",
			1
		));
		rules.add(new CompatRule(
			"emi-warn",
			"emi",
			"*",
			Action.WARN,
			null,
			"EMI present — client-heavy NBT reads; overlay diagnostics recommended",
			2
		));
		return Collections.unmodifiableList(rules);
	}
}
