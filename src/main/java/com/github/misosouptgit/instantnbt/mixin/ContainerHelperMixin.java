package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.ContainerNbtHooks;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inventory serialize boundary — suppress put hooks during bulk item write, then freeze if large.
 */
@Mixin(ContainerHelper.class)
public abstract class ContainerHelperMixin {
	//? if <1.20.5 {
	@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("HEAD"),
		require = 0
	)
	private static void instantnbt$saveHead(CompoundTag tag, NonNullList<ItemStack> list, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.beforeSaveAllItems();
	}

	@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("RETURN"),
		require = 0
	)
	private static void instantnbt$saveReturn(CompoundTag tag, NonNullList<ItemStack> list, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.afterSaveAllItems(cir.getReturnValue() != null ? cir.getReturnValue() : tag);
	}

	@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;Z)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("HEAD"),
		require = 0
	)
	private static void instantnbt$saveHead2(CompoundTag tag, NonNullList<ItemStack> list, boolean saveEmpty, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.beforeSaveAllItems();
	}

	@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;Z)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("RETURN"),
		require = 0
	)
	private static void instantnbt$saveReturn2(CompoundTag tag, NonNullList<ItemStack> list, boolean saveEmpty, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.afterSaveAllItems(cir.getReturnValue() != null ? cir.getReturnValue() : tag);
	}

	@Inject(
		method = "loadAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;)V",
		at = @At("HEAD"),
		require = 0
	)
	private static void instantnbt$loadHead(CompoundTag tag, NonNullList<ItemStack> list, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		ContainerNbtHooks.beforeLoadAllItems();
	}

	@Inject(
		method = "loadAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;)V",
		at = @At("RETURN"),
		require = 0
	)
	private static void instantnbt$loadReturn(CompoundTag tag, NonNullList<ItemStack> list, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		ContainerNbtHooks.afterLoadAllItems(tag);
	}
	//?} else {
	/*@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("HEAD"),
		require = 0
	)
	private static void instantnbt$saveHead(CompoundTag tag, NonNullList<ItemStack> list, net.minecraft.core.HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.beforeSaveAllItems();
	}

	@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("RETURN"),
		require = 0
	)
	private static void instantnbt$saveReturn(CompoundTag tag, NonNullList<ItemStack> list, net.minecraft.core.HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.afterSaveAllItems(cir.getReturnValue() != null ? cir.getReturnValue() : tag);
	}

	@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;ZLnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("HEAD"),
		require = 0
	)
	private static void instantnbt$saveHead2(CompoundTag tag, NonNullList<ItemStack> list, boolean saveEmpty, net.minecraft.core.HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.beforeSaveAllItems();
	}

	@Inject(
		method = "saveAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;ZLnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("RETURN"),
		require = 0
	)
	private static void instantnbt$saveReturn2(CompoundTag tag, NonNullList<ItemStack> list, boolean saveEmpty, net.minecraft.core.HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
		ContainerNbtHooks.afterSaveAllItems(cir.getReturnValue() != null ? cir.getReturnValue() : tag);
	}

	@Inject(
		method = "loadAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;Lnet/minecraft/core/HolderLookup$Provider;)V",
		at = @At("HEAD"),
		require = 0
	)
	private static void instantnbt$loadHead(CompoundTag tag, NonNullList<ItemStack> list, net.minecraft.core.HolderLookup.Provider provider, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		ContainerNbtHooks.beforeLoadAllItems();
	}

	@Inject(
		method = "loadAllItems(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/NonNullList;Lnet/minecraft/core/HolderLookup$Provider;)V",
		at = @At("RETURN"),
		require = 0
	)
	private static void instantnbt$loadReturn(CompoundTag tag, NonNullList<ItemStack> list, net.minecraft.core.HolderLookup.Provider provider, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		ContainerNbtHooks.afterLoadAllItems(tag);
	}
	*///?}
}
