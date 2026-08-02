package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.Tag;

/**
 * Safe write hook used by NBT mixins. Never throws into vanilla write paths.
 */
public final class TagWriteHooks {
	private TagWriteHooks() {}

	public static void onMutate(Tag tag) {
		try {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			if (!runtime.trackingActive()) {
				return;
			}
			OwnedTag owned = runtime.tracker().find(tag);
			if (owned == null || !owned.hasMeta()) {
				return;
			}
			if (owned.isFrozen() || owned.isShared()) {
				owned.ensureWritableSoft();
			} else {
				owned.markDirty();
			}
		} catch (Throwable ignored) {
			// Absolute last resort: vanilla mutation must proceed.
		}
	}
}
