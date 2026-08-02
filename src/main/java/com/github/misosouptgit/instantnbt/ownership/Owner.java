package com.github.misosouptgit.instantnbt.ownership;

/**
 * Logical owner of an OwnedTag: thread domain + module domain.
 */
public final class Owner {
	private final ThreadDomain thread;
	private final ModuleDomain module;

	public Owner(ThreadDomain thread, ModuleDomain module) {
		this.thread = thread == null ? ThreadDomain.UNKNOWN : thread;
		this.module = module == null ? ModuleDomain.RUNTIME : module;
	}

	public static Owner current(ModuleDomain module) {
		return new Owner(ThreadDomain.current(), module);
	}

	public ThreadDomain thread() {
		return thread;
	}

	public ModuleDomain module() {
		return module;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Owner)) {
			return false;
		}
		Owner other = (Owner) o;
		return thread == other.thread && module == other.module;
	}

	@Override
	public int hashCode() {
		return 31 * thread.hashCode() + module.hashCode();
	}

	@Override
	public String toString() {
		return "Owner{" + thread + "/" + module + "}";
	}
}
