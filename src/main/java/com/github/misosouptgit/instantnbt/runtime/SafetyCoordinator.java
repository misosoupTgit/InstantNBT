package com.github.misosouptgit.instantnbt.runtime;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.compat.FeatureRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Graduated safety responses — prefer feature-local soft disable over global kill (Plan 4.4 / 18).
 * Never throws into Minecraft game paths.
 */
public final class SafetyCoordinator {
	public enum Severity {
		SOFT,
		FEATURE,
		HARD,
		CRITICAL
	}

	private final InstantNbtRuntime runtime;
	private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
	private final AtomicInteger softTotal = new AtomicInteger();
	private final AtomicInteger featureTotal = new AtomicInteger();
	private final AtomicInteger hardTotal = new AtomicInteger();
	private final AtomicInteger criticalTotal = new AtomicInteger();

	public SafetyCoordinator(InstantNbtRuntime runtime) {
		this.runtime = runtime;
	}

	public void report(Severity severity, String scope, String reason) {
		if (severity == null) {
			severity = Severity.SOFT;
		}
		String key = (scope == null ? "general" : scope) + "|" + severity.name();
		int count = counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
		String detail = reason == null ? "unspecified" : reason;
		try {
			switch (severity) {
				case SOFT:
					softTotal.incrementAndGet();
					if (count == 1 || count % 32 == 0) {
						InstantNBT.LOGGER.debug("InstantNBT soft[{} x{}]: {}", scope, count, detail);
					}
					break;
				case FEATURE:
					featureTotal.incrementAndGet();
					disableScopedFeature(scope, detail);
					InstantNBT.LOGGER.warn("InstantNBT feature-disable[{} x{}]: {}", scope, count, detail);
					break;
				case HARD:
					hardTotal.incrementAndGet();
					if (runtime.degradedMode().ordinal() < DegradedMode.DEGRADED_SAFE.ordinal()) {
						runtime.enterDegraded(DegradedMode.DEGRADED_SAFE, scope + ":" + detail);
					}
					InstantNBT.LOGGER.warn("InstantNBT hard-degrade[{} x{}]: {}", scope, count, detail);
					break;
				case CRITICAL:
					criticalTotal.incrementAndGet();
					if (count >= 3) {
						runtime.killSwitch().engage(scope + ":" + detail, runtime);
					} else {
						runtime.enterDegraded(DegradedMode.DEGRADED_SAFE, "pre-critical:" + scope);
						InstantNBT.LOGGER.error("InstantNBT critical[{}/3] {}: {}", count, scope, detail);
					}
					break;
				default:
					break;
			}
		} catch (Throwable t) {
			InstantNBT.LOGGER.error("SafetyCoordinator failed while handling {}: {}", scope, t.toString());
		}
	}

	private void disableScopedFeature(String scope, String reason) {
		FeatureRegistry features = runtime.features();
		if (scope == null) {
			return;
		}
		String s = scope.toLowerCase();
		if (s.contains("delta") || s.contains("network")) {
			features.disable(FeatureRegistry.FEAT_DELTA_SYNC);
			runtime.network().configure(false, runtime.config().snapshotSync, runtime.config().integratedDirectPass, runtime.config().packetBatching);
		} else if (s.contains("direct") || s.contains("integrated")) {
			features.disable(FeatureRegistry.FEAT_DIRECT_PASS);
			runtime.network().configure(runtime.config().deltaSync && features.isEnabled(FeatureRegistry.FEAT_DELTA_SYNC), runtime.config().snapshotSync, false, runtime.config().packetBatching);
		} else if (s.contains("shared")) {
			features.disable(FeatureRegistry.FEAT_SHARED_TAG);
			runtime.sharedTags().setSuppressed(true);
		} else if (s.contains("codec") || s.contains("serializer") || s.contains("fast") || s.contains("unsafe")) {
			features.disable(FeatureRegistry.FEAT_FAST_CODEC);
			runtime.serializer().configure(false, true, false);
			runtime.serializer().configureUnsafeIo(false);
		} else if (s.contains("cow")) {
			features.disable(FeatureRegistry.FEAT_COW);
			com.github.misosouptgit.instantnbt.ownership.OwnedTag.configureCow(false,
				com.github.misosouptgit.instantnbt.ownership.CowStrategy.SHALLOW_FIRST, 4);
		} else if (s.contains("pool") || s.contains("memory")) {
			features.disable(FeatureRegistry.FEAT_MEMORY_POOL);
		}
		InstantNBT.LOGGER.info("Scoped feature soft-off for {} ({})", scope, reason);
	}

	public int softTotal() {
		return softTotal.get();
	}

	public int featureTotal() {
		return featureTotal.get();
	}

	public int hardTotal() {
		return hardTotal.get();
	}

	public int criticalTotal() {
		return criticalTotal.get();
	}
}
