package com.github.misosouptgit.instantnbt.mixin;

import com.github.misosouptgit.instantnbt.ownership.ChunkNbtHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tracks chunk NBT on the serializer boundary (Project Plan Runtime Core surface).
 */
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
	@Inject(method = "write", at = @At("RETURN"), require = 0)
	private static void instantnbt$write(CallbackInfoReturnable<CompoundTag> cir) {
		ChunkNbtHooks.onSaved(cir.getReturnValue());
	}

	@ModifyVariable(method = "read", at = @At("HEAD"), argsOnly = true, require = 0)
	private static CompoundTag instantnbt$read(CompoundTag tag) {
		ChunkNbtHooks.onLoaded(tag);
		return tag;
	}
}
