package com.hijimasa.clucknet.client;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.client.screen.ReceiverScreen;
import com.hijimasa.clucknet.client.screen.SenderScreen;
import com.hijimasa.clucknet.menu.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CluckNet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.SENDER_MENU.get(), SenderScreen::new);
            MenuScreens.register(ModMenuTypes.RECEIVER_MENU.get(), ReceiverScreen::new);
        });
    }
}
