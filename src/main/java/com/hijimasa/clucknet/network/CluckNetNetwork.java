package com.hijimasa.clucknet.network;

import com.hijimasa.clucknet.CluckNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class CluckNetNetwork {
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(new ResourceLocation(CluckNet.MOD_ID, "main"))
            .serverAcceptedVersions((status, version) -> true)
            .clientAcceptedVersions((status, version) -> true)
            .networkProtocolVersion(1)
            .simpleChannel();

    private CluckNetNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(UpdateSenderDestinationPacket.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateSenderDestinationPacket::encode)
                .decoder(UpdateSenderDestinationPacket::new)
                .consumerMainThread(UpdateSenderDestinationPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }
}
