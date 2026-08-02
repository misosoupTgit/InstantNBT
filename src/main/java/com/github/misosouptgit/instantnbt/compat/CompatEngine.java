package com.github.misosouptgit.instantnbt.compat;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.config.InstantNbtConfig;
import com.github.misosouptgit.instantnbt.config.RuntimePreset;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import dev.architectury.platform.Platform;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Capability Check + Fallback Planner (Project Plan 13.3 / 13.5).
 */
public final class CompatEngine {
	private final CompatibilityDatabase database = new CompatibilityDatabase();
	private CompatReport lastReport = new CompatReport();

	public CompatibilityDatabase database() {
		return database;
	}

	public CompatReport lastReport() {
		return lastReport;
	}

	public CompatReport scanAndApply(InstantNbtRuntime runtime) {
		database.loadDefaults();
		CompatReport report = new CompatReport();
		InstantNbtConfig config = runtime.config();
		FeatureRegistry features = runtime.features();

		Set<String> known = new HashSet<>();
		for (CompatRule rule : database.rules()) {
			if (!"*".equals(rule.modId())) {
				known.add(rule.modId().toLowerCase(Locale.ROOT));
			}
		}

		Collection<String> modIds = Platform.getModIds();
		boolean sawUnknown = false;
		for (String modId : modIds) {
			if (modId == null || modId.isEmpty()) {
				continue;
			}
			String id = modId.toLowerCase(Locale.ROOT);
			if (id.equals("minecraft") || id.equals("java") || id.equals("forge")
				|| id.equals("neoforge") || id.equals("fabricloader") || id.equals("fabric-api")
				|| id.equals("architectury") || id.equals(InstantNBT.MOD_ID)) {
				continue;
			}
			report.addPresent(id);
			String version = safeVersion(id);
			boolean matched = false;
			for (CompatRule rule : database.matching(id, version)) {
				matched = true;
				applyRule(rule, runtime, features, report);
			}
			if (!matched && !known.contains(id)) {
				report.addUnknown(id);
				sawUnknown = true;
			}
		}

		if (sawUnknown && config.forceLegacyForUnknown) {
			applyUnknownSafe(runtime, features, report);
		}

		if (!report.disabledFeatures().isEmpty()) {
			report.addRecommendation("Review /instantnbt compat and consider mode=safe if instability persists");
		}
		this.lastReport = report;
		InstantNBT.LOGGER.info(
			"Compat scan complete: present={}, unknown={}, fired={}, disabled={}",
			report.presentMods().size(),
			report.unknownMods().size(),
			report.firedRules().size(),
			report.disabledFeatures().size()
		);
		return report;
	}

	private void applyRule(CompatRule rule, InstantNbtRuntime runtime, FeatureRegistry features, CompatReport report) {
		report.addFiredRule(rule.id());
		switch (rule.action()) {
			case DISABLE_FEATURE:
				if (rule.feature() != null) {
					features.disable(rule.feature());
					report.addDisabledFeature(rule.feature(), rule.reason());
				}
				break;
			case FORCE_LEGACY_CODEC:
				features.disable(FeatureRegistry.FEAT_FAST_CODEC);
				runtime.serializer().configure(false, true, true);
				report.addDisabledFeature(FeatureRegistry.FEAT_FAST_CODEC, rule.reason());
				break;
			case FORCE_FULL_SYNC:
				features.disable(FeatureRegistry.FEAT_DELTA_SYNC);
				runtime.network().configure(false, runtime.config().snapshotSync, runtime.config().integratedDirectPass, runtime.config().packetBatching);
				report.addDisabledFeature(FeatureRegistry.FEAT_DELTA_SYNC, rule.reason());
				break;
			case APPLY_SAFE_PROFILE:
				runtime.config().applyPreset(RuntimePreset.SAFE);
				report.addWarning("Safe profile requested by rule " + rule.id() + ": " + rule.reason());
				break;
			case WARN:
			default:
				report.addWarning(rule.reason());
				break;
		}
	}

	private void applyUnknownSafe(InstantNbtRuntime runtime, FeatureRegistry features, CompatReport report) {
		features.disable(FeatureRegistry.FEAT_DELTA_SYNC);
		features.disable(FeatureRegistry.FEAT_DIRECT_PASS);
		runtime.serializer().configure(runtime.config().fastCodec, true, false);
		runtime.network().configure(false, runtime.config().snapshotSync, false, runtime.config().packetBatching);
		report.setUnknownSafeApplied(true);
		report.addDisabledFeature(FeatureRegistry.FEAT_DELTA_SYNC, "compat-unknown-safe");
		report.addDisabledFeature(FeatureRegistry.FEAT_DIRECT_PASS, "compat-unknown-safe");
		report.addRecommendation("Unknown mods detected; delta/direct-pass disabled (compat-unknown-safe)");
	}

	private static String safeVersion(String modId) {
		try {
			return Platform.getMod(modId).getVersion();
		} catch (Throwable ex) {
			return "";
		}
	}
}
