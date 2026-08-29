package p455w0rd.danknull.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.init.ModEvents;

/**
 * @author p455w0rd
 */
public class PacketSetDankNullInDock implements IMessage {

    private ItemStack dankNull;
    private int x;
    private int y;
    private int z;

    public PacketSetDankNullInDock() {}

    public PacketSetDankNullInDock(final TileEntity dockingStation, final ItemStack dankNull) {
        x = dockingStation.xCoord;
        y = dockingStation.yCoord;
        z = dockingStation.zCoord;
        this.dankNull = dankNull;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        dankNull = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeItemStack(buf, dankNull);
    }

    public static class Handler implements IMessageHandler<PacketSetDankNullInDock, IMessage> {

        @Override
        public IMessage onMessage(final PacketSetDankNullInDock message, final MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            // Writes to a tile entity - deferred off the netty thread.
            ModEvents.scheduleClientTask(() -> {
                // Upstream went through its proxy's getWorld(); the backport's proxy has no such hook, and this
                // handler is client-only anyway.
                final World world = Minecraft.getMinecraft().theWorld;
                if (world == null) {
                    return;
                }
                final TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileDankNullDock) {
                    ((TileDankNullDock) te).setDankNull(message.dankNull);
                }
            });
            return null;
        }
    }

}
