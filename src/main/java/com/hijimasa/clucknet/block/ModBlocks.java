package com.hijimasa.clucknet.block;

import com.hijimasa.clucknet.CluckNet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CluckNet.MOD_ID);

    public static final RegistryObject<SenderBlock> SENDER_BLOCK =
            BLOCKS.register("sender_block", () ->
                    new SenderBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.WOOD)));

    public static final RegistryObject<ReceiverBlock> RECEIVER_BLOCK =
            BLOCKS.register("receiver_block", () ->
                    new ReceiverBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLUE)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.WOOD)));

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
