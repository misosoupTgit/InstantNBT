package com.github.misosouptgit.instantnbt.ownership;

/**
 * Mixin-backed O(1) ownership slot on CompoundTag / ListTag.
 */
public interface InstantNbtTagged {
	OwnedTag instantnbt$getOwned();

	void instantnbt$setOwned(OwnedTag owned);
}
