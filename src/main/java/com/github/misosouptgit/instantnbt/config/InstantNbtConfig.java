package com.github.misosouptgit.instantnbt.config;

import com.github.misosouptgit.instantnbt.InstantNBT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * InstantNBT config loader (Project Plan 15). Safe defaults; simple TOML-ish subset.
 */
public final class InstantNbtConfig {
	public boolean runtimeEnabled = true;
	public RuntimePreset mode = RuntimePreset.BALANCED;

	public boolean arenaEnabled = true;
	public boolean poolEnabled = true;
	public boolean sharedTagEnabled = false;
	public boolean gcMonitorEnabled = true;

	public boolean ownershipStrict = true;
	public boolean enforceAcquireRelease = false;
	public boolean trackItemStackNbt = false;
	public boolean trackChunkNbt = false;
	public boolean copyCowEnabled = true;
	public boolean autoInternOnFreeze = false;
	public boolean autoFreezeSnapshot = true;
	/** Skip freeze/track for tiny compounds (keys). */
	public int minFreezeKeys = 3;
	public int chunkEncodeThresholdBytes = 64 * 1024;

	public boolean fastCodec = true;
	/** Swap vanilla NbtIo binary codec (region/player IO). */
	public boolean nbtIoRedirect = true;
	public boolean legacyFallback = true;
	public boolean lazyDeserialize = false;
	public boolean unsafeIO = false;

	public boolean deltaSync = false;
	public boolean snapshotSync = true;
	public boolean packetBatching = false;
	public boolean integratedDirectPass = false;

	public boolean autoDetectMods = true;
	/** soft = warn only; strict = disable delta/direct-pass; off = ignore unknowns. */
	public String unknownModPolicy = "soft";
	public boolean forceLegacyForUnknown = false;

	public boolean diagnosticsCommand = true;
	public boolean diagnosticsOverlay = false;
	public boolean exportJson = true;

	public boolean killSwitch = false;
	public boolean killSwitchPersistDisable = false;

	public static InstantNbtConfig defaults() {
		return new InstantNbtConfig();
	}

	public void applyPreset(RuntimePreset preset) {
		this.mode = preset == null ? RuntimePreset.BALANCED : preset;
		switch (this.mode) {
			case SAFE:
				deltaSync = false;
				integratedDirectPass = false;
				fastCodec = false;
				nbtIoRedirect = false;
				legacyFallback = true;
				forceLegacyForUnknown = true;
				unknownModPolicy = "strict";
				ownershipStrict = true;
				sharedTagEnabled = false;
				trackItemStackNbt = false;
				trackChunkNbt = false;
				copyCowEnabled = false;
				autoInternOnFreeze = false;
				autoFreezeSnapshot = false;
				lazyDeserialize = false;
				unsafeIO = false;
				break;
			case AGGRESSIVE:
				deltaSync = true;
				integratedDirectPass = true;
				packetBatching = true;
				fastCodec = true;
				nbtIoRedirect = true;
				legacyFallback = true;
				sharedTagEnabled = true;
				ownershipStrict = true;
				trackItemStackNbt = true;
				trackChunkNbt = true;
				copyCowEnabled = true;
				autoInternOnFreeze = true;
				autoFreezeSnapshot = true;
				minFreezeKeys = 4;
				lazyDeserialize = false;
				chunkEncodeThresholdBytes = 32 * 1024;
				forceLegacyForUnknown = false;
				unknownModPolicy = "soft";
				unsafeIO = false;
				break;
			case BALANCED:
			default:
				deltaSync = false;
				integratedDirectPass = false;
				packetBatching = false;
				fastCodec = true;
				nbtIoRedirect = true;
				legacyFallback = true;
				sharedTagEnabled = false;
				ownershipStrict = true;
				enforceAcquireRelease = false;
				trackItemStackNbt = false;
				trackChunkNbt = false;
				copyCowEnabled = true;
				autoInternOnFreeze = false;
				autoFreezeSnapshot = true;
				minFreezeKeys = 3;
				// Plan 9: pool/arena on — targets GC hitch (felt as FPS stutter), not render throughput.
				poolEnabled = true;
				arenaEnabled = true;
				lazyDeserialize = false;
				chunkEncodeThresholdBytes = 64 * 1024;
				forceLegacyForUnknown = false;
				unknownModPolicy = "soft";
				unsafeIO = false;
				diagnosticsOverlay = false;
				break;
		}
	}

