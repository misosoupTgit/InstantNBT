package com.github.misosouptgit.instantnbt.memory;

/**
 * Pressure observer that drives shrink / eviction steps (Project Plan 5.4).
 */
public final class GarbageMonitor {
	public enum Pressure {
		NORMAL,
		ELEVATED,
		CRITICAL
	}

	private Pressure pressure = Pressure.NORMAL;
	private long pressureEvents;
	private int retainedEstimate;

	public Pressure pressure() {
		return pressure;
	}

	public long pressureEvents() {
		return pressureEvents;
	}

	public void updateRetainedEstimate(int bytes) {
		this.retainedEstimate = Math.max(0, bytes);
		recompute();
	}

	public void noteAllocation(int bytes) {
		this.retainedEstimate += Math.max(0, bytes);
		recompute();
	}

	public void noteRelease(int bytes) {
		this.retainedEstimate = Math.max(0, retainedEstimate - Math.max(0, bytes));
		recompute();
	}

	private void recompute() {
		Pressure next;
		if (retainedEstimate > 16 * 1024 * 1024) {
			next = Pressure.CRITICAL;
		} else if (retainedEstimate > 4 * 1024 * 1024) {
			next = Pressure.ELEVATED;
		} else {
			next = Pressure.NORMAL;
		}
		if (next != pressure) {
			pressure = next;
			pressureEvents++;
		}
	}

	public boolean shouldShrinkPools() {
		return pressure != Pressure.NORMAL;
	}

	public boolean shouldDisableSnapshots() {
		return pressure == Pressure.CRITICAL;
	}
}
