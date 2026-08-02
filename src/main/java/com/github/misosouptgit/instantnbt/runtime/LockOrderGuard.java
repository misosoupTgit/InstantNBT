package com.github.misosouptgit.instantnbt.runtime;

/**
 * Thread-local lock-order tracker (Plan 12.3).
 * Violations are diagnostics-only — never throw into game threads.
 */
public final class LockOrderGuard {
	public enum Domain {
		ARENA(0),
		OWNED_META(1),
		SHARED_TAG(2);

		private final int rank;

		Domain(int rank) {
			this.rank = rank;
		}

		public int rank() {
			return rank;
		}
	}

	private static final ThreadLocal<int[]> HELD = ThreadLocal.withInitial(() -> new int[Domain.values().length]);
	private static final java.util.concurrent.atomic.AtomicInteger VIOLATIONS = new java.util.concurrent.atomic.AtomicInteger();

	private LockOrderGuard() {}

	public static void enter(Domain domain) {
		if (domain == null) {
			return;
		}
		int[] held = HELD.get();
		for (Domain other : Domain.values()) {
			if (held[other.ordinal()] > 0 && other.rank() > domain.rank()) {
				int n = VIOLATIONS.incrementAndGet();
				if (n == 1 || n % 16 == 0) {
					com.github.misosouptgit.instantnbt.InstantNBT.LOGGER.warn(
						"InstantNBT lock-order risk (x{}): acquiring {} while holding higher-rank locks",
						n,
						domain
					);
					try {
						InstantNbtRuntime.get().safety().report(
							SafetyCoordinator.Severity.SOFT,
							"lock-order",
							"acquire " + domain + " out of order"
						);
					} catch (Throwable ignored) {
						// diagnostics only
					}
				}
				break;
			}
		}
		held[domain.ordinal()]++;
	}

	public static void leave(Domain domain) {
		if (domain == null) {
			return;
		}
		int[] held = HELD.get();
		if (held[domain.ordinal()] > 0) {
			held[domain.ordinal()]--;
		}
	}

	public static int violations() {
		return VIOLATIONS.get();
	}
}
