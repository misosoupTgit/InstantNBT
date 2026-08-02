package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;

/**
 * ItemStack NBT tracking hooks. Never throws into vanilla.
 */
public final class ItemStackNbtHooks {
	private ItemStackNbtHooks() {}

	public static void onTagPresent(CompoundTag tag) {
		if (tag == null) {
			return;
		}
		try {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			if (!runtime.trackingActive()) {
				return;
			}
			OwnedTag existing = runtime.tracker().find(tag);
			if (existing == null) {
				if (!runtime.config().trackItemStackNbt) {
					return;
				}
				OwnedTag created = OwnedTag.of(tag);
				runtime.tracker().track(created);
				return;
			}
			TagWriteHooks.onMutate(tag);
		} catch (Throwable ignored) {
		}
	}
}
