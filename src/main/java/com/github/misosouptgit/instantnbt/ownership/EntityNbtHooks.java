package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.config.InstantNbtConfig;
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
			if (!InstantNbtRuntime.hotTracking()) {
				return;
			}
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			InstantNbtConfig config = runtime.config();
			boolean wantCow = config.copyCowEnabled || config.autoFreezeSnapshot;
			if (!wantCow) {
				OwnedTag existing = TrackedTagAccess.peek(tag);
				if (existing != null && saved) {
					TagWriteHooks.onMutate(tag);
				}
				return;
			}
			if (saved && !NbtSizeHeuristics.worthOwnership(tag, config)) {
				return;
			}
			OwnedTag owned = TrackedTagAccess.peek(tag);
			if (owned == null) {
				owned = OwnedTag.of(tag);
				runtime.tracker().track(owned);
			}
			if (saved && config.autoFreezeSnapshot) {
				if (!owned.isFrozen()) {
					owned.freeze();
				}
				if (config.autoInternOnFreeze
					&& config.sharedTagEnabled
					&& InstantNbtRuntime.hotOpts()) {
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
