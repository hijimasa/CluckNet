package com.hijimasa.clucknet.client;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.entity.ModEntities;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CluckNet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers {
    private ModEntityRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PACKET_CHICKEN.get(), ChickenRenderer::new);
    }
}
