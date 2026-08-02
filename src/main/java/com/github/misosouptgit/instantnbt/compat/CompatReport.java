package com.github.misosouptgit.instantnbt.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of Capability Check (Project Plan 13.3 / 13.6).
 */
public final class CompatReport {
	private final Set<String> presentMods = new LinkedHashSet<>();
	private final Set<String> unknownMods = new LinkedHashSet<>();
	private final List<String> firedRules = new ArrayList<>();
	private final List<String> disabledFeatures = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();
	private final List<String> recommendations = new ArrayList<>();
	private boolean unknownSafeApplied;

	public void addPresent(String modId) {
		presentMods.add(modId);
	}

	public void addUnknown(String modId) {
		unknownMods.add(modId);
	}

	public void addFiredRule(String ruleId) {
		firedRules.add(ruleId);
	}

	public void addDisabledFeature(String feature, String reason) {
		disabledFeatures.add(feature + " (" + reason + ")");
	}

	public void addWarning(String warning) {
		warnings.add(warning);
	}

	public void addRecommendation(String recommendation) {
		recommendations.add(recommendation);
	}

	public void setUnknownSafeApplied(boolean applied) {
		this.unknownSafeApplied = applied;
	}

	public Set<String> presentMods() {
		return Collections.unmodifiableSet(presentMods);
	}

	public Set<String> unknownMods() {
		return Collections.unmodifiableSet(unknownMods);
	}

	public List<String> firedRules() {
		return Collections.unmodifiableList(firedRules);
	}

	public List<String> disabledFeatures() {
		return Collections.unmodifiableList(disabledFeatures);
	}

	public List<String> warnings() {
		return Collections.unmodifiableList(warnings);
	}

	public List<String> recommendations() {
		return Collections.unmodifiableList(recommendations);
	}

	public boolean unknownSafeApplied() {
		return unknownSafeApplied;
	}
}
