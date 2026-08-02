package com.github.misosouptgit.instantnbt.memory;

/**
 * Allocation size classes (Project Plan 5.3).
 */
public enum AllocSizeClass {
	SMALL,
	MEDIUM,
	LARGE;

	public static final int SMALL_MAX = 256;
	public static final int MEDIUM_MAX = 4096;

	public static AllocSizeClass ofBytes(int bytes) {
		if (bytes <= SMALL_MAX) {
			return SMALL;
		}
		if (bytes <= MEDIUM_MAX) {
			return MEDIUM;
		}
		return LARGE;
	}
}
