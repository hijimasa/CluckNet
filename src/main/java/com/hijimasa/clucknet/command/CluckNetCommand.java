package com.hijimasa.clucknet.command;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.entity.ModEntities;
import com.hijimasa.clucknet.entity.PacketChicken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CluckNet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CluckNetCommand {
    private CluckNetCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("clucknet")
                        .then(Commands.literal("send")
                                .then(Commands.argument("destination", BlockPosArgument.blockPos())
                                        .executes(CluckNetCommand::executeSend))));
    }

    private static int executeSend(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        BlockPos dest = BlockPosArgument.getBlockPos(ctx, "destination");
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        Vec3 spawnPos = src.getPosition();

        PacketChicken chicken = ModEntities.PACKET_CHICKEN.get().create(level);
        if (chicken == null) {
            src.sendFailure(Component.literal("Failed to create PacketChicken"));
            return 0;
        }
        chicken.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, src.getRotation().y, 0.0F);
        chicken.setDestination(dest);
        level.addFreshEntity(chicken);

        BlockPos finalDest = dest;
        src.sendSuccess(
                () -> Component.literal("Released PacketChicken toward " + finalDest.toShortString()),
                true);
        return 1;
    }
}
