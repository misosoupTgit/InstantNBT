package com.github.misosouptgit.instantnbt.network;

import java.io.IOException;

/**
 * Signals that delta apply failed and a full sync must be issued (Project Plan 11.4).
 */
public final class ResyncRequiredException extends IOException {
	public ResyncRequiredException(String message) {
		super(message);
	}

	public ResyncRequiredException(String message, Throwable cause) {
		super(message, cause);
	}
}
