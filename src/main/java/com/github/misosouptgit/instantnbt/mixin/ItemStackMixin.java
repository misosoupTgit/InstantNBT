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
 * Tracks ItemStack NBT mutations (pre-DataComponent + modern save surfaces).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	//? if <1.20.5 {
	@Inject(method = "setTag(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
	private void instantnbt$setTag(CompoundTag tag, CallbackInfo ci) {
		ItemStackNbtHooks.onTagPresent(tag);
	}

	@Inject(method = "getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
	private void instantnbt$getOrCreateTag(CallbackInfoReturnable<CompoundTag> cir) {
		ItemStackNbtHooks.onTagPresent(cir.getReturnValue());
	}

	@Inject(method = "getTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
	private void instantnbt$getTag(CallbackInfoReturnable<CompoundTag> cir) {
		CompoundTag tag = cir.getReturnValue();
		if (tag != null) {
			ItemStackNbtHooks.onTagPresent(tag);
		}
	}
	//?} else if <1.21.2 {
	/*@Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
	private void instantnbt$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
		ItemStackNbtHooks.onTagPresent(cir.getReturnValue());
	}
	*///?} else {
	/*@Inject(method = "save", at = @At("RETURN"))
	private void instantnbt$saveModern(CallbackInfoReturnable<CompoundTag> cir) {
		ItemStackNbtHooks.onTagPresent(cir.getReturnValue());
	}
	*///?}
}
