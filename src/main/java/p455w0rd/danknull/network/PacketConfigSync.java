package p455w0rd.danknull.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import p455w0rd.danknull.init.ModConfig.Options;
import p455w0rd.danknull.init.ModEvents;

/**
 * Pushes the server-authoritative half of the config down to a joining client.
 *
 * <p>
 * Upstream serialised a {@code WeakHashMapSerializable<String, Object>} with GZIP + Java object serialisation.
 * The payload is only four strings and a boolean, so this writes them out explicitly instead: it avoids
 * deserialising arbitrary Java objects straight off the wire, and removes the need for the
 * {@code NonNullListSerializable}/{@code WeakHashMapSerializable} helper classes entirely.
 * </p>
 *
 * @author p455w0rd
 */
public class PacketConfigSync implements IMessage {

    private String creativeBlacklist;
    private String creativeWhitelist;
    private String oreBlacklist;
    private String oreWhitelist;
    private boolean disableOreDictMode;

    public PacketConfigSync() {}

    public PacketConfigSync(final String creativeBlacklist, final String creativeWhitelist, final String oreBlacklist,
        final String oreWhitelist, final boolean disableOreDictMode) {
        this.creativeBlacklist = creativeBlacklist == null ? "" : creativeBlacklist;
        this.creativeWhitelist = creativeWhitelist == null ? "" : creativeWhitelist;
        this.oreBlacklist = oreBlacklist == null ? "" : oreBlacklist;
        this.oreWhitelist = oreWhitelist == null ? "" : oreWhitelist;
        this.disableOreDictMode = disableOreDictMode;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        creativeBlacklist = ByteBufUtils.readUTF8String(buf);
        creativeWhitelist = ByteBufUtils.readUTF8String(buf);
        oreBlacklist = ByteBufUtils.readUTF8String(buf);
        oreWhitelist = ByteBufUtils.readUTF8String(buf);
        disableOreDictMode = buf.readBoolean();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, creativeBlacklist);
        ByteBufUtils.writeUTF8String(buf, creativeWhitelist);
        ByteBufUtils.writeUTF8String(buf, oreBlacklist);
        ByteBufUtils.writeUTF8String(buf, oreWhitelist);
        buf.writeBoolean(disableOreDictMode);
    }

    public static class Handler implements IMessageHandler<PacketConfigSync, IMessage> {

        @Override
        public IMessage onMessage(final PacketConfigSync message, final MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            // Deferred to the client tick so the option fields are never swapped out from under a render or tick
            // that is midway through reading them.
            ModEvents.scheduleClientTask(() -> {
                Options.creativeBlacklist = message.creativeBlacklist;
                Options.creativeWhitelist = message.creativeWhitelist;
                Options.oreBlacklist = message.oreBlacklist;
                Options.oreWhitelist = message.oreWhitelist;
                Options.disableOreDictMode = message.disableOreDictMode;
                // The parsed ore/item views are cached; without this the client keeps using its own local lists.
                Options.invalidateParsedLists();
            });
            return null;
        }
    }
}
