package p455w0rd.danknull.network;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import p455w0rd.danknull.DankNull;
import p455w0rd.danknull.init.ModEvents;

/**
 * Asks the server to open the /dank/null GUI for the sender.
 *
 * <p>
 * Upstream's payload was a {@code PlayerSlot}, which encoded an inventory category (main / armor / off-hand) plus
 * an index. 1.7.10 has no off-hand and the server never actually read the value - it just re-locates the
 * /dank/null itself - so the payload is reduced to the main-inventory index.
 * </p>
 *
 * @author p455w0rd
 */
public class PacketOpenGui implements IMessage, IMessageHandler<PacketOpenGui, IMessage> {

    /**
     * Mirrors {@code ModGuiHandler.GUIType.DANKNULL.ordinal()}.
     */
    // TODO(gui): swap for ModGuiHandler.GUIType.DANKNULL.ordinal() once ModGuiHandler lands.
    private static final int GUI_ID_DANKNULL = 0;

    private int slot;

    public PacketOpenGui() {}

    public PacketOpenGui(final int slot) {
        this.slot = slot;
    }

    @Override
    public IMessage onMessage(final PacketOpenGui message, final MessageContext ctx) {
        final EntityPlayer player = ctx.getServerHandler().playerEntity;
        // Opening a GUI touches the player's open container - main thread only.
        ModEvents.scheduleServerTask(
            () -> player.openGui(
                DankNull.INSTANCE,
                GUI_ID_DANKNULL,
                player.worldObj,
                (int) Math.floor(player.posX),
                (int) Math.floor(player.posY),
                (int) Math.floor(player.posZ)));
        return null;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        slot = buf.readByte();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(slot);
    }

    public int getSlot() {
        return slot;
    }

}
