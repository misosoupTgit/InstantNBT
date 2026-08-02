package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;

/**
 * Hooks for LevelChunk / ChunkSerializer NBT save-load paths. Never throws into vanilla.
 */
public final class ChunkNbtHooks {
	private ChunkNbtHooks() {}

	public static void onSaved(CompoundTag tag) {
		track(tag, true);
	}

	public static void onLoaded(CompoundTag tag) {
		track(tag, false);
	}

	private static void track(CompoundTag tag, boolean saved) {
		if (tag == null) {
			return;
		}
		try {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			if (!runtime.trackingActive()) {
				return;
			}
			if (!runtime.config().trackChunkNbt) {
				OwnedTag existing = runtime.tracker().find(tag);
				if (existing != null && saved) {
					TagWriteHooks.onMutate(tag);
				}
				return;
			}
			OwnedTag owned = runtime.tracker().find(tag);
			if (owned == null) {
				owned = OwnedTag.of(tag);
				runtime.tracker().track(owned);
			}
			if (saved && runtime.config().autoFreezeSnapshot) {
				if (!owned.isFrozen()) {
					owned.freeze();
				}
				if (runtime.config().autoInternOnFreeze
					&& runtime.config().sharedTagEnabled
					&& runtime.optimizationsActive()) {
					try {
						runtime.sharedTags().intern(owned);
					} catch (Throwable ignored) {
					}
				}
			} else if (saved) {
				TagWriteHooks.onMutate(tag);
			}
		} catch (Throwable ignored) {
		}
	}
}
