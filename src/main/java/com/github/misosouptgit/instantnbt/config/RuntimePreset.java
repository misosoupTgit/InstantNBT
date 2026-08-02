package com.github.misosouptgit.instantnbt.config;

/**
 * Runtime presets (Project Plan 15.3).
 */
public enum RuntimePreset {
	SAFE,
	BALANCED,
	AGGRESSIVE;

	public static RuntimePreset parse(String raw) {
		if (raw == null || raw.isEmpty()) {
			return BALANCED;
		}
		switch (raw.trim().toLowerCase()) {
			case "safe":
				return SAFE;
			case "aggressive":
				return AGGRESSIVE;
			case "balanced":
			default:
				return BALANCED;
		}
	}
}
