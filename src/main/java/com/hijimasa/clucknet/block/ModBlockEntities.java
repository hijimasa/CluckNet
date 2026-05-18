package com.hijimasa.clucknet.block;

import com.hijimasa.clucknet.CluckNet;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CluckNet.MOD_ID);

    public static final RegistryObject<BlockEntityType<SenderBlockEntity>> SENDER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("sender_block", () ->
                    BlockEntityType.Builder
                            .of(SenderBlockEntity::new, ModBlocks.SENDER_BLOCK.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<ReceiverBlockEntity>> RECEIVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("receiver_block", () ->
                    BlockEntityType.Builder
                            .of(ReceiverBlockEntity::new, ModBlocks.RECEIVER_BLOCK.get())
                            .build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
