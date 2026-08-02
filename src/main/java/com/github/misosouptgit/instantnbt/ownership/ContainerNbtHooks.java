package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.CompoundTag;

/**
 * Container inventory save/load boundary (chests, barrels, hoppers, …).
 */
public final class ContainerNbtHooks {
	private ContainerNbtHooks() {}

	public static void beforeSaveAllItems() {
		TagWriteHooks.enterInternal();
	}

	public static void afterSaveAllItems(CompoundTag tag) {
		try {
			EntityNbtHooks.onSaved(tag);
		} finally {
			TagWriteHooks.leaveInternal();
		}
	}

	public static void beforeLoadAllItems() {
		TagWriteHooks.enterInternal();
	}

	public static void afterLoadAllItems(CompoundTag tag) {
		try {
			EntityNbtHooks.onLoaded(tag);
		} finally {
			TagWriteHooks.leaveInternal();
		}
	}
}
