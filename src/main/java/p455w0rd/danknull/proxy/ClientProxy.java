package p455w0rd.danknull.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import p455w0rd.danknull.client.render.ModRenderers;
import p455w0rd.danknull.init.ModEvents;
import p455w0rd.danknull.init.ModKeyBindings;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(final FMLPreInitializationEvent event) {
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
