package com.github.misosouptgit.instantnbt.diagnostics;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.TagCopyHooks;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import com.github.misosouptgit.instantnbt.serializer.BinaryNbtCodec;
import com.github.misosouptgit.instantnbt.serializer.FastBinaryCodec;
import com.github.misosouptgit.instantnbt.serializer.NbtIoHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AFK NBT stress loop for Spark A/B tests. Runs on server tick end — no block placement needed.
 */
public final class StressHarness {
	private static final AtomicBoolean RUNNING = new AtomicBoolean();
	private static final AtomicInteger REMAINING_TICKS = new AtomicInteger();
	private static final AtomicInteger OPS_PER_TICK = new AtomicInteger(32);
	private static final AtomicLong OPS_DONE = new AtomicLong();
	private static volatile CompoundTag template;
	private static volatile MinecraftServer server;
	private static volatile boolean forceSave;

	private StressHarness() {}

	public static boolean isRunning() {
		return RUNNING.get();
	}

	/**
	 * @param seconds duration
	 * @param opsPerTick encode/copy cycles per server tick
	 * @param saveEveryTick if true, requests {@code saveAll(true, false, false)} each tick (heavy)
	 */
	public static void start(MinecraftServer minecraftServer, int seconds, int opsPerTick, boolean saveEveryTick) {
		if (minecraftServer == null) {
			throw new IllegalArgumentException("server");
		}
		int ticks = Math.max(20, Math.min(seconds, 600) * 20);
		OPS_PER_TICK.set(Math.max(1, Math.min(opsPerTick, 512)));
		forceSave = saveEveryTick;
		server = minecraftServer;
		template = buildHeavyTag();
		OwnedTag owned = OwnedTag.of(template);
		owned.freeze();
		InstantNbtRuntime.get().tracker().track(owned);
		OPS_DONE.set(0);
		REMAINING_TICKS.set(ticks);
		RUNNING.set(true);
		InstantNBT.LOGGER.info(
			"InstantNBT stress started: {}s ({} ticks), ops/tick={}, saveEveryTick={}",
			seconds,
			ticks,
			OPS_PER_TICK.get(),
			forceSave
		);
	}

	public static void stop(String reason) {
		if (!RUNNING.getAndSet(false)) {
			return;
		}
		REMAINING_TICKS.set(0);
		InstantNBT.LOGGER.info(
			"InstantNBT stress stopped ({}): ops={} cow={}/{} nbtIo={}/{} codec={}/{}",
			reason,
			OPS_DONE.get(),
			TagCopyHooks.hits(),
			TagCopyHooks.misses(),
			NbtIoHooks.writes(),
			NbtIoHooks.reads(),
			FastBinaryCodec.hits(),
			FastBinaryCodec.fallbacks()
		);
		server = null;
		template = null;
	}

	public static void tick() {
		if (!RUNNING.get()) {
			return;
		}
		int left = REMAINING_TICKS.decrementAndGet();
		if (left < 0) {
			stop("timeout");
			return;
		}
		CompoundTag base = template;
		if (base == null) {
			stop("no-template");
			return;
		}
		int ops = OPS_PER_TICK.get();
		try {
			for (int i = 0; i < ops; i++) {
				CompoundTag copy = base.copy();
				byte[] encoded = BinaryNbtCodec.encodeCompound(copy);
				CompoundTag decoded = BinaryNbtCodec.decodeCompound(encoded);
				decoded.putInt("_stress", i);
				OPS_DONE.addAndGet(3);
			}
			if (forceSave && server != null) {
				server.saveAllChunks(true, false, false);
			}
		} catch (Throwable ex) {
			InstantNBT.LOGGER.warn("InstantNBT stress tick failed: {}", ex.toString());
			stop("error");
		}
		if (left == 0) {
			stop("timeout");
		} else if (left % 100 == 0) {
			InstantNBT.LOGGER.info("InstantNBT stress remaining ~{}s ops={}", left / 20, OPS_DONE.get());
		}
	}

	private static CompoundTag buildHeavyTag() {
		CompoundTag root = new CompoundTag();
		ListTag items = new ListTag();
		for (int slot = 0; slot < 54; slot++) {
			CompoundTag stack = new CompoundTag();
			stack.putByte("Slot", (byte) slot);
			stack.putString("id", "minecraft:diamond");
			stack.putByte("Count", (byte) 64);
			CompoundTag blob = new CompoundTag();
			blob.putInt("idx", slot);
			blob.putString("note", "stress-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
			CompoundTag nest = new CompoundTag();
			for (int n = 0; n < 12; n++) {
				nest.putInt("k" + n, n * slot);
			}
			blob.put("nest", nest);
			ListTag pages = new ListTag();
			for (int p = 0; p < 8; p++) {
				pages.add(StringTag.valueOf("page-" + slot + "-" + p + "-XXXXXXXXXXXXXXXXXXXXXXXX"));
			}
			blob.put("pages", pages);
			stack.put("tag", blob);
			items.add(stack);
		}
		root.put("Items", items);
		root.putString("id", "minecraft:chest");
		root.putInt("stressGen", 1);
		return root;
	}
}
