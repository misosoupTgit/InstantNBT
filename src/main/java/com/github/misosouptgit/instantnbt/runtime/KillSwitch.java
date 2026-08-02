package com.github.misosouptgit.instantnbt.runtime;

import com.github.misosouptgit.instantnbt.InstantNBT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Emergency disable of all optimizations (Project Plan 18.4).
 */
public final class KillSwitch {
	private volatile boolean engaged;
	private volatile String reason = "";

	public boolean isEngaged() {
		return engaged;
	}

	public String reason() {
		return reason;
	}

	public synchronized void engage(String reason, InstantNbtRuntime runtime) {
		if (engaged) {
			return;
		}
		this.engaged = true;
		this.reason = reason == null ? "unspecified" : reason;
		InstantNBT.LOGGER.error("InstantNBT Kill Switch engaged: {}", this.reason);
		if (runtime != null) {
			runtime.enterDegraded(DegradedMode.DEGRADED_MINIMAL, "kill-switch:" + this.reason);
			persist(runtime);
			try {
				runtime.diagnostics().exportJson();
			} catch (Exception ex) {
				InstantNBT.LOGGER.warn("Failed to export diagnostics on kill switch: {}", ex.toString());
			}
		}
	}

	public synchronized void reset() {
		engaged = false;
		reason = "";
	}

	private void persist(InstantNbtRuntime runtime) {
		if (runtime == null || !runtime.config().killSwitchPersistDisable) {
			return;
		}
		try {
			Path path = Paths.get("config", "instantnbt-common.toml");
			runtime.config().killSwitch = true;
			runtime.config().write(path);
			Path marker = Paths.get("config", "instantnbt", "killswitch.engaged");
			Files.createDirectories(marker.getParent());
			Files.writeString(marker, Instant.now() + " " + reason + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			InstantNBT.LOGGER.error("Kill Switch persisted to {}", path.toAbsolutePath());
		} catch (Exception ex) {
			InstantNBT.LOGGER.warn("Failed to persist kill switch: {}", ex.toString());
		}
	}
}
