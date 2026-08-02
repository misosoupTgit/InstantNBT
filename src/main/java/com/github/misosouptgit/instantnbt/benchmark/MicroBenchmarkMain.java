package com.github.misosouptgit.instantnbt.benchmark;

import com.github.misosouptgit.instantnbt.memory.MemoryManager;
import com.github.misosouptgit.instantnbt.network.NetworkRuntime;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.serializer.SerializerFacade;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless micro-benchmark entrypoint for CI / Gradle (Project Plan 17).
 * Avoids Architectury Platform / full game bootstrap.
 */
public final class MicroBenchmarkMain {
	/** Soft budgets in ns/op (Tier A smoke regression). */
	private static final double BUDGET_POOL_NS = 5_000.0;
	private static final double BUDGET_OWNERSHIP_NS = 20_000.0;
	private static final double BUDGET_SERIALIZER_NS = 200_000.0;
	private static final double BUDGET_DELTA_NS = 500_000.0;

	private MicroBenchmarkMain() {}

	public static void main(String[] args) throws Exception {
		MemoryManager memory = new MemoryManager();
		memory.start();
		SerializerFacade serializer = new SerializerFacade();
		NetworkRuntime network = new NetworkRuntime(serializer);

		List<MicroBenchmark.Result> results = new ArrayList<>();
		results.add(benchPool(memory));
		results.add(benchOwnership());
		results.add(benchSerializer(serializer));
		results.add(benchDelta(network));

		StringBuilder json = new StringBuilder();
		json.append("{\"results\":[");
		boolean ok = true;
		for (int i = 0; i < results.size(); i++) {
			MicroBenchmark.Result r = results.get(i);
			System.out.println(r.format());
			if (i > 0) {
				json.append(',');
			}
			json.append(String.format(Locale.ROOT,
				"{\"name\":\"%s\",\"ops\":%d,\"nanosPerOp\":%.3f,\"opsPerSec\":%.3f}",
				r.name, r.ops, r.nanosPerOp, r.opsPerSec));
			ok &= withinBudget(r);
		}
		json.append("],\"passed\":").append(ok).append('}');

		Path outDir = Path.of("build", "benchmark");
		Files.createDirectories(outDir);
		Path out = outDir.resolve("micro-benchmark.json");
		Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
		System.out.println("Wrote " + out.toAbsolutePath());

		if (!ok) {
			System.err.println("Micro-benchmark exceeded soft budgets");
			System.exit(2);
		}
	}

	private static boolean withinBudget(MicroBenchmark.Result r) {
		if (Double.isNaN(r.nanosPerOp)) {
			return false;
		}
		if (r.name.startsWith("pool") && r.nanosPerOp > BUDGET_POOL_NS) {
			return false;
		}
		if (r.name.startsWith("ownership") && r.nanosPerOp > BUDGET_OWNERSHIP_NS) {
			return false;
		}
		if (r.name.startsWith("serializer") && r.nanosPerOp > BUDGET_SERIALIZER_NS) {
			return false;
		}
		if (r.name.startsWith("network") && r.nanosPerOp > BUDGET_DELTA_NS) {
			return false;
		}
		return true;
	}

	private static MicroBenchmark.Result benchPool(MemoryManager memory) {
		int n = 50_000;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			CompoundTag tag = memory.nbtPool().acquireCompound();
			tag.putInt("i", i);
			memory.nbtPool().releaseCompound(tag);
		}
		return result("pool.acquire/release", n, start);
	}

	private static MicroBenchmark.Result benchOwnership() {
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

	private static MicroBenchmark.Result benchSerializer(SerializerFacade serializer) {
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
			return new MicroBenchmark.Result("serializer.encode/decode(FAILED)", 0, Double.NaN);
		}
		return result("serializer.encode/decode", n, start);
	}

	private static MicroBenchmark.Result benchDelta(NetworkRuntime network) {
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
				byte[] packet = network.buildDelta(prev, next);
				network.applyDelta(a.copy(), packet, prev.generation());
			}
		} catch (Exception ex) {
			return new MicroBenchmark.Result("network.delta(FAILED)", 0, Double.NaN);
		}
		return result("network.delta", n, start);
	}

	private static MicroBenchmark.Result result(String name, long ops, long startNanos) {
		long elapsed = System.nanoTime() - startNanos;
		double perOp = ops == 0 ? Double.NaN : (double) elapsed / (double) ops;
		return new MicroBenchmark.Result(name, ops, perOp);
	}
}
