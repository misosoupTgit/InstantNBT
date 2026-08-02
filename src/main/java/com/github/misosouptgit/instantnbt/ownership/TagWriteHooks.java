package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;

/**
 * Safe write hook used by NBT mixins. Never throws into vanilla write paths.
 * Hot path: mixins skip entirely when {@code instantnbt$owned == null}.
 */
public final class TagWriteHooks {
	private static final ThreadLocal<Integer> SUPPRESS = ThreadLocal.withInitial(() -> 0);

	private TagWriteHooks() {}

	/** Suppress mutation hooks during InstantNBT-owned decode/copy construction. */
	public static void enterInternal() {
		SUPPRESS.set(SUPPRESS.get() + 1);
	}

	public static void leaveInternal() {
		int depth = SUPPRESS.get() - 1;
		if (depth <= 0) {
			SUPPRESS.remove();
		} else {
			SUPPRESS.set(depth);
		}
	}

	public static boolean isSuppressed() {
		return SUPPRESS.get() > 0;
	}

	public static void onMutateOwned(OwnedTag owned) {
		if (owned == null || !owned.hasMeta() || isSuppressed()) {
			return;
		}
		if (!InstantNbtRuntime.hotTracking()) {
			return;
		}
		try {
			if (owned.isFrozen() || owned.isShared()) {
				owned.ensureWritableSoft();
			} else {
				owned.markDirty();
			}
		} catch (Throwable ignored) {
			// Absolute last resort: vanilla mutation must proceed.
		}
	}

	/** Compatibility entry for non-mixin callers. */
	public static void onMutate(net.minecraft.nbt.Tag tag) {
		if (isSuppressed()) {
			return;
		}
		onMutateOwned(TrackedTagAccess.peek(tag));
	}
}
