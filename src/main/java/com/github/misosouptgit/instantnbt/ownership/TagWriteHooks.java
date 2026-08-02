package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.Tag;

/**
 * Safe write hook used by NBT mixins. No-op unless optimizations are active and the tag is tracked.
 */
public final class TagWriteHooks {
	private TagWriteHooks() {}

	public static void onMutate(Tag tag) {
		InstantNbtRuntime runtime = InstantNbtRuntime.get();
		if (!runtime.optimizationsActive()) {
			return;
		}
		OwnedTag owned = runtime.tracker().find(tag);
		if (owned == null || !owned.hasMeta()) {
			return;
		}
		try {
			if (owned.isFrozen() || owned.isShared()) {
				owned.ensureWritable();
			} else {
				owned.markDirty();
			}
		} catch (RuntimeException ignored) {
			// Safe-by-default: never break vanilla write paths.
		}
	}
}
