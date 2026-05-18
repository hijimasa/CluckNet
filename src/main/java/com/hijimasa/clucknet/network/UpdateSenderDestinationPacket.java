package com.hijimasa.clucknet.network;

import com.hijimasa.clucknet.block.SenderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.network.CustomPayloadEvent;
import org.jetbrains.annotations.Nullable;

public class UpdateSenderDestinationPacket {
    private final BlockPos senderPos;
    @Nullable
    private final BlockPos destination;

    public UpdateSenderDestinationPacket(BlockPos senderPos, @Nullable BlockPos destination) {
        this.senderPos = senderPos;
        this.destination = destination;
    }

    public UpdateSenderDestinationPacket(FriendlyByteBuf buf) {
        this.senderPos = buf.readBlockPos();
        this.destination = buf.readBoolean() ? buf.readBlockPos() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(senderPos);
        buf.writeBoolean(destination != null);
        if (destination != null) {
            buf.writeBlockPos(destination);
        }
    }

    public void handle(CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (!player.level().isLoaded(senderPos)) {
                return;
            }
            if (player.distanceToSqr(senderPos.getX() + 0.5, senderPos.getY() + 0.5, senderPos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(senderPos);
            if (be instanceof SenderBlockEntity sender) {
                sender.setDestination(destination);
            }
        });
        ctx.setPacketHandled(true);
    }
}
