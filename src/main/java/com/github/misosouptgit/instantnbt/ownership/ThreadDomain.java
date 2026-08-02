package com.github.misosouptgit.instantnbt.ownership;

/**
 * Coarse thread domains used for ownership handoff (Project Plan 12.2).
 */
public enum ThreadDomain {
	MAIN,
	RENDER,
	INTEGRATED_SERVER,
	IO,
	WORKER,
	UNKNOWN;

	private static final ThreadLocal<ThreadDomain> CURRENT = ThreadLocal.withInitial(() -> UNKNOWN);

	public static ThreadDomain current() {
		return CURRENT.get();
	}

	public static void setCurrent(ThreadDomain domain) {
		CURRENT.set(domain == null ? UNKNOWN : domain);
	}

	public static void clear() {
		CURRENT.remove();
	}
}
