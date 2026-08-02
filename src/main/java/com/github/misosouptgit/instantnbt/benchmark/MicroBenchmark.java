package com.github.misosouptgit.instantnbt.benchmark;

import com.github.misosouptgit.instantnbt.memory.NbtObjectPool;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import com.github.misosouptgit.instantnbt.serializer.SerializerFacade;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight micro benchmark runner (Project Plan 17.1 Micro).
 * Not JMH — intended for in-game /instantnbt benchmark smoke checks.
 */
public final class MicroBenchmark {
	public static final class Result {
		public final String name;
		public final long ops;
		public final double nanosPerOp;
		public final double opsPerSec;

		Result(String name, long ops, double nanosPerOp) {
			this.name = name;
			this.ops = ops;
			this.nanosPerOp = nanosPerOp;
			this.opsPerSec = nanosPerOp <= 0 ? 0 : 1_000_000_000.0 / nanosPerOp;
		}

		public String format() {
			return String.format(Locale.ROOT, "%s: %.1f ns/op (%.0f ops/s, n=%d)", name, nanosPerOp, opsPerSec, ops);
		}
	}

	private MicroBenchmark() {}

	public static List<Result> runAll(InstantNbtRuntime runtime) {
		List<Result> results = new ArrayList<>();
		results.add(benchPool(runtime));
		results.add(benchOwnership());
		results.add(benchSerializer(runtime));
		results.add(benchDelta(runtime));
		return results;
	}

	private static Result benchPool(InstantNbtRuntime runtime) {
		NbtObjectPool pool = runtime.memory().nbtPool();
		int n = 50_000;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			CompoundTag tag = pool.acquireCompound();
			tag.putInt("i", i);
			pool.releaseCompound(tag);
		}
		return result("pool.acquire/release", n, start);
	}

	private static Result benchOwnership() {
		int n = 20_000;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("i", i);
			OwnedTag owned = OwnedTag.of(tag);
			owned.share();
			owned.ensureWritable();
			owned.freeze();
		}
		return result("ownership.share/write/freeze", n, start);
	}

	private static Result benchSerializer(InstantNbtRuntime runtime) {
		SerializerFacade serializer = runtime.serializer();
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "instantnbt");
		tag.putInt("v", 1);
		OwnedTag owned = OwnedTag.of(tag);
		int n = 5_000;
		long start = System.nanoTime();
		try {
			for (int i = 0; i < n; i++) {
				byte[] data = serializer.encode(owned);
				serializer.decode(data);
			}
		} catch (Exception ex) {
			return new Result("serializer.encode/decode(FAILED)", 0, Double.NaN);
		}
		return result("serializer.encode/decode", n, start);
	}

	private static Result benchDelta(InstantNbtRuntime runtime) {
		CompoundTag a = new CompoundTag();
		a.putInt("x", 1);
		CompoundTag b = a.copy();
		b.putInt("x", 2);
		b.putString("y", "z");
		OwnedTag prev = OwnedTag.owned(a, null);
		OwnedTag next = OwnedTag.owned(b, null);
		next.markDirty();
		int n = 3_000;
		long start = System.nanoTime();
		try {
			for (int i = 0; i < n; i++) {
				byte[] packet = runtime.network().buildDelta(prev, next);
				runtime.network().applyDelta(a.copy(), packet, prev.generation());
			}
		} catch (Exception ex) {
			return new Result("network.delta(FAILED)", 0, Double.NaN);
		}
		return result("network.delta", n, start);
	}

	private static Result result(String name, long ops, long startNanos) {
		long elapsed = System.nanoTime() - startNanos;
		double perOp = ops == 0 ? Double.NaN : (double) elapsed / (double) ops;
		return new Result(name, ops, perOp);
	}
}
