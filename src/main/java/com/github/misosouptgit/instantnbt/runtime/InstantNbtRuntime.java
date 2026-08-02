package com.github.misosouptgit.instantnbt.runtime;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.compat.CompatEngine;
import com.github.misosouptgit.instantnbt.compat.FeatureRegistry;
import com.github.misosouptgit.instantnbt.config.InstantNbtConfig;
import com.github.misosouptgit.instantnbt.config.RuntimePreset;
import com.github.misosouptgit.instantnbt.diagnostics.CommandsBootstrap;
import com.github.misosouptgit.instantnbt.diagnostics.DiagnosticsService;
import com.github.misosouptgit.instantnbt.memory.MemoryManager;
import com.github.misosouptgit.instantnbt.network.IntegratedServerRuntime;
import com.github.misosouptgit.instantnbt.network.NetworkRuntime;
import com.github.misosouptgit.instantnbt.ownership.CowStrategy;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.OwnedTagTracker;
import com.github.misosouptgit.instantnbt.ownership.SharedTagRegistry;
import com.github.misosouptgit.instantnbt.serializer.SerializerFacade;
import com.github.misosouptgit.instantnbt.serializer.ValidationGuard;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * InstantNBT Runtime orchestrator (Project Plan 4).
 */
public final class InstantNbtRuntime {
	private static final InstantNbtRuntime INSTANCE = new InstantNbtRuntime();

	private final FeatureRegistry featureRegistry = new FeatureRegistry();
	private final MemoryManager memoryManager = new MemoryManager();
	private final SerializerFacade serializer = new SerializerFacade();
	private final NetworkRuntime networkRuntime = new NetworkRuntime(serializer);
	private final IntegratedServerRuntime integratedServer = new IntegratedServerRuntime(networkRuntime);
	private final SharedTagRegistry sharedTags = new SharedTagRegistry();
	private final OwnedTagTracker tracker = new OwnedTagTracker();
	private final CompatEngine compatEngine = new CompatEngine();
	private final DiagnosticsService diagnostics = new DiagnosticsService(this);
	private final KillSwitch killSwitch = new KillSwitch();

	private InstantNbtConfig config = InstantNbtConfig.defaults();
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

	public InstantNbtConfig config() {
		return config;
	}

	public FeatureRegistry features() {
		return featureRegistry;
	}

	public MemoryManager memory() {
		return memoryManager;
	}

	public SerializerFacade serializer() {
		return serializer;
	}

	public NetworkRuntime network() {
		return networkRuntime;
	}

	public IntegratedServerRuntime integrated() {
		return integratedServer;
	}

	public SharedTagRegistry sharedTags() {
		return sharedTags;
	}

	public OwnedTagTracker tracker() {
		return tracker;
	}

	public CompatEngine compat() {
		return compatEngine;
	}

	public DiagnosticsService diagnostics() {
		return diagnostics;
	}

	public KillSwitch killSwitch() {
		return killSwitch;
	}

	public boolean optimizationsActive() {
		return config.runtimeEnabled
			&& !killSwitch.isEngaged()
			&& degradedMode != DegradedMode.DEGRADED_MINIMAL;
	}

