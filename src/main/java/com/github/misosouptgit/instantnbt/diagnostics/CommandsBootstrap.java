package com.github.misosouptgit.instantnbt.diagnostics;

import com.github.misosouptgit.instantnbt.InstantNBT;
import dev.architectury.event.events.common.CommandRegistrationEvent;

/**
 * Registers diagnostics commands via Architectury.
 */
public final class CommandsBootstrap {
	private CommandsBootstrap() {}

	public static void register() {
		//? if >=1.19 {
		CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> InstantNbtCommands.register(dispatcher));
		InstantNBT.LOGGER.info("Registered /instantnbt diagnostics commands");
		//?} else {
		/*CommandRegistrationEvent.EVENT.register((dispatcher, dedicated) -> InstantNbtCommands.register(dispatcher));
		InstantNBT.LOGGER.info("Registered /instantnbt diagnostics commands (legacy)");
		*///?}
	}
}
