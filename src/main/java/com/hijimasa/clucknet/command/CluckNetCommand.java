package com.hijimasa.clucknet.command;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.block.SenderBlockEntity;
import com.hijimasa.clucknet.entity.ModEntities;
import com.hijimasa.clucknet.entity.PacketChicken;
import com.hijimasa.clucknet.stats.CluckNetStats;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
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
                                        .executes(CluckNetCommand::executeSend)))
                        .then(Commands.literal("link")
                                .then(Commands.argument("sender", BlockPosArgument.blockPos())
                                        .then(Commands.argument("destination", BlockPosArgument.blockPos())
                                                .executes(CluckNetCommand::executeLink))))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("sender", BlockPosArgument.blockPos())
                                        .executes(CluckNetCommand::executeFire)))
                        .then(Commands.literal("message")
                                .then(Commands.argument("sender", BlockPosArgument.blockPos())
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(CluckNetCommand::executeMessage))))
                        .then(Commands.literal("stats")
                                .executes(CluckNetCommand::executeStats)));
    }

    private static int executeStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        CluckNetStats s = CluckNetStats.INSTANCE;
        src.sendSuccess(() -> Component.literal(String.format(
                "[CluckNet stats] sent: data=%d parity=%d retransmit=%d  recv: chunks=%d msgs=%d parity-fix=%d  loss: timeouts=%d nacks=%d chickens=%d",
                s.dataChickensFired(), s.parityChickensFired(), s.retransmitted(),
                s.chunksReceived(), s.messagesReassembled(), s.messagesRecoveredByParity(),
                s.messagesTimedOut(), s.nacksSent(), s.chickensLost())),
                false);
        return 1;
    }

    private static int executeMessage(CommandContext<CommandSourceStack> ctx) {
        BlockPos senderPos = BlockPosArgument.getBlockPos(ctx, "sender");
        String text = StringArgumentType.getString(ctx, "text");
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();

        BlockEntity be = level.getBlockEntity(senderPos);
        if (!(be instanceof SenderBlockEntity sender)) {
            src.sendFailure(Component.literal("No SenderBlock at " + senderPos.toShortString()));
            return 0;
        }
        sender.setPendingMessage(text);
        int bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int chunks = Math.max(1, (bytes + SenderBlockEntity.MTU_BYTES - 1) / SenderBlockEntity.MTU_BYTES);
        src.sendSuccess(
                () -> Component.literal("Sender " + senderPos.toShortString() + " message buffered ("
                        + bytes + " bytes → " + chunks + " chunks)"),
                true);
        return chunks;
    }

    private static int executeSend(CommandContext<CommandSourceStack> ctx) {
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

        src.sendSuccess(
                () -> Component.literal("Released PacketChicken toward " + dest.toShortString()),
                true);
        return 1;
    }

    private static int executeLink(CommandContext<CommandSourceStack> ctx) {
        BlockPos senderPos = BlockPosArgument.getBlockPos(ctx, "sender");
        BlockPos destPos = BlockPosArgument.getBlockPos(ctx, "destination");
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();

        BlockEntity be = level.getBlockEntity(senderPos);
        if (!(be instanceof SenderBlockEntity sender)) {
            src.sendFailure(Component.literal("No SenderBlock at " + senderPos.toShortString()));
            return 0;
        }
        sender.setDestination(destPos);
        src.sendSuccess(
                () -> Component.literal("Linked " + senderPos.toShortString() + " → " + destPos.toShortString()),
                true);
        return 1;
    }

    private static int executeFire(CommandContext<CommandSourceStack> ctx) {
        BlockPos senderPos = BlockPosArgument.getBlockPos(ctx, "sender");
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();

        BlockEntity be = level.getBlockEntity(senderPos);
        if (!(be instanceof SenderBlockEntity sender)) {
            src.sendFailure(Component.literal("No SenderBlock at " + senderPos.toShortString()));
            return 0;
        }
        int released = sender.firePackets();
        if (released == 0) {
            src.sendFailure(Component.literal("Sender at " + senderPos.toShortString() + " could not fire (no destination?)"));
            return 0;
        }
        final int releasedFinal = released;
        src.sendSuccess(
                () -> Component.literal("Fired " + releasedFinal + " packet(s) from " + senderPos.toShortString()),
                true);
        return releasedFinal;
    }
}
