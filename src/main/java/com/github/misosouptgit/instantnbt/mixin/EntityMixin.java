package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.EntityNbtHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	//? if <1.21.6 {
	@Inject(method = "saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
	private void instantnbt$saveWithoutId(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
		EntityNbtHooks.onSaved(cir.getReturnValue());
	}

	@Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"))
	private void instantnbt$load(CompoundTag tag, CallbackInfo ci) {
		EntityNbtHooks.onLoaded(tag);
	}
	//?} else {
	/*@Inject(method = "saveWithoutId", at = @At("RETURN"))
	private void instantnbt$saveWithoutIdModern(CallbackInfoReturnable<?> cir) {
		Object value = cir.getReturnValue();
		if (value instanceof CompoundTag) {
			EntityNbtHooks.onSaved((CompoundTag) value);
		}
	}
	*///?}
}
