package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.InstantNbtTagged;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.TagCopyHooks;
import com.github.misosouptgit.instantnbt.ownership.TagWriteHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks tracked OwnedTags dirty on compound mutation (Project Plan: minimal Mixin surface).
 * Untracked tags: single null-field check then return (FPS/TPS-critical).
 */
@Mixin(CompoundTag.class)
public abstract class CompoundTagMixin implements InstantNbtTagged {
	@Unique
	private OwnedTag instantnbt$owned;

	@Override
	public OwnedTag instantnbt$getOwned() {
		return instantnbt$owned;
	}

	@Override
	public void instantnbt$setOwned(OwnedTag owned) {
		this.instantnbt$owned = owned;
	}

	@Inject(method = "put(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", at = @At("HEAD"), require = 0)
	private void instantnbt$put(String key, Tag value, CallbackInfoReturnable<Tag> cir) {
		if (instantnbt$owned == null || TagWriteHooks.isSuppressed()) {
			return;
		}
		TagWriteHooks.onMutateOwned(instantnbt$owned);
	}

	@Inject(method = "remove(Ljava/lang/String;)V", at = @At("HEAD"), require = 0)
	private void instantnbt$remove(String key, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (instantnbt$owned == null || TagWriteHooks.isSuppressed()) {
			return;
		}
		TagWriteHooks.onMutateOwned(instantnbt$owned);
	}

	@Inject(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), cancellable = true, require = 0)
	private void instantnbt$copy(CallbackInfoReturnable<CompoundTag> cir) {
		if (instantnbt$owned == null || !TagCopyHooks.enabled()) {
			return;
		}
		CompoundTag accelerated = TagCopyHooks.tryCopyCompound((CompoundTag) (Object) this);
		if (accelerated != null) {
			cir.setReturnValue(accelerated);
		}
	}
}
