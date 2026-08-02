package com.github.misosouptgit.instantnbt.diagnostics;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.compat.CompatReport;
import com.github.misosouptgit.instantnbt.memory.GarbageMonitor;
import com.github.misosouptgit.instantnbt.memory.MemoryManager;
import com.github.misosouptgit.instantnbt.memory.TagPool;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostics text + JSON export (Project Plan 16).
 */
public final class DiagnosticsService {
	private final InstantNbtRuntime runtime;

	public DiagnosticsService(InstantNbtRuntime runtime) {
		this.runtime = runtime;
	}

	public List<String> memoryLines() {
		MemoryManager memory = runtime.memory();
		GarbageMonitor gc = memory.garbageMonitor();
		List<String> lines = new ArrayList<>();
		lines.add("phase=" + runtime.phase() + " degraded=" + runtime.degradedMode());
		lines.add("started=" + memory.isStarted() + " pressure=" + gc.pressure() + " pressureEvents=" + gc.pressureEvents());
		lines.add("arenaLive=" + memory.allocator().arena().live() + " arenaGen=" + memory.allocator().arena().generation());
		lines.add("refFlushes=" + memory.refCounter().flushCount() + " bufferedOps=" + memory.refCounter().bufferedOps());
		lines.add("trackedOwnedTags=" + runtime.tracker().size() + " sharedRegistry=" + runtime.sharedTags().size());
		return lines;
	}

	public List<String> poolLines() {
		TagPool pool = runtime.memory().pool();
		List<String> lines = new ArrayList<>();
		lines.add(String.format("hitRate=%.2f hits=%d misses=%d shrinks=%d", pool.hitRate(), pool.hits(), pool.misses(), pool.shrinkEvents()));
		for (TagPool.Slot slot : TagPool.Slot.values()) {
			lines.add("slot." + slot.name().toLowerCase() + "=" + pool.inventory(slot));
		}
		return lines;
	}

	public List<String> ownershipLines() {
		List<String> lines = new ArrayList<>();
		lines.add("tracked=" + runtime.tracker().size());
		lines.add("sharedHits=" + runtime.sharedTags().hits() + " misses=" + runtime.sharedTags().misses()
			+ " collisions=" + runtime.sharedTags().collisions() + " suppressed=" + runtime.sharedTags().isSuppressed());
		lines.add("cowEnabledFeature=" + runtime.features().isEnabled(com.github.misosouptgit.instantnbt.compat.FeatureRegistry.FEAT_COW));
		return lines;
	}

	public List<String> networkLines() {
		List<String> lines = new ArrayList<>();
		lines.add("full=" + runtime.network().fullSyncs() + " delta=" + runtime.network().deltaSyncs()
			+ " snapshot=" + runtime.network().snapshotSyncs() + " direct=" + runtime.network().directPasses()
			+ " fallbacks=" + runtime.network().fallbacks());
		lines.add("transport directOffered=" + runtime.network().transport().directOffered()
			+ " packetsOffered=" + runtime.network().transport().packetsOffered()
			+ " packetsDelivered=" + runtime.network().transport().packetsDelivered());
		lines.add("integrated=" + runtime.integrated().isIntegrated());
		lines.add("encode=" + runtime.serializer().encodeCount() + " decode=" + runtime.serializer().decodeCount()
			+ " legacyRetries=" + runtime.serializer().legacyRetries()
			+ " guardRejections=" + runtime.serializer().guardRejections());
		return lines;
	}

	public List<String> compatLines() {
		CompatReport report = runtime.compat().lastReport();
		List<String> lines = new ArrayList<>();
		lines.add("present=" + report.presentMods().size() + " unknown=" + report.unknownMods().size()
			+ " unknownSafe=" + report.unknownSafeApplied());
		lines.add("fired=" + String.join(", ", report.firedRules()));
		for (String disabled : report.disabledFeatures()) {
			lines.add("disabled: " + disabled);
		}
		for (String warning : report.warnings()) {
			lines.add("warn: " + warning);
		}
		for (String rec : report.recommendations()) {
			lines.add("recommend: " + rec);
		}
		return lines;
	}

	public List<String> profilerLines() {
		List<String> lines = new ArrayList<>();
		lines.add("serializer encode/decode counts only (micro profiler TBD)");
		lines.add("encode=" + runtime.serializer().encodeCount() + " decode=" + runtime.serializer().decodeCount());
		lines.add("optimizationsActive=" + runtime.optimizationsActive() + " killSwitch=" + runtime.killSwitch().isEngaged());
		return lines;
	}

	public Path exportJson() throws IOException {
		Path dir = Paths.get("config", "instantnbt", "diagnostics");
		Files.createDirectories(dir);
		Path out = dir.resolve("dump-" + Instant.now().toString().replace(":", "-") + ".json");
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
		sb.append("  \"phase\": \"").append(runtime.phase()).append("\",\n");
		sb.append("  \"degraded\": \"").append(runtime.degradedMode()).append("\",\n");
		sb.append("  \"killSwitch\": ").append(runtime.killSwitch().isEngaged()).append(",\n");
		sb.append("  \"killReason\": \"").append(escape(runtime.killSwitch().reason())).append("\",\n");
		sb.append("  \"features\": [");
		boolean first = true;
		for (String f : runtime.features().enabledFeatures()) {
			if (!first) {
				sb.append(", ");
			}
			sb.append("\"").append(escape(f)).append("\"");
			first = false;
		}
		sb.append("],\n");
		sb.append("  \"memory\": ").append(toJsonArray(memoryLines())).append(",\n");
		sb.append("  \"pool\": ").append(toJsonArray(poolLines())).append(",\n");
		sb.append("  \"ownership\": ").append(toJsonArray(ownershipLines())).append(",\n");
		sb.append("  \"network\": ").append(toJsonArray(networkLines())).append(",\n");
		sb.append("  \"compat\": ").append(toJsonArray(compatLines())).append("\n");
		sb.append("}\n");
		Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
		InstantNBT.LOGGER.info("Diagnostics exported to {}", out.toAbsolutePath());
		return out;
	}

	private static String toJsonArray(List<String> lines) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append("\"").append(escape(lines.get(i))).append("\"");
		}
		sb.append("]");
		return sb.toString();
	}

	private static String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
