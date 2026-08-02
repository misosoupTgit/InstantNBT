package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.ThreadDomain;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	//? if >=1.17 {
	@Inject(method = "tickServer", at = @At("HEAD"))
	private void instantnbt$tickHead(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		ThreadDomain.setCurrent(ThreadDomain.INTEGRATED_SERVER);
		MinecraftServer self = (MinecraftServer) (Object) this;
		InstantNbtRuntime.get().integrated().setIntegrated(!self.isDedicatedServer());
	}

	@Inject(method = "tickServer", at = @At("RETURN"))
	private void instantnbt$tickEnd(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		InstantNbtRuntime.get().onServerTickEnd();
	}
	//?} else {
	/*@Inject(method = "tick", at = @At("HEAD"))
	private void instantnbt$tickHead(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		ThreadDomain.setCurrent(ThreadDomain.INTEGRATED_SERVER);
		MinecraftServer self = (MinecraftServer) (Object) this;
		InstantNbtRuntime.get().integrated().setIntegrated(!self.isDedicatedServer());
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void instantnbt$tickEnd(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		InstantNbtRuntime.get().onServerTickEnd();
	}
	*///?}
}
