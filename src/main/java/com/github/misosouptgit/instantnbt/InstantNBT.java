package com.github.misosouptgit.instantnbt;

import com.github.misosouptgit.instantnbt.ownership.ModuleDomain;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.Owner;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.Tag;

/**
 * Public InstantNBT API (Project Plan 14.2). Optional — never forced on other mods.
 */
public final class InstantNBT {
	public static final String MOD_ID = "instantnbt";
	public static final org.apache.logging.log4j.Logger LOGGER =
		org.apache.logging.log4j.LogManager.getLogger("InstantNBT");

	private static boolean initialized;

	private InstantNBT() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		InstantNbtRuntime.get().bootstrap();
		LOGGER.info("InstantNBT initialized (phase={})", InstantNbtRuntime.get().phase());
	}

	public static InstantNbtRuntime runtime() {
		return InstantNbtRuntime.get();
	}

	public static OwnedTag wrap(Tag tag) {
		return OwnedTag.of(tag);
	}

	public static OwnedTag share(Tag tag) {
		OwnedTag owned = ensureOwned(tag);
		if (!runtime().optimizationsActive() || !runtime().config().sharedTagEnabled) {
			owned.share();
			return owned;
		}
		return runtime().sharedTags().intern(owned);
	}

	public static OwnedTag share(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		if (!runtime().optimizationsActive() || !runtime().config().sharedTagEnabled) {
			tag.share();
			return tag;
		}
		return runtime().sharedTags().intern(tag);
	}

	public static OwnedTag snapshot(Tag tag) {
		OwnedTag owned = ensureOwned(tag).copyUnique(Owner.current(ModuleDomain.RUNTIME));
		owned.freeze();
		return owned;
	}

	public static OwnedTag snapshot(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		OwnedTag copy = tag.copyUnique(Owner.current(ModuleDomain.RUNTIME));
		copy.freeze();
		return copy;
	}

	public static OwnedTag freeze(Tag tag) {
		OwnedTag owned = ensureOwned(tag);
		owned.freeze();
		return owned;
	}

	public static OwnedTag freeze(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		tag.freeze();
		return tag;
	}

	public static OwnedTag copy(Tag tag) {
		return ensureOwned(tag).copyUnique(Owner.current(ModuleDomain.RUNTIME));
	}

	public static OwnedTag copy(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		return tag.copyUnique(Owner.current(ModuleDomain.RUNTIME));
	}

	public static boolean isShared(Tag tag) {
		OwnedTag tracked = runtime().tracker().find(tag);
		return tracked != null && tracked.isShared();
	}

	public static boolean isShared(OwnedTag tag) {
		return tag != null && tag.isShared();
	}

	public static OwnedTag pin(Tag tag) {
		OwnedTag owned = ensureOwned(tag);
		owned.pin();
		return owned;
	}

	public static OwnedTag pin(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		tag.pin();
		return tag;
	}

	public static void unpin(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		tag.unpin();
	}

	public static OwnedTag acquire(Tag tag) {
		OwnedTag owned = ensureOwned(tag);
		owned.acquire(Owner.current(ModuleDomain.EXTERNAL));
		return owned;
	}

	public static OwnedTag acquire(OwnedTag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		tag.acquire(Owner.current(ModuleDomain.EXTERNAL));
		return tag;
	}

	public static void release(OwnedTag tag) {
		if (tag == null) {
			return;
		}
		tag.release();
		runtime().sharedTags().releaseIfOrphaned(tag);
	}

	private static OwnedTag ensureOwned(Tag tag) {
		if (tag == null) {
			throw new IllegalArgumentException("tag");
		}
		OwnedTag existing = runtime().tracker().find(tag);
		if (existing != null) {
			return existing;
		}
		OwnedTag created = OwnedTag.of(tag);
		runtime().tracker().track(created);
		return created;
	}
}
