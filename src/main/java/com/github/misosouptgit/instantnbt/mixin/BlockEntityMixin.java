package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.EntityNbtHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
	//? if <1.20.5 {
	@Inject(method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), require = 0)
	private void instantnbt$saveAdditional(CompoundTag tag, CallbackInfo ci) {
		EntityNbtHooks.onSaved(tag);
	}

	@Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"), require = 0)
	private void instantnbt$load(CompoundTag tag, CallbackInfo ci) {
		EntityNbtHooks.onLoaded(tag);
	}

	@Inject(method = "getUpdateTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), require = 0)
	private void instantnbt$getUpdateTag(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<CompoundTag> cir) {
		EntityNbtHooks.onSaved(cir.getReturnValue());
	}
	//?} else {
	/*@Inject(
		method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
		at = @At("RETURN"),
		require = 0
	)
	private void instantnbt$saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, CallbackInfo ci) {
		EntityNbtHooks.onSaved(tag);
	}

	@Inject(
		method = "loadAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
		at = @At("HEAD"),
		require = 0
	)
	private void instantnbt$loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, CallbackInfo ci) {
		EntityNbtHooks.onLoaded(tag);
	}

	@Inject(
		method = "getUpdateTag(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("RETURN"),
		require = 0
	)
	private void instantnbt$getUpdateTag(net.minecraft.core.HolderLookup.Provider provider, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<CompoundTag> cir) {
		EntityNbtHooks.onSaved(cir.getReturnValue());
	}
	*///?}
}
