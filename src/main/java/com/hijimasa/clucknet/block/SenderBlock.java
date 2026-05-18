package com.hijimasa.clucknet.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SenderBlock extends Block implements EntityBlock {

    public SenderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SenderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        if (type != ModBlockEntities.SENDER_BLOCK_ENTITY.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((SenderBlockEntity) be).serverTick();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        boolean nowPowered = level.hasNeighborSignal(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SenderBlockEntity sender) {
            sender.onPowerStateChanged(nowPowered);
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SenderBlockEntity sender && player instanceof ServerPlayer sp) {
            sp.openMenu(sender, (FriendlyByteBuf buf) -> {
                buf.writeBlockPos(pos);
                BlockPos dest = sender.getDestination();
                buf.writeBoolean(dest != null);
                if (dest != null) {
                    buf.writeBlockPos(dest);
                }
            });
        }
        return InteractionResult.CONSUME;
    }
}
