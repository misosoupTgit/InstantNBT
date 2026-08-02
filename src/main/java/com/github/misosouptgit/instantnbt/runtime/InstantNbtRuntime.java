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

	/** Hot-path flags — avoid InstantNbtRuntime.get()+config on every NBT mutation/copy. */
	private static volatile boolean HOT_TRACKING;
	private static volatile boolean HOT_COPY_COW;
	private static volatile boolean HOT_OPTS;

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
	private final SafetyCoordinator safety = new SafetyCoordinator(this);

	private InstantNbtConfig config = InstantNbtConfig.defaults();
	private volatile RuntimePhase phase = RuntimePhase.BOOTSTRAP;
	private volatile DegradedMode degradedMode = DegradedMode.NONE;

	private InstantNbtRuntime() {}

	public static InstantNbtRuntime get() {
		return INSTANCE;
	}

	public static boolean hotTracking() {
		return HOT_TRACKING;
	}

	public static boolean hotCopyCow() {
		return HOT_COPY_COW;
	}

	public static boolean hotOpts() {
		return HOT_OPTS;
	}

	private void refreshHotFlags() {
		HOT_TRACKING = trackingActive();
		boolean opts = optimizationsActive();
		HOT_COPY_COW = opts && config.copyCowEnabled && featureRegistry.isEnabled(FeatureRegistry.FEAT_COW);
		// Tick maintenance only when something actually needs per-tick work.
		HOT_OPTS = opts && (
			memoryManager.isStarted()
				|| config.deltaSync
				|| config.packetBatching
				|| config.integratedDirectPass
		);
	}

	/** Called from KillSwitch.reset / external toggles. */
	public void refreshHotFlagsPublic() {
		refreshHotFlags();
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

	public SafetyCoordinator safety() {
		return safety;
	}

	/**
	 * Heavy optimizations (delta / direct-pass / shared intern). Off under kill / MINIMAL.
	 */
	public boolean optimizationsActive() {
		return config.runtimeEnabled
			&& !killSwitch.isEngaged()
			&& degradedMode != DegradedMode.DEGRADED_MINIMAL;
	}

	/**
	 * Lightweight tracking / soft CoW hooks. Remains available in DEGRADED_SAFE/COMPAT.
	 */
	public boolean trackingActive() {
		return config.runtimeEnabled && !killSwitch.isEngaged();
	}

	public synchronized void bootstrap() {
		if (phase == RuntimePhase.RUNNING || phase == RuntimePhase.DEGRADED) {
			return;
		}
		transition(RuntimePhase.BOOTSTRAP);

		Path configPath = Paths.get("config", "instantnbt-common.toml");
		// load() already applies preset then file overrides — do not re-applyPreset (wipes overrides).
		config = InstantNbtConfig.load(configPath);

		transition(RuntimePhase.CAPABILITY_SCAN);
		featureRegistry.scanDefaults();
		applyFeatureGates();
		compatEngine.scanAndApply(this);
		com.github.misosouptgit.instantnbt.compat.Tier1CompatProbe.logPresence();

		transition(RuntimePhase.RUNTIME_INIT);
		if (config.poolEnabled || config.arenaEnabled) {
			memoryManager.start();
		}
		serializer.configure(config.fastCodec, config.legacyFallback, config.mode == RuntimePreset.SAFE && !config.fastCodec);
		serializer.configureLazy(config.lazyDeserialize, config.chunkEncodeThresholdBytes);
		serializer.configureUnsafeIo(config.unsafeIO && config.mode == RuntimePreset.AGGRESSIVE);
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
		refreshHotFlags();
		InstantNBT.LOGGER.info(
			"InstantNBT Runtime {} (mode={}, features={}, degraded={}, copyCow={}, trackChunk={}, autoIntern={})",
			phase,
			config.mode,
			featureRegistry.enabledFeatures(),
			degradedMode,
			config.copyCowEnabled,
			config.trackChunkNbt,
			config.autoInternOnFreeze
		);
	}

	public synchronized void shutdown() {
		if (phase == RuntimePhase.SHUTDOWN) {
			return;
		}
		memoryManager.shutdown();
		sharedTags.clear();
		tracker.clear();
		HOT_TRACKING = false;
		HOT_COPY_COW = false;
		HOT_OPTS = false;
		transition(RuntimePhase.SHUTDOWN);
		InstantNBT.LOGGER.info("InstantNBT Runtime shut down");
	}

	public synchronized void enterDegraded(DegradedMode mode, String reason) {
		if (mode == null || mode == DegradedMode.NONE) {
			degradedMode = DegradedMode.NONE;
			if (phase == RuntimePhase.DEGRADED && !killSwitch.isEngaged()) {
				transition(RuntimePhase.RUNNING);
			}
			refreshHotFlags();
			return;
		}
		// Never escalate downward accidentally (MINIMAL stays MINIMAL).
		if (mode.ordinal() < degradedMode.ordinal()) {
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
			// Keep tracking / CoW / serializer available — only drop risky network shortcuts.
		} else if (mode == DegradedMode.DEGRADED_COMPAT) {
			featureRegistry.disable(FeatureRegistry.FEAT_DELTA_SYNC);
			networkRuntime.configure(false, config.snapshotSync, config.integratedDirectPass, config.packetBatching);
		}
		transition(RuntimePhase.DEGRADED);
		refreshHotFlags();
		InstantNBT.LOGGER.warn("InstantNBT entered {} ({})", mode, reason);
	}

	public void onServerTickEnd() {
		com.github.misosouptgit.instantnbt.diagnostics.StressHarness.tick();
		if (!HOT_OPTS) {
			return;
		}
		if (phase != RuntimePhase.RUNNING && phase != RuntimePhase.DEGRADED) {
			return;
		}
		if (memoryManager.isStarted()) {
			memoryManager.onTickEnd();
			memoryManager.respondToPressure();
			if (memoryManager.garbageMonitor().shouldDisableSnapshots()) {
				sharedTags.setSuppressed(true);
			}
		}
		networkRuntime.onTickEnd();
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
