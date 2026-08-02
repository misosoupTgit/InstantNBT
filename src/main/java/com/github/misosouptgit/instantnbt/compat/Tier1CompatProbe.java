package com.github.misosouptgit.instantnbt.compat;

import com.github.misosouptgit.instantnbt.InstantNBT;
import dev.architectury.platform.Platform;

/**
 * Tier1 (AE2 / Create) classpath / presence probe (Plan 13.7).
 * Uses reflection so compileOnly deps are optional.
 */
public final class Tier1CompatProbe {
	private Tier1CompatProbe() {}

	public static void logPresence() {
		boolean ae2 = Platform.isModLoaded("ae2") || Platform.isModLoaded("appliedenergistics2");
		boolean create = Platform.isModLoaded("create");
		boolean ae2Api = classPresent("appeng.api.stacks.AEItemKey") || classPresent("appeng.core.AEConfig");
		boolean createApi = classPresent("com.simibubi.create.Create") || classPresent("com.simibubi.create.foundation.utility.NBTHelper");
		InstantNBT.LOGGER.info(
			"Tier1 compat probe: ae2Mod={}, createMod={}, ae2ApiOnClasspath={}, createApiOnClasspath={}",
			ae2,
			create,
			ae2Api,
			createApi
		);
	}

	private static boolean classPresent(String name) {
		try {
			Class.forName(name, false, Tier1CompatProbe.class.getClassLoader());
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}
}
