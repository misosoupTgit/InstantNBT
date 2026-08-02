package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.config.InstantNbtConfig;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;

/**
 * ItemStack NBT tracking hooks. Never throws into vanilla.
 * Only attaches ownership for large tags when trackItemStackNbt is enabled (backpacks / curios etc.).
 */
public final class ItemStackNbtHooks {
	private ItemStackNbtHooks() {}

	public static void onTagPresent(CompoundTag tag) {
		if (tag == null || !InstantNbtRuntime.hotTracking()) {
			return;
		}
		try {
			OwnedTag existing = TrackedTagAccess.peek(tag);
			if (existing != null) {
				TagWriteHooks.onMutateOwned(existing);
				return;
			}
			InstantNbtConfig config = InstantNbtRuntime.get().config();
			if (!config.trackItemStackNbt || tag.size() < config.minFreezeKeys) {
				return;
			}
			OwnedTag created = OwnedTag.of(tag);
			if (config.autoFreezeSnapshot) {
				created.freeze();
			}
			InstantNbtRuntime.get().tracker().track(created);
		} catch (Throwable ignored) {
		}
	}
}
