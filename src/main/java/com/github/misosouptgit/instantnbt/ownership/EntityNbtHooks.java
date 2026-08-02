package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;

/**
 * Hooks for Entity / BlockEntity NBT save-load paths.
 */
public final class EntityNbtHooks {
	private EntityNbtHooks() {}

	public static void onSaved(CompoundTag tag) {
		if (tag == null) {
			return;
		}
		InstantNbtRuntime runtime = InstantNbtRuntime.get();
		if (!runtime.optimizationsActive()) {
			return;
		}
		OwnedTag owned = runtime.tracker().find(tag);
		if (owned == null) {
			owned = OwnedTag.of(tag);
			runtime.tracker().track(owned);
		}
		if (runtime.config().autoFreezeSnapshot && owned.hasMeta() && !owned.isFrozen()) {
			// Save path is a natural snapshot boundary.
			owned.markDirty();
		} else {
			TagWriteHooks.onMutate(tag);
		}
	}

	public static void onLoaded(CompoundTag tag) {
		if (tag == null) {
			return;
		}
		InstantNbtRuntime runtime = InstantNbtRuntime.get();
		if (!runtime.optimizationsActive()) {
			return;
		}
		OwnedTag owned = runtime.tracker().find(tag);
		if (owned == null) {
			owned = OwnedTag.of(tag);
			runtime.tracker().track(owned);
		}
	}
}
