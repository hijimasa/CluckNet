package com.hijimasa.clucknet.item;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CluckNet.MOD_ID);

    public static final RegistryObject<Item> SENDER_BLOCK_ITEM =
            ITEMS.register("sender_block", () ->
                    new BlockItem(ModBlocks.SENDER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> RECEIVER_BLOCK_ITEM =
            ITEMS.register("receiver_block", () ->
                    new BlockItem(ModBlocks.RECEIVER_BLOCK.get(), new Item.Properties()));

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