	public static InstantNbtConfig load(Path path) {
		InstantNbtConfig config = defaults();
		if (path == null) {
			return config;
		}
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path.getParent());
				config.write(path);
				return config;
			}
			config.read(path);
		} catch (IOException ex) {
			InstantNBT.LOGGER.warn("Failed to load config {}; using defaults ({})", path, ex.toString());
		}
		return config;
	}

	public void write(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			w.write("# InstantNBT config — safe defaults. Restart may be required after changes.\n");
			w.write("[runtime]\n");
			w.write("enabled = " + runtimeEnabled + "\n");
			w.write("mode = \"" + mode.name().toLowerCase(Locale.ROOT) + "\"\n\n");
			w.write("[memory]\n");
			w.write("arenaEnabled = " + arenaEnabled + "\n");
			w.write("poolEnabled = " + poolEnabled + "\n");
			w.write("sharedTagEnabled = " + sharedTagEnabled + "\n");
			w.write("gcMonitorEnabled = " + gcMonitorEnabled + "\n\n");
			w.write("[ownership]\n");
			w.write("strict = " + ownershipStrict + "\n");
			w.write("enforceAcquireRelease = " + enforceAcquireRelease + "\n");
			w.write("autoFreezeSnapshot = " + autoFreezeSnapshot + "\n");
			w.write("trackItemStackNbt = " + trackItemStackNbt + "\n");
			w.write("trackChunkNbt = " + trackChunkNbt + "\n");
			w.write("copyCowEnabled = " + copyCowEnabled + "\n");
			w.write("autoInternOnFreeze = " + autoInternOnFreeze + "\n");
			w.write("minFreezeKeys = " + minFreezeKeys + "\n\n");
			w.write("[serializer]\n");
			w.write("fastCodec = " + fastCodec + "\n");
			w.write("nbtIoRedirect = " + nbtIoRedirect + "\n");
			w.write("legacyFallback = " + legacyFallback + "\n");
			w.write("lazyDeserialize = " + lazyDeserialize + "\n");
			w.write("chunkEncodeThresholdBytes = " + chunkEncodeThresholdBytes + "\n");
			w.write("unsafeIO = " + unsafeIO + "\n\n");
			w.write("[network]\n");
			w.write("deltaSync = " + deltaSync + "\n");
			w.write("snapshotSync = " + snapshotSync + "\n");
			w.write("packetBatching = " + packetBatching + "\n");
			w.write("integratedDirectPass = " + integratedDirectPass + "\n\n");
			w.write("[compat]\n");
			w.write("autoDetectMods = " + autoDetectMods + "\n");
			w.write("unknownModPolicy = \"" + unknownModPolicy + "\"\n");
			w.write("forceLegacyForUnknown = " + forceLegacyForUnknown + "\n\n");
			w.write("[diagnostics]\n");
			w.write("commandEnabled = " + diagnosticsCommand + "\n");
			w.write("overlayEnabled = " + diagnosticsOverlay + "\n");
			w.write("exportJson = " + exportJson + "\n\n");
			w.write("[safety]\n");
			w.write("killSwitch = " + killSwitch + "\n");
			w.write("killSwitchPersistDisable = " + killSwitchPersistDisable + "\n");
		}
	}

	private void read(Path path) throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		String section = "";
		try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
					continue;
				}
				if (line.startsWith("[") && line.endsWith("]")) {
					section = line.substring(1, line.length() - 1).trim();
					continue;
				}
				int eq = line.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				String key = line.substring(0, eq).trim();
				String value = stripQuotes(line.substring(eq + 1).trim());
				values.put(section + "." + key, value);
			}
		}
		runtimeEnabled = bool(values, "runtime.enabled", runtimeEnabled);
		mode = RuntimePreset.parse(str(values, "runtime.mode", mode.name()));
		applyPreset(mode);

		arenaEnabled = bool(values, "memory.arenaEnabled", arenaEnabled);
		poolEnabled = bool(values, "memory.poolEnabled", poolEnabled);
		sharedTagEnabled = bool(values, "memory.sharedTagEnabled", sharedTagEnabled);
		gcMonitorEnabled = bool(values, "memory.gcMonitorEnabled", gcMonitorEnabled);

		ownershipStrict = bool(values, "ownership.strict", ownershipStrict);
		enforceAcquireRelease = bool(values, "ownership.enforceAcquireRelease", enforceAcquireRelease);
		autoFreezeSnapshot = bool(values, "ownership.autoFreezeSnapshot", autoFreezeSnapshot);
		trackItemStackNbt = bool(values, "ownership.trackItemStackNbt", trackItemStackNbt);
		trackChunkNbt = bool(values, "ownership.trackChunkNbt", trackChunkNbt);
		copyCowEnabled = bool(values, "ownership.copyCowEnabled", copyCowEnabled);
		autoInternOnFreeze = bool(values, "ownership.autoInternOnFreeze", autoInternOnFreeze);
		minFreezeKeys = integer(values, "ownership.minFreezeKeys", minFreezeKeys);

		fastCodec = bool(values, "serializer.fastCodec", fastCodec);
		nbtIoRedirect = bool(values, "serializer.nbtIoRedirect", nbtIoRedirect);
		legacyFallback = bool(values, "serializer.legacyFallback", legacyFallback);
		lazyDeserialize = bool(values, "serializer.lazyDeserialize", lazyDeserialize);
		chunkEncodeThresholdBytes = integer(values, "serializer.chunkEncodeThresholdBytes", chunkEncodeThresholdBytes);
		unsafeIO = bool(values, "serializer.unsafeIO", unsafeIO);

		deltaSync = bool(values, "network.deltaSync", deltaSync);
		snapshotSync = bool(values, "network.snapshotSync", snapshotSync);
		packetBatching = bool(values, "network.packetBatching", packetBatching);
		integratedDirectPass = bool(values, "network.integratedDirectPass", integratedDirectPass);

		autoDetectMods = bool(values, "compat.autoDetectMods", autoDetectMods);
		unknownModPolicy = str(values, "compat.unknownModPolicy", unknownModPolicy);
		forceLegacyForUnknown = bool(values, "compat.forceLegacyForUnknown", forceLegacyForUnknown);
		if (forceLegacyForUnknown && "soft".equalsIgnoreCase(unknownModPolicy)) {
			// Backward-compat: old configs that set forceLegacyForUnknown=true mean strict.
			unknownModPolicy = "strict";
		}

		diagnosticsCommand = bool(values, "diagnostics.commandEnabled", diagnosticsCommand);
		diagnosticsOverlay = bool(values, "diagnostics.overlayEnabled", diagnosticsOverlay);
		exportJson = bool(values, "diagnostics.exportJson", exportJson);

		killSwitch = bool(values, "safety.killSwitch", killSwitch);
		killSwitchPersistDisable = bool(values, "safety.killSwitchPersistDisable", killSwitchPersistDisable);
	}

	private static String stripQuotes(String value) {
		if (value.length() >= 2) {
			char a = value.charAt(0);
			char b = value.charAt(value.length() - 1);
			if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}

	private static boolean bool(Map<String, String> map, String key, boolean fallback) {
		String v = map.get(key);
		if (v == null) {
			return fallback;
		}
		return Boolean.parseBoolean(v);
	}

	private static int integer(Map<String, String> map, String key, int fallback) {
		String v = map.get(key);
		if (v == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private static String str(Map<String, String> map, String key, String fallback) {
		String v = map.get(key);
		return v == null ? fallback : v;
	}
}
