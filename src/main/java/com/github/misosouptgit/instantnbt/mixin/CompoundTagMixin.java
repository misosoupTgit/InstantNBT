package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.TagWriteHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks tracked OwnedTags dirty on compound mutation (Project Plan: minimal Mixin surface).
 */
@Mixin(CompoundTag.class)
public abstract class CompoundTagMixin {
	@Inject(method = "put(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", at = @At("HEAD"), require = 0)
	private void instantnbt$put(String key, Tag value, CallbackInfoReturnable<Tag> cir) {
		TagWriteHooks.onMutate((Tag) (Object) this);
	}

	@Inject(method = "remove(Ljava/lang/String;)V", at = @At("HEAD"), require = 0)
	private void instantnbt$remove(String key, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		TagWriteHooks.onMutate((Tag) (Object) this);
	}

	@Inject(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), cancellable = true, require = 0)
	private void instantnbt$copy(CallbackInfoReturnable<CompoundTag> cir) {
		CompoundTag accelerated = com.github.misosouptgit.instantnbt.ownership.TagCopyHooks.tryCopyCompound((CompoundTag) (Object) this);
		if (accelerated != null) {
			cir.setReturnValue(accelerated);
		}
	}
}
