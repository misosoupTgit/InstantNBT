package com.github.misosouptgit.instantnbt.ownership;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Accelerates {@link Tag#copy()} for frozen/shared OwnedTags via structural CoW (Plan 7).
 * Read-mostly copies become cheap; first write deep-splits via {@link TagWriteHooks}.
 */
public final class TagCopyHooks {
	private static final AtomicLong HITS = new AtomicLong();
	private static final AtomicLong MISSES = new AtomicLong();
	private static final AtomicLong BYTES_SAVED_EST = new AtomicLong();

	private TagCopyHooks() {}

	/**
	 * @return accelerated copy, or {@code null} to fall back to vanilla deep copy
	 */
	public static CompoundTag tryCopyCompound(CompoundTag original) {
		if (original == null) {
			return null;
		}
		try {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			if (!runtime.optimizationsActive() || !runtime.config().copyCowEnabled) {
				MISSES.incrementAndGet();
				return null;
			}
			OwnedTag owned = runtime.tracker().find(original);
			if (owned == null || (!owned.isFrozen() && !owned.isShared())) {
				MISSES.incrementAndGet();
				return null;
			}
			CompoundTag copy = CowEngine.shallowStructureCopyCompound(original);
			OwnedTag copyOwned = OwnedTag.of(copy);
			if (owned.isFrozen()) {
				copyOwned.freeze();
			} else {
				copyOwned.share();
			}
			runtime.tracker().track(copyOwned);
			trackNestedShared(runtime, copy, owned.isFrozen());
			HITS.incrementAndGet();
			BYTES_SAVED_EST.addAndGet(Math.max(16, CowEngine.estimateSize(original) * 8L));
			return copy;
		} catch (Throwable ignored) {
			MISSES.incrementAndGet();
			return null;
		}
	}

	public static ListTag tryCopyList(ListTag original) {
		if (original == null) {
			return null;
		}
		try {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			if (!runtime.optimizationsActive() || !runtime.config().copyCowEnabled) {
				MISSES.incrementAndGet();
				return null;
			}
			OwnedTag owned = runtime.tracker().find(original);
			if (owned == null || (!owned.isFrozen() && !owned.isShared())) {
				MISSES.incrementAndGet();
				return null;
			}
			ListTag copy = CowEngine.shallowStructureCopyList(original);
			OwnedTag copyOwned = OwnedTag.of(copy);
			if (owned.isFrozen()) {
				copyOwned.freeze();
			} else {
				copyOwned.share();
			}
			runtime.tracker().track(copyOwned);
			trackNestedShared(runtime, copy, owned.isFrozen());
			HITS.incrementAndGet();
			BYTES_SAVED_EST.addAndGet(Math.max(16, CowEngine.estimateSize(original) * 8L));
			return copy;
		} catch (Throwable ignored) {
			MISSES.incrementAndGet();
			return null;
		}
	}

	private static void trackNestedShared(InstantNbtRuntime runtime, Tag tag, boolean freeze) {
		if (tag instanceof CompoundTag) {
			CompoundTag compound = (CompoundTag) tag;
			for (String key : compound.getAllKeys()) {
				Tag child = compound.get(key);
				if (child instanceof CompoundTag || child instanceof ListTag) {
					if (runtime.tracker().find(child) == null) {
						OwnedTag nested = OwnedTag.of(child);
						if (freeze) {
							nested.freeze();
						} else {
							nested.share();
						}
						runtime.tracker().track(nested);
					}
					trackNestedShared(runtime, child, freeze);
				}
			}
		} else if (tag instanceof ListTag) {
			ListTag list = (ListTag) tag;
			for (int i = 0; i < list.size(); i++) {
				Tag child = list.get(i);
				if (child instanceof CompoundTag || child instanceof ListTag) {
					if (runtime.tracker().find(child) == null) {
						OwnedTag nested = OwnedTag.of(child);
						if (freeze) {
							nested.freeze();
						} else {
							nested.share();
						}
						runtime.tracker().track(nested);
					}
					trackNestedShared(runtime, child, freeze);
				}
			}
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
