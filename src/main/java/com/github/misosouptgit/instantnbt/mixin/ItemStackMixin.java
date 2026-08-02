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
 * Tracks ItemStack NBT / CustomData mutations. Soft require=0 on modern APIs.
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
	//?} else {
	/*@Inject(method = "save", at = @At("RETURN"), require = 0)
	private void instantnbt$saveModern(CallbackInfoReturnable<CompoundTag> cir) {
		ItemStackNbtHooks.onTagPresent(cir.getReturnValue());
	}

	@Inject(method = "set", at = @At("TAIL"), require = 0)
	private void instantnbt$setComponent(Object type, Object value, CallbackInfoReturnable<?> cir) {
		trackCustomData(value);
	}

	private static void trackCustomData(Object value) {
		if (value == null) {
			return;
		}
		try {
			String name = value.getClass().getName();
			if (!name.endsWith("CustomData") && !name.contains("CustomData")) {
				return;
			}
			Object tag = value.getClass().getMethod("copyTag").invoke(value);
			if (tag instanceof CompoundTag) {
				ItemStackNbtHooks.onTagPresent((CompoundTag) tag);
			}
		} catch (Throwable ignored) {
		}
	}
	*///?}
}
