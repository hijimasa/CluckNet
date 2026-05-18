package com.hijimasa.clucknet.item;

import com.hijimasa.clucknet.CluckNet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CluckNet.MOD_ID);

    public static final RegistryObject<CreativeModeTab> CLUCKNET_TAB =
            CREATIVE_TABS.register("clucknet", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.clucknet"))
                    .icon(() -> new ItemStack(ModItems.SENDER_BLOCK_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SENDER_BLOCK_ITEM.get());
                        output.accept(ModItems.RECEIVER_BLOCK_ITEM.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        CREATIVE_TABS.register(bus);
    }
}
