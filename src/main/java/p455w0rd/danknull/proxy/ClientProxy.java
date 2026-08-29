package p455w0rd.danknull.proxy;

import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import p455w0rd.danknull.client.render.ModRenderers;
import p455w0rd.danknull.init.ModEvents;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.init.ModKeyBindings;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(final FMLPreInitializationEvent event) {
        // Opts this mod's resource pack into GTNHLib's JSON model/blockstate pipeline, which is what lets the
        // 1.12-era blockstate + model JSONs drive the dock's rendering on 1.7.10 under Angelica. Must happen before
        // resources are first loaded, hence preInit.
        ModelRegistry.registerModid(ModGlobals.MODID);
        super.preInit(event);
        ModEvents.Client.register();
        ModKeyBindings.register();
    }

    @Override
    public void init(final FMLInitializationEvent event) {
        super.init(event);
        ModRenderers.register();
    }
}
