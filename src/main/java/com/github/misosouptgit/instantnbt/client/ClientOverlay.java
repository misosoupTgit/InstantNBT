package com.github.misosouptgit.instantnbt.client;

import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;

/**
 * F3 debug overlay for Runtime state (Project Plan 16.3).
 */
public final class ClientOverlay {
	private ClientOverlay() {}

	public static void register() {
		EnvExecutor.runInEnv(Env.CLIENT, () -> ClientOverlay::registerClient);
	}

	private static void registerClient() {
		//? if >=1.17 {
		ClientGuiEvent.DEBUG_TEXT_LEFT.register(texts -> {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			if (!runtime.config().diagnosticsOverlay) {
				return;
			}
			texts.add(String.format(
				"[InstantNBT] %s deg=%s opts=%s ks=%s",
				runtime.phase(),
				runtime.degradedMode(),
				runtime.optimizationsActive(),
				runtime.killSwitch().isEngaged()
			));
			texts.add(String.format(
				"[InstantNBT] pool=%.0f%% shared=%d tracked=%d netF/D=%d/%d",
				runtime.memory().pool().hitRate() * 100.0,
				runtime.sharedTags().size(),
				runtime.tracker().size(),
				runtime.network().fullSyncs(),
				runtime.network().deltaSyncs()
			));
			texts.add(String.format(
				"[InstantNBT] cow %d/%d nbtIo %d/%d codec %d/%d",
				com.github.misosouptgit.instantnbt.ownership.TagCopyHooks.hits(),
				com.github.misosouptgit.instantnbt.ownership.TagCopyHooks.misses(),
				com.github.misosouptgit.instantnbt.serializer.NbtIoHooks.writes(),
				com.github.misosouptgit.instantnbt.serializer.NbtIoHooks.reads(),
				com.github.misosouptgit.instantnbt.serializer.FastBinaryCodec.hits(),
				com.github.misosouptgit.instantnbt.serializer.FastBinaryCodec.fallbacks()
			));
		});
		//?}
	}
}
