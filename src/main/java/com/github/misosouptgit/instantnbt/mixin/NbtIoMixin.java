package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.serializer.NbtIoHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.DataInput;
import java.io.DataOutput;

/**
 * Replaces vanilla NbtIo binary encode/decode with FastBinary when enabled.
 * Compressed APIs still use GZIP; only the inner binary codec is swapped.
 */
@Mixin(NbtIo.class)
public abstract class NbtIoMixin {
	@Inject(
		method = "write(Lnet/minecraft/nbt/CompoundTag;Ljava/io/DataOutput;)V",
		at = @At("HEAD"),
		cancellable = true,
		require = 0
	)
	private static void instantnbt$write(CompoundTag tag, DataOutput output, CallbackInfo ci) {
		if (NbtIoHooks.tryWrite(tag, output)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "read(Ljava/io/DataInput;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("HEAD"),
		cancellable = true,
		require = 0
	)
	private static void instantnbt$read(DataInput input, CallbackInfoReturnable<CompoundTag> cir) {
		CompoundTag fast = NbtIoHooks.tryRead(input);
		if (fast != null) {
			cir.setReturnValue(fast);
		}
	}

	//? if >=1.20.2 {
	/*@Inject(
		method = "read(Ljava/io/DataInput;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;",
		at = @At("HEAD"),
		cancellable = true,
		require = 0
	)
	private static void instantnbt$readAccounted(DataInput input, net.minecraft.nbt.NbtAccounter accounter, CallbackInfoReturnable<CompoundTag> cir) {
		CompoundTag fast = NbtIoHooks.tryRead(input);
		if (fast != null) {
			cir.setReturnValue(fast);
		}
	}
	*///?}
}
