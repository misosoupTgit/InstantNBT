package com.github.misosouptgit.instantnbt.network;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.ownership.OwnedTag;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Real Architectury/Netty packet bridge for InstantNBT sync payloads (Project Plan 11.3).
 */
public final class SyncPackets {
	public static final ResourceLocation SYNC_ID = id("sync");

	private static boolean registered;

	private SyncPackets() {}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		//? if <1.20.5 {
		NetworkManager.registerReceiver(NetworkManager.Side.S2C, SYNC_ID, SyncPackets::handleClient);
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, SYNC_ID, SyncPackets::handleServer);
		InstantNBT.LOGGER.info("Registered InstantNBT sync packets ({})", SYNC_ID);
		//?} else {
		/*registerModern();
		*///?}
	}

	//? if >=1.20.5 {
	/*private static void registerModern() {
		var type = InstantNbtSyncPayload.TYPE;
		var codec = InstantNbtSyncPayload.STREAM_CODEC;
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, type, codec, (payload, context) ->
			context.queue(() -> applyIncoming(payload.data(), payload.baseGeneration(), payload.full(), context.getPlayer())));
		EnvExecutor.runInEnv(Env.CLIENT, () -> () ->
			NetworkManager.registerReceiver(NetworkManager.Side.S2C, type, codec, (payload, context) ->
				context.queue(() -> applyIncoming(payload.data(), payload.baseGeneration(), payload.full(), context.getPlayer()))));
		if (dev.architectury.platform.Platform.getEnvironment() != Env.CLIENT) {
			NetworkManager.registerS2CPayloadType(type, codec);
		}
		InstantNBT.LOGGER.info("Registered InstantNBT modern sync payloads ({})", SYNC_ID);
	}
	*///?}

	public static void sendToPlayer(ServerPlayer player, byte[] payload, long baseGeneration, boolean full) {
		if (player == null || payload == null) {
			return;
		}
		//? if <1.20.5 {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		write(buf, payload, baseGeneration, full);
		NetworkManager.sendToPlayer(player, SYNC_ID, buf);
		InstantNbtRuntime.get().network().transport().offerPacket(payload);
		//?} else {
		/*NetworkManager.sendToPlayer(player, new InstantNbtSyncPayload(full, baseGeneration, payload));
		InstantNbtRuntime.get().network().transport().offerPacket(payload);
		*///?}
	}

	public static void sendToServer(byte[] payload, long baseGeneration, boolean full) {
		if (payload == null) {
			return;
		}
		//? if <1.20.5 {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		write(buf, payload, baseGeneration, full);
		NetworkManager.sendToServer(SYNC_ID, buf);
		InstantNbtRuntime.get().network().transport().offerPacket(payload);
		//?} else {
		/*NetworkManager.sendToServer(new InstantNbtSyncPayload(full, baseGeneration, payload));
		InstantNbtRuntime.get().network().transport().offerPacket(payload);
		*///?}
	}

	private static void write(FriendlyByteBuf buf, byte[] payload, long baseGeneration, boolean full) {
		buf.writeBoolean(full);
		buf.writeLong(baseGeneration);
		buf.writeByteArray(payload);
	}

	private static void handleClient(FriendlyByteBuf buf, NetworkManager.PacketContext context) {
		boolean full = buf.readBoolean();
		long baseGeneration = buf.readLong();
		byte[] payload = buf.readByteArray();
		context.queue(() -> applyIncoming(payload, baseGeneration, full, context.getPlayer()));
	}

	private static void handleServer(FriendlyByteBuf buf, NetworkManager.PacketContext context) {
		boolean full = buf.readBoolean();
		long baseGeneration = buf.readLong();
		byte[] payload = buf.readByteArray();
		context.queue(() -> applyIncoming(payload, baseGeneration, full, context.getPlayer()));
	}

	private static void applyIncoming(byte[] payload, long baseGeneration, boolean full, Player player) {
		try {
			InstantNbtRuntime runtime = InstantNbtRuntime.get();
			if (!runtime.optimizationsActive()) {
				return;
			}
			OwnedTag applied = runtime.network().apply(payload, full ? -1L : baseGeneration);
			runtime.tracker().track(applied);
			runtime.network().rememberApplied(player, applied);
			runtime.network().transport().offerPacket(payload);
			InstantNBT.LOGGER.debug(
				"Applied InstantNBT {} packet ({} bytes, gen={}, player={})",
				full ? "FULL" : "DELTA",
				payload.length,
				baseGeneration,
				player == null ? "?" : player.getScoreboardName()
			);
		} catch (ResyncRequiredException ex) {
			InstantNBT.LOGGER.warn("InstantNBT delta apply failed; requesting full resync: {}", ex.toString());
			InstantNbtRuntime.get().network().requestFullResync(player);
		} catch (Exception ex) {
			InstantNBT.LOGGER.warn("Failed to apply InstantNBT sync packet: {}", ex.toString());
			InstantNbtRuntime.get().network().requestFullResync(player);
		}
	}

	private static ResourceLocation id(String path) {
		//? if >=1.20.6 {
		/*return ResourceLocation.fromNamespaceAndPath(InstantNBT.MOD_ID, path);
		*///?} else {
		return new ResourceLocation(InstantNBT.MOD_ID, path);
		//?}
	}
}
