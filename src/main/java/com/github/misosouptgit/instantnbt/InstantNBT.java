package com.github.misosouptgit.instantnbt;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * InstantNBT Runtime bootstrap entry.
 */
public final class InstantNBT {
	public static final String MOD_ID = "instantnbt";
	public static final Logger LOGGER = LogManager.getLogger("InstantNBT");

	private static boolean initialized;

	private InstantNBT() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		InstantNbtRuntime.get().bootstrap();
		LOGGER.info("InstantNBT initialized (phase={})", InstantNbtRuntime.get().phase());
	}

	public static InstantNbtRuntime runtime() {
		return InstantNbtRuntime.get();
	}
}
