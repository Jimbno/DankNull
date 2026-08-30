package p455w0rd.danknull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.proxy.CommonProxy;

@Mod(
    modid = ModGlobals.MODID,
    name = ModGlobals.NAME,
    version = Tags.VERSION,
    dependencies = "required-after:gtnhlib@[0.11.24,);after:angelica;after:gregtech",
    acceptedMinecraftVersions = "[1.7.10]",
    guiFactory = "p455w0rd.danknull.init.ModGuiFactory")
public class DankNull {

    public static final Logger LOGGER = LogManager.getLogger(ModGlobals.MODID);

    @Mod.Instance(ModGlobals.MODID)
    public static DankNull INSTANCE;

    @SidedProxy(clientSide = "p455w0rd.danknull.proxy.ClientProxy", serverSide = "p455w0rd.danknull.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
