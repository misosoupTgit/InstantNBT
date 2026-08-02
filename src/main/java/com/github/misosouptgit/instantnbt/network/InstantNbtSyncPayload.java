package com.github.misosouptgit.instantnbt.network;

//? if >=1.20.5 {
/*import com.github.misosouptgit.instantnbt.InstantNBT;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InstantNbtSyncPayload(boolean full, long baseGeneration, byte[] data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<InstantNbtSyncPayload> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(InstantNBT.MOD_ID, "sync"));

	public static final StreamCodec<FriendlyByteBuf, InstantNbtSyncPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, InstantNbtSyncPayload::full,
		ByteBufCodecs.VAR_LONG, InstantNbtSyncPayload::baseGeneration,
		ByteBufCodecs.BYTE_ARRAY, InstantNbtSyncPayload::data,
		InstantNbtSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
*///?} else {
/**
 * Stub kept so package layout is stable on pre-CustomPayload targets.
 */
final class InstantNbtSyncPayload {
	private InstantNbtSyncPayload() {}
}
//?}
