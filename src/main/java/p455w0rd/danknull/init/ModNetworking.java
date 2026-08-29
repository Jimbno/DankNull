package p455w0rd.danknull.init;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import p455w0rd.danknull.network.PacketChangeMode;
import p455w0rd.danknull.network.PacketConfigSync;
import p455w0rd.danknull.network.PacketEmptyDock;
import p455w0rd.danknull.network.PacketOpenGui;
import p455w0rd.danknull.network.PacketSetDankNullInDock;
import p455w0rd.danknull.network.PacketUpdateSlot;

/**
 * @author p455w0rd
 */
public class ModNetworking {

    private static int packetId = 0;
    private static SimpleNetworkWrapper INSTANCE = null;

    private static int nextID() {
        return packetId++;
    }

    public static SimpleNetworkWrapper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(ModGlobals.MODID);
        }
        return INSTANCE;
    }

    /**
     * Upstream also carried a {@code PacketMouseWheel}, which it never registered and which only existed for the
     * ItemScroller/MouseTweaks integrations. It is not ported.
     */
    public static void registerMessages() {
        getInstance().registerMessage(PacketChangeMode.Handler.class, PacketChangeMode.class, nextID(), Side.SERVER);
        getInstance().registerMessage(PacketConfigSync.Handler.class, PacketConfigSync.class, nextID(), Side.CLIENT);
        getInstance().registerMessage(PacketEmptyDock.Handler.class, PacketEmptyDock.class, nextID(), Side.CLIENT);
        getInstance().registerMessage(
            PacketSetDankNullInDock.Handler.class,
            PacketSetDankNullInDock.class,
            nextID(),
            Side.CLIENT);
        getInstance().registerMessage(PacketOpenGui.class, PacketOpenGui.class, nextID(), Side.SERVER);
        getInstance().registerMessage(PacketUpdateSlot.Handler.class, PacketUpdateSlot.class, nextID(), Side.CLIENT);
    }

}
