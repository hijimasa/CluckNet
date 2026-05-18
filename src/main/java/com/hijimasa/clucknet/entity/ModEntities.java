package com.hijimasa.clucknet.entity;

import com.hijimasa.clucknet.CluckNet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = CluckNet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CluckNet.MOD_ID);

    public static final RegistryObject<EntityType<PacketChicken>> PACKET_CHICKEN =
            ENTITY_TYPES.register("packet_chicken", () ->
                    EntityType.Builder.<PacketChicken>of(PacketChicken::new, MobCategory.CREATURE)
                            .sized(0.4F, 0.7F)
                            .clientTrackingRange(10)
                            .build("packet_chicken"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(PACKET_CHICKEN.get(), Chicken.createAttributes().build());
    }
}
