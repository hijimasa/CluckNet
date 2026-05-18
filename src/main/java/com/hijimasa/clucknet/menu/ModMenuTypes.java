package com.hijimasa.clucknet.menu;

import com.hijimasa.clucknet.CluckNet;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CluckNet.MOD_ID);

    public static final RegistryObject<MenuType<SenderMenu>> SENDER_MENU =
            MENUS.register("sender_menu", () -> IForgeMenuType.create(SenderMenu::new));

    public static final RegistryObject<MenuType<ReceiverMenu>> RECEIVER_MENU =
            MENUS.register("receiver_menu", () -> IForgeMenuType.create(ReceiverMenu::new));

    private ModMenuTypes() {}

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
