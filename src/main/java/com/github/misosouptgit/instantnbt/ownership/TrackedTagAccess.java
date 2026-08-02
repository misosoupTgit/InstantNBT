package com.github.misosouptgit.instantnbt.ownership;

import net.minecraft.nbt.Tag;

/**
 * Hot-path ownership peek/bind without map locks.
 */
public final class TrackedTagAccess {
	private TrackedTagAccess() {}

	public static OwnedTag peek(Tag tag) {
		if (tag instanceof InstantNbtTagged) {
			return ((InstantNbtTagged) tag).instantnbt$getOwned();
		}
		return null;
	}

	public static void bind(Tag tag, OwnedTag owned) {
		if (tag instanceof InstantNbtTagged) {
			((InstantNbtTagged) tag).instantnbt$setOwned(owned);
		}
	}
}
