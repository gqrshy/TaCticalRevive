package com.tacticalrevive.network.packet;

import com.tacticalrevive.TacticalRevive;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Packet sent from server to client to sync bleeding state.
 */
public record BleedingUpdatePacket(
        UUID playerUuid,
        boolean isBleeding,
        int timeLeft,
        float reviveProgress
) implements CustomPacketPayload {

    public static final ResourceLocation ID = TacticalRevive.id("bleeding_update");
    public static final Type<BleedingUpdatePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, BleedingUpdatePacket> CODEC =
            StreamCodec.of(BleedingUpdatePacket::write, BleedingUpdatePacket::read);

    private static BleedingUpdatePacket read(FriendlyByteBuf buf) {
        return new BleedingUpdatePacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readFloat()
        );
    }

    private static void write(FriendlyByteBuf buf, BleedingUpdatePacket packet) {
        buf.writeUUID(packet.playerUuid);
        buf.writeBoolean(packet.isBleeding);
        buf.writeVarInt(packet.timeLeft);
        buf.writeFloat(packet.reviveProgress);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
