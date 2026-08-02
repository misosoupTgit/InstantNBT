package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;

/**
 * Hooks for Entity / BlockEntity NBT save-load paths. Never throws into vanilla.
 */
public final class EntityNbtHooks {
	private EntityNbtHooks() {}

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
			OwnedTag owned = runtime.tracker().find(tag);
			if (owned == null) {
				owned = OwnedTag.of(tag);
				runtime.tracker().track(owned);
			}
			if (saved && runtime.config().autoFreezeSnapshot) {
				// Save boundary = natural immutable snapshot for subsequent cheap CoW copies.
				if (!owned.isFrozen()) {
					owned.freeze();
				}
			} else if (saved) {
				TagWriteHooks.onMutate(tag);
			}
		} catch (Throwable ignored) {
		}
	}
}
