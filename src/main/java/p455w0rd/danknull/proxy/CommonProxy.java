package p455w0rd.danknull.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import p455w0rd.danknull.init.ModBlocks;
import p455w0rd.danknull.init.ModConfig;
import p455w0rd.danknull.init.ModEvents;
import p455w0rd.danknull.init.ModGuiHandler;
import p455w0rd.danknull.init.ModItems;
import p455w0rd.danknull.init.ModNetworking;
import p455w0rd.danknull.init.ModRecipes;

public class CommonProxy {

    public void preInit(final FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        ModBlocks.register();
        ModItems.register();
        ModNetworking.registerMessages();
        ModEvents.register();
    }

    public void init(final FMLInitializationEvent event) {
        // Recipes run in init, not preInit: the dock's Item is created by GameRegistry.registerBlock, so
        // ModItems.resolveDockItem() can only find it once block registration has completed.
        ModRecipes.register();
        ModGuiHandler.register();
    }

    public void postInit(final FMLPostInitializationEvent event) {}
}
