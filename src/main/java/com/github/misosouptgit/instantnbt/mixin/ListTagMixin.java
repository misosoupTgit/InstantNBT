package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.TagWriteHooks;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ListTag.class)
public abstract class ListTagMixin {
	//? if >=1.17 {
	@Inject(method = "set(ILnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", at = @At("HEAD"))
	private void instantnbt$set(int index, Tag tag, CallbackInfoReturnable<Tag> cir) {
		TagWriteHooks.onMutate((Tag) (Object) this);
	}

	@Inject(method = "add(ILnet/minecraft/nbt/Tag;)V", at = @At("HEAD"))
	private void instantnbt$add(int index, Tag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		TagWriteHooks.onMutate((Tag) (Object) this);
	}

	@Inject(method = "remove(I)Lnet/minecraft/nbt/Tag;", at = @At("HEAD"))
	private void instantnbt$remove(int index, CallbackInfoReturnable<Tag> cir) {
		TagWriteHooks.onMutate((Tag) (Object) this);
	}
	//?} else {
	/*@Inject(method = "setTag", at = @At("HEAD"))
	private void instantnbt$setTag(int index, Tag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		TagWriteHooks.onMutate((Tag) (Object) this);
	}
	*///?}
}
