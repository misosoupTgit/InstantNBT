package com.github.misosouptgit.instantnbt.runtime;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.compat.FeatureRegistry;
import com.github.misosouptgit.instantnbt.memory.MemoryManager;

/**
 * InstantNBT Runtime orchestrator (Project Plan 4).
 */
public final class InstantNbtRuntime {
	private static final InstantNbtRuntime INSTANCE = new InstantNbtRuntime();

	private final FeatureRegistry featureRegistry = new FeatureRegistry();
	private final MemoryManager memoryManager = new MemoryManager();
	private final KillSwitch killSwitch = new KillSwitch();

	private volatile RuntimePhase phase = RuntimePhase.BOOTSTRAP;
	private volatile DegradedMode degradedMode = DegradedMode.NONE;

	private InstantNbtRuntime() {}

	public static InstantNbtRuntime get() {
		return INSTANCE;
	}

	public RuntimePhase phase() {
		return phase;
	}

	public DegradedMode degradedMode() {
		return degradedMode;
	}

	public FeatureRegistry features() {
		return featureRegistry;
	}

	public MemoryManager memory() {
		return memoryManager;
	}

	public KillSwitch killSwitch() {
		return killSwitch;
	}

	public boolean optimizationsActive() {
		return !killSwitch.isEngaged() && degradedMode != DegradedMode.DEGRADED_MINIMAL;
	}

	/**
	 * Startup sequence (Project Plan 4.3).
	 */
	public synchronized void bootstrap() {
		if (phase == RuntimePhase.RUNNING || phase == RuntimePhase.DEGRADED) {
			return;
		}
		transition(RuntimePhase.BOOTSTRAP);
		transition(RuntimePhase.CAPABILITY_SCAN);
		featureRegistry.scanDefaults();

		transition(RuntimePhase.RUNTIME_INIT);
		memoryManager.start();

		transition(RuntimePhase.WARMUP);
		// Warmup benchmark is optional; skipped in Phase 2 skeleton.

		transition(RuntimePhase.RUNNING);
		InstantNBT.LOGGER.info(
			"InstantNBT Runtime RUNNING (features={}, degraded={})",
			featureRegistry.enabledFeatures(),
			degradedMode
		);
	}

	public synchronized void shutdown() {
		if (phase == RuntimePhase.SHUTDOWN) {
			return;
		}
		memoryManager.shutdown();
		transition(RuntimePhase.SHUTDOWN);
		InstantNBT.LOGGER.info("InstantNBT Runtime shut down");
	}

	public synchronized void enterDegraded(DegradedMode mode, String reason) {
		if (mode == null || mode == DegradedMode.NONE) {
			degradedMode = DegradedMode.NONE;
			if (phase == RuntimePhase.DEGRADED) {
				transition(RuntimePhase.RUNNING);
			}
			return;
		}
		degradedMode = mode;
		if (mode == DegradedMode.DEGRADED_MINIMAL) {
			featureRegistry.disableAllOptimizations();
		} else if (mode == DegradedMode.DEGRADED_SAFE) {
			featureRegistry.disable(FeatureRegistry.FEAT_DELTA_SYNC);
			featureRegistry.disable(FeatureRegistry.FEAT_DIRECT_PASS);
		}
		transition(RuntimePhase.DEGRADED);
		InstantNBT.LOGGER.warn("InstantNBT entered {} ({})", mode, reason);
	}

	public void onServerTickEnd() {
		if (phase != RuntimePhase.RUNNING && phase != RuntimePhase.DEGRADED) {
			return;
		}
		if (optimizationsActive()) {
			memoryManager.onTickEnd();
			memoryManager.respondToPressure();
		}
	}

	private void transition(RuntimePhase next) {
		this.phase = next;
	}
}
