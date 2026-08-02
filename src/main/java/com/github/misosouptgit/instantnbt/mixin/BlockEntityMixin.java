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
	//? if <1.21.5 {
	@Inject(method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
	private void instantnbt$saveAdditional(CompoundTag tag, CallbackInfo ci) {
		EntityNbtHooks.onSaved(tag);
	}

	@Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"))
	private void instantnbt$load(CompoundTag tag, CallbackInfo ci) {
		EntityNbtHooks.onLoaded(tag);
	}
	//?} else {
	/*@Inject(method = "saveAdditional", at = @At("RETURN"))
	private void instantnbt$saveAdditionalModern(CompoundTag tag, CallbackInfo ci) {
		EntityNbtHooks.onSaved(tag);
	}
	*///?}
}
