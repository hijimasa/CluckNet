package com.hijimasa.clucknet;

import com.hijimasa.clucknet.block.ModBlockEntities;
import com.hijimasa.clucknet.block.ModBlocks;
import com.hijimasa.clucknet.entity.ModEntities;
import com.hijimasa.clucknet.item.ModCreativeTabs;
import com.hijimasa.clucknet.item.ModItems;
import com.hijimasa.clucknet.menu.ModMenuTypes;
import com.hijimasa.clucknet.network.CluckNetNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CluckNet.MOD_ID)
public class CluckNet {
    public static final String MOD_ID = "clucknet";
    public static final Logger LOGGER = LoggerFactory.getLogger(CluckNet.class);

    public CluckNet() {
        LOGGER.info("Initializing {} mod", MOD_ID);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);
        modBus.addListener(this::clientSetup);

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModCreativeTabs.register(modBus);
        ModEntities.register(modBus);
        ModMenuTypes.register(modBus);

        CluckNetNetwork.register();

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("{} mod initialized", MOD_ID);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup", MOD_ID);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("{} client setup", MOD_ID);
    }
}
