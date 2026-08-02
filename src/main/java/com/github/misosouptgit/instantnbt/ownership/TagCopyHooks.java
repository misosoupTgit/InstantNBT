package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Accelerates {@link net.minecraft.nbt.Tag#copy()} for frozen/shared roots via structural CoW.
 * Nested compounds stay unique per tree; only immutable leaves are identity-shared (safe).
 * Nested nodes are NOT tracker-registered (avoids O(nodes) map cost that erased CoW gains).
 */
public final class TagCopyHooks {
	private static final AtomicLong HITS = new AtomicLong();
	private static final AtomicLong MISSES = new AtomicLong();
	private static final AtomicLong BYTES_SAVED_EST = new AtomicLong();

	private TagCopyHooks() {}

	public static boolean enabled() {
		return InstantNbtRuntime.hotCopyCow();
	}

	public static CompoundTag tryCopyCompound(CompoundTag original) {
		if (original == null || !enabled()) {
			return null;
		}
		try {
			OwnedTag owned = TrackedTagAccess.peek(original);
			if (owned == null || (!owned.isFrozen() && !owned.isShared())) {
				return null;
			}
			CompoundTag copy = CowEngine.shallowStructureCopyCompound(original);
			OwnedTag copyOwned = OwnedTag.of(copy);
			if (owned.isFrozen()) {
				copyOwned.freeze();
			} else {
				copyOwned.share();
			}
			InstantNbtRuntime.get().tracker().track(copyOwned);
			HITS.incrementAndGet();
			BYTES_SAVED_EST.addAndGet(Math.max(16, original.size() * 24L));
			return copy;
		} catch (Throwable ignored) {
			MISSES.incrementAndGet();
			return null;
		}
	}

	public static ListTag tryCopyList(ListTag original) {
		if (original == null || !enabled()) {
			return null;
		}
		try {
			OwnedTag owned = TrackedTagAccess.peek(original);
			if (owned == null || (!owned.isFrozen() && !owned.isShared())) {
				return null;
			}
			ListTag copy = CowEngine.shallowStructureCopyList(original);
			OwnedTag copyOwned = OwnedTag.of(copy);
			if (owned.isFrozen()) {
				copyOwned.freeze();
			} else {
				copyOwned.share();
			}
			InstantNbtRuntime.get().tracker().track(copyOwned);
			HITS.incrementAndGet();
			BYTES_SAVED_EST.addAndGet(Math.max(16, original.size() * 24L));
			return copy;
		} catch (Throwable ignored) {
			MISSES.incrementAndGet();
			return null;
		}
	}

	public static long hits() {
		return HITS.get();
	}

	public static long misses() {
		return MISSES.get();
	}

	public static long estimatedBytesSaved() {
		return BYTES_SAVED_EST.get();
	}

	public static void resetStats() {
		HITS.set(0);
		MISSES.set(0);
		BYTES_SAVED_EST.set(0);
	}
}
