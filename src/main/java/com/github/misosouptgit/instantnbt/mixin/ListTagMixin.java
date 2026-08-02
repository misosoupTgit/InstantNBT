package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.InstantNbtTagged;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.ownership.TagCopyHooks;
import com.github.misosouptgit.instantnbt.ownership.TagWriteHooks;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ListTag.class)
public abstract class ListTagMixin implements InstantNbtTagged {
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

	//? if >=1.17 {
	@Inject(method = "set(ILnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", at = @At("HEAD"), require = 0)
	private void instantnbt$set(int index, Tag tag, CallbackInfoReturnable<Tag> cir) {
		if (instantnbt$owned == null || TagWriteHooks.isSuppressed()) {
			return;
		}
		TagWriteHooks.onMutateOwned(instantnbt$owned);
	}

	@Inject(method = "add(ILnet/minecraft/nbt/Tag;)V", at = @At("HEAD"), require = 0)
	private void instantnbt$add(int index, Tag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (instantnbt$owned == null || TagWriteHooks.isSuppressed()) {
			return;
		}
		TagWriteHooks.onMutateOwned(instantnbt$owned);
	}

	@Inject(method = "remove(I)Lnet/minecraft/nbt/Tag;", at = @At("HEAD"), require = 0)
	private void instantnbt$remove(int index, CallbackInfoReturnable<Tag> cir) {
		if (instantnbt$owned == null || TagWriteHooks.isSuppressed()) {
			return;
		}
		TagWriteHooks.onMutateOwned(instantnbt$owned);
	}

	@Inject(method = "copy()Lnet/minecraft/nbt/ListTag;", at = @At("HEAD"), cancellable = true, require = 0)
	private void instantnbt$copy(CallbackInfoReturnable<ListTag> cir) {
		if (instantnbt$owned == null || !TagCopyHooks.enabled()) {
			return;
		}
		ListTag accelerated = TagCopyHooks.tryCopyList((ListTag) (Object) this);
		if (accelerated != null) {
			cir.setReturnValue(accelerated);
		}
	}
	//?} else {
	/*@Inject(method = "setTag", at = @At("HEAD"), require = 0)
	private void instantnbt$setTag(int index, Tag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (instantnbt$owned == null || TagWriteHooks.isSuppressed()) {
			return;
		}
		TagWriteHooks.onMutateOwned(instantnbt$owned);
	}
	*///?}
}
