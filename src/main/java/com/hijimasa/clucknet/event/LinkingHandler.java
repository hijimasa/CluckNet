package com.hijimasa.clucknet.event;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.block.ReceiverBlock;
import com.hijimasa.clucknet.block.SenderBlock;
import com.hijimasa.clucknet.block.SenderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sneak-right-click pairing flow (Phase 4c):
 * 1) Sneak-right-click a Receiver → token captured for the player.
 * 2) Sneak-right-click a Sender → token consumed, sender's destination set to receiver pos.
 *
 * Tokens are server-side only and transient (lost on logout / server restart).
 */
@Mod.EventBusSubscriber(modid = CluckNet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LinkingHandler {
    private static final Map<UUID, BlockPos> CAPTURED = new ConcurrentHashMap<>();

    private LinkingHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof ReceiverBlock) {
            BlockPos immutable = pos.immutable();
            CAPTURED.put(player.getUUID(), immutable);
            player.sendSystemMessage(Component.translatable("clucknet.link.captured",
                    immutable.toShortString()).withStyle(ChatFormatting.AQUA));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (block instanceof SenderBlock) {
            BlockPos receiverPos = CAPTURED.remove(player.getUUID());
            if (receiverPos == null) {
                player.sendSystemMessage(Component.translatable("clucknet.link.no_token")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SenderBlockEntity sender) {
                    sender.setDestination(receiverPos);
                    player.sendSystemMessage(Component.translatable("clucknet.link.completed",
                                    pos.toShortString(), receiverPos.toShortString())
                            .withStyle(ChatFormatting.GREEN));
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CAPTURED.remove(event.getEntity().getUUID());
    }
}
