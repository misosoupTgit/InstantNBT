package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.ItemStackNbtHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tracks ItemStack NBT / CustomData mutations.
 * Modern (>=1.20.5) path only hooks save surfaces to avoid DataComponent descriptor mismatches.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	//? if <1.20.5 {
	@Inject(method = "setTag(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), require = 0)
	private void instantnbt$setTag(CompoundTag tag, CallbackInfo ci) {
		ItemStackNbtHooks.onTagPresent(tag);
	}

	@Inject(method = "getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), require = 0)
	private void instantnbt$getOrCreateTag(CallbackInfoReturnable<CompoundTag> cir) {
		ItemStackNbtHooks.onTagPresent(cir.getReturnValue());
	}

	@Inject(method = "getTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), require = 0)
	private void instantnbt$getTag(CallbackInfoReturnable<CompoundTag> cir) {
		CompoundTag tag = cir.getReturnValue();
		if (tag != null) {
			ItemStackNbtHooks.onTagPresent(tag);
		}
	}
	//?} else {
	/*@Inject(method = "save(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), require = 0)
	private void instantnbt$saveModern(net.minecraft.core.HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
		ItemStackNbtHooks.onTagPresent(cir.getReturnValue());
	}

	@Inject(method = "save(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", at = @At("RETURN"), require = 0)
	private void instantnbt$saveInto(net.minecraft.core.HolderLookup.Provider provider, net.minecraft.nbt.Tag tag, CallbackInfoReturnable<net.minecraft.nbt.Tag> cir) {
		Object value = cir.getReturnValue();
		if (value instanceof CompoundTag) {
			ItemStackNbtHooks.onTagPresent((CompoundTag) value);
		}
	}
	*///?}
}