	public synchronized void bootstrap() {
		if (phase == RuntimePhase.RUNNING || phase == RuntimePhase.DEGRADED) {
			return;
		}
		transition(RuntimePhase.BOOTSTRAP);

		Path configPath = Paths.get("config", "instantnbt-common.toml");
		config = InstantNbtConfig.load(configPath);
		config.applyPreset(config.mode);

		transition(RuntimePhase.CAPABILITY_SCAN);
		featureRegistry.scanDefaults();
		applyFeatureGates();
		compatEngine.scanAndApply(this);

		transition(RuntimePhase.RUNTIME_INIT);
		if (config.poolEnabled || config.arenaEnabled) {
			memoryManager.start();
		}
		serializer.configure(config.fastCodec, config.legacyFallback, config.mode == RuntimePreset.SAFE && !config.fastCodec);
		serializer.configureLazy(config.lazyDeserialize, config.chunkEncodeThresholdBytes);
		serializer.setGuard(ValidationGuard.defaults());
		networkRuntime.configure(config.deltaSync, config.snapshotSync, config.integratedDirectPass, config.packetBatching);
		OwnedTag.configureCow(featureRegistry.isEnabled(FeatureRegistry.FEAT_COW), CowStrategy.SHALLOW_FIRST, 4);
		sharedTags.setSuppressed(!config.sharedTagEnabled || !featureRegistry.isEnabled(FeatureRegistry.FEAT_SHARED_TAG));
		com.github.misosouptgit.instantnbt.network.SyncPackets.register();

		if (config.diagnosticsCommand) {
			CommandsBootstrap.register();
		}

		if (config.killSwitch) {
			killSwitch.engage("config.safety.killSwitch", this);
		}

		transition(RuntimePhase.WARMUP);

		if (killSwitch.isEngaged()) {
			transition(RuntimePhase.DEGRADED);
		} else {
			transition(RuntimePhase.RUNNING);
		}
		InstantNBT.LOGGER.info(
			"InstantNBT Runtime {} (mode={}, features={}, degraded={})",
			phase,
			config.mode,
			featureRegistry.enabledFeatures(),
			degradedMode
		);
	}

	public synchronized void shutdown() {
		if (phase == RuntimePhase.SHUTDOWN) {
			return;
		}
		memoryManager.shutdown();
		sharedTags.clear();
		tracker.clear();
		transition(RuntimePhase.SHUTDOWN);
		InstantNBT.LOGGER.info("InstantNBT Runtime shut down");
	}

	public synchronized void enterDegraded(DegradedMode mode, String reason) {
		if (mode == null || mode == DegradedMode.NONE) {
			degradedMode = DegradedMode.NONE;
			if (phase == RuntimePhase.DEGRADED && !killSwitch.isEngaged()) {
				transition(RuntimePhase.RUNNING);
			}
			return;
		}
		degradedMode = mode;
		if (mode == DegradedMode.DEGRADED_MINIMAL) {
			featureRegistry.disableAllOptimizations();
			OwnedTag.configureCow(false, CowStrategy.SHALLOW_FIRST, 4);
			sharedTags.setSuppressed(true);
		} else if (mode == DegradedMode.DEGRADED_SAFE) {
			featureRegistry.disable(FeatureRegistry.FEAT_DELTA_SYNC);
			featureRegistry.disable(FeatureRegistry.FEAT_DIRECT_PASS);
			networkRuntime.configure(false, config.snapshotSync, false, config.packetBatching);
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
			networkRuntime.onTickEnd();
			if (memoryManager.garbageMonitor().shouldDisableSnapshots()) {
				sharedTags.setSuppressed(true);
			}
		}
	}

	private void applyFeatureGates() {
		if (!config.sharedTagEnabled) {
			featureRegistry.disable(FeatureRegistry.FEAT_SHARED_TAG);
		}
		if (!config.deltaSync) {
			featureRegistry.disable(FeatureRegistry.FEAT_DELTA_SYNC);
		}
		if (!config.integratedDirectPass) {
			featureRegistry.disable(FeatureRegistry.FEAT_DIRECT_PASS);
		}
		if (!config.poolEnabled) {
			featureRegistry.disable(FeatureRegistry.FEAT_MEMORY_POOL);
		}
		if (!config.fastCodec) {
			featureRegistry.disable(FeatureRegistry.FEAT_FAST_CODEC);
		}
		if (config.mode == RuntimePreset.SAFE) {
			featureRegistry.disable(FeatureRegistry.FEAT_DELTA_SYNC);
			featureRegistry.disable(FeatureRegistry.FEAT_DIRECT_PASS);
		}
	}

	private void transition(RuntimePhase next) {
		this.phase = next;
	}
}
