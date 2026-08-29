package p455w0rd.danknull.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import p455w0rd.danknull.container.ContainerDankNull;
import p455w0rd.danknull.init.ModEvents;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * @author BrockWS
 */
public class PacketUpdateSlot implements IMessage {

    private int slot;
    private ItemStack stack;

    public PacketUpdateSlot() {}

    public PacketUpdateSlot(final int slot, final ItemStack stack) {
        this.slot = slot;
        this.stack = stack;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        slot = buf.readInt();
        stack = ByteBufUtils.readItemStack(buf);
        // The size is always on the wire, even for an empty stack, so it has to be consumed unconditionally.
        final int count = buf.readInt();
        if (stack != null) {
            stack.stackSize = count;
        }
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(slot);
        // Vanilla's stack encoding stores the size in a single byte, so a /dank/null stack would truncate. Send a
        // size-1 copy and carry the real count alongside it.
        final ItemStack tempStack = DankNullStackUtils.copyWithSize(stack, 1);
        ByteBufUtils.writeItemStack(buf, tempStack);
        buf.writeInt(DankNullStackUtils.getCount(stack));
    }

    public static class Handler implements IMessageHandler<PacketUpdateSlot, IMessage> {

        @Override
        public IMessage onMessage(final PacketUpdateSlot message, final MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            // Writes into the open container - deferred off the netty thread.
            ModEvents.scheduleClientTask(() -> {
                final EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
                // The /dank/null screen can close between the server sending this and the client tick draining
                // it. Writing into whatever container is open by then would ghost-edit an unrelated GUI, or crash
                // outright on an index past its slot count.
                if (player == null || !(player.openContainer instanceof ContainerDankNull)) {
                    return;
                }
                if (message.slot < 0 || message.slot >= player.openContainer.inventorySlots.size()) {
                    return;
                }
                player.openContainer.putStackInSlot(message.slot, message.stack);
            });
            return null;
        }
    }
}
